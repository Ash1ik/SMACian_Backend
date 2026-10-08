/*
 * BloodRequest - JPA Entity for a blood-seeking request.
 *
 * Maps to the "blood_requests" table. SEPARATE from newsfeed posts by
 * design: requests filter by blood group/urgency/location/status and close
 * when fulfilled - none of which fits the posts model. Existing blood-text
 * posts stay in posts (no backfill, no coupling).
 *
 * Each request belongs to exactly one user (the requester); deleting the
 * user deletes their requests (ON DELETE CASCADE). Photos live in
 * "blood_request_images" (max 2, same BYTEA approach as feed images).
 */
package com.smacian.backend.entity

import com.smacian.backend.entity.enums.BloodGroup
import com.smacian.backend.entity.enums.BloodRequestStatus
import com.smacian.backend.entity.enums.Urgency
import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "blood_requests",
    indexes = [
        Index(name = "idx_blood_requests_requester_id", columnList = "requester_id"),
        Index(name = "idx_blood_requests_group_status", columnList = "blood_group,status"),
        Index(name = "idx_blood_requests_status_created", columnList = "status,created_at")
    ]
)
class BloodRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var requester: User? = null

    // Stored as the short label ("A+") via the converter - VARCHAR(3).
    @field:Convert(converter = BloodGroupLabelConverter::class)
    @field:Column(name = "blood_group", nullable = false, length = 3)
    var bloodGroup: BloodGroup? = null

    @field:Column(name = "bags", nullable = false)
    var bags: Int = 1

    @field:Column(name = "urgency", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    var urgency: Urgency = Urgency.STANDARD

    @field:Column(name = "hospital", nullable = false, length = 255)
    var hospital: String = ""

    @field:Column(name = "location", nullable = false, length = 255)
    var location: String = ""

    @field:Column(name = "needed_by", nullable = false)
    var neededBy: LocalDate = LocalDate.now()

    // Digits only (stripped of spaces/dashes/+ at write time), 6-15 chars.
    @field:Column(name = "contact_number", nullable = false, length = 20)
    var contactNumber: String = ""

    @field:Column(name = "note", length = 2000)
    var note: String? = null

    @field:Column(name = "status", nullable = false, length = 12)
    @Enumerated(EnumType.STRING)
    var status: BloodRequestStatus = BloodRequestStatus.OPEN

    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()

    @PrePersist
    fun onCreate() {
        val now = LocalDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
