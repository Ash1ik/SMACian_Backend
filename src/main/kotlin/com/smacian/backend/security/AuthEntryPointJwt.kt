/*
 * AuthEntryPointJwt - Handles "not logged in" requests to protected endpoints.
 *
 * Default Spring Security behavior → plain HTML 401 page.
 * Our behavior → clean JSON error the mobile app can understand:
 *
 * {
 *   "success": false,
 *   "status": 401,
 *   "error": "Unauthorized",
 *   "message": "Authentication required. Please log in to access this resource",
 *   "path": "/api/user/profile"
 * }
 */
package com.smacian.backend.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.smacian.backend.exception.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class AuthEntryPointJwt(
    // Spring provides an ObjectMapper (from spring-boot-starter-web)
    // that converts Kotlin objects into JSON.
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        // Build the standard clean JSON error response.
        val errorResponse = ErrorResponse(
            status = HttpServletResponse.SC_UNAUTHORIZED, // 401
            error = "Unauthorized",
            message = "Authentication required. Please log in to access this resource",
            path = request.requestURI
        )

        // Tell the client the body is JSON, then set the 401 status.
        response.contentType = "application/json"
        response.status = HttpServletResponse.SC_UNAUTHORIZED

        // Write the JSON object to the response body.
        objectMapper.writeValue(response.outputStream, errorResponse)
    }
}