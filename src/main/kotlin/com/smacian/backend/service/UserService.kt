/*
 * UserService - Handles all logged-in user profile operations.
 *
 * These methods can only be called by an authenticated user
 * (JWT token required - enforced by SecurityConfig).
 *
 * Public methods:
 *   1. getProfile(userId)              - view own profile
 *   2. updateProfile(userId, request)  - full profile update (upsert)
 *   3. updateProfilePhoto(userId, file) - upload/replace avatar
 *   4. updateCoverPhoto(userId, file)   - upload/replace cover photo
 *   5. getPublicProfile(id)             - view ANOTHER user's profile
 *   6. searchPeople(query, page, size)  - paged people search
 *   7. deleteExperience(userId, expId)  - delete own experience entry
 *   8. deleteEducation(userId, eduId)   - delete own education entry
 */
package com.smacian.backend.service

import com.smacian.backend.dto.request.UpdateProfileRequestExtended
import com.smacian.backend.dto.request.UserEducationRequest
import com.smacian.backend.dto.request.UserExperienceRequest
import com.smacian.backend.dto.response.PagedResponse
import com.smacian.backend.dto.response.PeopleListItemResponse
import com.smacian.backend.dto.response.PublicUserResponse
import com.smacian.backend.dto.response.UserEducationResponse
import com.smacian.backend.dto.response.UserExperienceResponse
import com.smacian.backend.dto.response.UserResponse
import com.smacian.backend.entity.User
import com.smacian.backend.entity.UserEducation
import com.smacian.backend.entity.UserExperience
import com.smacian.backend.entity.enums.BloodGroup
import com.smacian.backend.entity.enums.Gender
import com.smacian.backend.entity.enums.RelationshipStatus
import com.smacian.backend.exception.BadRequestException
import com.smacian.backend.exception.ForbiddenException
import com.smacian.backend.exception.ResourceNotFoundException
import com.smacian.backend.exception.ValidationException
import com.smacian.backend.repository.UserEducationRepository
import com.smacian.backend.repository.UserExperienceRepository
import com.smacian.backend.repository.UserRepository
import com.smacian.backend.util.ProfileValidation
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.time.LocalDate

