/*
 * CommentLikeResponse - Result of a comment/reply like/unlike call.
 *
 * JSON: { "commentId": 5, "liked": true, "likeCount": 3 }
 */
package com.smacian.backend.dto.response

data class CommentLikeResponse(
    val commentId: Long,
    val liked: Boolean,
    val likeCount: Long
)
