/*
 * ResourceNotFoundException - Thrown when we look for something in the
 * database but it doesn't exist.
 *
 * Examples:
 *   - Login with an unregistered email
 *   - Password reset for a phone that has no account
 *   - User ID not found during a profile update
 *
 * Returns HTTP 404 (Not Found) with a readable message.
 */
package com.smacian.backend.exception

class ResourceNotFoundException(message: String) : RuntimeException(message)