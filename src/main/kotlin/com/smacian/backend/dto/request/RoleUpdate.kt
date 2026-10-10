/*
 * RoleUpdate - Body for PATCH /api/admin/users/{id}/role.
 *
 * JSON: { "role": "ADMIN" }
 *
 * Accepts "ADMIN" or "USER" (any case); anything else is a 400.
 */
package com.smacian.backend.dto.request

data class RoleUpdate(
    val role: String? = null
)
