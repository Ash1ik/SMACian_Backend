/*
 * AdminController - Platform management endpoints (ADMIN role ONLY).
 *
 *   DELETE /api/admin/posts/{id}        -> delete any post (+cascades)
 *   DELETE /api/admin/comments/{id}     -> delete any comment (+subtree)
 *   PATCH  /api/admin/users/{id}/active -> ban/unban  {"active": false}
 *   PATCH  /api/admin/users/{id}/role   -> promote/demote {"role": "ADMIN"}
 *   POST   /api/admin/broadcast         -> announce to everyone
 *
 * Every method requires ROLE_ADMIN (JWT + @PreAuthorize). Anonymous or
 * non-admin callers get 401/403 from Spring before touching the service.
 * Admins can never change their OWN active flag or role (no self-lockout).
 */
package com.smacian.backend.controller

import com.smacian.backend.dto.request.ActiveUpdate
import com.smacian.backend.dto.request.BroadcastRequest
import com.smacian.backend.dto.request.RoleUpdate
import com.smacian.backend.dto.response.MessageResponse
import com.smacian.backend.security.CurrentUser
import com.smacian.backend.service.AdminService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController(
    private val adminService: AdminService
) {

    @DeleteMapping("/posts/{id}")
    fun deletePost(@PathVariable id: Long): ResponseEntity<Void> {
        adminService.deletePost(id)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/comments/{commentId}")
    fun deleteComment(@PathVariable commentId: Long): ResponseEntity<Void> {
        adminService.deleteComment(commentId)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/users/{id}/active")
    fun setActive(
        @PathVariable id: Long,
        @RequestBody request: ActiveUpdate
    ): ResponseEntity<MessageResponse> {
        val response = adminService.setActive(CurrentUser.getUserId(), id, request.active)
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/users/{id}/role")
    fun setRole(
        @PathVariable id: Long,
        @RequestBody request: RoleUpdate
    ): ResponseEntity<MessageResponse> {
        val response = adminService.setRole(CurrentUser.getUserId(), id, request.role)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/broadcast")
    fun broadcast(@RequestBody request: BroadcastRequest): ResponseEntity<MessageResponse> {
        val response = adminService.broadcast(request.title, request.body, request.push)
        return ResponseEntity.ok(response)
    }
}
