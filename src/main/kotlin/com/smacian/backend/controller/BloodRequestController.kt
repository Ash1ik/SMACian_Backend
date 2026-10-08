/*
 * BloodRequestController - REST endpoints for blood-seeking requests.
 *
 *   POST   /api/blood-requests                  -> create (multipart fields + images[])
 *   GET    /api/blood-requests?...              -> filtered list, newest first
 *   GET    /api/blood-requests/{id}              -> single request
 *   PATCH  /api/blood-requests/{id}/status      -> close (FULFILLED/CANCELLED)
 *   DELETE /api/blood-requests/{id}              -> delete own request
 *   GET    /api/blood-requests/images/{imageId}  -> stream one photo (PUBLIC)
 *
 * ALL data endpoints need login (JWT). Only the image stream is public:
 * the mobile <Image> loads it without an Authorization header (permitAll
 * in SecurityConfig - same pattern as feed post images).
 *
 * SECURITY: the requester ID is NEVER taken from the request. It is read
 * from the JWT (CurrentUser.getUserId()).
 */
package com.smacian.backend.controller

import com.smacian.backend.dto.request.BloodRequestStatusUpdate
import com.smacian.backend.dto.response.BloodRequestResponse
import com.smacian.backend.dto.response.PagedResponse
import com.smacian.backend.security.CurrentUser
import com.smacian.backend.service.BloodRequestService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/blood-requests")
class BloodRequestController(
    private val bloodRequestService: BloodRequestService
) {

    // ====================================================================
    // 1. CREATE (multipart: fields + optional images[])
    // ====================================================================
    //   curl -X POST http://localhost:8080/api/blood-requests \
    //        -H "Authorization: Bearer <token>" \
    //        -F "bloodGroup=B+" -F "bags=2" -F "urgency=URGENT" \
    //        -F "hospital=Dhaka Medical" -F "location=Dhaka" \
    //        -F "neededBy=2026-10-10" -F "contactNumber=01712345678" \
    //        -F "note=Surgery patient" -F "images=@photo.jpg"
    // ====================================================================
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createRequest(
        @RequestParam(required = false) bloodGroup: String?,
        @RequestParam(required = false) bags: String?,
        @RequestParam(required = false) urgency: String?,
        @RequestParam(required = false) hospital: String?,
        @RequestParam(required = false) location: String?,
        @RequestParam(required = false) neededBy: String?,
        @RequestParam(required = false) contactNumber: String?,
        @RequestParam(required = false) note: String?,
        @RequestPart(required = false) images: List<MultipartFile>?
    ): ResponseEntity<BloodRequestResponse> {
        val request = bloodRequestService.createRequest(
            CurrentUser.getUserId(),
            bloodGroup, bags, urgency, hospital, location,
            neededBy, contactNumber, note, images
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(request)
    }

    // ====================================================================
    // 2. LIST (filtered, newest first, default OPEN)
    // ====================================================================
    // ?bloodGroup=B%2B&urgency=URGENT&location=dhaka&status=OPEN&page=0&size=20

    @GetMapping
    fun listRequests(
        @RequestParam(required = false) bloodGroup: String?,
        @RequestParam(required = false) urgency: String?,
        @RequestParam(required = false) location: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PagedResponse<BloodRequestResponse> =
        bloodRequestService.listRequests(bloodGroup, urgency, location, status, page, size)

    // ====================================================================
    // 3. SINGLE REQUEST - GET /api/blood-requests/{id}
    // ====================================================================

    @GetMapping("/{id}")
    fun getRequest(@PathVariable id: Long): ResponseEntity<BloodRequestResponse> {
        val request = bloodRequestService.getRequest(id)
        return ResponseEntity.ok(request)
    }

    // ====================================================================
    // 3b. UPDATE - PUT /api/blood-requests/{id} (requester only, OPEN only)
    // ====================================================================
    // Same multipart fields as create, ALL optional (omit = keep).
    // bloodGroup can't change (medical fact); status changes via PATCH.
    // images: omit = keep photos, send 0-2 parts = replace the whole set.

    @PutMapping(value = ["/{id}"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateRequest(
        @PathVariable id: Long,
        @RequestParam(required = false) bags: String?,
        @RequestParam(required = false) urgency: String?,
        @RequestParam(required = false) hospital: String?,
        @RequestParam(required = false) location: String?,
        @RequestParam(required = false) neededBy: String?,
        @RequestParam(required = false) contactNumber: String?,
        @RequestParam(required = false) note: String?,
        @RequestPart(required = false) images: List<MultipartFile>?
    ): ResponseEntity<BloodRequestResponse> {
        val updated = bloodRequestService.updateRequest(
            CurrentUser.getUserId(), id,
            bags, urgency, hospital, location,
            neededBy, contactNumber, note, images
        )
        return ResponseEntity.ok(updated)
    }

    // ====================================================================
    // 4. CLOSE - PATCH /api/blood-requests/{id}/status (requester only)
    // ====================================================================
    // Body { "status": "FULFILLED" } or { "status": "CANCELLED" }.
    // 403 if not the requester, 400 if already closed or bad value.

    @PatchMapping("/{id}/status")
    fun updateStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: BloodRequestStatusUpdate
    ): ResponseEntity<BloodRequestResponse> {
        val updated = bloodRequestService.updateStatus(CurrentUser.getUserId(), id, request.status)
        return ResponseEntity.ok(updated)
    }

    // ====================================================================
    // 5. DELETE (requester only, photos cascade)
    // ====================================================================

    @DeleteMapping("/{id}")
    fun deleteRequest(@PathVariable id: Long): ResponseEntity<Void> {
        bloodRequestService.deleteRequest(CurrentUser.getUserId(), id)
        return ResponseEntity.noContent().build()
    }

    // ====================================================================
    // 6. STREAM PHOTO (public - mobile <Image> sends no JWT)
    // ====================================================================
    // The images[].url values point HERE. permitAll() in SecurityConfig.
    // Same 24h public cache as the other photo streams.

    @GetMapping("/images/{imageId}")
    fun streamRequestImage(@PathVariable imageId: Long): ResponseEntity<ByteArray> {
        val (data, contentType) = bloodRequestService.getRequestImageStream(imageId)
        return ResponseEntity.status(HttpStatus.OK)
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
            .body(data)
    }
}
