/*
 * NotificationResponse - One notification as the mobile app receives it.
 *
 * JSON: { "id": 21, "type": "LIKE", "title": "Ahmed liked your post",
 *         "body": "Ahmed Khan liked your post.", "referenceId": 7,
 *         "actorId": 3, "actorName": "Ahmed Khan",
 *         "actorPhotoUrl": "http://.../api/user/profile/photo/3",
 *         "read": false, "createdAt": "2026-10-08T14:30:00" }
 *
 * The app navigates by type+referenceId (LIKE/COMMENT/REPLY/SHARE ->
 * open post; BLOOD_MATCH -> open blood request) and renders the actor's
 * avatar+name from actorPhotoUrl/actorName. actorName falls back to
 * "Someone" for legacy rows without an actor; actorPhotoUrl is null then.
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
    val actorId: Long?,
    val actorName: String,
    val actorPhotoUrl: String?,
    val read: Boolean,
    val createdAt: LocalDateTime
) {
    companion object {
        fun fromEntity(entity: Notification): NotificationResponse {
            val actor = entity.actor
            return NotificationResponse(
                id = entity.id!!,
                type = entity.type.name,
                title = entity.title,
                body = entity.body,
                referenceId = entity.referenceId,
                actorId = actor?.id,
                actorName = actor?.fullName ?: "Someone",
                actorPhotoUrl = actor?.profilePhotoUrl,
                read = entity.read,
                createdAt = entity.createdAt
            )
        }
    }
}
