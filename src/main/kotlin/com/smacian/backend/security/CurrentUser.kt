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

import org.springframework.security.core.context.SecurityContextHolder

object CurrentUser {

    /*
     * Returns the authenticated user's ID.
     *
     * @throws RuntimeException if no user is logged in
     *         (shouldn't happen - SecurityConfig only lets authenticated
     *          users reach these endpoints)
     */
    fun getUserId(): Long {
        val authentication = SecurityContextHolder.getContext().authentication

        requireNotNull(authentication) { "No authenticated user found" }
        check(authentication.isAuthenticated) { "No authenticated user found" }

        // In JwtAuthenticationFilter, the "name" is the user ID as a String.
        return authentication.name.toLong()
    }
}