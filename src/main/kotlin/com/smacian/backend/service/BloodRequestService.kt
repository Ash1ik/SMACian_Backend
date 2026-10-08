/*
 * BloodRequestService - Handles blood-seeking requests (CRUD + images).
 *
 * SEPARATE from the newsfeed by design: requests filter by blood group /
 * urgency / location / status and close when fulfilled - none of which
 * fits the posts model. No likes/comments on v1.
 *
 * Images follow the feed pipeline exactly (ImageProcessing: magic-byte
 * sniff, decode, downscale to 1600px, recompress; BYTEA + metadata +
 * sort_order; max 2 per request; 5MB/file pre-check). Streamed publicly
 * via GET /api/blood-requests/images/{id} (mobile <Image> sends no JWT).
 *
 * Rules:
 *   - Only the requester can close/delete their request (403 otherwise).
 *   - Only OPEN requests can be closed (FULFILLED/CANCELLED/EXPIRED -> 400).
 *   - Status body accepts FULFILLED or CANCELLED only (EXPIRED is job-set).
 *   - OPEN rows whose needed_by passed are expired nightly (see expireOverdue).
 */
package com.smacian.backend.service

import com.smacian.backend.dto.response.BloodRequestResponse
import com.smacian.backend.dto.response.PagedResponse
import com.smacian.backend.dto.response.PostImageResponse
import com.smacian.backend.entity.BloodRequest
import com.smacian.backend.entity.BloodRequestImage
import com.smacian.backend.entity.enums.BloodGroup
import com.smacian.backend.entity.enums.BloodRequestStatus
import com.smacian.backend.entity.enums.Urgency
import com.smacian.backend.exception.BadRequestException
import com.smacian.backend.exception.ForbiddenException
import com.smacian.backend.exception.ResourceNotFoundException
import com.smacian.backend.exception.ValidationException
import com.smacian.backend.repository.BloodRequestImageRepository
import com.smacian.backend.repository.BloodRequestRepository
import com.smacian.backend.repository.UserRepository
import com.smacian.backend.util.BloodRequestValidation
import com.smacian.backend.util.ImageProcessing
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class BloodRequestService(
    private val bloodRequestRepository: BloodRequestRepository,
    private val bloodRequestImageRepository: BloodRequestImageRepository,
    private val userRepository: UserRepository
) {

    companion object {
        const val MAX_IMAGES_PER_REQUEST = 2
        const val MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024
        private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    }

    // ====================================================================
    // 1. CREATE (multipart form fields + optional images[])
    // ====================================================================
    @Transactional
    fun createRequest(
        requesterId: Long,
        bloodGroup: String?,
        bags: String?,
        urgency: String?,
        hospital: String?,
        location: String?,
        neededBy: String?,
        contactNumber: String?,
        note: String?,
        images: List<MultipartFile>?
    ): BloodRequestResponse {

        // ---- field validation (collect-all-errors -> 400 fieldErrors) ----
        val (valid, errors) = BloodRequestValidation.validate(
            bloodGroup, bags, urgency, hospital, location,
            neededBy, contactNumber, note
        )
        if (valid == null) {
            throw ValidationException(errors)
        }

        // ---- images: max 2, same pipeline as feed posts ----
        val files = images?.filter { !it.isEmpty } ?: emptyList()
        if (files.size > MAX_IMAGES_PER_REQUEST) {
            throw BadRequestException("A blood request can have at most $MAX_IMAGES_PER_REQUEST photos")
        }
        files.forEach { validateImageFile(it) }
        // Sequential (never parallel): one decoded bitmap at a time.
        val processed = files.map { ImageProcessing.process(it) }

        val requester = userRepository.findById(requesterId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        val request = BloodRequest().apply {
            this.requester = requester
            this.bloodGroup = valid.bloodGroup
            this.bags = valid.bags
            this.urgency = valid.urgency
            this.hospital = valid.hospital
            this.location = valid.location
            this.neededBy = valid.neededBy
            this.contactNumber = valid.contactNumber
            this.note = valid.note
            this.status = BloodRequestStatus.OPEN
        }
        val saved = bloodRequestRepository.save(request)

        processed.forEachIndexed { index, img ->
            val image = BloodRequestImage().apply {
                this.bloodRequest = saved
                this.imageData = img.bytes
                this.contentType = img.contentType
                this.width = img.width
                this.height = img.height
                this.sortOrder = index
            }
            bloodRequestImageRepository.save(image)
        }

        return toResponse(saved)
    }

    // ====================================================================
    // 2. LIST (filtered, newest first, default OPEN)
    // ====================================================================
    @Transactional(readOnly = true)
    fun listRequests(
        bloodGroup: String?,
        urgency: String?,
        location: String?,
        status: String?,
        page: Int,
        size: Int
    ): PagedResponse<BloodRequestResponse> {

        // Garbage filter values -> 400 (same closed vocabularies as create).
        val bg = bloodGroup?.trim()?.takeIf { it.isNotEmpty() }?.let {
            BloodGroup.fromLabelOrNull(it)
                ?: throw BadRequestException("Invalid blood group '$it'. Allowed: A+, A-, B+, B-, AB+, AB-, O+, O-")
        }
        val urg = urgency?.trim()?.takeIf { it.isNotEmpty() }?.let {
            Urgency.fromStringOrNull(it)
                ?: throw BadRequestException("Invalid urgency '$it'. Allowed: EMERGENCY, URGENT, STANDARD")
        }
        // Default OPEN hides fulfilled/cancelled/expired unless asked.
        val st = status?.trim()?.takeIf { it.isNotEmpty() }?.let {
            BloodRequestStatus.fromStringOrNull(it)
                ?: throw BadRequestException("Invalid status '$it'. Allowed: OPEN, FULFILLED, CANCELLED, EXPIRED")
        } ?: BloodRequestStatus.OPEN

        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 20)

        val pageable = PageRequest.of(safePage, safeSize)
        val result = bloodRequestRepository.findRequests(
            bg, urg, location?.trim() ?: "", st, pageable
        )

        val content = toResponseList(result.content)

        return PagedResponse(
            content = content,
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    // ====================================================================
    // 3. SINGLE REQUEST
    // ====================================================================
    @Transactional(readOnly = true)
    fun getRequest(requestId: Long): BloodRequestResponse {
        return toResponse(getRequestById(requestId))
    }

    // ====================================================================
    // 4. CLOSE (requester only): FULFILLED or CANCELLED
    // ====================================================================
    @Transactional
    fun updateStatus(requesterId: Long, requestId: Long, status: String?): BloodRequestResponse {

        val request = getRequestById(requestId)

        if (request.requester!!.id != requesterId) {
            throw ForbiddenException("You can only update your own blood requests")
        }

        if (request.status != BloodRequestStatus.OPEN) {
            throw BadRequestException("Only OPEN requests can be closed (current: ${request.status.name})")
        }

        val target = BloodRequestStatus.fromStringOrNull(status)
        if (target != BloodRequestStatus.FULFILLED && target != BloodRequestStatus.CANCELLED) {
            throw BadRequestException("Status must be FULFILLED or CANCELLED")
        }

        request.status = target
        return toResponse(bloodRequestRepository.save(request))
    }

    // ====================================================================
    // 5. DELETE (requester only; images cascade)
    // ====================================================================
    @Transactional
    fun deleteRequest(requesterId: Long, requestId: Long) {

        val request = getRequestById(requestId)

        if (request.requester!!.id != requesterId) {
            throw ForbiddenException("You can only delete your own blood requests")
        }

        bloodRequestRepository.delete(request)
    }

    // ====================================================================
    // 6. IMAGE STREAM GETTER (bytes + content type, or 404)
    // ====================================================================
    // Public endpoint - no ownership check (same as feed post images).
    @Transactional(readOnly = true)
    fun getRequestImageStream(imageId: Long): Pair<ByteArray, String> {

        val image = bloodRequestImageRepository.findById(imageId)
            .orElseThrow { ResourceNotFoundException("Blood request image not found") }

        val data = image.imageData
        val contentType = image.contentType

        if (data == null || contentType == null) {
            throw ResourceNotFoundException("Blood request image not found")
        }

        return data to contentType
    }

    // ====================================================================
    // 7. NIGHTLY EXPIRY (called by ScheduledTasks)
    // ====================================================================
    // OPEN requests whose needed_by passed become EXPIRED. Bulk update
    // (no entity loading). Returns rows expired (for the job log).
    @Transactional
    fun expireOverdue(): Int =
        bloodRequestRepository.expireOverdue(
            BloodRequestStatus.OPEN,
            BloodRequestStatus.EXPIRED,
            LocalDate.now(),
            LocalDateTime.now()
        )

    // ====================================================================
    // Helpers
    // ====================================================================

    private fun getRequestById(requestId: Long): BloodRequest =
        bloodRequestRepository.findById(requestId)
            .orElseThrow { ResourceNotFoundException("Blood request not found") }

    // Single-request mapping (requester lazy-loads: 1 extra SELECT).
    private fun toResponse(request: BloodRequest): BloodRequestResponse {
        val images = bloodRequestImageRepository
            .findMetadataByRequestIdOrdered(request.id!!)
            .map { toImageResponse(it) }
        return BloodRequestResponse.fromEntity(request, images)
    }

    // Whole page: 1 metadata query for ALL requests (no BYTEA, no per-row).
    // Requesters come JOIN FETCHed - no per-row lazy SELECT.
    private fun toResponseList(requests: List<BloodRequest>): List<BloodRequestResponse> {
        if (requests.isEmpty()) return emptyList()
        val ids = requests.map { it.id!! }
        val imagesByRequest: Map<Long, List<PostImageResponse>> =
            bloodRequestImageRepository.findMetadataByRequestIds(ids)
                .groupBy(
                    { (it[0] as Long) },
                    { row -> toImageResponse(arrayOf(row[1], row[2], row[3], row[4])) }
                )
        return requests.map { request ->
            BloodRequestResponse.fromEntity(
                request,
                imagesByRequest[request.id!!] ?: emptyList()
            )
        }
    }

    // (id, width, height, sortOrder) metadata row -> DTO (same shape as feed).
    private fun toImageResponse(row: Array<Any>): PostImageResponse {
        val id = row[0] as Long
        return PostImageResponse(
            id = id,
            url = imageStreamUrl(id),
            width = row[1] as Int?,
            height = row[2] as Int?,
            sortOrder = row[3] as Int
        )
    }

    private fun imageStreamUrl(imageId: Long): String =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/blood-requests/images/{imageId}")
            .buildAndExpand(imageId)
            .toUriString()

    // CHEAP pre-checks (allow-list + 5MB) BEFORE the expensive decode in
    // ImageProcessing.process, which re-verifies via magic bytes + decode.
    private fun validateImageFile(file: MultipartFile) {
        val type = file.contentType
        if (type == null || type !in ALLOWED_IMAGE_TYPES) {
            throw BadRequestException("Only JPG, PNG, WEBP or GIF images are allowed")
        }
        if (file.size > MAX_IMAGE_SIZE_BYTES) {
            throw BadRequestException("Each photo must be less than 5MB")
        }
    }
}
