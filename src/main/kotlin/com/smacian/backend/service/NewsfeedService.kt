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

import com.smacian.backend.dto.response.PagedResponse
import com.smacian.backend.dto.response.PostResponse
import com.smacian.backend.entity.Post
import com.smacian.backend.entity.PostImage
import com.smacian.backend.exception.BadRequestException
import com.smacian.backend.exception.ForbiddenException
import com.smacian.backend.exception.ResourceNotFoundException
import com.smacian.backend.repository.PostImageRepository
import com.smacian.backend.repository.PostRepository
import com.smacian.backend.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@Service
class NewsfeedService(
    private val postRepository: PostRepository,
    private val postImageRepository: PostImageRepository,
    private val userRepository: UserRepository
) {

    companion object {
        const val MAX_IMAGES_PER_POST = 5
        const val MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024
        const val MAX_CONTENT_LENGTH = 5000
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

        val imageUrls = imageUrlsFor(savedPost.id!!)
        return PostResponse.fromEntity(savedPost, imageUrls)
    }

    // ====================================================================
    // 2. NEWSFEED (paged, newest first)
    // ====================================================================
    @Transactional(readOnly = true)
    fun getFeed(page: Int, size: Int): PagedResponse<PostResponse> {

        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 50)

        val pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = postRepository.findAllByOrderByCreatedAtDesc(pageable)

        val content = result.content.map { post ->
            PostResponse.fromEntity(post, imageUrlsFor(post.id!!))
        }

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
    fun getPost(postId: Long): PostResponse {
        val post = getPostById(postId)
        return PostResponse.fromEntity(post, imageUrlsFor(post.id!!))
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

    // Absolute URLs streamed by our own controller (same pattern as the
    // profile/cover photoStreamUrl - forward-headers build the public host).
    private fun imageUrlsFor(postId: Long): List<String> {
        val images = postImageRepository.findByPostIdOrderBySortOrderAsc(postId)
        return images.map { image ->
            ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/feed/images/{imageId}")
                .buildAndExpand(image.id!!)
                .toUriString()
        }
    }
}
