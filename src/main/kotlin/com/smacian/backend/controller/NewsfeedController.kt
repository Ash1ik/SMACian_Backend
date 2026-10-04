/*
 * NewsfeedController - REST endpoints for newsfeed posts.
 *
 *   POST   /api/feed                  -> create post (multipart: content + images[])
 *   GET    /api/feed?page=0&size=20    -> paged feed, newest first
 *   GET    /api/feed/mine?page=0&size -> my own posts
 *   GET    /api/feed/{id}              -> single post
 *   DELETE /api/feed/{id}              -> delete own post
 *   POST   /api/feed/{id}/like         -> like (idempotent)
 *   DELETE /api/feed/{id}/like         -> unlike (idempotent)
 *   POST   /api/feed/{id}/share        -> share (bumps shareCount)
 *   GET    /api/feed/{id}/comments     -> nested comment tree
 *   POST   /api/feed/{id}/comments     -> comment or reply (parentId)
 *   DELETE /api/feed/comments/{cid}    -> delete own comment (+replies)
 *   GET    /api/feed/images/{imageId}  -> stream one post image (PUBLIC)
 *
 * All endpoints need login (JWT) EXCEPT the image stream: the mobile
 * <Image> loads it without an Authorization header, so it is permitAll()
 * in SecurityConfig (same pattern as profile/cover photo streams).
 *
 * SECURITY: the author ID is NEVER taken from the request. It is read from
 * the JWT (CurrentUser.getUserId()) - a user can only post/delete as
 * themselves (prevents the "IDOR" security flaw).
 */
package com.smacian.backend.controller

import com.smacian.backend.dto.request.CommentRequest
import com.smacian.backend.dto.response.CommentResponse
import com.smacian.backend.dto.response.LikeResponse
import com.smacian.backend.dto.response.PagedResponse
import com.smacian.backend.dto.response.PostResponse
import com.smacian.backend.security.CurrentUser
import com.smacian.backend.service.NewsfeedService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/feed")
class NewsfeedController(
    private val newsfeedService: NewsfeedService
) {

    // ====================================================================
    // 1. CREATE POST (text and/or images)
    // ====================================================================
    // Multipart request - text comes as a regular form field, images as
    // one or more "images" file parts (single image works the same way):
    //   curl -X POST http://localhost:8080/api/feed \
    //        -H "Authorization: Bearer <token>" \
    //        -F "content=Hello world" \
    //        -F "images=@photo1.jpg" \
    //        -F "images=@photo2.jpg"
    // ====================================================================
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createPost(
        @RequestParam(required = false) content: String?,
        @RequestPart(required = false) images: List<MultipartFile>?
    ): ResponseEntity<PostResponse> {
        val post = newsfeedService.createPost(CurrentUser.getUserId(), content, images)
        return ResponseEntity.status(HttpStatus.CREATED).body(post)
    }

    // ====================================================================
    // 2. NEWSFEED (paged, newest first) - ?page=0&size=20
    // ====================================================================

    @GetMapping
    fun getFeed(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PagedResponse<PostResponse> =
        newsfeedService.getFeed(CurrentUser.getUserId(), page, size)

    // ====================================================================
    // 2b. MY POSTS (paged, newest first) - GET /api/feed/mine?page=0&size=20
    // ====================================================================
    // Declared BEFORE /{id} so "mine" matches this literal route and is
    // never parsed as a post id. Author comes from the JWT, not the request.

    @GetMapping("/mine")
    fun getMyPosts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PagedResponse<PostResponse> =
        newsfeedService.getMyPosts(CurrentUser.getUserId(), page, size)

    // ====================================================================
    // 3. SINGLE POST - GET /api/feed/{id}
    // ====================================================================

    @GetMapping("/{id}")
    fun getPost(@PathVariable id: Long): ResponseEntity<PostResponse> {
        val post = newsfeedService.getPost(id, CurrentUser.getUserId())
        return ResponseEntity.ok(post)
    }

    // ====================================================================
    // 4. DELETE POST (own only) - returns 204, 403 if not the author
    // ====================================================================

    @DeleteMapping("/{id}")
    fun deletePost(@PathVariable id: Long): ResponseEntity<Void> {
        newsfeedService.deletePost(CurrentUser.getUserId(), id)
        return ResponseEntity.noContent().build()
    }

    // ====================================================================
    // 5. LIKE a post (idempotent) -> { postId, liked: true, likeCount }
    // ====================================================================

    @PostMapping("/{id}/like")
    fun likePost(@PathVariable id: Long): ResponseEntity<LikeResponse> {
        val result = newsfeedService.likePost(CurrentUser.getUserId(), id)
        return ResponseEntity.ok(result)
    }

    // ====================================================================
    // 6. UNLIKE a post (idempotent) -> { postId, liked: false, likeCount }
    // ====================================================================

    @DeleteMapping("/{id}/like")
    fun unlikePost(@PathVariable id: Long): ResponseEntity<LikeResponse> {
        val result = newsfeedService.unlikePost(CurrentUser.getUserId(), id)
        return ResponseEntity.ok(result)
    }

    // ====================================================================
    // 7. SHARE a post -> returns the post with bumped shareCount
    // ====================================================================
    // Counts the share; the app handles the actual forwarding UI
    // (system share sheet, chat, story...).

    @PostMapping("/{id}/share")
    fun sharePost(@PathVariable id: Long): ResponseEntity<PostResponse> {
        val post = newsfeedService.sharePost(CurrentUser.getUserId(), id)
        return ResponseEntity.ok(post)
    }

    // ====================================================================
    // 8. COMMENTS tree - GET /api/feed/{id}/comments
    // ====================================================================
    // Top-level comments with nested `replies` (any depth, oldest first).

    @GetMapping("/{id}/comments")
    fun getComments(@PathVariable id: Long): ResponseEntity<List<CommentResponse>> {
        val comments = newsfeedService.getComments(id)
        return ResponseEntity.ok(comments)
    }

    // ====================================================================
    // 9. ADD COMMENT or REPLY - body { "content": "...", "parentId": null }
    // ====================================================================
    // parentId omitted/null = top-level comment; otherwise a reply to
    // that comment (must belong to the same post).

    @PostMapping("/{id}/comments")
    fun addComment(
        @PathVariable id: Long,
        @Valid @RequestBody request: CommentRequest
    ): ResponseEntity<CommentResponse> {
        val comment = newsfeedService.addComment(CurrentUser.getUserId(), id, request.content, request.parentId)
        return ResponseEntity.status(HttpStatus.CREATED).body(comment)
    }

    // ====================================================================
    // 10. DELETE COMMENT (own only, whole reply subtree goes too)
    // ====================================================================

    @DeleteMapping("/comments/{commentId}")
    fun deleteComment(@PathVariable commentId: Long): ResponseEntity<Void> {
        newsfeedService.deleteComment(CurrentUser.getUserId(), commentId)
        return ResponseEntity.noContent().build()
    }

    // ====================================================================
    // 5. STREAM POST IMAGE (public - mobile <Image> sends no JWT)
    // ====================================================================
    // The imageUrls in PostResponse point HERE. permitAll() in
    // SecurityConfig because <Image> src loads without any Authorization
    // header. Returns the raw BYTEA bytes with the stored content type.
    @GetMapping("/images/{imageId}")
    fun streamPostImage(@PathVariable imageId: Long): ResponseEntity<ByteArray> {
        val (data, contentType) = newsfeedService.getPostImageStream(imageId)
        return ResponseEntity.status(HttpStatus.OK)
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
            .body(data)
    }
}
