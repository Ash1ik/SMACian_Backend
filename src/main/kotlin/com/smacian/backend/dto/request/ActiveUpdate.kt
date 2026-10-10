/*
 * ActiveUpdate - Body for PATCH /api/admin/users/{id}/active.
 *
 * JSON: { "active": false }
 */
package com.smacian.backend.dto.request

data class ActiveUpdate(
    val active: Boolean = true
)
