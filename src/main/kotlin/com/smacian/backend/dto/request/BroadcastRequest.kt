/*
 * BroadcastRequest - Body for POST /api/admin/broadcast.
 *
 * JSON: { "title": "Office closed tomorrow", "body": "...", "push": true }
 *
 * One announcement to EVERY active user: an inbox row each, plus an
 * instant FCM push to their devices unless push = false.
 */
package com.smacian.backend.dto.request

data class BroadcastRequest(
    val title: String? = null,
    val body: String? = null,
    val push: Boolean = true
)
