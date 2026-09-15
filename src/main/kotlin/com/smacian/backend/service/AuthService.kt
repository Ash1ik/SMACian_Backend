/*
 * AuthService - ALL the business logic for authentication.
 *
 * The "brain" of the auth feature. Controllers are thin - they receive
 * HTTP requests and delegate here. This service does the real thinking
 * (validation, DB checks, business rules).
 *
 * Public methods:
 *   1. register(...)               - creates a new user account
 *   2. login(...)                  - verifies credentials → returns JWT
 *   3. sendRegistrationOtp(...)    - OTP to verify contact during signup
 *   4. sendForgotPasswordOtp(...)  - OTP for password reset
 *   5. resetPassword(...)          - verify OTP + set new password
 */
package com.smacian.backend.service

import com.smacian.backend.dto.request.LoginRequest
import com.smacian.backend.dto.request.RegisterRequest
import com.smacian.backend.dto.request.ResetPasswordRequest
import com.smacian.backend.dto.response.AuthResponse
import com.smacian.backend.dto.response.UserResponse
import com.smacian.backend.entity.User
import com.smacian.backend.entity.enums.OtpPurpose
import com.smacian.backend.exception.BadRequestException
import com.smacian.backend.exception.ResourceNotFoundException
import com.smacian.backend.repository.UserRepository
import com.smacian.backend.security.JwtService
import com.smacian.backend.util.ContactUtils
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val otpService: OtpService,
    private val cloudinaryService: CloudinaryService
) {

    // ====================================================================
    // 1. REGISTRATION
    // ====================================================================
    /*
     * Creates a new user account (called after OTP verification, step 7).
     *
     * a. Validate contact format
     * b. Verify the OTP code
     * c. Check contact isn't already registered
     * d. Validate age (13+)
     * e. Hash password with BCrypt
     * f. Save user
     * g. Generate JWT (auto-login)
     */
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {

        // ---- a. Normalize + validate contact ----
        val contact = ContactUtils.normalize(request.contact) ?: ""
        ContactUtils.validateContact(contact)

        // ---- b. Verify the OTP (must have been sent first) ----
        otpService.verifyOtp(contact, request.otpCode, OtpPurpose.REGISTRATION)

        // ---- c. Check uniqueness ----
        checkContactIsAvailable(contact)

        // ---- d. Validate date of birth (13+ years old) ----
        val dob = parseAndValidateDateOfBirth(request.dateOfBirth)

        // ---- e. Build the user ----
        val user = User().apply {
            firstName = request.firstName.trim()
            lastName = request.lastName.trim()
            dateOfBirth = dob
            this.gender = request.gender
            passwordHash = passwordEncoder.encode(request.password)  // BCrypt hash

            // Set email OR phone depending on contact type
            if (ContactUtils.isEmail(contact)) {
                this.email = contact
            } else {
                this.phone = contact
            }

            // Default initials avatar (if user skipped photo upload)
            profilePhotoUrl = cloudinaryService.getDefaultAvatarUrl(firstName, lastName)

            // ---- f. Terms acceptance ----
            termsAccepted = true
            termsAcceptedAt = LocalDateTime.now()
            isActive = true
        }

        // ---- g. Save to database ----
        val savedUser = userRepository.save(user)

        // ---- h. Generate JWT → user is logged in immediately ----
        val token = jwtService.generateToken(savedUser)
        return AuthResponse(token = token, user = UserResponse.fromEntity(savedUser))
    }

    // ====================================================================
    // 2. LOGIN
    // ====================================================================
    /*
     * Verifies credentials and returns a JWT token.
     *
     * SECURITY DETAIL: both "user not found" and "wrong password" throw
     * the SAME generic error - attackers can't discover which emails
     * or phones are registered.
     */
    @Transactional(readOnly = true)
    fun login(request: LoginRequest): AuthResponse {

        val contact = ContactUtils.normalize(request.contact) ?: ""
        ContactUtils.validateContact(contact)

        // Find the user by email or phone.
        val user = findByContact(contact)
            .orElseThrow { BadCredentialsException("Invalid credentials") }

        // Compare entered password against stored BCrypt hash.
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            // Same generic message - don't reveal which part was wrong.
            throw BadCredentialsException("Invalid credentials")
        }

        // Success → generate the JWT token.
        val token = jwtService.generateToken(user)
        return AuthResponse(token = token, user = UserResponse.fromEntity(user))
    }

    // ====================================================================
    // 3. SEND OTP (during registration - contact verification)
    // ====================================================================
    @Transactional
    fun sendRegistrationOtp(contactRaw: String) {

        val contact = ContactUtils.normalize(contactRaw) ?: ""
        ContactUtils.validateContact(contact)

        // Don't send OTP to an already-registered account.
        if (findByContact(contact).isPresent) {
            throw BadRequestException("An account with this contact already exists. Please log in instead")
        }

        otpService.sendOtp(contact, OtpPurpose.REGISTRATION)
    }

    // ====================================================================
    // 4. SEND OTP (forgot password)
    // ====================================================================
    @Transactional
    fun sendForgotPasswordOtp(contactRaw: String) {

        val contact = ContactUtils.normalize(contactRaw) ?: ""
        ContactUtils.validateContact(contact)

        // Only send if an account exists (it's the user's own contact).
        findByContact(contact).orElseThrow {
            ResourceNotFoundException("No account found with this contact. Please check and try again")
        }

        otpService.sendOtp(contact, OtpPurpose.PASSWORD_RESET)
    }

    // ====================================================================
    // 5. RESET PASSWORD (forgot password - final step)
    // ====================================================================
    /*
     * a. Verify OTP again (extra security)
     * b. Passwords must match
     * c. Find user
     * d. Hash + save the new password
     */
    @Transactional
    fun resetPassword(request: ResetPasswordRequest) {

        val contact = ContactUtils.normalize(request.contact) ?: ""
        ContactUtils.validateContact(contact)

        // ---- a. Verify OTP (PASSWORD_RESET purpose) ----
        otpService.verifyOtp(contact, request.otpCode, OtpPurpose.PASSWORD_RESET)

        // ---- b. Passwords must match ----
        if (request.newPassword != request.confirmPassword) {
            throw BadRequestException("New password and confirmation do not match")
        }

        // ---- c. Find user ----
        val user = findByContact(contact).orElseThrow {
            ResourceNotFoundException("No account found with this contact")
        }

        // ---- d. Update the password hash ----
        user.passwordHash = passwordEncoder.encode(request.newPassword)
        userRepository.save(user)
    }

    // ====================================================================
    // HELPER METHODS
    // ====================================================================

    /*
     * Finds a user by either email OR phone.
     * Returns an empty Optional if the user doesn't exist.
     */
    private fun findByContact(contact: String): java.util.Optional<User> =
        if (ContactUtils.isEmail(contact)) {
            userRepository.findByEmail(contact)
        } else {
            userRepository.findByPhone(contact)
        }

    /*
     * Throws an error if the email/phone is already registered.
     */
    private fun checkContactIsAvailable(contact: String) {
        if (ContactUtils.isEmail(contact)) {
            if (userRepository.existsByEmail(contact)) {
                throw BadRequestException("An account with this email already exists")
            }
        } else {
            if (userRepository.existsByPhone(contact)) {
                throw BadRequestException("An account with this phone number already exists")
            }
        }
    }

    /*
     * Parses "yyyy-MM-dd" into LocalDate and validates the age (13+).
     */
    private fun parseAndValidateDateOfBirth(dobRaw: String): LocalDate {

        val dob = try {
            LocalDate.parse(dobRaw)
        } catch (e: Exception) {
            throw BadRequestException("Invalid date of birth format. Please use yyyy-MM-dd (e.g. 1998-01-12)")
        }

        // Cannot be in the future.
        if (dob.isAfter(LocalDate.now())) {
            throw BadRequestException("Date of birth cannot be in the future")
        }

        // Must be at least 13 years old.
        val age = Period.between(dob, LocalDate.now()).years
        if (age < 13) {
            throw BadRequestException("You must be at least 13 years old to create an account")
        }

        return dob
    }
}