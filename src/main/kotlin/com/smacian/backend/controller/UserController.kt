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

import com.smacian.backend.dto.request.FcmTokenRequest
import com.smacian.backend.dto.request.UpdateProfileRequestExtended
import com.smacian.backend.dto.response.MessageResponse
import com.smacian.backend.dto.response.UserResponse
import com.smacian.backend.security.CurrentUser
import com.smacian.backend.service.DeviceService
import com.smacian.backend.service.UserService
import com.smacian.backend.util.HttpCache.matchesEtag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/user")
class UserController(
    private val userService: UserService,
    private val deviceService: DeviceService
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
    // 2. UPDATE MY PROFILE (details, social links, experience, education)
    // ====================================================================
    // Accepts UpdateProfileRequestExtended. experiences/educations with an
    // id are updated (must belong to the user), without an id they are
    // created; existing entries with no id sent are left untouched.

    @PutMapping("/profile")
    fun updateProfile(@Valid @RequestBody request: UpdateProfileRequestExtended): ResponseEntity<UserResponse> {
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
    // 3b. STREAM PROFILE PHOTO (public - mobile <Image> sends no JWT)
    // ====================================================================
    // profilePhotoUrl the upload endpoint stores points HERE. This is
    // permitAll() in SecurityConfig because the mobile app's <Image> tag
    // loads it without any Authorization header. We return the raw BYTEA
    // bytes with the stored content type so React Native renders it.
    @GetMapping("/profile/photo/{userId}")
    fun streamProfilePhoto(
        @PathVariable userId: Long,
        @RequestParam(required = false) v: String?,
        @RequestHeader(value = "If-None-Match", required = false) ifNoneMatch: String?
    ): ResponseEntity<ByteArray> {
        // Version-bound ETag: a new upload = new ?v= = new cache key.
        // No v (legacy URLs) -> no ETag, so a revalidation can never 304
        // stale bytes after the photo was replaced.
        val etag = v?.let { "\"profile-$userId-$it\"" }
        if (etag != null && matchesEtag(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build()
        }
        val (data, contentType) = userService.getProfilePhotoStream(userId)
        val body = ResponseEntity.status(HttpStatus.OK)
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
        if (etag != null) body.eTag(etag)
        return body.body(data)
    }

    // ====================================================================
    // 4. DELETE MY EXPERIENCE ENTRY
    // ====================================================================
    // 404 if the entry doesn't exist, 403 if it belongs to another user.

    @DeleteMapping("/experiences/{experienceId}")
    fun deleteExperience(@PathVariable experienceId: Long): ResponseEntity<Void> {
        userService.deleteExperience(CurrentUser.getUserId(), experienceId)
        return ResponseEntity.noContent().build()
    }

    // ====================================================================
    // 5. DELETE MY EDUCATION ENTRY
    // ====================================================================

    @DeleteMapping("/educations/{educationId}")
    fun deleteEducation(@PathVariable educationId: Long): ResponseEntity<Void> {
        userService.deleteEducation(CurrentUser.getUserId(), educationId)
        return ResponseEntity.noContent().build()
    }

    // ====================================================================
    // 6. UPLOAD COVER PHOTO
    // ====================================================================
    @PostMapping(value = ["/cover/photo"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateCoverPhoto(@RequestPart("file") file: MultipartFile): ResponseEntity<UserResponse> {
        val updated = userService.updateCoverPhoto(CurrentUser.getUserId(), file)
        return ResponseEntity.ok(updated)
    }

    // ====================================================================
    // 8. STREAM COVER PHOTO (public - mobile <Image> sends no JWT)
    // ====================================================================
    // Mirror of streamProfilePhoto. coverPhotoUrl the upload endpoint
    // stores points HERE (permitAll in SecurityConfig - mobile <Image> src
    // has no Authorization header). Returns raw BYTEA + stored content type.
    @GetMapping("/cover/photo/{userId}")
    fun streamCoverPhoto(
        @PathVariable userId: Long,
        @RequestParam(required = false) v: String?,
        @RequestHeader(value = "If-None-Match", required = false) ifNoneMatch: String?
    ): ResponseEntity<ByteArray> {
        // Same version-bound ETag pattern as streamProfilePhoto above.
        val etag = v?.let { "\"cover-$userId-$it\"" }
        if (etag != null && matchesEtag(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build()
        }
        val (data, contentType) = userService.getCoverPhotoStream(userId)
        val body = ResponseEntity.status(HttpStatus.OK)
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
        if (etag != null) body.eTag(etag)
        return body.body(data)
    }

    // ====================================================================
    // 9. REGISTER FCM PUSH TOKEN (my device, JWT)
    // ====================================================================
    // The app POSTs its current FCM token on every login (idempotent).
    // Re-login on a shared device reassigns the token to the new user.

    @PostMapping("/fcm-token")
    fun registerFcmToken(@RequestBody request: FcmTokenRequest): ResponseEntity<MessageResponse> {
        deviceService.registerToken(CurrentUser.getUserId(), request.token)
        return ResponseEntity.ok(MessageResponse(true, "Push token registered"))
    }

    // ====================================================================
    // 10. UNREGISTER FCM PUSH TOKEN (logout, JWT)
    // ====================================================================
    // Deletes MY row for this token so a signed-out device stops receiving
    // my pushes. Unknown token = still success (idempotent).

    @DeleteMapping("/fcm-token")
    fun unregisterFcmToken(@RequestBody request: FcmTokenRequest): ResponseEntity<Void> {
        deviceService.unregisterToken(CurrentUser.getUserId(), request.token)
        return ResponseEntity.noContent().build()
    }
}