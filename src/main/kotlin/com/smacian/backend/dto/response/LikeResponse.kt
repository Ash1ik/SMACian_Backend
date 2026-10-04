/*
 * LikeResponse - Result of a like/unlike call.
 *
 * JSON: { "postId": 7, "liked": true, "likeCount": 12 }
 */
package com.smacian.backend.dto.response

data class LikeResponse(
    val postId: Long,
    val liked: Boolean,
    val likeCount: Long
)
