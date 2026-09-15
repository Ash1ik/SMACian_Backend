/*
 * ContactUtils - Helper for our unified "contact" field.
 *
 * Users register/login with either an EMAIL or a PHONE number.
 * The app sends ONE field called "contact". This object detects which type:
 *
 *   - Contains "@"      → EMAIL   (e.g. ahmed@example.com)
 *   - All digits 7-15   → PHONE   (e.g. 01712345678)
 *   - Anything else     → INVALID
 *
 * A Kotlin `object` = a singleton (single instance shared everywhere).
 * It replaces Java's "static methods" utility class pattern.
 */
package com.smacian.backend.util

import com.smacian.backend.exception.BadRequestException

object ContactUtils {

    // A simple email format check: text@text.text
    private const val EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"

    // Phone: only digits, 7 to 15 characters (international format).
    private const val PHONE_REGEX = "^[0-9]{7,15}$"

    /*
     * Checks if a contact value is an email address.
     * Rule: contains "@".
     */
    fun isEmail(contact: String?): Boolean =
        !contact.isNullOrEmpty() && contact.contains("@")

    /*
     * Checks if a contact value is a valid phone number.
     * Rule: only digits, length 7-15.
     */
    fun isPhone(contact: String?): Boolean =
        !contact.isNullOrEmpty() && contact.matches(PHONE_REGEX.toRegex())

    /*
     * Validates a contact and throws a readable error if invalid.
     * A valid contact is EITHER a valid email OR a valid phone.
     */
    fun validateContact(contact: String?) {
        if (contact.isNullOrBlank()) {
            throw BadRequestException("Please enter your email or phone number")
        }
        when {
            isEmail(contact) -> {
                // Email must look like someone@something.com
                if (!contact.matches(EMAIL_REGEX.toRegex())) {
                    throw BadRequestException("Please enter a valid email address")
                }
            }
            isPhone(contact) -> {
                // Phone already passed the digits-only check
            }
            else -> {
                throw BadRequestException("Please enter a valid email address or phone number")
            }
        }
    }

    /*
     * Normalizes a contact for consistent storage/comparison:
     *   - Email: trims spaces + lowercases ("Ahmed@Example.COM" → "ahmed@example.com")
     *   - Phone: keeps digits only ("01712-345 678" → "01712345678")
     *
     * This way "  Ahmed@Example.COM " and "ahmed@example.com"
     * are recognized as the same account.
     */
    fun normalize(contact: String?): String? {
        if (contact.isNullOrBlank()) return null

        val trimmed = contact.trim()
        return if (isEmail(trimmed)) {
            trimmed.lowercase()                        // emails are case-insensitive
        } else {
            trimmed.replace(Regex("[^0-9]"), "")       // phone: digits only
        }
    }
}