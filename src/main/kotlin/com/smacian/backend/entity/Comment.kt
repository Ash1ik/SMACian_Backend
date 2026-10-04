/*
 * Comment - JPA Entity for a comment on a newsfeed post.
 *
 * Maps to the "comments" table. Nested replies are modeled with a
 * self-referencing parent: a top-level comment has parent = null, a reply
 * points at the comment it answers (any depth). Deleting a comment deletes
 * its whole reply subtree (ON DELETE CASCADE on parent_id).
 *
 * Deleting the post or the author deletes their comment rows
 * (ON DELETE CASCADE).
 */
package com.smacian.backend.entity

import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDateTime

@Entity
@Table(name = "comments")
class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var post: Post? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var author: User? = null

    // Null = top-level comment on the post. Non-null = reply to that comment.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    var parent: Comment? = null

    @field:Column(name = "content", nullable = false, columnDefinition = "TEXT")
    var content: String = ""

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
