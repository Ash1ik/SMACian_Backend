/*
 * PostImageResponse - One post image as the mobile app receives it.
 *
 * JSON: { "id": 11, "url": "https://.../api/feed/images/11",
 *         "width": 1600, "height": 1200, "sortOrder": 0 }
 *
 * width/height are the STORED dimensions (after the 1600px downscale) so
 * Android can reserve the correct layout space BEFORE downloading bytes.
 * Both are null only for legacy rows (pre-dimensions) or WebP uploads
 * (no JDK decoder) - the app must handle null as "unknown, wrap content".
 * sortOrder is the upload position (0..4); lists are always pre-sorted.
 */
package com.smacian.backend.dto.response

data class PostImageResponse(
    val id: Long,
    val url: String,
    val width: Int?,
    val height: Int?,
    val sortOrder: Int
)
