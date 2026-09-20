package com.smacian.backend.dto.request

/*
 * JSON body for the URL-based photo endpoints (no Cloudinary needed):
 *
 *   PUT /api/user/profile/photo   { "url": "https://..." }
 *   PUT /api/user/cover/photo     { "url": "https://..." }
 *
 * Requirements:
 *   - "url" is OPTIONAL. Omitted or blank => the current photo is kept
 *     unchanged (the endpoint never wipes a photo you didn't ask to change).
 *   - If provided, it must be a valid http(s) URL.
 */
class UpdatePhotoUrlRequest(
    val url: String? = null
)
