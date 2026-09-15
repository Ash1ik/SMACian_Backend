/*
 * UserService - Handles all logged-in user profile operations.
 *
 * These methods can only be called by an authenticated user
 * (JWT token required - enforced by SecurityConfig).
 *
 * Public methods:
 *   1. getProfile(userId)              - view own profile
 *   2. updateProfile(userId, request)  - update name, DOB, gender
 *   3. updateProfilePhoto(userId, file) - upload/replace avatar
 *   4. updateCoverPhoto(userId, file)   - upload/replace cover photo
 */
package com.smacian.backend.service

import com.smacian.backend.dto.request.UpdateProfileRequest
import com.smacian.backend.dto.response.UserResponse
import com.smacian.backend.entity.User
import com.smacian.backend.exception.BadRequestException
import com.smacian.backend.exception.ResourceNotFoundException
import com.smacian.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.time.Period

@Service
class UserService(
    private val userRepository: UserRepository,
    private val cloudinaryService: CloudinaryService
) {

    // ====================================================================
    // 1. GET PROFILE
    // ====================================================================
    // userId comes from the JWT token (never from the request body).
    @Transactional(readOnly = true)
    fun getProfile(userId: Long): UserResponse =
        UserResponse.fromEntity(getUserById(userId))

    // ====================================================================
    // 2. UPDATE PROFILE (name, date of birth, gender)
    // ====================================================================
    // Contact (email/phone) is intentionally NOT changeable here - changing
    // login credentials is sensitive and handled separately in the app.
    @Transactional
    fun updateProfile(userId: Long, request: UpdateProfileRequest): UserResponse {

        val user = getUserById(userId)

        // Parse + validate date of birth (13+ years old).
        val dob = parseDateOfBirth(request.dateOfBirth)

        // Update the fields.
        user.apply {
            firstName = request.firstName.trim()
            lastName = request.lastName.trim()
            dateOfBirth = dob
            gender = request.gender
        }

        // Save and return the updated profile.
        return UserResponse.fromEntity(userRepository.save(user))
    }

    // ====================================================================
    // 3. UPDATE PROFILE PHOTO (avatar)
    // ====================================================================
    @Transactional
    fun updateProfilePhoto(userId: Long, file: MultipartFile): UserResponse {

        val user = getUserById(userId)

        if (file.isEmpty) {
            throw BadRequestException("No photo was uploaded. Please select an image")
        }

        // Upload to Cloudinary → get a public URL.
        val photoUrl = cloudinaryService.uploadImage(file, userId, "avatars")

        // Save the URL in the user record.
        user.profilePhotoUrl = photoUrl
        return UserResponse.fromEntity(userRepository.save(user))
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

        val coverUrl = cloudinaryService.uploadImage(file, userId, "covers")

        user.coverPhotoUrl = coverUrl
        return UserResponse.fromEntity(userRepository.save(user))
    }

    // ====================================================================
    // HELPER METHODS
    // ====================================================================

    /*
     * Finds a user by ID, throws a clean 404 if not found.
     */
    private fun getUserById(userId: Long): User =
        userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User account not found") }

    /*
     * Parses "yyyy-MM-dd" into LocalDate + age validation (13+).
     * Same rules as registration.
     */
    private fun parseDateOfBirth(dobRaw: String): LocalDate {

        val dob = try {
            LocalDate.parse(dobRaw)
        } catch (e: Exception) {
            throw BadRequestException("Invalid date of birth format. Please use yyyy-MM-dd")
        }

        if (dob.isAfter(LocalDate.now())) {
            throw BadRequestException("Date of birth cannot be in the future")
        }

        val age = Period.between(dob, LocalDate.now()).years
        if (age < 13) {
            throw BadRequestException("You must be at least 13 years old")
        }

        return dob
    }
}