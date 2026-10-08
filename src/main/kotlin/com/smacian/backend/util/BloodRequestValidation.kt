/*
 * BloodRequestValidation - Collect-all-errors validation for blood-request
 * creation (mirrors the ProfileValidation pattern: UI-keyed map, thrown
 * as ValidationException -> 400 with fieldErrors).
 *
 * Field keys (exact, as the mobile app expects):
 *   bloodGroup, bags, urgency, hospital, location, neededBy, contactNumber, note
 */
package com.smacian.backend.util

import com.smacian.backend.entity.enums.BloodGroup
import com.smacian.backend.entity.enums.Urgency
import java.time.LocalDate

object BloodRequestValidation {

    const val MAX_NOTE_LENGTH = 2000

    data class Validated(
        val bloodGroup: BloodGroup,
        val bags: Int,
        val urgency: Urgency,
        val hospital: String,
        val location: String,
        val neededBy: LocalDate,
        val contactNumber: String,
        val note: String?
    )

    /*
     * All params are raw form strings (null = absent). Returns the parsed
     * values or a map of field -> message (empty = valid).
     */
    fun validate(
        bloodGroup: String?,
        bags: String?,
        urgency: String?,
        hospital: String?,
        location: String?,
        neededBy: String?,
        contactNumber: String?,
        note: String?
    ): Pair<Validated?, Map<String, String>> {

        val errors = linkedMapOf<String, String>()

        // ---- bloodGroup: closed list, label ("B+") or name ("B_POSITIVE") ----
        val bg = BloodGroup.fromLabelOrNull(bloodGroup?.trim()?.takeIf { it.isNotEmpty() })
        if (bg == null) {
            errors["bloodGroup"] = "Blood group is required (A+, A-, B+, B-, AB+, AB-, O+, O-)"
        }

        // ---- bags: integer 1-10 ----
        val bagCount = bags?.trim()?.toIntOrNull()
        if (bagCount == null || bagCount !in 1..10) {
            errors["bags"] = "Bags is required (1-10)"
        }

        // ---- urgency: closed enum ----
        val urg = Urgency.fromStringOrNull(urgency)
        if (urg == null) {
            errors["urgency"] = "Urgency is required (EMERGENCY, URGENT, STANDARD)"
        }

        // ---- hospital / location: non-blank, <= 255 ----
        val cleanHospital = hospital?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanHospital == null) {
            errors["hospital"] = "Hospital is required"
        } else if (cleanHospital.length > 255) {
            errors["hospital"] = "Hospital must be 255 characters or less"
        }

        val cleanLocation = location?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanLocation == null) {
            errors["location"] = "Location is required"
        } else if (cleanLocation.length > 255) {
            errors["location"] = "Location must be 255 characters or less"
        }

        // ---- neededBy: yyyy-MM-dd, today or future ----
        var needed: LocalDate? = null
        val neededRaw = neededBy?.trim()?.takeIf { it.isNotEmpty() }
        if (neededRaw == null) {
            errors["neededBy"] = "Needed-by date is required (yyyy-MM-dd)"
        } else {
            needed = try {
                LocalDate.parse(neededRaw)
            } catch (e: Exception) {
                null
            }
            if (needed == null) {
                errors["neededBy"] = "Needed-by date must be yyyy-MM-dd (e.g. 2026-10-10)"
            } else if (needed.isBefore(LocalDate.now())) {
                errors["neededBy"] = "Needed-by date must be today or a future date"
            }
        }

        // ---- contactNumber: strip spaces/dashes/+, then 6-15 digits ----
        val stripped = contactNumber?.trim()
            ?.replace(" ", "")?.replace("-", "")?.replace("+", "")
        if (stripped.isNullOrEmpty()) {
            errors["contactNumber"] = "Contact number is required"
        } else if (!stripped.all { it.isDigit() } || stripped.length !in 6..15) {
            errors["contactNumber"] = "Contact number must be 6-15 digits"
        }

        // ---- note: optional, <= 2000 ----
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanNote != null && cleanNote.length > MAX_NOTE_LENGTH) {
            errors["note"] = "Note must be $MAX_NOTE_LENGTH characters or less"
        }

        if (errors.isNotEmpty()) {
            return null to errors
        }
        return Validated(
            bloodGroup = bg!!,
            bags = bagCount!!,
            urgency = urg!!,
            hospital = cleanHospital!!,
            location = cleanLocation!!,
            neededBy = needed!!,
            contactNumber = stripped!!,
            note = cleanNote
        ) to emptyMap()
    }
}
