/*
 * NewsfeedService - Handles newsfeed posts (text + images).
 *
 * Images are stored in the LOCAL PostgreSQL database (BYTEA) - the same
 * approach as profile/cover photos (no Cloudinary, no external service).
 * Each image streams via GET /api/feed/images/{id} so the mobile <Image>
 * can load it without a JWT (permitAll in SecurityConfig).
 *
 * Rules:
 *   - A post needs text OR at least one image (not neither).
 *   - Max 5 images per post, each max 5MB (matches multipart limits).
 *   - Only JPG, PNG, WEBP or GIF (same rule as profile photos).
 *   - Only the author can delete their post (403 otherwise).
 */
package com.smacian.backend.service

import com.smacian.backend.dto.response.CommentResponse
import com.smacian.backend.dto.response.LikeResponse
import com.smacian.backend.dto.response.PagedResponse
import com.smacian.backend.dto.response.PostImageResponse
import com.smacian.backend.dto.response.PostResponse
import com.smacian.backend.entity.Comment
import com.smacian.backend.entity.Post
import com.smacian.backend.entity.PostImage
import com.smacian.backend.entity.PostLike
import com.smacian.backend.entity.enums.NotificationType
import com.smacian.backend.exception.BadRequestException
import com.smacian.backend.exception.ForbiddenException
import com.smacian.backend.exception.ResourceNotFoundException
import com.smacian.backend.repository.CommentRepository
import com.smacian.backend.repository.PostImageRepository
import com.smacian.backend.repository.PostLikeRepository
import com.smacian.backend.repository.PostRepository
import com.smacian.backend.repository.UserRepository
import com.smacian.backend.util.ImageProcessing
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@Service
class NewsfeedService(
    private val postRepository: PostRepository,
    private val postImageRepository: PostImageRepository,
    private val postLikeRepository: PostLikeRepository,
    private val commentRepository: CommentRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService,
    private val pushService: PushService,
    transactionManager: PlatformTransactionManager
) {

    private val log = LoggerFactory.getLogger(NewsfeedService::class.java)

    // Runs the like INSERT in its own transaction (see likePost): on a lost
    // race only the inner tx rolls back and the outer one stays usable.
    private val newTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    companion object {
        const val MAX_IMAGES_PER_POST = 5
        // Pre-processing ORIGINAL size cap per file (phone-camera JPEGs run
        // 3-10MB). Must fit spring.servlet.multipart.max-file-size - the
        // STORED bytes are smaller (downscaled + recompressed, see below).
        const val MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024
        const val MAX_CONTENT_LENGTH = 5000
        const val MAX_COMMENT_LENGTH = 2000
        private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    }

    // ====================================================================
    // 1. CREATE POST (text and/or up to 5 images)
    // ====================================================================
    @Transactional
    fun createPost(authorId: Long, content: String?, images: List<MultipartFile>?): PostResponse {

        val cleanContent = content?.trim()?.takeIf { it.isNotEmpty() }
        val files = images?.filter { !it.isEmpty } ?: emptyList()

        if (cleanContent == null && files.isEmpty()) {
            throw BadRequestException("Post needs text or at least one image")
        }

        if (cleanContent != null && cleanContent.length > MAX_CONTENT_LENGTH) {
            throw BadRequestException("Post text must be $MAX_CONTENT_LENGTH characters or less")
        }

        if (files.size > MAX_IMAGES_PER_POST) {
            throw BadRequestException("A post can have at most $MAX_IMAGES_PER_POST images")
        }

        files.forEach { validateImageFile(it) }

        // Process SEQUENTIALLY (never parallel): one decoded bitmap at a
        // time keeps peak heap far below the 384m cap.
        val processed = files.map { ImageProcessing.process(it) }

        val author = userRepository.findById(authorId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        val post = Post().apply {
            this.author = author
            this.content = cleanContent
        }
        val savedPost = postRepository.save(post)

        // Store each processed image row in upload order (0, 1, 2...).
        processed.forEachIndexed { index, img ->
            val image = PostImage().apply {
                this.post = savedPost
                this.imageData = img.bytes
                this.contentType = img.contentType
                this.width = img.width
                this.height = img.height
                this.sortOrder = index
            }
            postImageRepository.save(image)
        }

        // Push fan-out after commit (external HTTPS never inside the tx):
        // everyone except me gets pinged about the new post.
        val postId = savedPost.id!!
        val pushTitle = "${author.fullName} shared a new post"
        val pushBody = cleanContent?.take(120)
            ?: if (processed.size == 1) "Shared a photo." else "Shared ${processed.size} photos."
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    try {
                        pushService.pushNewPostToAllExcept(authorId, pushTitle, pushBody, postId)
                    } catch (e: Exception) {
                        log.error("Post-commit FCM push failed for new post {}", postId, e)
                    }
                }
            }
        )

        return toPostResponse(savedPost, authorId)
    }

    // ====================================================================
    // 2. NEWSFEED (paged, newest first)
    // ====================================================================
    @Transactional(readOnly = true)
    fun getFeed(viewerId: Long, page: Int, size: Int): PagedResponse<PostResponse> {

        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 50)

        val pageable = PageRequest.of(safePage, safeSize)
        val result = postRepository.findFeedPage(pageable)

        val content = toPostResponseList(result.content, viewerId)

        return PagedResponse(
            content = content,
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    // ====================================================================
    // 2b. MY POSTS (paged, newest first)
    // ====================================================================
    @Transactional(readOnly = true)
    fun getMyPosts(authorId: Long, page: Int, size: Int): PagedResponse<PostResponse> {

        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 50)

        val pageable = PageRequest.of(safePage, safeSize)
        val result = postRepository.findMyPostsPage(authorId, pageable)

        val content = toPostResponseList(result.content, authorId)

        return PagedResponse(
            content = content,
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    // ====================================================================
    // 3. SINGLE POST
    // ====================================================================
    @Transactional(readOnly = true)
    fun getPost(postId: Long, viewerId: Long): PostResponse {
        val post = getPostById(postId)
        return toPostResponse(post, viewerId)
    }

    // ====================================================================
    // 3b. UPDATE POST (author only)
    // ====================================================================
    // Multipart, same parts as create. Semantics per field:
    //   content    null -> keep text; otherwise replace (blank clears, but
    //              the post must still have >= 1 image then).
    //   images     null -> keep images; non-null -> REPLACE the whole set
    //              (validate count + type + size, store in upload order).
    // updatedAt bumps automatically via @PreUpdate.
    @Transactional
    fun updatePost(
        requesterId: Long,
        postId: Long,
        content: String?,
        images: List<MultipartFile>?
    ): PostResponse {

        val post = getPostById(postId)

        if (post.author!!.id != requesterId) {
            throw ForbiddenException("You can only edit your own posts")
        }

        // ---- text ----
        // null = field absent -> keep. Present (even blank) = replace.
        val newContent = if (content != null) content.trim().takeIf { it.isNotEmpty() } else post.content

        // ---- images ----
        val files = images?.filter { !it.isEmpty }  // null = part absent -> keep
        if (files != null) {
            if (files.size > MAX_IMAGES_PER_POST) {
                throw BadRequestException("A post can have at most $MAX_IMAGES_PER_POST images")
            }
            files.forEach { validateImageFile(it) }
            // Same pipeline as create: processed sequentially, stored with
            // dimensions in upload order (replaces the whole set).
            val processed = files.map { ImageProcessing.process(it) }
            postImageRepository.deleteByPostId(postId)
            processed.forEachIndexed { index, img ->
                val image = PostImage().apply {
                    this.post = post
                    this.imageData = img.bytes
                    this.contentType = img.contentType
                    this.width = img.width
                    this.height = img.height
                    this.sortOrder = index
                }
                postImageRepository.save(image)
            }
        }

        // ---- invariant: text OR >= 1 image ----
        val remainingImages = postImageRepository.countByPostId(postId)
        if (newContent == null && remainingImages == 0L) {
            throw BadRequestException("Post needs text or at least one image")
        }
        if (newContent != null && newContent.length > MAX_CONTENT_LENGTH) {
            throw BadRequestException("Post text must be $MAX_CONTENT_LENGTH characters or less")
        }

        post.content = newContent
        return toPostResponse(postRepository.save(post), requesterId)
    }

    // ====================================================================
    // 4. DELETE POST (author only)
    // ====================================================================
    // PostImage rows are removed by ON DELETE CASCADE.
    @Transactional
    fun deletePost(requesterId: Long, postId: Long) {

        val post = getPostById(postId)

        if (post.author!!.id != requesterId) {
            throw ForbiddenException("You can only delete your own posts")
        }

        postRepository.delete(post)
    }

    // ====================================================================
    // 5. IMAGE STREAM GETTER (bytes + content type, or 404)
    // ====================================================================
    // The controller streams these back so the mobile <Image> can render
    // them. Public endpoint - no ownership check (same as profile photos).
    @Transactional(readOnly = true)
    fun getPostImageStream(imageId: Long): Pair<ByteArray, String> {

        val image = postImageRepository.findById(imageId)
            .orElseThrow { ResourceNotFoundException("Post image not found") }

        val data = image.imageData
        val contentType = image.contentType

        if (data == null || contentType == null) {
            throw ResourceNotFoundException("Post image not found")
        }

        return data to contentType
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    // Builds the full PostResponse for ONE post (single-post endpoints).
    // Authors here are lazy-loaded (1 extra SELECT) - acceptable outside lists.
    // Image rows come from the METADATA query (no BYTEA). Reshares embed
    // their ultimate original (depth <= 1, shares never chain).
    private fun toPostResponse(post: Post, viewerId: Long): PostResponse {
        val postId = post.id!!
        val shared = post.sharedFrom?.let { ref ->
            val original = postRepository.findById(ref.id!!)
                .orElseThrow { ResourceNotFoundException("Post not found") }
            toPostResponse(original, viewerId)
        }
        return PostResponse.fromEntity(
            entity = post,
            images = postImageRepository.findMetadataByPostIdOrdered(postId).map { toImageResponse(it) },
            likeCount = postLikeRepository.countByPostId(postId),
            commentCount = commentRepository.countByPostId(postId),
            likedByMe = postLikeRepository.existsByPostIdAndUserId(postId, viewerId),
            shareCount = postRepository.countResharesByPostId(postId),
            sharedPost = shared
        )
    }

    // Maps a WHOLE page with 4 extra queries TOTAL (not per post):
    // 1 image metadata, 1 like counts, 1 comment counts, 1 liked ids
    // (+1 originals fetch ONLY when the page contains reshares).
    // Authors come JOIN FETCHed - no per-post lazy SELECT. No BYTEA loaded.
    private fun toPostResponseList(posts: List<Post>, viewerId: Long): List<PostResponse> {
        if (posts.isEmpty()) return emptyList()
        val ids = posts.map { it.id!! }

        // Reshare sources in this page: proxy `.id` reads cost no SELECT.
        // Originals already on the page are reused (no refetch).
        val wantedOriginalIds = posts.mapNotNull { it.sharedFrom?.id }.distinct()
        val pageById = posts.associateBy { it.id!! }
        val missingOriginalIds = wantedOriginalIds.filter { it !in pageById }
        val fetchedOriginals = if (missingOriginalIds.isNotEmpty()) {
            postRepository.findAllWithAuthorByIds(missingOriginalIds)
        } else emptyList()
        val allById = pageById + fetchedOriginals.associateBy { it.id!! }

        val allIds = ids + missingOriginalIds
        // (postId, id, width, height, sortOrder) ordered by sort_order;
        // groupBy preserves encounter order, so each post's images stay
        // in upload order. Still zero BYTEA.
        val imagesByPost: Map<Long, List<PostImageResponse>> =
            postImageRepository.findMetadataByPostIds(allIds)
                .groupBy(
                    { (it[0] as Long) },
                    { row -> toImageResponse(arrayOf(row[1], row[2], row[3], row[4])) }
                )
        val likeCounts = postLikeRepository.countByPostIds(allIds)
            .associate { (it[0] as Long) to (it[1] as Long) }
        val commentCounts = commentRepository.countByPostIds(allIds)
            .associate { (it[0] as Long) to (it[1] as Long) }
        val likedIds = postLikeRepository.findLikedPostIds(allIds, viewerId).toSet()
        val reshareCounts = postRepository.countResharesByPostIds(allIds)
            .associate { (it[0] as Long) to (it[1] as Long) }

        // Recursive build, depth <= 1 (shares always point at the ultimate
        // original, never at another reshare).
        fun build(post: Post): PostResponse {
            val postId = post.id!!
            val shared = post.sharedFrom?.let { ref -> allById[ref.id]?.let { build(it) } }
            return PostResponse.fromEntity(
                entity = post,
                images = imagesByPost[postId] ?: emptyList(),
                likeCount = likeCounts[postId] ?: 0L,
                commentCount = commentCounts[postId] ?: 0L,
                likedByMe = postId in likedIds,
                shareCount = reshareCounts[postId] ?: 0L,
                sharedPost = shared
            )
        }

        return posts.map { build(it) }
    }

    // (id, width, height, sortOrder) metadata row -> DTO. width/height are
    // nullable (legacy rows, WebP) - the app treats null as "unknown".
    private fun toImageResponse(row: Array<Any>): PostImageResponse {
        val id = row[0] as Long
        return PostImageResponse(
            id = id,
            url = imageStreamUrl(id),
            width = row[1] as Int?,
            height = row[2] as Int?,
            sortOrder = row[3] as Int
        )
    }

    private fun getPostById(postId: Long): Post =
        postRepository.findById(postId)
            .orElseThrow { ResourceNotFoundException("Post not found") }

    // CHEAP pre-checks (allow-list + size) BEFORE the expensive decode in
    // ImageProcessing.process, which re-verifies via magic bytes + decode.
    private fun validateImageFile(file: MultipartFile) {
        val type = file.contentType
        if (type == null || type !in ALLOWED_IMAGE_TYPES) {
            throw BadRequestException("Only JPG, PNG, WEBP or GIF images are allowed")
        }
        if (file.size > MAX_IMAGE_SIZE_BYTES) {
            throw BadRequestException("Each image must be less than 10MB")
        }
    }

    // Absolute URL streamed by our own controller (same pattern as the
    // profile/cover photoStreamUrl - forward-headers build the public host).
    // Takes an image ID only, so callers never load BYTEA to build URLs.
    private fun imageStreamUrl(imageId: Long): String =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/feed/images/{imageId}")
            .buildAndExpand(imageId)
            .toUriString()

    // ====================================================================
    // 6. LIKE a post (idempotent - liking twice stays liked)
    // ====================================================================
    @Transactional
    fun likePost(userId: Long, postId: Long): LikeResponse {

        val post = getPostById(postId)

        if (!postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            val liker = userRepository.findById(userId)
                .orElseThrow { ResourceNotFoundException("User not found") }
            // Insert in its OWN transaction using ID-only references: on a
            // lost race (concurrent double-like) only the inner tx rolls
            // back (Postgres aborts on constraint violation) while the
            // outer one stays usable - we fall through as "liked".
            var isNewLike = false
            try {
                newTx.executeWithoutResult {
                    val like = PostLike().apply {
                        this.post = postRepository.getReferenceById(postId)
                        this.user = userRepository.getReferenceById(userId)
                    }
                    postLikeRepository.saveAndFlush(like)
                }
                isNewLike = true
            } catch (e: DataIntegrityViolationException) {
                // lost the race - the other request already liked
            }
            // Notify the author (never for self-likes). Only on NEW likes -
            // repeat taps and lost races are silent. Push mirrors the inbox
            // row, after commit.
            if (isNewLike) {
                val authorId = post.author!!.id!!
                if (authorId != userId) {
                    val likeTitle = "${liker.fullName} liked your post"
                    notificationService.notify(
                        authorId,
                        NotificationType.LIKE,
                        likeTitle,
                        "${liker.fullName} liked your post.",
                        postId,
                        actorId = userId
                    )
                    TransactionSynchronizationManager.registerSynchronization(
                        object : TransactionSynchronization {
                            override fun afterCommit() {
                                try {
                                    pushService.pushToUser(
                                        authorId, likeTitle,
                                        "${liker.fullName} liked your post.",
                                        NotificationType.LIKE, postId
                                    )
                                } catch (e: Exception) {
                                    log.error("Post-commit FCM push failed for like on post {}", postId, e)
                                }
                            }
                        }
                    )
                }
            }
        }

        return LikeResponse(
            postId = post.id!!,
            liked = true,
            likeCount = postLikeRepository.countByPostId(post.id!!)
        )
    }

    // ====================================================================
    // 7. UNLIKE a post (idempotent - unliking twice stays unliked)
    // ====================================================================
    @Transactional
    fun unlikePost(userId: Long, postId: Long): LikeResponse {

        // 404 first so unliking a missing post doesn't silently succeed.
        getPostById(postId)
        postLikeRepository.deleteByPostIdAndUserId(postId, userId)

        return LikeResponse(
            postId = postId,
            liked = false,
            likeCount = postLikeRepository.countByPostId(postId)
        )
    }

    // ====================================================================
    // 8. SHARE a post - Facebook-style reshare onto my own feed
    // ====================================================================
    // Creates a NEW post by me embedding the original (with my optional
    // text on top) + atomically bumps the ORIGINAL's share counter.
    // Sharing a reshare flattens to the ULTIMATE original (never chains).
    // Likes/comments stay separate per copy. Deleting the original deletes
    // its reshares (ON DELETE CASCADE on shared_from_id).
    @Transactional
    fun sharePost(userId: Long, postId: Long, content: String?): PostResponse {

        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }
        val post = getPostById(postId)

        // Flatten: a reshare always points at the ultimate original.
        val original = post.sharedFrom ?: post
        val originalId = original.id!!

        val cleanContent = content?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanContent != null && cleanContent.length > MAX_CONTENT_LENGTH) {
            throw BadRequestException("Post text must be $MAX_CONTENT_LENGTH characters or less")
        }

        // No counter to bump: shareCount is DERIVED from reshare rows, so
        // creating this row IS the count. It can never drift out of sync.
        // The reshare itself: my post, my optional text, no own images.
        val reshare = Post().apply {
            this.author = user
            this.content = cleanContent
            this.sharedFrom = original
        }
        val savedReshare = postRepository.save(reshare)

        // Notify the ORIGINAL's author (never for self-shares).
        // referenceId = the original (stable even if the reshare is deleted).
        // Push mirrors the inbox row, after commit.
        val originalAuthorId = original.author!!.id!!
        if (originalAuthorId != userId) {
            val shareTitle = "${user.fullName} shared your post"
            notificationService.notify(
                originalAuthorId,
                NotificationType.SHARE,
                shareTitle,
                "${user.fullName} shared your post.",
                originalId,
                actorId = userId
            )
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        try {
                            pushService.pushToUser(
                                originalAuthorId, shareTitle,
                                "${user.fullName} shared your post.",
                                NotificationType.SHARE, originalId
                            )
                        } catch (e: Exception) {
                            log.error("Post-commit FCM push failed for share of post {}", originalId, e)
                        }
                    }
                }
            )
        }

        return toPostResponse(savedReshare, userId)
    }

    // ====================================================================
    // 9. ADD COMMENT or REPLY (parentId = null -> top-level comment)
    // ====================================================================
    @Transactional
    fun addComment(authorId: Long, postId: Long, content: String?, parentId: Long?): CommentResponse {

        val cleanContent = content?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw BadRequestException("Comment text is required")

        if (cleanContent.length > MAX_COMMENT_LENGTH) {
            throw BadRequestException("Comment must be $MAX_COMMENT_LENGTH characters or less")
        }

        val post = getPostById(postId)
        val author = userRepository.findById(authorId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        // A reply must answer a comment on the SAME post.
        val parent = parentId?.let { pid ->
            val parentComment = commentRepository.findById(pid)
                .orElseThrow { ResourceNotFoundException("Parent comment not found") }
            if (parentComment.post!!.id != postId) {
                throw BadRequestException("Parent comment belongs to a different post")
            }
            parentComment
        }

        val comment = Comment().apply {
            this.post = post
            this.author = author
            this.parent = parent
            this.content = cleanContent
        }
        val saved = commentRepository.save(comment)

        // Notify: top-level -> post author; reply -> parent comment author.
        // Never for self-actions. Push mirrors the inbox row, after commit.
        val pushTarget = if (parent == null) {
            val postAuthorId = post.author!!.id!!
            if (postAuthorId != authorId) {
                notificationService.notify(
                    postAuthorId,
                    NotificationType.COMMENT,
                    "${author.fullName} commented on your post",
                    cleanContent.take(200),
                    postId,
                    actorId = authorId
                )
                Triple(postAuthorId, "${author.fullName} commented on your post", NotificationType.COMMENT)
            } else null
        } else {
            val parentAuthorId = parent.author!!.id!!
            if (parentAuthorId != authorId) {
                notificationService.notify(
                    parentAuthorId,
                    NotificationType.REPLY,
                    "${author.fullName} replied to your comment",
                    cleanContent.take(200),
                    postId,
                    actorId = authorId
                )
                Triple(parentAuthorId, "${author.fullName} replied to your comment", NotificationType.REPLY)
            } else null
        }
        if (pushTarget != null) {
            val (targetId, pushTitle, pushType) = pushTarget
            val pushBody = cleanContent.take(200)
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        try {
                            pushService.pushToUser(targetId, pushTitle, pushBody, pushType, postId)
                        } catch (e: Exception) {
                            log.error("Post-commit FCM push failed for comment on post {}", postId, e)
                        }
                    }
                }
            )
        }

        return CommentResponse.fromEntity(saved)
    }

    // ====================================================================
    // 10. COMMENTS for a post (nested tree, oldest first)
    // ====================================================================
    @Transactional(readOnly = true)
    fun getComments(postId: Long): List<CommentResponse> {

        // 404 for ghost posts instead of an empty list.
        getPostById(postId)

        val all = commentRepository.findByPostIdOrderByCreatedAtAsc(postId)

        // Group replies under their parent id, then build the tree
        // recursively (replies nest to any depth).
        val byParentId: Map<Long?, List<Comment>> = all.groupBy { it.parent?.id }

        fun buildTree(parentId: Long?): List<CommentResponse> =
            (byParentId[parentId] ?: emptyList()).map { comment ->
                CommentResponse.fromEntity(comment, buildTree(comment.id))
            }

        return buildTree(null)
    }

    // ====================================================================
    // 11. DELETE COMMENT (author only; the whole reply subtree goes too)
    // ====================================================================
    @Transactional
    fun deleteComment(requesterId: Long, commentId: Long) {

        val comment = commentRepository.findById(commentId)
            .orElseThrow { ResourceNotFoundException("Comment not found") }

        if (comment.author!!.id != requesterId) {
            throw ForbiddenException("You can only delete your own comments")
        }

        commentRepository.delete(comment)
    }
}
