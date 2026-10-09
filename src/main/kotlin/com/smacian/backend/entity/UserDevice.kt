/*
 * UserDevice - JPA Entity for one Android device's FCM push token.
 *
 * Maps to the "user_devices" table. One user may own several rows (phone +
 * tablet); one token belongs to exactly one user at a time (re-login on a
 * shared device reassigns the row). Invalid tokens are pruned by
 * PushService when FCM reports them dead.
 *
 * Deleting the user deletes their device rows (ON DELETE CASCADE).
 */
package com.smacian.backend.entity

import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_devices",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_devices_token", columnNames = ["fcm_token"])
    ],
    indexes = [
        Index(name = "idx_user_devices_user_id", columnList = "user_id")
    ]
)
class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var user: User? = null

    @field:Column(name = "fcm_token", nullable = false, length = 255)
    var fcmToken: String = ""

    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()

    @PrePersist
    fun onCreate() {
        updatedAt = LocalDateTime.now()
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
