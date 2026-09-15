/*
 * OtpPurpose - Tells us WHY an OTP (One-Time Password) was created.
 *
 * OTP codes are used for two different things:
 *   1. REGISTRATION   - verifying the user's email/phone during signup
 *   2. PASSWORD_RESET - verifying identity before choosing a new password
 *
 * Storing the purpose prevents a registration OTP from being used
 * to reset a password and vice versa.
 */
package com.smacian.backend.entity.enums

enum class OtpPurpose {
    REGISTRATION,
    PASSWORD_RESET
}