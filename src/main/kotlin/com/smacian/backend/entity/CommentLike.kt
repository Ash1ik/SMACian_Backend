/*
 * CommentLike - JPA Entity for one user's like on one comment (or reply).
 *
 * Maps to the "comment_likes" table. One row = one like. The unique
 * constraint on (comment_id, user_id) means a user can like a comment
 * only once - liking again is a no-op, unliking deletes the row.
 *
 * Deleting the comment (or its whole reply subtree) or the user deletes
 * their like rows (ON DELETE CASCADE).
 */
package com.smacian.backend.entity

import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDateTime

@Entity
@Table(
    name = "comment_likes",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_comment_likes_comment_user", columnNames = ["comment_id", "user_id"])
    ],
    indexes = [
        Index(name = "idx_comment_likes_comment_id", columnList = "comment_id")
    ]
)
class CommentLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var comment: Comment? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var user: User? = null

    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @PrePersist
    fun onCreate() {
        createdAt = LocalDateTime.now()
    }
}
