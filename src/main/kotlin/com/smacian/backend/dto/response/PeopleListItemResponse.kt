/*
 * PeopleListItemResponse - One row in the people search list
 * (GET /api/users?search=...).
 */
package com.smacian.backend.dto.response

import com.smacian.backend.entity.User
import com.smacian.backend.entity.enums.BloodGroup

data class PeopleListItemResponse(
    val id: Long,
    val fullName: String,
    val profilePhotoUrl: String?,
    val designation: String?,
    val bloodGroup: BloodGroup?
) {
    companion object {
        fun fromEntity(entity: User): PeopleListItemResponse =
            PeopleListItemResponse(
                id = entity.id!!,
                fullName = entity.fullName,
                profilePhotoUrl = entity.profilePhotoUrl,
                designation = entity.designation,
                bloodGroup = entity.bloodGroup
            )
    }
}