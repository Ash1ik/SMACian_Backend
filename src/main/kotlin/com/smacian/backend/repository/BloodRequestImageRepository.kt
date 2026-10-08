/*
 * BloodRequestImageRepository - Data Access Layer for BloodRequestImage.
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.BloodRequestImage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface BloodRequestImageRepository : JpaRepository<BloodRequestImage, Long> {

    /*
     * Full metadata for ONE request, no BYTEA: (id, width, height, sortOrder),
     * upload order. Used by single-request mapping.
     */
    @Query(
        "SELECT bi.id, bi.width, bi.height, bi.sortOrder FROM BloodRequestImage bi " +
        "WHERE bi.bloodRequest.id = :requestId ORDER BY bi.sortOrder ASC"
    )
    fun findMetadataByRequestIdOrdered(@Param("requestId") requestId: Long): List<Array<Any>>

    /*
     * Metadata for a WHOLE page of requests in ONE query, no BYTEA:
     * (requestId, id, width, height, sortOrder), ordered by sort_order.
     * The service groups per request preserving upload order.
     */
    @Query(
        "SELECT bi.bloodRequest.id, bi.id, bi.width, bi.height, bi.sortOrder FROM BloodRequestImage bi " +
        "WHERE bi.bloodRequest.id IN :requestIds ORDER BY bi.sortOrder ASC"
    )
    fun findMetadataByRequestIds(@Param("requestIds") requestIds: List<Long>): List<Array<Any>>

    /*
     * Delete ALL images of a request in ONE statement (no SELECT, no BYTEA).
     * Returns rows deleted.
     */
    fun deleteByBloodRequestId(requestId: Long): Long
}
