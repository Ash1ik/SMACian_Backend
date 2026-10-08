/*
 * BloodRequestStatus - Lifecycle of a blood request.
 *
 * OPEN      = visible in listings, seeking donors
 * FULFILLED = requester closed it, blood arranged
 * CANCELLED = requester withdrew it
 * EXPIRED   = needed_by passed while still OPEN (set by the nightly job)
 */
package com.smacian.backend.entity.enums

enum class BloodRequestStatus {
    OPEN,
    FULFILLED,
    CANCELLED,
    EXPIRED;

    companion object {
        fun fromStringOrNull(value: String?): BloodRequestStatus? =
            value?.let { v -> entries.firstOrNull { it.name.equals(v.trim(), ignoreCase = true) } }
    }
}
