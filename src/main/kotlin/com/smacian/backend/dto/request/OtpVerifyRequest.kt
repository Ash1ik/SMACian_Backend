/*
 * OtpVerifyRequest - Used when the user submits the 6-digit OTP.
 *
 * We validate: code matches, not expired, correct purpose, not used.
 *
 * Example: { "contact": "ahmed@example.com", "otpCode": "482917" }
 */
package com.smacian.backend.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class OtpVerifyRequest(

    @field:NotBlank(message = "Please enter your email or phone number")
    val contact: String,

    @field:NotBlank(message = "OTP code is required")
    @field:Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
    @field:Pattern(regexp = "^[0-9]{6}$", message = "OTP must contain only digits")
    val otpCode: String
)