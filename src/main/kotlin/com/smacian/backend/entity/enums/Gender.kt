/*
 * Gender - An enum representing the genders a user can select.
 *
 * An enum is a special class in Kotlin that holds a fixed set of
 * predefined values. This is safer than storing free text in the
 * database, because only these 3 values are ever allowed.
 *
 * In PostgreSQL this is stored as a VARCHAR column ('MALE', 'FEMALE'...).
 */
package com.smacian.backend.entity.enums

enum class Gender {
    // The three allowed values, matching the mobile app's GenderChoices.
    MALE,
    FEMALE,
    OTHER
}