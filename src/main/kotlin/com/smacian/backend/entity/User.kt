/*
 * User - JPA Entity (Kotlin version)
 *
 * This class maps to the "users" table in our PostgreSQL database.
 * Each property below becomes a column in the database table.
 *
 * Kotlin notes:
 *   - `var`  = mutable property (has getter AND setter, like Java fields)
 *   - `val`  = read-only property (has getter only - we use for id & timestamps)
 *   - `@field:` annotation target = apply the annotation to the underlying Java
 *     field, which is exactly what Hibernate (JPA) needs.
 *
 * The `kotlin-jpa` plugin automatically adds a no-argument constructor,
 * which JPA requires, so we don't write it manually.
 */
package com.smacian.backend.entity

import com.smacian.backend.entity.enums.BloodGroup
import com.smacian.backend.entity.enums.Gender
import com.smacian.backend.entity.enums.RelationshipStatus
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "users",
    // Unique constraints enforce "at most one user per email / per phone".
    uniqueConstraints = [
        UniqueConstraint(name = "uk_users_email", columnNames = ["email"]),
        UniqueConstraint(name = "uk_users_phone", columnNames = ["phone"])
    ]
)
class User {

    // ==================== Primary Key ====================
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // PostgreSQL auto-generates: 1, 2, 3...
    @Column(name = "id")
    var id: Long? = null

    // ==================== Personal Information ====================

    @field:Column(name = "first_name", nullable = false, length = 50)
    var firstName: String = ""

    @field:Column(name = "last_name", nullable = false, length = 50)
    var lastName: String = ""

    @field:Column(name = "date_of_birth", nullable = false)
    var dateOfBirth: LocalDate = LocalDate.now()

    // Enum is stored as a short string ('MALE', 'FEMALE', 'OTHER').
    @field:Column(name = "gender", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    var gender: Gender = Gender.OTHER

    // ==================== Contact (Login Credentials) ====================
    // A user registers with either email OR phone (not both required).
    // Both allow NULL in the database, but our AuthService enforces
    // that at least one must be provided.

    @field:Column(name = "email", length = 255, unique = true)
    var email: String? = null

    @field:Column(name = "phone", length = 15, unique = true)
    var phone: String? = null

    // ==================== Password ====================
    // Stored as a BCrypt hash - NEVER plain text. Example:
    // "$2a$10$Qk.YkWlhWmcYiq2HhV2O1uHDo."
    // BCrypt is a one-way hash: it can never be converted back.

    @field:Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String = ""

    // ==================== Photos ====================
    // Store the Cloudinary URLs returned after image upload.

    @field:Column(name = "profile_photo_url", length = 500)
    var profilePhotoUrl: String? = null

    @field:Column(name = "cover_photo_url", length = 500)
    var coverPhotoUrl: String? = null

    // ==================== Local Photo Storage ====================
    // Multipart uploads are stored right here in Postgres (BYTEA) so the
    // photo feature needs ZERO external services. The GET endpoints stream
    // these back:
    //   GET /api/user/profile/photo/{userId}
    //   GET /api/user/cover/photo/{userId}
    // profilePhotoUrl / coverPhotoUrl above simply point at those endpoints.
    @field:Lob
    @field:Column(name = "profile_photo_data")
    var profilePhotoData: ByteArray? = null

    @field:Column(name = "profile_photo_content_type", length = 100)
    var profilePhotoContentType: String? = null

    @field:Lob
    @field:Column(name = "cover_photo_data")
    var coverPhotoData: ByteArray? = null

    @field:Column(name = "cover_photo_content_type", length = 100)
    var coverPhotoContentType: String? = null

    // ==================== Extended Profile Details ====================

    @field:Column(name = "designation", length = 100)
    var designation: String? = null

    @field:Column(name = "bio", length = 500)
    var bio: String? = null

    @field:Column(name = "location", length = 255)
    var location: String? = null

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "blood_group", length = 16)
    var bloodGroup: BloodGroup? = null

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "relationship_status", length = 32)
    var relationshipStatus: RelationshipStatus? = null

    // Social links stored as JSONB in PostgreSQL (each a valid URL).
    @field:JdbcTypeCode(SqlTypes.JSON)
    @field:Column(name = "social_links", columnDefinition = "jsonb")
    var socialLinks: List<String>? = null

    // ==================== Account Status ====================

    @field:Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    // ==================== Terms & Conditions ====================

    @field:Column(name = "terms_accepted", nullable = false)
    var termsAccepted: Boolean = false

    @field:Column(name = "terms_accepted_at")
    var termsAcceptedAt: LocalDateTime? = null

    // ==================== Timestamps ====================

    // updatedAt is var because it changes on every update.
    // createdAt is a read-only val set once when the row is created.
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()

    // ==================== Auto-set Timestamps ====================

    /*
     * @PrePersist runs BEFORE the first save of this entity.
     * We auto-set both timestamps here.
     */
    @PrePersist
    fun onCreate() {
        val now = LocalDateTime.now()
        this.createdAt = now
        this.updatedAt = now
    }

    /*
     * @PreUpdate runs BEFORE every update to an existing row.
     * We only refresh the updatedAt timestamp.
     */
    @PreUpdate
    fun onUpdate() {
        this.updatedAt = LocalDateTime.now()
    }

    /*
     * Convenience property: full name (used for default avatars).
     * Example: "Ahmed" + " " + "Khan" = "Ahmed Khan"
     */
    val fullName: String
        get() = "${firstName.trim()} ${lastName.trim()}"
}