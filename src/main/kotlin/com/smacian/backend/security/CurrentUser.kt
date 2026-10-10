/*
 * CurrentUser - Helper to get the currently-logged-in user's ID.
 *
 * When a valid JWT arrives, JwtAuthenticationFilter stores the user ID
 * (as the authentication "name") in Spring's SecurityContextHolder.
 * This object reads it back out.
 *
 * We NEVER take the user ID from request bodies - that would allow
 * users to access other people's profiles (a flaw called IDOR).
 * The ID always comes from the JWT token.
 */
package com.smacian.backend.security

import com.smacian.backend.exception.AuthenticationRequiredException
import org.springframework.security.core.context.SecurityContextHolder

object CurrentUser {

    /*
     * Returns the authenticated user's ID.
     *
     * @throws AuthenticationRequiredException (HTTP 401) if no user is
     *         logged in (shouldn't happen - SecurityConfig only lets
     *         authenticated users reach these endpoints).
     */
    fun getUserId(): Long {
        val authentication = SecurityContextHolder.getContext().authentication

        if (authentication == null || !authentication.isAuthenticated) {
            throw AuthenticationRequiredException()
        }

        // In JwtAuthenticationFilter, the "name" is the user ID as a String.
        return try {
            authentication.name.toLong()
        } catch (e: NumberFormatException) {
            throw AuthenticationRequiredException()
        }
    }
}