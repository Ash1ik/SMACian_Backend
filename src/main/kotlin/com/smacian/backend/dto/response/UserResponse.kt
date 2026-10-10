/*
 * UserResponse - How user data is returned to the client.
 *
 * We map the User ENTITY to this DTO to hide sensitive fields
 * (like passwordHash) from the app. Returning DTOs instead of entities
 * is an important security practice.
 *
 * The ORIGINAL fields (id, firstName, lastName, email, phone, gender,
 * dateOfBirth, profilePhotoUrl, coverPhotoUrl, isActive) are kept with
 * exact names for backward compatibility. New profile fields and the
 * nested experience/education lists are nullable/empty by default.
 */
package com.smacian.backend.dto.response

import com.smacian.backend.entity.User
import com.smacian.backend.entity.enums.BloodGroup
import com.smacian.backend.entity.enums.Gender
import com.smacian.backend.entity.enums.RelationshipStatus
import java.time.LocalDate
import java.time.LocalDateTime

data class UserResponse(
    val id: Long?,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val gender: Gender,
    // Nullable since Google auto-register (no DOB from the ID token).
    // Profile update requires it, so it fills in on first edit.
    val dateOfBirth: LocalDate?,
    val profilePhotoUrl: String?,
    val coverPhotoUrl: String?,
    val isActive: Boolean,
    // "USER" or "ADMIN" - the app shows admin UI when ADMIN.
    val role: String,

    // ==================== Extended Profile Fields (nullable) ====================
    val designation: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val bloodGroup: BloodGroup? = null,
    val relationshipStatus: RelationshipStatus? = null,
    val socialLinks: List<String> = emptyList(),
    val experiences: List<UserExperienceResponse> = emptyList(),
    val educations: List<UserEducationResponse> = emptyList(),
    val updatedAt: LocalDateTime? = null
) {

    companion object {

        // Used where nested lists don't matter (auth/register/login).
        fun fromEntity(user: User): UserResponse =
            fromEntity(user, emptyList(), emptyList())

        // Used by profile endpoints: includes experience + education.
        fun fromEntity(
            user: User,
            experiences: List<UserExperienceResponse>,
            educations: List<UserEducationResponse>
        ): UserResponse {
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
                isActive = user.isActive,
                role = user.role.name,
                designation = user.designation,
                bio = user.bio,
                location = user.location,
                bloodGroup = user.bloodGroup,
                relationshipStatus = user.relationshipStatus,
                socialLinks = user.socialLinks ?: emptyList(),
                experiences = experiences,
                educations = educations,
                updatedAt = user.updatedAt
            )
        }
    }
}