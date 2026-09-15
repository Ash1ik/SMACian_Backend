/*
 * AuthResponse - Returned after successful login or registration.
 *
 * Contains:
 *   - token: the JWT the app stores and sends on every request
 *   - tokenType: always "Bearer" (JWT convention)
 *   - user: the logged-in user's public profile
 */
package com.smacian.backend.dto.response

data class AuthResponse(
    val token: String,
    val tokenType: String = "Bearer",
    val user: UserResponse
)