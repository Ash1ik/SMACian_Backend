/*
 * BloodGroup - Allowed blood groups for a user profile.
 *
 * The API uses the display labels ("A+", "O-"...) in JSON, matching
 * the mobile app's UI. The enum NAME is used for database storage.
 */
package com.smacian.backend.entity.enums

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class BloodGroup(val label: String) {

    A_POSITIVE("A+"),
    A_NEGATIVE("A-"),
    B_POSITIVE("B+"),
    B_NEGATIVE("B-"),
    AB_POSITIVE("AB+"),
    AB_NEGATIVE("AB-"),
    O_POSITIVE("O+"),
    O_NEGATIVE("O-");

    // Serialize as the label ("A+") in JSON.
    @JsonValue
    fun jsonLabel(): String = label

    companion object {
        // Deserialize accepting either the label ("A+") or the name ("A_POSITIVE").
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fromJson(value: String): BloodGroup? =
            entries.firstOrNull {
                it.label.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
            }

        fun fromLabelOrNull(value: String?): BloodGroup? =
            value?.let { v -> entries.firstOrNull { it.label.equals(v, ignoreCase = true) || it.name.equals(v, ignoreCase = true) } }
    }
}