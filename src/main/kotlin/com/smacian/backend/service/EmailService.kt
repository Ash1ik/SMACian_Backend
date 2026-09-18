/*
 * EmailService - Sends transactional emails (OTP codes) using Brevo's API.
 *
 * Brevo free plan: up to 300 emails/day - perfect for OTP delivery.
 *
 * Delivery:
 *   - If BREVO_API_KEY is not set (e.g. local learning), the OTP is only
 *     printed to the console by OtpService - email sending is skipped.
 *   - If it is set, otp/send also emails the code to the user.
 */
package com.smacian.backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Service
class EmailService(
    @Value("\${brevo.api-key:}") private val brevoApiKey: String,
    @Value("\${brevo.sender-email:}") private val senderEmail: String
) {

    private val log = LoggerFactory.getLogger(EmailService::class.java)

    // Brevo transactional email endpoint.
    private val brevoApiUrl = "https://api.brevo.com/v3/smtp/email"

    // Reusable HTTP client (Spring's modern RestClient).
    private val restClient: RestClient = RestClient.builder().build()

    private val objectMapper = ObjectMapper()

    /*
     * Composes and sends the OTP email.
     * Skips quietly when email is not configured (dev mode).
     */
    fun sendOtpEmail(toEmail: String, code: String) {

        if (brevoApiKey.isBlank() || senderEmail.isBlank()) {
            log.warn("Brevo email not configured (BREVO_API_KEY / BREVO_SENDER_EMAIL missing). OTP for {} stays console-only.", toEmail)
            return
        }

        log.info("Sending OTP email to {}", toEmail)

        val requestBody = mapOf(
            "sender" to mapOf("name" to "SMACian", "email" to senderEmail),
            "to" to listOf(mapOf("email" to toEmail)),
            "subject" to "Your SMACian verification code",
            "htmlContent" to """
                <div style="font-family:Arial,sans-serif;padding:20px">
                    <h2 style="color:#333;">SMACian Verification Code</h2>
                    <p>Your one-time code is:</p>
                    <p style="font-size:32px;font-weight:bold;letter-spacing:4px;color:#1a73e8;">$code</p>
                    <p>This code expires in <strong>10 minutes</strong>.</p>
                    <p style="color:#888;font-size:12px;">If you didn't request this, you can safely ignore this email.</p>
                </div>
            """.trimIndent()
        )

        try {
            @Suppress("UNCHECKED_CAST")
            val response = restClient.post()
                .uri(brevoApiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("api-key", brevoApiKey)
                .accept(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(requestBody))
                .retrieve()
                .toEntity(String::class.java)

            log.info("Brevo email sent to {} -> {}", toEmail, response.statusCode)
        } catch (e: RestClientResponseException) {
            log.error("Brevo email FAILED: {} - {}", e.statusCode, e.responseBodyAsString)
            throw RuntimeException("Failed to send OTP email. Check the email configuration", e)
        }
    }
}