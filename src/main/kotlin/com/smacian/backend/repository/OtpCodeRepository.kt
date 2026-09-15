/*
 * OtpCodeRepository - Data Access Layer for the OtpCode entity.
 *
 * Manages database operations for OTP records. Uses both named query
 * methods AND a custom @Query for the bulk UPDATE.
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.OtpCode
import com.smacian.backend.entity.enums.OtpPurpose
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface OtpCodeRepository : JpaRepository<OtpCode, Long> {

    /*
     * SQL (approximately):
     *   SELECT * FROM otp_codes
     *   WHERE contact = :contact AND purpose = :purpose AND used = false
     *   ORDER BY created_at DESC LIMIT 1
     *
     * Gets the LATEST unused OTP (users may request multiple in a row).
     */
    fun findTopByContactAndPurposeAndUsedFalseOrderByCreatedAtDesc(
        contact: String,
        purpose: OtpPurpose
    ): Optional<OtpCode>

    /*
     * Marks ALL unused OTPs as "used" for a contact+purpose.
     * Successful verification invalidates any older codes.
     *
     * SQL: UPDATE otp_codes SET used = true
     *      WHERE contact = :contact AND purpose = :purpose AND used = false
     */
    @Modifying // @Modifying marks this as an UPDATE, not a SELECT
    @Query(
        "UPDATE OtpCode o SET o.used = true " +
        "WHERE o.contact = :contact AND o.purpose = :purpose AND o.used = false"
    )
    fun markAllAsUsed(@Param("contact") contact: String, @Param("purpose") purpose: OtpPurpose)

    /*
     * SQL: DELETE FROM otp_codes WHERE expires_at < :expiresAt AND used = false
     * Cleanup method called by the scheduled task.
     */
    fun deleteByExpiresAtBeforeAndUsedFalse(expiresAt: LocalDateTime)
}