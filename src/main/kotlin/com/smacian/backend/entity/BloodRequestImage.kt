/*
 * BloodRequestImage - JPA Entity for one photo on a blood request.
 *
 * Maps to the "blood_request_images" table. Same BYTEA + metadata +
 * sort_order approach as feed post images (max 2 per request).
 * Served publicly via GET /api/blood-requests/images/{id} (mobile
 * <Image> sends no JWT - see SecurityConfig).
 *
 * Deleting the request deletes its images (ON DELETE CASCADE).
 */
package com.smacian.backend.entity

import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDateTime

@Entity
@Table(
    name = "blood_request_images",
    indexes = [
        Index(name = "idx_blood_request_images_request_id", columnList = "request_id")
    ]
)
class BloodRequestImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var bloodRequest: BloodRequest? = null

    // Raw image bytes - same @Lob mapping as feed post images.
    @field:Lob
    @field:Column(name = "image_data", nullable = false)
    var imageData: ByteArray? = null

    // MIME type at upload time (e.g. "image/jpeg") - used for streaming.
    @field:Column(name = "content_type", length = 100)
    var contentType: String? = null

    // Stored dimensions AFTER processing (downscaled to fit 1600x1600).
    // Null for WebP (no JDK decoder) - app treats null as "unknown".
    @field:Column(name = "width")
    var width: Int? = null

    @field:Column(name = "height")
    var height: Int? = null

    // Position of the image inside the request (0, 1).
    @field:Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0

    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @PrePersist
    fun onCreate() {
        createdAt = LocalDateTime.now()
    }
}
