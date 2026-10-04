/*
 * CommentResponse - One comment (or reply) as the mobile app receives it.
 *
 * JSON: { "id": 5, "content": "Nice!", "authorId": 3,
 *         "authorName": "Ahmed Khan", "authorPhotoUrl": "https://...",
 *         "parentId": null, "createdAt": "...",
 *         "replies": [ { ...nested CommentResponse... } ] }
 *
 * parentId = null means a top-level comment on the post; otherwise it is
 * the id of the comment this is a reply to. Replies nest to any depth.
 */
package com.smacian.backend.dto.response

import com.smacian.backend.entity.Comment
import java.time.LocalDateTime

data class CommentResponse(
    val id: Long,
    val content: String,
    val authorId: Long,
    val authorName: String,
    val authorPhotoUrl: String?,
    val parentId: Long?,
    val createdAt: LocalDateTime,
    val replies: List<CommentResponse> = emptyList()
) {
    companion object {
        fun fromEntity(entity: Comment, replies: List<CommentResponse> = emptyList()): CommentResponse {
            val author = entity.author!!
            return CommentResponse(
                id = entity.id!!,
                content = entity.content,
                authorId = author.id!!,
                authorName = author.fullName,
                authorPhotoUrl = author.profilePhotoUrl,
                parentId = entity.parent?.id,
                createdAt = entity.createdAt,
                replies = replies
            )
        }
    }
}
