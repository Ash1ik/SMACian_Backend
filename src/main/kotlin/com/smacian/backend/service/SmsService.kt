/*
 * SmsService - Sends OTP codes via Twilio's Messages API.
 *
 * Mirrors EmailService: blank credentials = skip quietly (console-only,
 * dev mode). Twilio trial accounts can only SMS VERIFIED numbers from
 * the trial sender - unverified destinations get a Twilio 21608 error,
 * which surfaces as a 500 with the Twilio message in the server log.
 *
 * Numbers arrive as normalized digits (ContactUtils strips to 0-9).
 * toE164() converts Bangladeshi formats (017.., 88017.., 171..) to E.164;
 * anything else is rejected with a 400 (this app serves BD users).
 */
package com.smacian.backend.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.util.Base64

@Service
class SmsService(
    @Value("\${twilio.account-sid:}") private val accountSid: String,
    @Value("\${twilio.auth-token:}") private val authToken: String,
    @Value("\${twilio.from-number:}") private val fromNumber: String
) {

    private val log = LoggerFactory.getLogger(SmsService::class.java)

    private val restClient: RestClient = RestClient.builder().build()

    /*
     * Sends the OTP text. toDigits = normalized digits from OtpService.
     * Skips quietly when Twilio is not configured (dev mode).
     */
    fun sendOtpSms(toDigits: String, code: String) {

        if (accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) {
            log.warn("Twilio not configured (TWILIO_* missing). OTP for {} stays console-only.", mask(toDigits))
            return
        }

        val to = toE164(toDigits)
            ?: throw com.smacian.backend.exception.BadRequestException(
                "Only Bangladeshi mobile numbers are supported for SMS OTP"
            )

        log.info("Sending OTP SMS to {}", mask(to))

        val form = LinkedMultiValueMap<String, String>()
        form.add("To", to)
        form.add("From", fromNumber)
        form.add("Body", "Your SMACian verification code is: $code. It expires in 10 minutes.")

        val basic = Base64.getEncoder()
            .encodeToString("$accountSid:$authToken".toByteArray(Charsets.UTF_8))

        try {
            restClient.post()
                .uri("https://api.twilio.com/2010-04-01/Accounts/$accountSid/Messages.json")
                .header("Authorization", "Basic $basic")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity()

            log.info("Twilio SMS sent to {}", mask(to))
        } catch (e: RestClientResponseException) {
            log.error("Twilio SMS FAILED: {} - {}", e.statusCode, e.responseBodyAsString)
            throw RuntimeException("Failed to send OTP SMS. Check the SMS configuration", e)
        }
    }

    /*
     * Bangladeshi digits -> E.164. "01712345678" -> "+8801712345678",
     * "8801712345678" -> "+8801712345678", "1712345678" -> "+8801712345678".
     * Null when not a recognizable BD mobile (caller rejects with 400).
     */
    fun toE164(digits: String): String? {
        val d = digits.trim()
        if (!d.all { it.isDigit() }) return null
        return when {
            d.startsWith("880") && d.length == 13 -> "+$d"
            d.startsWith("0") && d.length == 11 -> "+880" + d.drop(1)
            d.startsWith("1") && d.length == 10 -> "+880$d"
            else -> null
        }
    }

    private fun mask(e164: String): String =
        if (e164.length <= 4) "****" else "***" + e164.takeLast(4)
}
