/*
 * UserExperienceRepository - Data access for work experience entries.
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.UserExperience
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserExperienceRepository : JpaRepository<UserExperience, Long> {

    // All experiences of one user, oldest first.
    fun findByUser_IdOrderByStartDateAsc(userId: Long): List<UserExperience>

    // An experience + ownership check in one query.
    fun findByIdAndUser_Id(id: Long, userId: Long): Optional<UserExperience>
}