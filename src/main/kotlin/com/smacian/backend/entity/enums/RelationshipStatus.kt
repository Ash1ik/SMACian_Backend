/*
 * RelationshipStatus - Allowed relationship statuses for a user profile.
 *
 * The API uses the display labels ("Single", "In a relationship"...) in
 * JSON, matching the mobile app. The enum NAME is used in the database.
 */
package com.smacian.backend.entity.enums

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class RelationshipStatus(val label: String) {

    SINGLE("Single"),
    IN_A_RELATIONSHIP("In a relationship"),
    ENGAGED("Engaged"),
    MARRIED("Married");

    @JsonValue
    fun jsonLabel(): String = label

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fromJson(value: String): RelationshipStatus? =
            entries.firstOrNull {
                it.label.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
            }

        fun fromLabelOrNull(value: String?): RelationshipStatus? =
            value?.let { v -> entries.firstOrNull { it.label.equals(v, ignoreCase = true) || it.name.equals(v, ignoreCase = true) } }
    }
}