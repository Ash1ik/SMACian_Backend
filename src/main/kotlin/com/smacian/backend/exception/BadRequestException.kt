/*
 * BadRequestException - Thrown when user input is valid in format but
 * violates a business rule.
 *
 * Examples:
 *   - Trying to register with an already-used email
 *   - OTP is expired
 *   - User is under 13 years old
 *
 * Returns HTTP 400 (Bad Request) with a readable message.
 *
 * Flow: Service throws → GlobalExceptionHandler catches → JSON error out.
 */
package com.smacian.backend.exception

class BadRequestException(message: String) : RuntimeException(message)