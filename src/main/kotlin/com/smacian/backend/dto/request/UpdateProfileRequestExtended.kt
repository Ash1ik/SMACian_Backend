/*
 * UpdateProfileRequestExtended - JSON body for PUT /api/user/profile.
 *
 * Extends the original profile update with designation, bio, location,
 * social links and nested experience/education entries (upsert).
 *
 * Validation is NOT done with annotations here - it runs in
 * ProfileValidation (service layer) so every failing field comes back
 * with its UI-matching key in the fieldErrors response.
 *
 * gender / bloodGroup / relationshipStatus are sent as the display
 * strings ("MALE", "A+", "Single"...) and converted to enums in the
 * service, so an invalid value yields a proper field error.
 */
package com.smacian.backend.dto.request

data class UpdateProfileRequestExtended(
    val firstName: String = "",
    val lastName: String = "",
    val dateOfBirth: String = "",
    val gender: String? = null,
    val designation: String? = null,
    val bio: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val location: String? = null,
    val socialLinks: List<String>? = null,
    val bloodGroup: String? = null,
    val relationshipStatus: String? = null,
    val profilePhotoUrl: String? = null,
    val coverPhotoUrl: String? = null,
    val experiences: List<UserExperienceRequest>? = emptyList(),
    val educations: List<UserEducationRequest>? = emptyList()
)