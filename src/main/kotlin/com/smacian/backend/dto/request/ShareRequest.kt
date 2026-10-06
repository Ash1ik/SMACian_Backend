/*
 * ShareRequest - Optional body for POST /api/feed/{id}/share.
 *
 * JSON: { "content": "Must read!" } or empty/absent body for a plain reshare.
 *
 * content = the sharer's own optional text shown above the embedded
 * original (Facebook-style "say something about this").
 */
package com.smacian.backend.dto.request

data class ShareRequest(
    val content: String? = null
)
