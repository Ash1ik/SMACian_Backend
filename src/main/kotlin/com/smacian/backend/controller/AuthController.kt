/*
 * AuthController - REST endpoints for authentication & registration.
 *
 * A Controller's ONLY job is to receive HTTP requests and return HTTP
 * responses. All real logic lives in AuthService and OtpService.
 *
 * These endpoints are PUBLIC (no login needed):
 *
 *   POST /api/auth/register          → create a new account
 *   POST /api/auth/login             → login, get a JWT token
 *   POST /api/auth/otp/send          → request a registration OTP
 *   POST /api/auth/otp/verify        → verify an OTP
 *   POST /api/auth/forgot-password   → request a password-reset OTP
 *   POST /api/auth/reset-password    → set a new password
 */
package com.smacian.backend.controller

import com.smacian.backend.dto.request.LoginRequest
import com.smacian.backend.dto.request.OtpSendRequest
import com.smacian.backend.dto.request.OtpVerifyRequest
import com.smacian.backend.dto.request.RegisterRequest
import com.smacian.backend.dto.request.ResetPasswordRequest
import com.smacian.backend.dto.response.AuthResponse
import com.smacian.backend.dto.response.LoginResponse
import com.smacian.backend.dto.response.MessageResponse
import com.smacian.backend.entity.enums.OtpPurpose
import com.smacian.backend.service.AuthService
import com.smacian.backend.service.OtpService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val otpService: OtpService
) {

    // ====================================================================
    // 1. REGISTER - create a new account (final step of signup)
    // ====================================================================
    // @Valid triggers ALL validation rules in RegisterRequest automatically
    // (empty names, weak passwords, missing OTP, etc.).

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val response = authService.register(request)
        // 201 Created = "a new resource was successfully created".
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    // ====================================================================
    // 2. LOGIN - verify credentials, get a JWT token
    // ====================================================================

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> =
        ResponseEntity.ok(authService.login(request))

    // ====================================================================
    // 3. OTP SEND - during registration (contact verification)
    // ====================================================================
    // The app calls this at the "Code" step. We send a 6-digit code
    // to the user's email/phone.

    @PostMapping("/otp/send")
    fun sendRegistrationOtp(@Valid @RequestBody request: OtpSendRequest): ResponseEntity<MessageResponse> {
        authService.sendRegistrationOtp(request.contact)
        return ResponseEntity.ok(MessageResponse(true, "OTP sent successfully. It expires in 10 minutes"))
    }

    // ====================================================================
    // 4. OTP VERIFY - the user entered a code; check it's correct.
    // ====================================================================
    // We use validateOtp (NOT verifyOtp) so the code is NOT consumed yet -
    // the final register() call consumes it. This prevents a double-
    // verification failure in the app's flow (step 7 → finish).

    @PostMapping("/otp/verify")
    fun verifyOtp(@Valid @RequestBody request: OtpVerifyRequest): ResponseEntity<MessageResponse> {
        otpService.validateOtp(request.contact, request.otpCode, OtpPurpose.REGISTRATION)
        return ResponseEntity.ok(MessageResponse(true, "Contact verified successfully. You can now register"))
    }

    // ====================================================================
    // 5. FORGOT PASSWORD - step 1: send OTP for the account
    // ====================================================================

    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody request: OtpSendRequest): ResponseEntity<MessageResponse> {
        authService.sendForgotPasswordOtp(request.contact)
        return ResponseEntity.ok(MessageResponse(true, "Password reset OTP sent successfully. It expires in 10 minutes"))
    }

    // ====================================================================
    // 6. RESET PASSWORD - step 2: verify OTP + set new password
    // ====================================================================

    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<MessageResponse> {
        authService.resetPassword(request)
        return ResponseEntity.ok(MessageResponse(true, "Password has been updated successfully. You can now log in with your new password"))
    }
}