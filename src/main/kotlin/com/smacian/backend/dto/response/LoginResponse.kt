/*
 * LoginResponse - Returned after a successful login.
 *
 * Contains ONLY:
 *   - token:      the JWT the app stores and sends on every request
 *   - tokenType:  always "Bearer" (JWT convention)
 *   - message:    success message
 *
 * Deliberately does NOT include the user object. The app can fetch the
 * profile separately via GET /api/user/profile.
 */
package com.smacian.backend.dto.response

data class LoginResponse(
    val token: String,
    val tokenType: String = "Bearer",
    val message: String = "Login successful"
)