@Service
class UserService(
    private val userRepository: UserRepository,
    private val experienceRepository: UserExperienceRepository,
    private val educationRepository: UserEducationRepository
) {

    // ====================================================================
    // 1. GET PROFILE (own)
    // ====================================================================
    @Transactional(readOnly = true)
    fun getProfile(userId: Long): UserResponse =
        buildUserResponse(getUserById(userId))

    // ====================================================================
    // 2. UPDATE PROFILE (extended: details + experience/education upsert)
    // ====================================================================
    @Transactional
    fun updateProfile(userId: Long, request: UpdateProfileRequestExtended): UserResponse {

        val user = getUserById(userId)

        // ---- a. Validate everything (UI-matching field error keys) ----
        val errors = ProfileValidation.validate(request).toMutableMap()

        // ---- b. Normalize contact + check uniqueness (DB-aware) ----
        val newEmail = request.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val newPhone = request.phone?.replace(Regex("[^0-9]"), "")?.takeIf { it.isNotBlank() }

        if (newEmail != null && userRepository.existsByEmailAndIdNot(newEmail, userId)) {
            errors["email"] = "An account with this email already exists"
        }
        if (newPhone != null && userRepository.existsByPhoneAndIdNot(newPhone, userId)) {
            errors["phone"] = "An account with this phone number already exists"
        }

        if (errors.isNotEmpty()) {
            throw ValidationException(errors)
        }

        // ---- c. Apply scalar fields ----
        user.apply {
            firstName = request.firstName.trim()
            lastName = request.lastName.trim()
            dateOfBirth = LocalDate.parse(request.dateOfBirth)
            gender = Gender.valueOf(request.gender!!.trim().uppercase())
            designation = request.designation?.trim()?.takeIf { it.isNotBlank() }
            bio = request.bio?.trim()?.takeIf { it.isNotBlank() }
            location = request.location?.trim()?.takeIf { it.isNotBlank() }
            bloodGroup = BloodGroup.fromLabelOrNull(request.bloodGroup)
            relationshipStatus = RelationshipStatus.fromLabelOrNull(request.relationshipStatus)
            socialLinks = request.socialLinks
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }

            if (newEmail != null) email = newEmail
            if (newPhone != null) phone = newPhone
        }

        userRepository.save(user)

        // ---- d. Upsert experiences (id -> update own, no id -> create) ----
        request.experiences.orEmpty().forEach { upsertExperience(user, it) }

        // ---- e. Upsert educations ----
        request.educations.orEmpty().forEach { upsertEducation(user, it) }

        return buildUserResponse(user)
    }

    // ====================================================================
    // 3. UPDATE PROFILE PHOTO (avatar)
    // ====================================================================
    // The upload is stored in the LOCAL PostgreSQL database (no Cloudinary,
    // no external service). The returned UserResponse.profilePhotoUrl points
    // at our own streaming endpoint so the mobile <Image> can load it.
    @Transactional
    fun updateProfilePhoto(userId: Long, file: MultipartFile): UserResponse {

        val user = getUserById(userId)

        if (file.isEmpty) {
            throw BadRequestException("No photo was uploaded. Please select an image")
        }

        // Validate content type + size before storing.
        validateImageFile(file)

        user.profilePhotoData = file.bytes
        user.profilePhotoContentType = file.contentType
        user.profilePhotoUrl = photoStreamUrl(userId, "profile")
        return buildUserResponse(userRepository.save(user))
    }

    // ====================================================================
    // 4. UPDATE COVER PHOTO
    // ====================================================================
    @Transactional
    fun updateCoverPhoto(userId: Long, file: MultipartFile): UserResponse {

        val user = getUserById(userId)

        if (file.isEmpty) {
            throw BadRequestException("No photo was uploaded. Please select an image")
        }

        validateImageFile(file)

        user.coverPhotoData = file.bytes
        user.coverPhotoContentType = file.contentType
        user.coverPhotoUrl = photoStreamUrl(userId, "cover")
        return buildUserResponse(userRepository.save(user))
    }

    // ====================================================================
    // Helpers: photo validation + absolute stream URL
    // ====================================================================

    private fun validateImageFile(file: MultipartFile) {
        val allowed = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
        val type = file.contentType
        if (type == null || type !in allowed) {
            throw BadRequestException("Only JPG, PNG, WEBP or GIF images are allowed")
        }
        if (file.size > 5 * 1024 * 1024) {
            throw BadRequestException("Image size must be less than 5MB")
        }
    }

    // Builds an absolute URL streamed by our own controller. forward-headers
    // strategy turns Render's X-Forwarded-* into the real https host.
    private fun photoStreamUrl(userId: Long, which: String): String =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/user/$which/photo/{userId}")
            .buildAndExpand(userId)
            .toUriString()

    // ====================================================================
    // PHOTO STREAM GETTERS (return bytes + content type, or 404)
    // ====================================================================
    // The controller streams these back so the mobile <Image> can render
    // the photo. If no photo has been uploaded yet the stream URL never gets
    // into the DB, but we still guard against a stale/hand-crafted URL.
    @Transactional(readOnly = true)
    fun getProfilePhotoStream(userId: Long): Pair<ByteArray, String> {

        val user = getUserById(userId)

        val data = user.profilePhotoData
        val contentType = user.profilePhotoContentType

        if (data == null || contentType == null) {
            throw ResourceNotFoundException("Profile photo not found")
        }

        return data to contentType
    }

    @Transactional(readOnly = true)
    fun getCoverPhotoStream(userId: Long): Pair<ByteArray, String> {

        val user = getUserById(userId)

        val data = user.coverPhotoData
        val contentType = user.coverPhotoContentType

        if (data == null || contentType == null) {
            throw ResourceNotFoundException("Cover photo not found")
        }

        return data to contentType
    }

    // ====================================================================
    // 5. GET PUBLIC PROFILE (another user) - email/phone hidden
    // ====================================================================
    @Transactional(readOnly = true)
    fun getPublicProfile(id: Long): PublicUserResponse {

        val user = userRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("User not found") }

        // Inactive accounts behave as if they don't exist.
        if (!user.isActive) {
            throw ResourceNotFoundException("User not found")
        }

        val experiences = experienceRepository.findByUser_IdOrderByStartDateAsc(id)
            .map { UserExperienceResponse.fromEntity(it) }
        val educations = educationRepository.findByUser_IdOrderByStartDateAsc(id)
            .map { UserEducationResponse.fromEntity(it) }

        return PublicUserResponse.fromEntity(user, experiences, educations)
    }

    // ====================================================================
    // 6. SEARCH PEOPLE (paged, name OR designation, updatedAt desc)
    // ====================================================================
    @Transactional(readOnly = true)
    fun searchPeople(query: String, page: Int, size: Int): PagedResponse<PeopleListItemResponse> {

        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 50)
        val cleanQuery = query.trim()

        val pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"))
        val result = userRepository.searchPeople(cleanQuery, pageable)

        val content = result.content.map { PeopleListItemResponse.fromEntity(it) }

        return PagedResponse(
            content = content,
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    // ====================================================================
    // 7. DELETE EXPERIENCE (own only)
    // ====================================================================
    @Transactional
    fun deleteExperience(userId: Long, experienceId: Long) {
        if (!experienceRepository.existsById(experienceId)) {
            throw ResourceNotFoundException("Experience not found")
        }
        val entry = experienceRepository.findByIdAndUser_Id(experienceId, userId)
            .orElseThrow { ForbiddenException("You can only delete your own experience entries") }
        experienceRepository.delete(entry)
    }

    // ====================================================================
    // 8. DELETE EDUCATION (own only)
    // ====================================================================
    @Transactional
    fun deleteEducation(userId: Long, educationId: Long) {
        if (!educationRepository.existsById(educationId)) {
            throw ResourceNotFoundException("Education not found")
        }
        val entry = educationRepository.findByIdAndUser_Id(educationId, userId)
            .orElseThrow { ForbiddenException("You can only delete your own education entries") }
        educationRepository.delete(entry)
    }

    // ====================================================================
    // HELPER METHODS
    // ====================================================================

    private fun getUserById(userId: Long): User =
        userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User account not found") }

    private fun buildUserResponse(user: User): UserResponse {
        val experiences = experienceRepository.findByUser_IdOrderByStartDateAsc(user.id!!)
            .map { UserExperienceResponse.fromEntity(it) }
        val educations = educationRepository.findByUser_IdOrderByStartDateAsc(user.id!!)
            .map { UserEducationResponse.fromEntity(it) }
        return UserResponse.fromEntity(user, experiences, educations)
    }

    private fun upsertExperience(user: User, req: UserExperienceRequest) {
        val entry = if (req.id != null) {
            if (!experienceRepository.existsById(req.id!!)) {
                throw ResourceNotFoundException("Experience not found")
            }
            experienceRepository.findByIdAndUser_Id(req.id!!, user.id!!)
                .orElseThrow { ForbiddenException("You can only edit your own experience entries") }
        } else {
            UserExperience()
        }

        entry.apply {
            this.user = user
            organization = req.organization.trim()
            designation = req.designation.trim()
            startDate = req.startDate.trim()
            endDate = req.endDate.trim()
            description = req.description?.trim()?.takeIf { it.isNotBlank() }
        }
        experienceRepository.save(entry)
    }

    private fun upsertEducation(user: User, req: UserEducationRequest) {
        val entry = if (req.id != null) {
            if (!educationRepository.existsById(req.id!!)) {
                throw ResourceNotFoundException("Education not found")
            }
            educationRepository.findByIdAndUser_Id(req.id!!, user.id!!)
                .orElseThrow { ForbiddenException("You can only edit your own education entries") }
        } else {
            UserEducation()
        }

        entry.apply {
            this.user = user
            institution = req.institution.trim()
            fieldOfStudy = req.fieldOfStudy.trim()
            startDate = req.startDate.trim()
            endDate = req.endDate.trim()
        }
        educationRepository.save(entry)
    }
}