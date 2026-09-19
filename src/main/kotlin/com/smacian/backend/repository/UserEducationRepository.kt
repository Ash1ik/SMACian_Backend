/*
 * UserEducationRepository - Data access for education entries.
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.UserEducation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserEducationRepository : JpaRepository<UserEducation, Long> {

    // All education entries of one user, oldest first.
    fun findByUser_IdOrderByStartDateAsc(userId: Long): List<UserEducation>

    // An education entry + ownership check in one query.
    fun findByIdAndUser_Id(id: Long, userId: Long): Optional<UserEducation>
}