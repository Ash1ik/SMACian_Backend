/*
 * NotificationService - In-app notification center (store + fan-out + read state).
 *
 * All writes are plain local DB inserts in the CALLER's transaction (no
 * external I/O like FCM yet, so nothing can hold the pool). Triggers live
 * in NewsfeedService/BloodRequestService; this class owns rows + queries.
 *
 * Rules: self-actions never notify (callers check actor != recipient);
 * reads/deletes are owner-only (recipient must equal the JWT user).
 */
package com.smacian.backend.service

import com.smacian.backend.dto.response.NotificationResponse
import com.smacian.backend.dto.response.PagedResponse
import com.smacian.backend.dto.response.UnreadCountResponse
import com.smacian.backend.entity.Notification
import com.smacian.backend.entity.enums.NotificationType
import com.smacian.backend.exception.ForbiddenException
import com.smacian.backend.exception.ResourceNotFoundException
import com.smacian.backend.repository.NotificationRepository
import com.smacian.backend.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository
) {

    // ====================================================================
    // SEND (called by feature services, same transaction)
    // ====================================================================

    // One notification to one user. No-op when recipient doesn't exist
    // (defensive - callers normally pass known IDs).
    @Transactional
    fun notify(
        recipientId: Long,
        type: NotificationType,
        title: String,
        body: String,
        referenceId: Long?
    ) {
        val recipient = userRepository.findById(recipientId).orElse(null) ?: return
        val notification = Notification().apply {
            this.recipient = recipient
            this.type = type
            this.title = title.take(200)
            this.body = body.take(500)
            this.referenceId = referenceId
            this.read = false
        }
        notificationRepository.save(notification)
    }

    // One notification to EVERY active user except one (blood-request fan-out).
    // IDs first (no BYTEA), then one saveAll - ~500 rows is trivial.
    @Transactional
    fun notifyAllExcept(
        excludedUserId: Long,
        type: NotificationType,
        title: String,
        body: String,
        referenceId: Long?
    ): Int {
        val ids = userRepository.findActiveUserIdsExcept(excludedUserId)
        if (ids.isEmpty()) return 0
        val rows = ids.map { id ->
            Notification().apply {
                this.recipient = userRepository.getReferenceById(id)
                this.type = type
                this.title = title.take(200)
                this.body = body.take(500)
                this.referenceId = referenceId
                this.read = false
            }
        }
        notificationRepository.saveAll(rows)
        return rows.size
    }

    // ====================================================================
    // READ (mine only)
    // ====================================================================
    @Transactional(readOnly = true)
    fun listMine(userId: Long, page: Int, size: Int): PagedResponse<NotificationResponse> {

        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 50)

        val pageable = PageRequest.of(safePage, safeSize)
        val result = notificationRepository.findByRecipient(userId, pageable)

        return PagedResponse(
            content = result.content.map { NotificationResponse.fromEntity(it) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun unreadCount(userId: Long): UnreadCountResponse =
        UnreadCountResponse(notificationRepository.countByRecipientIdAndReadFalse(userId))

    @Transactional
    fun markRead(userId: Long, notificationId: Long): NotificationResponse {

        val notification = getOwned(userId, notificationId)
        notification.read = true

        return NotificationResponse.fromEntity(notificationRepository.save(notification))
    }

    @Transactional
    fun markAllRead(userId: Long): Int =
        notificationRepository.markAllRead(userId)

    @Transactional
    fun deleteMine(userId: Long, notificationId: Long) {
        notificationRepository.delete(getOwned(userId, notificationId))
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    // 404 for unknown id, 403 for someone else's row (no existence oracle).
    private fun getOwned(userId: Long, notificationId: Long): Notification {

        val notification = notificationRepository.findById(notificationId)
            .orElseThrow { ResourceNotFoundException("Notification not found") }

        if (notification.recipient!!.id != userId) {
            throw ForbiddenException("You can only manage your own notifications")
        }

        return notification
    }
}
