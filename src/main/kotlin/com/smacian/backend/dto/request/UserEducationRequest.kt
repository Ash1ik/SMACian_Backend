/*
 * UserEducationRequest - JSON body for creating/updating one
 * education entry inside PUT /api/user/profile.
 *
 * id present   -> update the existing entry (must belong to the user)
 * id absent    -> create a new entry
 */
package com.smacian.backend.dto.request

data class UserEducationRequest(
    val id: Long? = null,
    val institution: String = "",
    val fieldOfStudy: String = "",
    val startDate: String = "",
    val endDate: String = ""
)