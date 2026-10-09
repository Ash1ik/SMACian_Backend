/*
 * NotificationResponse - One notification as the mobile app receives it.
 *
 * JSON: { "id": 21, "type": "LIKE", "title": "Ahmed liked your post",
 *         "body": "Ahmed Khan liked your post.", "referenceId": 7,
 *         "read": false, "createdAt": "2026-10-08T14:30:00" }
 *
 * The app navigates by type+referenceId (LIKE/COMMENT/REPLY/SHARE ->
 * open post; BLOOD_MATCH -> open blood request).
 */
package com.smacian.backend.dto.response

import com.smacian.backend.entity.Notification
import java.time.LocalDateTime

data class NotificationResponse(
    val id: Long,
    val type: String,
    val title: String,
    val body: String,
    val referenceId: Long?,
    val read: Boolean,
    val createdAt: LocalDateTime
) {
    companion object {
        fun fromEntity(entity: Notification): NotificationResponse =
            NotificationResponse(
                id = entity.id!!,
                type = entity.type.name,
                title = entity.title,
                body = entity.body,
                referenceId = entity.referenceId,
                read = entity.read,
                createdAt = entity.createdAt
            )
    }
}
