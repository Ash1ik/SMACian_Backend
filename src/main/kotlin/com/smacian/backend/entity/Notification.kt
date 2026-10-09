/*
 * Notification - JPA Entity for one in-app notification for one user.
 *
 * Maps to the "notifications" table. Fan-out model: "send to everyone"
 * writes ONE row per recipient (500 users = trivial), so per-user read
 * state is a plain boolean with no join gymnastics.
 *
 * Deleting the recipient deletes their notifications (ON DELETE CASCADE).
 * Push delivery (FCM) is a later layer - this table is the source of truth
 * the app pulls (GET /api/notifications) and badges from (unread-count).
 */
package com.smacian.backend.entity

import com.smacian.backend.entity.enums.NotificationType
import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDateTime

@Entity
@Table(
    name = "notifications",
    indexes = [
        Index(name = "idx_notifications_recipient_created", columnList = "recipient_id,created_at")
    ]
)
class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var recipient: User? = null

    @field:Column(name = "type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var type: NotificationType = NotificationType.LIKE

    @field:Column(name = "title", nullable = false, length = 200)
    var title: String = ""

    @field:Column(name = "body", nullable = false, length = 500)
    var body: String = ""

    // What the notification is about (postId / bloodRequestId, per type).
    // Nullable - BROADCASTs may carry no reference.
    @field:Column(name = "reference_id")
    var referenceId: Long? = null

    @field:Column(name = "is_read", nullable = false)
    var read: Boolean = false

    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @PrePersist
    fun onCreate() {
        createdAt = LocalDateTime.now()
    }
}
