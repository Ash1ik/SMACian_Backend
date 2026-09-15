/*
 * ErrorResponse - The standard JSON format for ALL error responses.
 *
 * Every error (validation, business logic, unexpected) is converted to
 * this shape by the GlobalExceptionHandler before returning to the app.
 *
 * Example:
 * {
 *   "success": false,
 *   "timestamp": "2026-09-14T10:30:00",
 *   "status": 400,
 *   "error": "Validation Failed",
 *   "message": "Please fix the following errors",
 *   "path": "/api/auth/register",
 *   "fieldErrors": [
 *     { "field": "password", "message": "Password must contain a digit" }
 *   ]
 * }
 *
 * fieldErrors is only set for validation errors; it stays empty otherwise.
 */
package com.smacian.backend.exception

import java.time.LocalDateTime

data class ErrorResponse(
    val success: Boolean = false,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val fieldErrors: List<FieldErrorDetail>? = null
) {

    /*
     * A single field-level validation error.
     * Example: { "field": "password", "message": "Password is required" }
     */
    data class FieldErrorDetail(
        val field: String,
        val message: String
    )
}