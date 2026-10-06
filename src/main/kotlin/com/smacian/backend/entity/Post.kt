/*
 * Post - JPA Entity for a newsfeed post.
 *
 * Maps to the "posts" table. Each post belongs to exactly one user
 * (the author); deleting the user deletes their posts (ON DELETE CASCADE).
 * Images live in the separate "post_images" table (one row per image).
 */
package com.smacian.backend.entity

import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDateTime

@Entity
@Table(
    name = "posts",
    indexes = [
        Index(name = "idx_posts_created_at", columnList = "created_at"),
        Index(name = "idx_posts_author_id", columnList = "author_id")
    ]
)
class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var author: User? = null

    // Reshare source (Facebook-style share). Null = original post.
    // A reshare embeds this post; deleting it deletes its reshares too.
    // Shares always point at the ULTIMATE original (never chains).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_from_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    var sharedFrom: Post? = null

    // Free text of the post. Nullable - an image-only post is allowed
    // (but a post with neither text nor images is rejected in the service).
    @field:Column(name = "content", columnDefinition = "TEXT")
    var content: String? = null

    // How many times the post was shared. Incremented by POST /{id}/share.
    // columnDefinition carries the DEFAULT so existing rows get 0 when
    // ddl-auto=update adds this column to a non-empty table.
    @field:Column(name = "share_count", nullable = false, columnDefinition = "integer default 0")
    var shareCount: Int = 0

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
