/*
 * GlobalExceptionHandler - THE central place where ALL errors are caught
 * and converted into clean, readable JSON responses.
 *
 * Instead of try-catch blocks everywhere, exceptions "bubble up" and are
 * handled here. @RestControllerAdvice applies this to ALL controllers.
 *
 * Flow:
 *   Controller/Service throws exception
 *        ↓
 *   This handler catches it (@ExceptionHandler)
 *        ↓
 *   Returns a consistent ErrorResponse JSON with the right HTTP status
 */
package com.smacian.backend.exception

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /*
     * Reads the current request path (e.g. "/api/auth/register")
     * so every error response shows which API call failed.
     * WebRequest.getDescription gives "uri=/api/auth/register", so we
     * strip the "uri=" prefix.
     */
    private fun getPath(request: WebRequest): String =
        request.getDescription(false).removePrefix("uri=")

    // ====================================================================
    // 1. @Valid VALIDATION ERRORS (most common)
    // ====================================================================
    // Triggered when a DTO field fails its validation annotation
    // (password too short, email wrong format, etc.). Returns HTTP 400
    // with a list of ALL failing fields.

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(ex: MethodArgumentNotValidException, request: WebRequest): ResponseEntity<ErrorResponse> {

        // Collect every failing field into a list.
        val fieldErrors = ex.bindingResult.fieldErrors.map { error ->
            ErrorResponse.FieldErrorDetail(
                field = error.field,
                message = error.defaultMessage ?: "Invalid value"
            )
        }

        val response = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Validation Failed",
            message = "Please fix the following errors",
            path = getPath(request),
            fieldErrors = fieldErrors
        )

        return ResponseEntity.badRequest().body(response)
    }

    // ====================================================================
    // 2. BUSINESS LOGIC ERRORS
    // ====================================================================
    // Triggered when input violates a business rule:
    //   - OTP expired / incorrect / already used
    //   - Email or phone already registered
    //   - Under 13 years old, passwords don't match, etc.
    // Returns HTTP 400 with the custom exception message.

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Bad Request",
            message = ex.message ?: "Invalid request",
            path = getPath(request)
        )
        return ResponseEntity.badRequest().body(response)
    }

    // ====================================================================
    // 3. RESOURCE NOT FOUND
    // ====================================================================
    // User/record doesn't exist → HTTP 404.

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(ex: ResourceNotFoundException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse(
            status = HttpStatus.NOT_FOUND.value(),
            error = "Not Found",
            message = ex.message ?: "Resource not found",
            path = getPath(request)
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
    }

    // ====================================================================
    // 3b. FIELD-LEVEL VALIDATION FROM THE SERVICE LAYER
    // ====================================================================
    // Thrown by validators like ProfileValidation so every failing field
    // is returned with its UI-matching key (e.g. "endDate").

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val fieldErrors = ex.errors.map { (field, message) ->
            ErrorResponse.FieldErrorDetail(field = field, message = message)
        }

        val response = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Validation Failed",
            message = "Please fix the following errors",
            path = getPath(request),
            fieldErrors = fieldErrors
        )
        return ResponseEntity.badRequest().body(response)
    }

    // ====================================================================
    // 3c. NOT THE RESOURCE OWNER
    // ====================================================================
    // Acting on another user's resource (e.g. deleting their experience)
    // -> HTTP 403.

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse(
            status = HttpStatus.FORBIDDEN.value(),
            error = "Forbidden",
            message = ex.message ?: "You don't have permission to do this",
            path = getPath(request)
        )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response)
    }

    // ====================================================================
    // 4. WRONG LOGIN CREDENTIALS
    // ====================================================================
    // Wrong password during login → HTTP 401. We use a GENERIC message so
    // attackers can't discover which emails/phones are registered.

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse(
            status = HttpStatus.UNAUTHORIZED.value(),
            error = "Unauthorized",
            message = "Invalid email/phone or password",
            path = getPath(request)
        )
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response)
    }

    // ====================================================================
    // 5. DUPLICATE EMAIL / PHONE (database unique constraint)
    // ====================================================================
    // Safety net if two users register at the same instant. HTTP 409.

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException, request: WebRequest): ResponseEntity<ErrorResponse> {

        // Read the database error to generate a friendly message.
        var message = "A conflict occurred with your request"
        val dbError = ex.message ?: ""
        when {
            dbError.contains("uk_users_email") ->
                message = "An account with this email already exists"
            dbError.contains("uk_users_phone") ->
                message = "An account with this phone number already exists"
            dbError.contains("not-null") ->
                message = "A required field is missing"
        }

        val response = ErrorResponse(
            status = HttpStatus.CONFLICT.value(),
            error = "Conflict",
            message = message,
            path = getPath(request)
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
    }

    // ====================================================================
    // 6. INVALID JSON BODY
    // ====================================================================
    // Malformed JSON, unknown enum value (e.g. gender = "SUPERMAN"),
    // unparseable date → HTTP 400.

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableMessage(ex: HttpMessageNotReadableException, request: WebRequest): ResponseEntity<ErrorResponse> {

        var message = "Invalid request body. Please check your input"
        if (ex.message?.lowercase()?.contains("enum") == true) {
            message = "Invalid value provided for a field. Please check allowed values (e.g. gender must be MALE, FEMALE or OTHER)"
        }

        val response = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Bad Request",
            message = message,
            path = getPath(request)
        )
        return ResponseEntity.badRequest().body(response)
    }

    // ====================================================================
    // 7. FILE TOO LARGE (photo upload)
    // ====================================================================
    // Uploaded photo exceeds 5MB → HTTP 413.

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSize(ex: MaxUploadSizeExceededException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse(
            status = HttpStatus.PAYLOAD_TOO_LARGE.value(),
            error = "File Too Large",
            message = "File size must be less than 5MB",
            path = getPath(request)
        )
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response)
    }

    // ====================================================================
    // 8. MISSING FILE PART (photo upload)
    // ====================================================================
    // Upload request without an actual file attached.

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingFilePart(ex: MissingServletRequestPartException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Missing File",
            message = "No file was attached. Please upload a photo",
            path = getPath(request)
        )
        return ResponseEntity.badRequest().body(response)
    }

    // ====================================================================
    // 9. CATCH-ALL FOR UNEXPECTED ERRORS
    // ====================================================================
    // Last safety net. Log full details for developers (server console)
    // but return a GENERIC message to the user (no internal details).

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception occurred", ex)

        val response = ErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = "Internal Server Error",
            message = "Something went wrong. Please try again later",
            path = getPath(request)
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }
}