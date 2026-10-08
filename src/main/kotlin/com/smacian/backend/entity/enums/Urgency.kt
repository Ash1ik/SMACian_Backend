/*
 * Urgency - How fast a blood request needs blood.
 */
package com.smacian.backend.entity.enums

enum class Urgency {
    EMERGENCY,
    URGENT,
    STANDARD;

    companion object {
        // Accepts "URGENT", "urgent", etc. Null when unknown.
        fun fromStringOrNull(value: String?): Urgency? =
            value?.let { v -> entries.firstOrNull { it.name.equals(v.trim(), ignoreCase = true) } }
    }
}
