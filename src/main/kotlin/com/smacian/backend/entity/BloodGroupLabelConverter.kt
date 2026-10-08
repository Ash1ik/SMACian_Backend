/*
 * BloodGroupLabelConverter - Stores BloodGroup by its short label ("A+")
 * instead of the enum name ("A_POSITIVE"), so the blood_requests table
 * keeps a compact VARCHAR(3) column. Applied explicitly per-field
 * (NOT autoApply - the users table keeps storing enum names).
 */
package com.smacian.backend.entity

import com.smacian.backend.entity.enums.BloodGroup
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = false)
class BloodGroupLabelConverter : AttributeConverter<BloodGroup, String> {

    override fun convertToDatabaseColumn(attribute: BloodGroup?): String? =
        attribute?.label

    override fun convertToEntityAttribute(dbData: String?): BloodGroup? =
        dbData?.let {
            BloodGroup.fromLabelOrNull(it)
                ?: throw IllegalArgumentException("Unknown blood group in database: '$it'")
        }
}
