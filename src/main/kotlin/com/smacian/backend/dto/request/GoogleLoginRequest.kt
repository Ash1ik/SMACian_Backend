/*
 * GoogleLoginRequest - Body for POST /api/auth/google.
 *
 * JSON: { "idToken": "eyJhbGciOi..." }
 *
 * The idToken is minted by the Android app via Credential Manager and verified
 * server-side against Google's certs (audience = our Web OAuth client ID).
 */
package com.smacian.backend.dto.request

import jakarta.validation.constraints.NotBlank

data class GoogleLoginRequest(

    @field:NotBlank(message = "Google ID token is required")
    val idToken: String
)
