/*
 * ChangePasswordRequest - Logged-in user changes their own password.
 *
 * Unlike the forgot-password flow (OTP, no login), the user must prove
 * they know the CURRENT password. New password follows the same strength
 * rules as registration.
 *
 * Example:
 * {
 *   "currentPassword": "OldP@ss123",
 *   "newPassword": "NewP@ss567",
 *   "confirmPassword": "NewP@ss567"
 * }
 */
package com.smacian.backend.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class ChangePasswordRequest(

    @field:NotBlank(message = "Current password is required")
    val currentPassword: String,

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
