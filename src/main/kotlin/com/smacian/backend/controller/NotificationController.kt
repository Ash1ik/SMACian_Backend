/*
 * NotificationController - In-app notification center endpoints.
 *
 *   GET    /api/notifications?page=0&size=20 -> my notifications, newest first
 *   GET    /api/notifications/unread-count   -> {"unreadCount": N} (badge)
 *   PATCH  /api/notifications/{id}/read      -> mark one read
 *   PATCH  /api/notifications/read-all       -> mark all read (returns count)
 *   DELETE /api/notifications/{id}           -> delete one of mine
 *
 * ALL need login (JWT). Recipient is NEVER taken from the request - it is
 * always the JWT user (prevents reading/deleting others' notifications).
 */
package com.smacian.backend.controller

import com.smacian.backend.dto.response.NotificationResponse
import com.smacian.backend.dto.response.PagedResponse
import com.smacian.backend.dto.response.UnreadCountResponse
import com.smacian.backend.security.CurrentUser
import com.smacian.backend.service.NotificationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {

    // Declared BEFORE /{id}/read so "read-all" matches this literal route.
    @PatchMapping("/read-all")
    fun markAllRead(): ResponseEntity<Map<String, Int>> {
        val updated = notificationService.markAllRead(CurrentUser.getUserId())
        return ResponseEntity.ok(mapOf("markedRead" to updated))
    }

    @GetMapping
    fun listMine(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PagedResponse<NotificationResponse> =
        notificationService.listMine(CurrentUser.getUserId(), page, size)

    @GetMapping("/unread-count")
    fun unreadCount(): UnreadCountResponse =
        notificationService.unreadCount(CurrentUser.getUserId())

    @PatchMapping("/{id}/read")
    fun markRead(@PathVariable id: Long): ResponseEntity<NotificationResponse> {
        val notification = notificationService.markRead(CurrentUser.getUserId(), id)
        return ResponseEntity.ok(notification)
    }

    @DeleteMapping("/{id}")
    fun deleteMine(@PathVariable id: Long): ResponseEntity<Void> {
        notificationService.deleteMine(CurrentUser.getUserId(), id)
        return ResponseEntity.noContent().build()
    }
}
