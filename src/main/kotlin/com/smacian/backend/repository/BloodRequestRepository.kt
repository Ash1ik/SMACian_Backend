/*
 * BloodRequestRepository - Data Access Layer for the BloodRequest entity.
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.BloodRequest
import com.smacian.backend.entity.enums.BloodGroup
import com.smacian.backend.entity.enums.BloodRequestStatus
import com.smacian.backend.entity.enums.Urgency
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
interface BloodRequestRepository : JpaRepository<BloodRequest, Long> {

    /*
     * Filtered list, newest first, requester JOIN FETCHed (no per-row
     * lazy SELECT). All filters optional (null/blank = no filter).
     * Pass a Pageable WITHOUT sort (ordering is fixed in the query).
     */
    @Query(
        value = "SELECT br FROM BloodRequest br JOIN FETCH br.requester " +
            "WHERE (:bg IS NULL OR br.bloodGroup = :bg) " +
            "AND (:urg IS NULL OR br.urgency = :urg) " +
            "AND (:loc = '' OR lower(br.location) LIKE lower(concat('%', :loc, '%'))) " +
            "AND (:status IS NULL OR br.status = :status) " +
            "ORDER BY br.createdAt DESC",
        countQuery = "SELECT COUNT(br) FROM BloodRequest br " +
            "WHERE (:bg IS NULL OR br.bloodGroup = :bg) " +
            "AND (:urg IS NULL OR br.urgency = :urg) " +
            "AND (:loc = '' OR lower(br.location) LIKE lower(concat('%', :loc, '%'))) " +
            "AND (:status IS NULL OR br.status = :status)"
    )
    fun findRequests(
        @Param("bg") bloodGroup: BloodGroup?,
        @Param("urg") urgency: Urgency?,
        @Param("loc") location: String,
        @Param("status") status: BloodRequestStatus?,
        pageable: Pageable
    ): Page<BloodRequest>

    /*
     * Nightly expiry: OPEN requests whose needed_by passed become EXPIRED.
     * Bulk update (no entity loading); updatedAt stamped explicitly since
     * @PreUpdate does not fire on bulk queries. Returns rows updated.
     */
    @Modifying
    @Query(
        "UPDATE BloodRequest br SET br.status = :expired, br.updatedAt = :now " +
        "WHERE br.status = :open AND br.neededBy < :today"
    )
    fun expireOverdue(
        @Param("open") open: BloodRequestStatus,
        @Param("expired") expired: BloodRequestStatus,
        @Param("today") today: LocalDate,
        @Param("now") now: LocalDateTime
    ): Int
}
