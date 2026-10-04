/*
 * CommentRequest - Body for POST /api/feed/{id}/comments.
 *
 * JSON: { "content": "Nice post!", "parentId": null }
 *
 * parentId = null (or omitted) -> top-level comment on the post.
 * parentId = <comment id>       -> reply to that comment (any depth).
 */
package com.smacian.backend.dto.request

data class CommentRequest(
    val content: String?,
    val parentId: Long? = null
)
