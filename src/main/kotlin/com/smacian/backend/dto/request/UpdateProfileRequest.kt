/*
 * UpdateProfileRequest - JSON body for updating profile info
 * (name, date of birth, gender).
 *
 * Example:
 * {
 *   "firstName": "Ahmed",
 *   "lastName": "Khan",
 *   "dateOfBirth": "1998-01-12",
 *   "gender": "MALE"
 * }
 */
package com.smacian.backend.dto.request

import com.smacian.backend.entity.enums.Gender
import jakarta.validation.constraints.*

data class UpdateProfileRequest(

    @field:NotBlank(message = "First name is required")
    @field:Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    @field:Pattern(regexp = "^[a-zA-Z ]+$", message = "First name can only contain letters and spaces")
    val firstName: String,

    @field:NotBlank(message = "Last name is required")
    @field:Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    @field:Pattern(regexp = "^[a-zA-Z ]+$", message = "Last name can only contain letters and spaces")
    val lastName: String,

    @field:NotNull(message = "Date of birth is required")
    val dateOfBirth: String,

    @field:NotNull(message = "Please select a gender")
    val gender: Gender
)