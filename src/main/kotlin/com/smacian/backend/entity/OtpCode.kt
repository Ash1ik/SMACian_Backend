/*
 * OtpCode - JPA Entity (Kotlin version)
 *
 * Maps to the "otp_codes" table. Every OTP we send creates a row here.
 *
 * An OTP is a 6-digit number (e.g. "482917") sent to a user's email/phone
 * to prove ownership. Lifecycle:
 *   1. sendOtp() → record created with used=false
 *   2. verifyOtp() → checks code + expiry → marks used=true
 */
package com.smacian.backend.entity

import com.smacian.backend.entity.enums.OtpPurpose
import java.time.LocalDateTime
import jakarta.persistence.*

@Entity
@Table(name = "otp_codes")
class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    // The email/phone where the OTP was sent.
    @field:Column(name = "contact", nullable = false, length = 255)
    var contact: String = ""

    // The 6-digit code. String, not a number - to preserve leading zeros
    // like "005832".
    @field:Column(name = "code", nullable = false, length = 6)
    var code: String = ""

    // Purpose: REGISTRATION or PASSWORD_RESET.
    @field:Column(name = "purpose", nullable = false)
    @Enumerated(EnumType.STRING)
    var purpose: OtpPurpose = OtpPurpose.REGISTRATION

    // When this OTP stops being valid (10 minutes after creation).
    @field:Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime = LocalDateTime.now()

    // Once verified successfully, we mark it used so it can't be reused.
    @field:Column(name = "used", nullable = false)
    var used: Boolean = false

    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @PrePersist
    fun onCreate() {
        this.createdAt = LocalDateTime.now()
    }
}