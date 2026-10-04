/*
 * PostResponse - One newsfeed post as the mobile app receives it.
 *
 * JSON: { "id": 7, "content": "Hello world", "authorId": 3,
 *         "authorName": "Ahmed Khan", "authorPhotoUrl": "https://...",
 *         "imageUrls": ["https://.../api/feed/images/11", ...],
 *         "likeCount": 12, "commentCount": 4, "shareCount": 2,
 *         "likedByMe": true,
 *         "createdAt": "2026-10-04T10:15:00", "updatedAt": "..." }
 *
 * imageUrls are absolute URLs served by GET /api/feed/images/{id}
 * (public - the mobile <Image> loads them without a JWT).
 * likedByMe is relative to the requesting user (from their JWT).
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
    val imageUrls: List<String>,
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
            imageUrls: List<String>,
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
                imageUrls = imageUrls,
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
