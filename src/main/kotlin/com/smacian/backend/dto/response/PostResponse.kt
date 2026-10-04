/*
 * PostResponse - One newsfeed post as the mobile app receives it.
 *
 * JSON: { "id": 7, "content": "Hello world", "authorId": 3,
 *         "authorName": "Ahmed Khan", "authorPhotoUrl": "https://...",
 *         "images": [{ "id": 11, "url": "https://.../api/feed/images/11",
 *                      "width": 1600, "height": 1200, "sortOrder": 0 }],
 *         "likeCount": 12, "commentCount": 4, "shareCount": 2,
 *         "likedByMe": true,
 *         "createdAt": "2026-10-04T10:15:00", "updatedAt": "..." }
 *
 * images carry id + absolute stream URL + stored dimensions + sortOrder
 * (pre-sorted) so Android can lay out before downloading bytes. URLs are
 * served by GET /api/feed/images/{id} (public - mobile <Image> sends no JWT).
 * likedByMe is relative to the requesting user (from their JWT).
 *
 * BREAKING (v2, 2026-10): replaces imageUrls: List<String>. No production
 * Android client existed at change time, so the rename was done cleanly
 * instead of shipping both fields.
 */
package com.smacian.backend.dto.response

import com.smacian.backend.entity.Post
import java.time.LocalDateTime

data class PostResponse(
    val id: Long,
    val content: String?,
    val authorId: Long,
    val authorName: String,
    val authorPhotoUrl: String?,
    val images: List<PostImageResponse>,
    val likeCount: Long,
    val commentCount: Long,
    val shareCount: Int,
    val likedByMe: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun fromEntity(
            entity: Post,
            images: List<PostImageResponse>,
            likeCount: Long,
            commentCount: Long,
            likedByMe: Boolean
        ): PostResponse {
            val author = entity.author!!
            return PostResponse(
                id = entity.id!!,
                content = entity.content,
                authorId = author.id!!,
                authorName = author.fullName,
                authorPhotoUrl = author.profilePhotoUrl,
                images = images,
                likeCount = likeCount,
                commentCount = commentCount,
                shareCount = entity.shareCount,
                likedByMe = likedByMe,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        }
    }
}
