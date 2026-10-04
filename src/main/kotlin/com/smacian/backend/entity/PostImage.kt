/*
 * PostImage - JPA Entity for one image attached to a newsfeed post.
 *
 * Maps to the "post_images" table. Image bytes are stored locally in
 * PostgreSQL (BYTEA) - the same approach as profile/cover photos
 * (no Cloudinary, no external service). The mobile app loads each image
 * via GET /api/feed/images/{id} (public, no JWT - see SecurityConfig).
 *
 * Deleting the post deletes its images (ON DELETE CASCADE).
 */
package com.smacian.backend.entity

import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDateTime

@Entity
@Table(name = "post_images")
class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var post: Post? = null

    // Raw image bytes - same @Lob mapping as profile/cover photos in User.
    @field:Lob
    @field:Column(name = "image_data", nullable = false)
    var imageData: ByteArray? = null

    // MIME type at upload time (e.g. "image/jpeg") - used for streaming.
    @field:Column(name = "content_type", length = 100)
    var contentType: String? = null

    // Position of the image inside the post (0, 1, 2...).
    @field:Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0

    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @PrePersist
    fun onCreate() {
        createdAt = LocalDateTime.now()
    }
}
