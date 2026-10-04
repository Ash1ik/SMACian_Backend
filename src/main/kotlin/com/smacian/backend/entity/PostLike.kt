/*
 * PostLike - JPA Entity for one user's like on one newsfeed post.
 *
 * Maps to the "post_likes" table. One row = one like. The unique
 * constraint on (post_id, user_id) means a user can like a post only
 * once - liking again is a no-op, unliking deletes the row.
 *
 * Deleting the post or the user deletes their like rows
 * (ON DELETE CASCADE).
 */
package com.smacian.backend.entity

import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDateTime

@Entity
@Table(
    name = "post_likes",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_post_likes_post_user", columnNames = ["post_id", "user_id"])
    ]
)
class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var post: Post? = null

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
