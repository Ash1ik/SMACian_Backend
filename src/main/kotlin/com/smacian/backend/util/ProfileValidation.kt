/*
 * ProfileValidation - Service-layer validation for the extended profile
 * update (PUT /api/user/profile).
 *
 * Mirrors the mobile app's AuthValidation rules:
 *   - firstName/lastName: 2-50 letters+spaces
 *   - dateOfBirth: yyyy-MM-dd, valid, 1950..now, age >= 13
 *   - gender: MALE | FEMALE | OTHER
 *   - email: RFC5322-ish (local <= 64, total <= 254, TLD >= 2 letters)
 *   - phone: 7-15 digits
 *   - bloodGroup / relationshipStatus: from the allowed enums
 *   - bio <= 500, designation <= 100, location <= 255
 *   - socialLinks: max 10, each a valid http(s) URL
 *   - experience: organization/designation 2-100, "MMM yyyy" dates,
 *     end >= start unless "Present"
 *   - education: institution/fieldOfStudy 2-100, same date rules
 *
 * Errors are collected (not fail-fast) so the client gets every invalid
 * field in one fieldErrors response, keyed by the UI field name.
 */
package com.smacian.backend.util

import com.smacian.backend.dto.request.UpdateProfileRequestExtended
import com.smacian.backend.dto.request.UserEducationRequest
import com.smacian.backend.dto.request.UserExperienceRequest
import com.smacian.backend.entity.enums.BloodGroup
import com.smacian.backend.entity.enums.RelationshipStatus
import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object ProfileValidation {

    private val NAME_REGEX = Regex("^[a-zA-Z ]+$")
    private val PHONE_REGEX = Regex("^[0-9]{7,15}$")
    private val EMAIL_LOCAL_REGEX = Regex("^[A-Za-z0-9.!#\$%&'*+/=?^_`{|}~-]+$")
    private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    private val SOCIAL_LINKS_MAX = 10

    fun validate(request: UpdateProfileRequestExtended): Map<String, String> {
        val errors = linkedMapOf<String, String>()

        // ---- Names ----
        validateName(request.firstName, "firstName", "First name", errors)
        validateName(request.lastName, "lastName", "Last name", errors)

        // ---- Gender ----
        val genderUpper = request.gender?.trim()?.uppercase()
        if (genderUpper.isNullOrBlank()) {
            errors["gender"] = "Please select a gender"
        } else if (genderUpper !in setOf("MALE", "FEMALE", "OTHER")) {
            errors["gender"] = "Gender must be MALE, FEMALE or OTHER"
        }

        // ---- Date of birth ----
        if (request.dateOfBirth.isBlank()) {
            errors["dateOfBirth"] = "Date of birth is required"
        } else {
            val dob = try { LocalDate.parse(request.dateOfBirth) } catch (e: Exception) { null }
            when {
                dob == null ->
                    errors["dateOfBirth"] = "Invalid date of birth format. Please use yyyy-MM-dd"
                dob.year < 1950 ->
                    errors["dateOfBirth"] = "Date of birth must be after 1950"
                dob.isAfter(LocalDate.now()) ->
                    errors["dateOfBirth"] = "Date of birth cannot be in the future"
                else -> {
                    val age = Period.between(dob, LocalDate.now()).years
                    if (age < 13) errors["dateOfBirth"] = "You must be at least 13 years old"
                }
            }
        }

        // ---- Email (only when provided) ----
        val email = request.email?.trim()?.lowercase()
        if (!email.isNullOrBlank() && !isValidEmail(email)) {
            errors["email"] = "Please enter a valid email address"
        }

        // ---- Phone (only when provided) ----
        val rawPhone = request.phone
        if (!rawPhone.isNullOrBlank()) {
            val phone = rawPhone.replace(Regex("[^0-9]"), "")
            if (!phone.matches(PHONE_REGEX)) {
                errors["phone"] = "Phone number must be between 7 and 15 digits"
            }
        }

        // ---- Designation / bio / location lengths ----
        if (!request.designation.isNullOrBlank() && request.designation.trim().length > 100) {
            errors["designation"] = "Designation must be 100 characters or less"
        }
        if (!request.bio.isNullOrBlank() && request.bio.trim().length > 500) {
            errors["bio"] = "Bio must be 500 characters or less"
        }
        if (!request.location.isNullOrBlank() && request.location.trim().length > 255) {
            errors["location"] = "Location must be 255 characters or less"
        }

        // ---- Blood group / relationship status from enums ----
        if (!request.bloodGroup.isNullOrBlank() && BloodGroup.fromLabelOrNull(request.bloodGroup) == null) {
            errors["bloodGroup"] = "Blood group must be one of: A+, A-, B+, B-, AB+, AB-, O+, O-"
        }
        if (!request.relationshipStatus.isNullOrBlank() &&
            RelationshipStatus.fromLabelOrNull(request.relationshipStatus) == null
        ) {
            errors["relationshipStatus"] = "Relationship status must be one of: Single, In a relationship, Engaged, Married"
        }

        // ---- Social links ----
        val links = request.socialLinks?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        if (links.size > SOCIAL_LINKS_MAX) {
            errors["socialLinks"] = "You can add at most $SOCIAL_LINKS_MAX social links"
        } else {
            val invalid = links.filterNot { isValidUrl(it) }
            if (invalid.isNotEmpty()) {
                errors["socialLinks"] = "Each social link must be a valid http(s) URL"
            }
        }

        // ---- Photos (only validated, never required - absent keeps current) ----
        val photo = request.profilePhotoUrl?.trim()?.takeIf { it.isNotBlank() }
        if (photo != null && !isValidUrl(photo)) {
            errors["profilePhotoUrl"] = "Profile picture URL must be a valid http(s) URL"
        }

        val cover = request.coverPhotoUrl?.trim()?.takeIf { it.isNotBlank() }
        if (cover != null && !isValidUrl(cover)) {
            errors["coverPhotoUrl"] = "Cover picture URL must be a valid http(s) URL"
        }

        // ---- Timeline entries ----
        request.experiences.orEmpty().forEach { validateExperience(it, errors) }
        request.educations.orEmpty().forEach { validateEducation(it, errors) }

        return errors
    }

    // ====================================================================
    // HELPERS
    // ====================================================================

    private fun validateName(value: String, key: String, label: String, errors: MutableMap<String, String>) {
        if (value.isBlank()) {
            errors[key] = "$label is required"
        } else if (value.trim().length !in 2..50) {
            errors[key] = "$label must be between 2 and 50 characters"
        } else if (!value.matches(NAME_REGEX)) {
            errors[key] = "$label can only contain letters and spaces"
        }
    }

    private fun validateExperience(req: UserExperienceRequest, errors: MutableMap<String, String>) {
        if (req.organization.isBlank()) {
            errors["organization"] = "Organization is required"
        } else if (req.organization.trim().length !in 2..100) {
            errors["organization"] = "Organization must be between 2 and 100 characters"
        }

        if (req.designation.isBlank()) {
            errors["designation"] = "Designation is required"
        } else if (req.designation.trim().length !in 2..100) {
            errors["designation"] = "Designation must be between 2 and 100 characters"
        }

        validateTimelineDates(req.startDate, req.endDate, errors)
    }

    private fun validateEducation(req: UserEducationRequest, errors: MutableMap<String, String>) {
        if (req.institution.isBlank()) {
            errors["institution"] = "Institution is required"
        } else if (req.institution.trim().length !in 2..100) {
            errors["institution"] = "Institution must be between 2 and 100 characters"
        }

        if (req.fieldOfStudy.isBlank()) {
            errors["fieldOfStudy"] = "Field of study is required"
        } else if (req.fieldOfStudy.trim().length !in 2..100) {
            errors["fieldOfStudy"] = "Field of study must be between 2 and 100 characters"
        }

        validateTimelineDates(req.startDate, req.endDate, errors)
    }

    private fun validateTimelineDates(startDate: String, endDate: String, errors: MutableMap<String, String>) {
        if (startDate.isBlank() || parseMonthYear(startDate) == null) {
            errors["startDate"] = "Start date must be a valid month and year (e.g. Jan 2025)"
        }

        if (endDate.isBlank()) {
            errors["endDate"] = "End date is required"
        } else if (endDate.trim().equals("Present", ignoreCase = true)) {
            // "Present" is always allowed.
        } else {
            val start = parseMonthYear(startDate)
            val end = parseMonthYear(endDate)
            if (end == null) {
                errors["endDate"] = "End date must be a valid month and year (e.g. Jan 2025) or Present"
            } else if (start != null && end.isBefore(start)) {
                errors["endDate"] = "End date cannot be before start date"
            }
        }
    }

    private fun parseMonthYear(value: String): YearMonth? = try {
        YearMonth.parse(value.trim(), MONTH_YEAR)
    } catch (e: Exception) {
        null
    }

    /*
     * RFC5322-ish email check: local <= 64 chars, total <= 254,
     * domain must have a TLD of at least 2 letters.
     */
    private fun isValidEmail(email: String): Boolean {
        if (email.length > 254) return false
        val at = email.lastIndexOf('@')
        if (at <= 0 || at == email.length - 1) return false

        val local = email.substring(0, at)
        val domain = email.substring(at + 1)
        if (local.length > 64 || !local.matches(EMAIL_LOCAL_REGEX)) return false
        if (local.startsWith(".") || local.endsWith(".") || local.contains("..")) return false

        val domainParts = domain.split('.')
        if (domainParts.size < 2 || domainParts.any { it.isEmpty() }) return false
        if (!domain.all { it.isLetterOrDigit() || it == '-' || it == '.' }) return false
        val tld = domainParts.last()
        return tld.length >= 2 && tld.all { it.isLetter() }
    }

    private fun isValidUrl(url: String): Boolean = try {
        val uri = java.net.URI(url)
        (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
    } catch (e: Exception) {
        false
    }
}