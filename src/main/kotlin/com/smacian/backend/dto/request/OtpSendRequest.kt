/*
 * OtpSendRequest - Used when a user requests an OTP.
 *
 * Two scenarios:
 *   1. During registration (contact verification)
 *   2. During forgot password (identity verification)
 *
 * Example: { "contact": "ahmed@example.com" }
 */
package com.smacian.backend.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class OtpSendRequest(

    @field:NotBlank(message = "Please enter your email or phone number")
    @field:Size(max = 255, message = "Contact field is too long")
    val contact: String
)