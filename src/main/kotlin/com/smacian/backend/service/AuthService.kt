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

import com.smacian.backend.dto.request.ChangePasswordRequest
import com.smacian.backend.dto.request.GoogleLoginRequest
import com.smacian.backend.dto.request.LoginRequest
import com.smacian.backend.dto.request.RegisterRequest
import com.smacian.backend.dto.request.ResetPasswordRequest
import com.smacian.backend.dto.response.AuthResponse
import com.smacian.backend.dto.response.LoginResponse
import com.smacian.backend.dto.response.UserResponse
import com.smacian.backend.entity.User
import com.smacian.backend.entity.enums.OtpPurpose
import com.smacian.backend.exception.BadRequestException
import com.smacian.backend.exception.ResourceNotFoundException
import com.smacian.backend.repository.UserRepository
import com.smacian.backend.security.JwtService
import com.smacian.backend.util.ContactUtils
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.springframework.beans.factory.annotation.Value
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
    private val cloudinaryService: CloudinaryService,
    @Value("\${GOOGLE_WEB_CLIENT_ID:}") private val googleWebClientId: String
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
    fun login(request: LoginRequest): LoginResponse {

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
        return LoginResponse(token = token)
    }

    // ====================================================================
    // 2b. GOOGLE SIGN-IN (Android Credential Manager ID token)
    // ====================================================================
    /*
     * a. Fail closed when GOOGLE_WEB_CLIENT_ID is missing (never accept
     *    tokens we can't check an audience against).
     * b. Verify signature + expiry + audience server-side against Google's
     *    certs. Malformed tokens throw; bad signature/audience/expiry
     *    returns null. BOTH become 401 - claims are never trusted raw.
     * c. Identify by token `sub` (stable Google id), NOT email alone:
     *      sub known          -> log in (no duplicate rows, ever)
     *      email known        -> link googleSub, log in (password login kept)
     *      neither            -> auto-register from token claims, log in
     * d. Google-verified emails skip OTP entirely. ID tokens carry no DOB,
     *    so Google users get dateOfBirth = null (profile update requires it
     *    later). Avatar hotlinks the Google picture URL when present.
     *
     * Returns LoginResponse - the EXACT shape as POST /api/auth/login
     * (token only), so the app reuses its login flow unchanged.
     */
    @Transactional
    fun googleLogin(request: GoogleLoginRequest): LoginResponse {

        if (googleWebClientId.isBlank()) {
            throw IllegalStateException("Google sign-in is not configured (GOOGLE_WEB_CLIENT_ID missing)")
        }

        val rawToken = request.idToken.trim()
        if (rawToken.isEmpty()) {
            throw BadCredentialsException("Invalid Google token")
        }

        val verifier = GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(listOf(googleWebClientId))
            .setIssuer("https://accounts.google.com")
            .build()

        val idToken = try {
            verifier.verify(rawToken)
        } catch (e: Exception) {
            null
        } ?: throw BadCredentialsException("Invalid Google token")

        val payload = idToken.payload
        val sub = payload.subject?.takeIf { it.isNotBlank() }
            ?: throw BadCredentialsException("Invalid Google token")

        // ---- known Google account -> log in ----
        val existingBySub = userRepository.findByGoogleSub(sub).orElse(null)
        if (existingBySub != null) {
            return LoginResponse(token = jwtService.generateToken(existingBySub))
        }

        // ---- email gate: must exist AND be Google-verified ----
        val email = (payload.email ?: "").trim().lowercase()
        if (email.isEmpty() || payload.emailVerified != true) {
            throw BadCredentialsException("Invalid Google token")
        }

        // ---- known email (OTP-registered) -> link sub, log in ----
        // Password login keeps working afterwards (hash untouched).
        val existingByEmail = findByContact(email).orElse(null)
        if (existingByEmail != null) {
            existingByEmail.googleSub = sub
            userRepository.save(existingByEmail)
            return LoginResponse(token = jwtService.generateToken(existingByEmail))
        }

        // ---- brand new -> auto-register from token claims, no OTP ----
        val givenName = (payload["given_name"] as? String)?.trim().takeIf { !it.isNullOrEmpty() }
            ?: email.substringBefore("@").take(50)
        val familyName = (payload["family_name"] as? String)?.trim() ?: ""
        val picture = (payload["picture"] as? String)?.trim()?.takeIf { it.isNotEmpty() }

        val user = User().apply {
            this.email = email
            firstName = givenName.take(50)
            lastName = familyName.take(50)
            // Random unusable secret: password login can never match it.
            passwordHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString())
            googleSub = sub
            profilePhotoUrl = picture
                ?: cloudinaryService.getDefaultAvatarUrl(firstName, lastName)
            termsAccepted = true
            termsAcceptedAt = LocalDateTime.now()
            isActive = true
        }

        val savedUser = userRepository.save(user)
        return LoginResponse(token = jwtService.generateToken(savedUser))
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
    // 4b. CHANGE PASSWORD - logged-in user, knows current password
    // ====================================================================
    /*
     * Unlike resetPassword (OTP, no login), this requires the JWT AND the
     * current password. 400 (not 401) on a wrong current password - the
     * caller IS authenticated, only the input is wrong.
     *
     * NOTE: stateless JWTs can't be revoked: other sessions stay valid
     * until their 24h expiry. Document, don't solve, at this scale.
     */
    @Transactional
    fun changePassword(userId: Long, request: ChangePasswordRequest) {

        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User account not found")
        }

        // ---- a. Prove knowledge of the current password ----
        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw BadRequestException("Current password is incorrect")
        }

        // ---- b. New passwords must match ----
        if (request.newPassword != request.confirmPassword) {
            throw BadRequestException("New password and confirmation do not match")
        }

        // ---- c. Refuse a no-op change (same password, new hash or not) ----
        if (passwordEncoder.matches(request.newPassword, user.passwordHash)) {
            throw BadRequestException("New password must be different from the current password")
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