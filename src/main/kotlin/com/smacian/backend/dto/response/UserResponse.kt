/*
 * UserResponse - How user data is returned to the client.
 *
 * We map the User ENTITY to this DTO to hide sensitive fields
 * (like passwordHash) from the app. Returning DTOs instead of entities
 * is an important security practice.
 *
 * Example JSON:
 * {
 *   "id": 1,
 *   "firstName": "Ahmed",
 *   "lastName": "Khan",
 *   "email": "ahmed@example.com",
 *   "phone": null,
 *   "gender": "MALE",
 *   "dateOfBirth": "1998-01-12",
 *   "profilePhotoUrl": "https://res.cloudinary.com/...",
 *   "coverPhotoUrl": null,
 *   "isActive": true
 * }
 */
package com.smacian.backend.dto.response

import com.smacian.backend.entity.User
import com.smacian.backend.entity.enums.Gender
import java.time.LocalDate

data class UserResponse(
    val id: Long?,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val gender: Gender,
    val dateOfBirth: LocalDate,
    val profilePhotoUrl: String?,
    val coverPhotoUrl: String?,
    val isActive: Boolean
) {

    /*
     * Companion object = where Kotlin puts "static" methods.
     *
     * fromEntity() converts a User entity into a UserResponse DTO,
     * copying only the SAFE public fields (never the password).
     */
    companion object {
        fun fromEntity(user: User): UserResponse {
            return UserResponse(
                id = user.id,
                firstName = user.firstName,
                lastName = user.lastName,
                email = user.email,
                phone = user.phone,
                gender = user.gender,
                dateOfBirth = user.dateOfBirth,
                profilePhotoUrl = user.profilePhotoUrl,
                coverPhotoUrl = user.coverPhotoUrl,
                isActive = user.isActive
            )
        }
    }
}