/*
 * ResetPasswordRequest - Final step of the "Forgot Password" flow.
 *
 * After OTP verification, the user sets a new password.
 * The OTP is checked again for extra security.
 *
 * Example:
 * {
 *   "contact": "ahmed@example.com",
 *   "otpCode": "482917",
 *   "newPassword": "NewP@ss567",
 *   "confirmPassword": "NewP@ss567"
 * }
 */
package com.smacian.backend.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class ResetPasswordRequest(

    @field:NotBlank(message = "Please enter your email or phone number")
    val contact: String,

    @field:NotBlank(message = "OTP code is required")
    @field:Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
    @field:Pattern(regexp = "^[0-9]{6}$", message = "OTP must contain only digits")
    val otpCode: String,

    @field:NotBlank(message = "New password is required")
    @field:Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&#^()_+\\-=])[A-Za-z\\d@\$!%*?&#^()_+\\-=]{8,}$",
        message = "Password must contain at least 1 uppercase letter, 1 lowercase letter, 1 digit, and 1 special character (!@#\$%^&*)"
    )
    val newPassword: String,

    @field:NotBlank(message = "Please confirm your new password")
    val confirmPassword: String
)