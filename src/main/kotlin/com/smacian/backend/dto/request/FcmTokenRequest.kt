/*
 * FcmTokenRequest - Body for registering/unregistering a push token.
 *
 * JSON: { "token": "eXaMpLe..." }
 *
 * The app sends the CURRENT FCM token on every login (idempotent upsert)
 * and on logout so a signed-out device stops receiving pushes.
 */
package com.smacian.backend.dto.request

data class FcmTokenRequest(
    val token: String? = null
)
