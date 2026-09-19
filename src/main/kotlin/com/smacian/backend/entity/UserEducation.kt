/*
 * UserEducation - JPA Entity for a user's education entry.
 *
 * Maps to the "user_educations" table. Each entry belongs to exactly
 * one user; deleting the user deletes their entries (ON DELETE CASCADE).
 */
package com.smacian.backend.entity

import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDateTime

@Entity
@Table(name = "user_educations")
class UserEducation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var user: User? = null

    @field:Column(name = "institution", nullable = false, length = 100)
    var institution: String = ""

    @field:Column(name = "field_of_study", nullable = false, length = 100)
    var fieldOfStudy: String = ""

    // Stored as "MMM yyyy" (e.g. "Jan 2025").
    @field:Column(name = "start_date", nullable = false, length = 16)
    var startDate: String = ""

    // "MMM yyyy" or the literal "Present".
    @field:Column(name = "end_date", nullable = false, length = 16)
    var endDate: String = ""

    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @PrePersist
    fun onCreate() {
        createdAt = LocalDateTime.now()
    }
}