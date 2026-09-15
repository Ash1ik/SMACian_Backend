/*
 * RegisterRequest - The JSON body the mobile app sends when a user
 * completes all 8 signup steps.
 *
 * A Kotlin `data class` auto-generates: getters, setters, toString(),
 * equals(), hashCode() and a copy() method - perfect for DTOs.
 *
 * Example JSON:
 * {
 *   "firstName": "Ahmed",
 *   "lastName": "Khan",
 *   "dateOfBirth": "1998-01-12",
 *   "gender": "MALE",
 *   "contact": "ahmed@example.com",
 *   "password": "MyP@ss123",
 *   "otpCode": "482917",
 *   "termsAccepted": true
 * }
 *
 * `@field:` targets the validation annotation to the backing field so
 * the Bean Validation provider (Hibernate Validator) sees it correctly.
 */
package com.smacian.backend.dto.request

import com.smacian.backend.entity.enums.Gender
import jakarta.validation.constraints.*

data class RegisterRequest(

    // ==================== Name ====================

    @field:NotBlank(message = "First name is required")
    @field:Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    @field:Pattern(regexp = "^[a-zA-Z ]+$", message = "First name can only contain letters and spaces")
    val firstName: String,

    @field:NotBlank(message = "Last name is required")
    @field:Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    @field:Pattern(regexp = "^[a-zA-Z ]+$", message = "Last name can only contain letters and spaces")
    val lastName: String,

    // ==================== Date of Birth ====================
    // Received as a String "yyyy-MM-dd"; parsed in AuthService so we can
    // also validate the age (13+).

    @field:NotBlank(message = "Date of birth is required")
    val dateOfBirth: String,

    // ==================== Gender ====================

    @field:NotNull(message = "Please select a gender")
    val gender: Gender,

    // ==================== Contact ====================
    // One unified field - can be an email OR phone. Auto-detected in backend.

    @field:NotBlank(message = "Please enter your email or phone number")
    @field:Size(max = 255, message = "Contact field is too long")
    val contact: String,

    // ==================== Password ====================
    // Regex enforces: uppercase + lowercase + digit + special character.

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&#^()_+\\-=])[A-Za-z\\d@\$!%*?&#^()_+\\-=]{8,}$",
        message = "Password must contain at least 1 uppercase letter, 1 lowercase letter, 1 digit, and 1 special character (!@#\$%^&*)"
    )
    val password: String,

    // ==================== OTP Code ====================

    @field:NotBlank(message = "OTP code is required")
    @field:Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
    @field:Pattern(regexp = "^[0-9]{6}$", message = "OTP must contain only digits")
    val otpCode: String,

    // ==================== Terms ====================

    @field:AssertTrue(message = "You must accept the Terms of Service to register")
    val termsAccepted: Boolean
)