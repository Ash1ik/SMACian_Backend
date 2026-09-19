/*
 * PublicUserResponse - Full public profile of another user
 * (GET /api/users/{id}). Email and phone are deliberately hidden.
 */
package com.smacian.backend.dto.response

import com.smacian.backend.entity.User
import com.smacian.backend.entity.enums.BloodGroup
import com.smacian.backend.entity.enums.Gender
import com.smacian.backend.entity.enums.RelationshipStatus

data class PublicUserResponse(
    val id: Long?,
    val fullName: String,
    val profilePhotoUrl: String?,
    val coverPhotoUrl: String?,
    val designation: String?,
    val bio: String?,
    val location: String?,
    val bloodGroup: BloodGroup?,
    val gender: Gender,
    val relationshipStatus: RelationshipStatus?,
    val socialLinks: List<String> = emptyList(),
    val experience: List<UserExperienceResponse> = emptyList(),
    val education: List<UserEducationResponse> = emptyList(),
    val posts: List<Any> = emptyList()
) {
    companion object {
        fun fromEntity(
            entity: User,
            experiences: List<UserExperienceResponse>,
            educations: List<UserEducationResponse>
        ): PublicUserResponse =
            PublicUserResponse(
                id = entity.id,
                fullName = entity.fullName,
                profilePhotoUrl = entity.profilePhotoUrl,
                coverPhotoUrl = entity.coverPhotoUrl,
                designation = entity.designation,
                bio = entity.bio,
                location = entity.location,
                bloodGroup = entity.bloodGroup,
                gender = entity.gender,
                relationshipStatus = entity.relationshipStatus,
                socialLinks = entity.socialLinks ?: emptyList(),
                experience = experiences,
                education = educations,
                posts = emptyList()
            )
    }
}