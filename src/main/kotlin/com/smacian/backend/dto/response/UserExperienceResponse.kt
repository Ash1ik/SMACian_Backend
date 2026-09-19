/*
 * UserExperienceResponse - How one experience entry is returned to the app.
 */
package com.smacian.backend.dto.response

import com.smacian.backend.entity.UserExperience

data class UserExperienceResponse(
    val id: Long,
    val organization: String,
    val designation: String,
    val startDate: String,
    val endDate: String,
    val description: String?
) {
    companion object {
        fun fromEntity(entity: UserExperience): UserExperienceResponse =
            UserExperienceResponse(
                id = entity.id!!,
                organization = entity.organization,
                designation = entity.designation,
                startDate = entity.startDate,
                endDate = entity.endDate,
                description = entity.description
            )
    }
}