/*
 * JwtAuthenticationFilter - Runs on EVERY API request like a security guard.
 *
 * Workflow per request:
 *   1. Look for the "Authorization" header
 *   2. If it starts with "Bearer " → extract the JWT token
 *   3. Validate signature + expiry, get the user ID from the token
 *   4. Load the user from the database
 *   5. Mark the request as authenticated in Spring's SecurityContext
 *   6. Continue the request to the controller
 *
 * If there is no/invalid token, we just continue the chain - Spring
 * Security's authorization rules decide what happens next.
 */
package com.smacian.backend.security

import com.smacian.backend.repository.UserRepository
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // =====================================================
        // Step 1: Read the "Authorization" header.
        // Expected: "Authorization: Bearer eyJhbGci..."
        // =====================================================
        val header = request.getHeader("Authorization")

        // No header (or wrong prefix) = not logged in.
        // Continue the chain and let Spring Security apply its rules.
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        // Extract the token by removing the "Bearer " prefix.
        val token = header.substring(7)

        try {
            // =====================================================
            // Step 2: Get the user ID from the token.
            // This also validates the signature and expiry.
            // =====================================================
            val userId = jwtService.getUserIdFromToken(token)

            // =====================================================
            // Step 3: Load the full user from the database.
            // =====================================================
            val user = userRepository.findById(userId).orElse(null)

            // =====================================================
            // Step 4: If the user exists and isn't already
            // authenticated in this request, authenticate them.
            // =====================================================
            if (user != null && SecurityContextHolder.getContext().authentication == null) {

                // Build Spring's UserDetails object.
                // We store the user ID as the "username".
                val userDetails = User.withUsername(user.id.toString())
                    .password(user.passwordHash)
                    .authorities("ROLE_USER")
                    .build()

                val authToken = UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.authorities
                )

                // Attach request details (IP, session) for audit purposes.
                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)

                // KEY LINE: marks the request as authenticated.
                SecurityContextHolder.getContext().authentication = authToken
            }

        } catch (e: ExpiredJwtException) {
            // Token past its expiry - just don't authenticate.
            log.debug("JWT token expired: {}", e.message)
        } catch (e: JwtException) {
            // Tampered / malformed token.
            log.debug("Invalid JWT token received: {}", e.message)
        }

        // =====================================================
        // Step 5: Continue the request chain to the controller.
        // =====================================================
        filterChain.doFilter(request, response)
    }
}