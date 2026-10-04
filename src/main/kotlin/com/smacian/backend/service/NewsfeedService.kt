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
import com.smacian.backend.dto.response.PostResponse
import com.smacian.backend.entity.Comment
import com.smacian.backend.entity.Post
import com.smacian.backend.entity.PostImage
import com.smacian.backend.entity.PostLike
import com.smacian.backend.exception.BadRequestException
import com.smacian.backend.exception.ForbiddenException
import com.smacian.backend.exception.ResourceNotFoundException
import com.smacian.backend.repository.CommentRepository
import com.smacian.backend.repository.PostImageRepository
import com.smacian.backend.repository.PostLikeRepository
import com.smacian.backend.repository.PostRepository
import com.smacian.backend.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
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
    transactionManager: PlatformTransactionManager
) {

    // Runs the like INSERT in its own transaction (see likePost): on a lost
    // race only the inner tx rolls back and the outer one stays usable.
    private val newTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    companion object {
        const val MAX_IMAGES_PER_POST = 5
        const val MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024
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

        val author = userRepository.findById(authorId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        val post = Post().apply {
            this.author = author
            this.content = cleanContent
        }
        val savedPost = postRepository.save(post)

        // Store each image row in upload order (0, 1, 2...).
        files.forEachIndexed { index, file ->
            val image = PostImage().apply {
                this.post = savedPost
                this.imageData = file.bytes
                this.contentType = file.contentType
                this.sortOrder = index
            }
            postImageRepository.save(image)
        }

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
            postImageRepository.deleteByPostId(postId)
            files.forEachIndexed { index, file ->
                val image = PostImage().apply {
                    this.post = post
                    this.imageData = file.bytes
                    this.contentType = file.contentType
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
    private fun toPostResponse(post: Post, viewerId: Long): PostResponse {
        val postId = post.id!!
        return PostResponse.fromEntity(
            entity = post,
            imageUrls = postImageRepository.findIdsByPostIdOrdered(postId).map { imageStreamUrl(it) },
            likeCount = postLikeRepository.countByPostId(postId),
            commentCount = commentRepository.countByPostId(postId),
            likedByMe = postLikeRepository.existsByPostIdAndUserId(postId, viewerId)
        )
    }

    // Maps a WHOLE page with 4 extra queries TOTAL (not per post):
    // 1 image-id pairs, 1 like counts, 1 comment counts, 1 liked ids.
    // Authors come JOIN FETCHed - no per-post lazy SELECT. No BYTEA loaded.
    private fun toPostResponseList(posts: List<Post>, viewerId: Long): List<PostResponse> {
        if (posts.isEmpty()) return emptyList()
        val ids = posts.map { it.id!! }

        // (postId, imageId) pairs ordered by sort_order; groupBy preserves
        // encounter order, so each post's URLs stay in upload order.
        val imageIdsByPost: Map<Long, List<Long>> = postImageRepository.findImageIdsByPostIds(ids)
            .groupBy({ (it[0] as Long) }, { (it[1] as Long) })
        val likeCounts = postLikeRepository.countByPostIds(ids)
            .associate { (it[0] as Long) to (it[1] as Long) }
        val commentCounts = commentRepository.countByPostIds(ids)
            .associate { (it[0] as Long) to (it[1] as Long) }
        val likedIds = postLikeRepository.findLikedPostIds(ids, viewerId).toSet()

        return posts.map { post ->
            val postId = post.id!!
            PostResponse.fromEntity(
                entity = post,
                imageUrls = (imageIdsByPost[postId] ?: emptyList()).map { imageStreamUrl(it) },
                likeCount = likeCounts[postId] ?: 0L,
                commentCount = commentCounts[postId] ?: 0L,
                likedByMe = postId in likedIds
            )
        }
    }

    private fun getPostById(postId: Long): Post =
        postRepository.findById(postId)
            .orElseThrow { ResourceNotFoundException("Post not found") }

    private fun validateImageFile(file: MultipartFile) {
        val type = file.contentType
        if (type == null || type !in ALLOWED_IMAGE_TYPES) {
            throw BadRequestException("Only JPG, PNG, WEBP or GIF images are allowed")
        }
        if (file.size > MAX_IMAGE_SIZE_BYTES) {
            throw BadRequestException("Each image must be less than 5MB")
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
            if (!userRepository.existsById(userId)) {
                throw ResourceNotFoundException("User not found")
            }
            // Insert in its OWN transaction using ID-only references: on a
            // lost race (concurrent double-like) only the inner tx rolls
            // back (Postgres aborts on constraint violation) while the
            // outer one stays usable - we fall through as "liked".
            try {
                newTx.executeWithoutResult {
                    val like = PostLike().apply {
                        this.post = postRepository.getReferenceById(postId)
                        this.user = userRepository.getReferenceById(userId)
                    }
                    postLikeRepository.saveAndFlush(like)
                }
            } catch (e: DataIntegrityViolationException) {
                // lost the race - the other request already liked
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
    // 8. SHARE a post (increments the share counter)
    // ====================================================================
    // A "share" here = the user forwarded the post (to chat, story, etc.).
    // We count it; the app does the actual forwarding UI-side.
    @Transactional
    fun sharePost(userId: Long, postId: Long): PostResponse {

        if (!userRepository.existsById(userId)) {
            throw ResourceNotFoundException("User not found")
        }

        // Atomic counter bump - concurrent shares can never lose increments
        // (no read-modify-write). Returns 0 when the post doesn't exist.
        val updated = postRepository.incrementShareCount(postId)
        if (updated == 0) {
            throw ResourceNotFoundException("Post not found")
        }

        // Fresh read: the post was never loaded above, so no stale L1 entry.
        val post = getPostById(postId)
        return toPostResponse(post, userId)
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

        return CommentResponse.fromEntity(commentRepository.save(comment))
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
