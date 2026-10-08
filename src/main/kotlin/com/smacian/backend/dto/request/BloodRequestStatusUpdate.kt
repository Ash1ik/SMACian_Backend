/*
 * BloodRequestStatusUpdate - Body for PATCH /api/blood-requests/{id}/status.
 *
 * JSON: { "status": "FULFILLED" }
 *
 * Only FULFILLED or CANCELLED are accepted (requester closes their own
 * request). EXPIRED is set by the nightly job, never by clients.
 */
package com.smacian.backend.dto.request

data class BloodRequestStatusUpdate(
    val status: String? = null
)
