/*
 * UserExperienceRequest - JSON body for creating/updating one
 * experience entry inside PUT /api/user/profile.
 *
 * id present   -> update the existing entry (must belong to the user)
 * id absent    -> create a new entry
 */
package com.smacian.backend.dto.request

data class UserExperienceRequest(
    val id: Long? = null,
    val organization: String = "",
    val designation: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val description: String? = null
)