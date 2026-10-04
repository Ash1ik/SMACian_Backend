/*
 * OtpService - Handles OTP (One-Time Password) codes.
 *
 * An OTP is a 6-digit code (e.g. "482917") that proves a user owns a
 * particular email/phone. Used for:
 *   1. REGISTRATION    - verifying contact during signup
 *   2. PASSWORD_RESET  - verifying identity before resetting password
 *
 * Flow:
 *   sendOtp()     → generate random 6-digit code → store → "deliver"
 *   verifyOtp()   → check code, expiry, purpose → mark as used (consuming)
 *   validateOtp() → check code, expiry, purpose → do NOT mark as used
 *
 * DELIVERY NOTE (learning version):
 *   The OTP is printed in the server console so you can see it.
 *   To send real email/SMS later, replace deliverOtp() with your
 *   free provider (SendGrid for email, Twilio free trial for SMS).
 */
package com.smacian.backend.service

import com.smacian.backend.entity.OtpCode
import com.smacian.backend.entity.enums.OtpPurpose
import com.smacian.backend.exception.BadRequestException
import com.smacian.backend.exception.TooManyRequestsException
import com.smacian.backend.repository.OtpCodeRepository
import com.smacian.backend.util.ContactUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.security.SecureRandom
import java.time.LocalDateTime

@Service
class OtpService(
    private val otpCodeRepository: OtpCodeRepository,
    private val emailService: EmailService
) {

    private val log = LoggerFactory.getLogger(OtpService::class.java)

    // OTP is valid for 10 minutes.
    private val OTP_EXPIRY_MINUTES = 10L

    // Anti-abuse limits: resend cooldown + max wrong guesses per code.
    private val OTP_RESEND_COOLDOWN_SECONDS = 60L
    private val OTP_MAX_ATTEMPTS = 5

    // Cryptographically-secure random generator.
    private val random = SecureRandom()

    /*
     * Step 1: Generate a random 6-digit code.
     * Stored as String to preserve leading zeros ("005832").
     */
    private fun generateCode(): String =
        (100000 + random.nextInt(900000)).toString()

    /*
     * Creates and "sends" an OTP for a contact + purpose.
     */
    @Transactional
    fun sendOtp(contact: String, purpose: OtpPurpose) {

        // Resend cooldown: one code per contact+purpose per minute.
        // Stops inbox-spam via our Brevo quota and OTP-flooding.
        otpCodeRepository.findTopByContactAndPurposeOrderByCreatedAtDesc(contact, purpose)
            .ifPresent { latest ->
                val secondsSinceLast = java.time.Duration.between(latest.createdAt, LocalDateTime.now()).seconds
                if (secondsSinceLast < OTP_RESEND_COOLDOWN_SECONDS) {
                    throw TooManyRequestsException(
                        "Please wait ${OTP_RESEND_COOLDOWN_SECONDS - secondsSinceLast} seconds before requesting a new code"
                    )
                }
            }

        // Build the OTP record.
        val otpCode = OtpCode().apply {
            this.contact = contact
            code = generateCode()
            this.purpose = purpose
            used = false
            expiresAt = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES)
        }

        // Save to database.
        otpCodeRepository.save(otpCode)

        // ===== DELIVERY (after commit) =====
        // The Brevo HTTPS call runs AFTER the transaction commits, so a slow
        // or failing mail provider can never hold a pooled DB connection and
        // a mail failure can never roll back the OTP row (user just retries).
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    try {
                        deliverOtp(contact, otpCode.code)
                    } catch (e: Exception) {
                        log.error("Post-commit OTP delivery failed for {}", contact, e)
                    }
                }
            }
        )
    }

    /*
     * CONSUMING verification - marks the OTP as used.
     * Called by register() and resetPassword() (the final consuming step).
     */
    @Transactional
    fun verifyOtp(contact: String, code: String, purpose: OtpPurpose): OtpCode {

        // Shared validation logic.
        val otpCode = findAndValidate(contact, code, purpose)

        // Success → mark as used (prevents reuse).
        otpCode.used = true
        otpCodeRepository.save(otpCode)

        // Invalidate ALL other unused OTPs for this contact too.
        otpCodeRepository.markAllAsUsed(contact, purpose)

        return otpCode
    }

    /*
     * NON-CONSUMING check - validates but does NOT mark as used.
     *
     * Used by the standalone /api/auth/otp/verify endpoint so the app
     * can confirm the code during the signup Code step, BEFORE the
     * final register() call that actually consumes it.
     *
     * Without this, the flow would break:
     *   app verifies in step 7 (marks used)
     *   → register() tries to verify again → fails (already used)
     *
     * NOT read-only: wrong guesses increment the attempt counter, so the
     * brute-force budget is enforced on this endpoint too.
     */
    @Transactional
    fun validateOtp(contact: String, code: String, purpose: OtpPurpose) {
        // Just run the checks - no mutation.
        findAndValidate(contact, code, purpose)
    }

    /*
     * Shared validation logic: finds the latest unused OTP for the
     * contact+purpose and checks code match + expiry.
     */
    private fun findAndValidate(contact: String, code: String, purpose: OtpPurpose): OtpCode {

        // Find the LATEST unused OTP.
        val otpCode = otpCodeRepository
            .findTopByContactAndPurposeAndUsedFalseOrderByCreatedAtDesc(contact, purpose)
            .orElseThrow {
                BadRequestException("No valid OTP found. Please request a new code")
            }

        // Expired? (now is after expiresAt)
        if (otpCode.expiresAt.isBefore(LocalDateTime.now())) {
            throw BadRequestException("OTP has expired. Please request a new code")
        }

        // Guessing budget spent? Bounds the 6-digit brute-force space.
        if (otpCode.attempts >= OTP_MAX_ATTEMPTS) {
            throw TooManyRequestsException("Too many incorrect attempts. Please request a new code")
        }

        // Wrong code? Count the guess, then reject.
        if (otpCode.code != code) {
            otpCode.attempts = otpCode.attempts + 1
            otpCodeRepository.save(otpCode)
            throw BadRequestException("Incorrect OTP code. Please check and try again")
        }

        return otpCode
    }

    /*
     * The real "sending" logic.
     * Email contacts get the code delivered by Brevo; phone numbers are
     * logged to the console until an SMS provider is added.
     */
    private fun deliverOtp(contact: String, code: String) {
        // NOTE: the code itself is NEVER logged at INFO - logs are not a
        // secret store. For local testing without Brevo, enable DEBUG for
        // this class to see the code in the console.
        log.debug("OTP code for {} : {}", contact, code)
        log.info("Delivering OTP to {}", contact)

        // Email contacts -> real delivery via Brevo. Phones stay console-only for now.
        if (ContactUtils.isEmail(contact)) {
            emailService.sendOtpEmail(contact, code)
        }
    }

    /*
     * Deletes expired OTP records to keep the database tidy.
     * Called daily by ScheduledTasks.
     */
    @Transactional
    fun deleteExpiredOtps() {
        otpCodeRepository.deleteByExpiresAtBeforeAndUsedFalse(LocalDateTime.now())
    }
}