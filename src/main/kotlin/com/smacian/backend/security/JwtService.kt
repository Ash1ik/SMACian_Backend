/*
 * JwtService - Creates and validates JWT tokens.
 *
 * A JWT looks like: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abcdefghijklmnopqrstuvwxyz
 * It has 3 dot-separated parts:
 *   1. Header    - the signing algorithm (HS256)
 *   2. Payload   - user ID, issue time, expiry time
 *   3. Signature - prevents tampering (signed with our SECRET key)
 *
 * Flow:
 *   Login success → generateToken(user) → JWT sent to the app
 *   App sends request → Authorization: Bearer <token>
 *   → getUserIdFromToken(token) → identifies which user is calling
 *
 * The token is signed with a SECRET only our server knows. If anyone
 * edits the payload, the signature won't match and validation fails.
 */
package com.smacian.backend.security

import com.smacian.backend.entity.User
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    // Spring injects these values from application.yml.
    // In production they come from environment variables.
    @Value("\${app.jwt.secret}") private val jwtSecret: String,
    @Value("\${app.jwt.expiration-ms}") private val jwtExpirationMs: Long
) {

    /*
     * Creates a JWT token for a user (after successful login/registration).
     *
     * Inside the token:
     *   - subject   = user ID (main payload - identifies the user)
     *   - issuedAt  = when the token was created
     *   - expiration = now + 24 hours
     */
    fun generateToken(user: User): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtExpirationMs)

        return Jwts.builder()
            .subject(user.id.toString())              // store user ID as the subject
            .issuedAt(now)                            // issue time
            .expiration(expiryDate)                   // expiry time
            .signWith(getSigningKey())                // sign with our secret
            .compact()                                // build the final string
    }

    /*
     * Extracts the user ID from a valid token.
     *
     * Parsing ALSO validates the signature and expiry:
     *   - valid token   → returns the "sub" (subject = user ID)
     *   - expired/tampered → throws an exception (caller handles it)
     */
    fun getUserIdFromToken(token: String): Long {
        val claims = Jwts.parser()
            .verifyWith(getSigningKey())     // verify signature using secret
            .build()
            .parseSignedClaims(token)        // parse the 3 parts of the JWT
            .payload                          // extract the payload (part 2)

        return claims.subject.toLong()       // String → Long
    }

    /*
     * Creates the cryptographic signing key from our secret string.
     * HS256 requires at least 32 bytes (256 bits) - our secret is longer.
     * Same key is used for signing AND verifying (symmetrical).
     */
    private fun getSigningKey(): SecretKey =
        Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))
}