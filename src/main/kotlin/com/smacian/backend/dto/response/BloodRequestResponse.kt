/*
 * BloodRequestResponse - One blood request as the mobile app receives it.
 *
 * JSON: { "id": 9, "requesterId": 1, "requesterName": "Ashik Iqbal",
 *         "requesterPhotoUrl": "http://.../api/user/profile/photo/1",
 *         "bloodGroup": "B+", "bags": 2, "urgency": "URGENT",
 *         "hospital": "Dhaka Medical College Hospital", "location": "Dhaka",
 *         "neededBy": "2026-10-10", "contactNumber": "01712345678",
 *         "note": "Surgery patient.", "status": "OPEN",
 *         "images": [{ "id": 44, "url": ".../api/blood-requests/images/44",
 *                      "width": 1200, "height": 1600, "sortOrder": 0 }],
 *         "createdAt": "2026-10-08T14:30:00", "updatedAt": "..." }
 *
 * Timestamps are zoneless LocalDateTime (same as PostResponse); neededBy
 * serializes as "yyyy-MM-dd". images reuse the PostImageResponse shape.
 */
package com.smacian.backend.dto.response

import com.smacian.backend.entity.BloodRequest
import java.time.LocalDate
import java.time.LocalDateTime

data class BloodRequestResponse(
    val id: Long,
    val requesterId: Long,
    val requesterName: String,
    val requesterPhotoUrl: String?,
    val bloodGroup: String,
    val bags: Int,
    val urgency: String,
    val hospital: String,
    val location: String,
    val neededBy: LocalDate,
    val contactNumber: String,
    val note: String?,
    val status: String,
    val images: List<PostImageResponse>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun fromEntity(entity: BloodRequest, images: List<PostImageResponse>): BloodRequestResponse {
            val requester = entity.requester!!
            return BloodRequestResponse(
                id = entity.id!!,
                requesterId = requester.id!!,
                requesterName = requester.fullName,
                requesterPhotoUrl = requester.profilePhotoUrl,
                bloodGroup = entity.bloodGroup!!.label,
                bags = entity.bags,
                urgency = entity.urgency.name,
                hospital = entity.hospital,
                location = entity.location,
                neededBy = entity.neededBy,
                contactNumber = entity.contactNumber,
                note = entity.note,
                status = entity.status.name,
                images = images,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        }
    }
}
