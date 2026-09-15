/*
 * MessageResponse - A simple wrapper for success/informational messages.
 *
 * Some endpoints don't return data - just a friendly message.
 * Example: POST /api/auth/otp/send returns:
 * { "success": true, "message": "OTP sent successfully to your email" }
 */
package com.smacian.backend.dto.response

data class MessageResponse(
    val success: Boolean,
    val message: String
)