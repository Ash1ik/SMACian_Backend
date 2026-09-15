/*
 * UserController - REST endpoints for logged-in user profile management.
 *
 * These endpoints are PROTECTED (login required). SecurityConfig
 * enforces this - requests need a valid JWT in the "Authorization"
 * header to reach these methods.
 *
 * Endpoints:
 *   GET  /api/user/profile          → view my profile
 *   PUT  /api/user/profile          → update name, DOB, gender
 *   POST /api/user/profile/photo    → upload/replace avatar (multipart)
 *   POST /api/user/cover/photo      → upload/replace cover photo (multipart)
 *
 * SECURITY: the user ID is NEVER taken from the request. It is read from
 * the JWT (CurrentUser.getUserId()). A user can only ever access/modify
 * their OWN profile - this prevents the "IDOR" security flaw.
 */
package com.smacian.backend.controller

import com.smacian.backend.dto.request.UpdateProfileRequest
import com.smacian.backend.dto.response.UserResponse
import com.smacian.backend.security.CurrentUser
import com.smacian.backend.service.UserService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/user")
class UserController(
    private val userService: UserService
) {

    // ====================================================================
    // 1. GET MY PROFILE
    // ====================================================================
    // Example response:
    // {
    //   "id": 1,
    //   "firstName": "Ahmed",
    //   "lastName": "Khan",
    //   "email": "ahmed@example.com",
    //   ...
    // }

    @GetMapping("/profile")
    fun getProfile(): ResponseEntity<UserResponse> {
        val profile = userService.getProfile(CurrentUser.getUserId())
        return ResponseEntity.ok(profile)
    }

    // ====================================================================
    // 2. UPDATE MY PROFILE (name, date of birth, gender)
    // ====================================================================

    @PutMapping("/profile")
    fun updateProfile(@Valid @RequestBody request: UpdateProfileRequest): ResponseEntity<UserResponse> {
        val updated = userService.updateProfile(CurrentUser.getUserId(), request)
        return ResponseEntity.ok(updated)
    }

    // ====================================================================
    // 3. UPLOAD PROFILE PHOTO (avatar)
    // ====================================================================
    // Multipart request - the app sends "file" as a form-data part:
    //   curl -X POST http://localhost:8080/api/user/profile/photo \
    //        -H "Authorization: Bearer <token>" \
    //        -F "file=@myphoto.jpg"
    // ====================================================================
    @PostMapping(value = ["/profile/photo"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateProfilePhoto(@RequestPart("file") file: MultipartFile): ResponseEntity<UserResponse> {
        val updated = userService.updateProfilePhoto(CurrentUser.getUserId(), file)
        return ResponseEntity.ok(updated)
    }

    // ====================================================================
    // 4. UPLOAD COVER PHOTO
    // ====================================================================
    @PostMapping(value = ["/cover/photo"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateCoverPhoto(@RequestPart("file") file: MultipartFile): ResponseEntity<UserResponse> {
        val updated = userService.updateCoverPhoto(CurrentUser.getUserId(), file)
        return ResponseEntity.ok(updated)
    }
}