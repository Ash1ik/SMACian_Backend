/*
 * ValidationException - Thrown by service-layer validators so that EVERY
 * failing field comes back as a fieldErrors entry with the UI-matching key.
 *
 * Example: { "firstName": "...", "endDate": "..." }
 */
package com.smacian.backend.exception

class ValidationException(
    val errors: Map<String, String>
) : RuntimeException("Validation failed")