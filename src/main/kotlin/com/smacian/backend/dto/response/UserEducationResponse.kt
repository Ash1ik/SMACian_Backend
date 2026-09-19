/*
 * UserEducationResponse - How one education entry is returned to the app.
 */
package com.smacian.backend.dto.response

import com.smacian.backend.entity.UserEducation

data class UserEducationResponse(
    val id: Long,
    val institution: String,
    val fieldOfStudy: String,
    val startDate: String,
    val endDate: String
) {
    companion object {
        fun fromEntity(entity: UserEducation): UserEducationResponse =
            UserEducationResponse(
                id = entity.id!!,
                institution = entity.institution,
                fieldOfStudy = entity.fieldOfStudy,
                startDate = entity.startDate,
                endDate = entity.endDate
            )
    }
}