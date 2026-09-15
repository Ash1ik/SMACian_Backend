/*
 * LoginRequest - JSON body for login.
 *
 * Users log in with either email OR phone + password.
 * Example: { "contact": "ahmed@example.com", "password": "MyP@ss123" }
 */
package com.smacian.backend.dto.request

import jakarta.validation.constraints.NotBlank

data class LoginRequest(

    @field:NotBlank(message = "Please enter your email or phone number")
    val contact: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)