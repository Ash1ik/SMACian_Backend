/*
 * ForbiddenException - Thrown when an authenticated user tries to act
 * on a resource that belongs to a different user (e.g. deleting someone
 * else's experience entry). Mapped to HTTP 403.
 */
package com.smacian.backend.exception

class ForbiddenException(message: String) : RuntimeException(message)