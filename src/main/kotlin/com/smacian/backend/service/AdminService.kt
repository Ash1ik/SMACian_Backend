/*
 * AdminService - Platform management operations (ADMIN role only;
 * enforced by @PreAuthorize on the controller).
 *
 *   - deletePost / deleteComment: remove ANY user's content (DB cascades
 *     clean up likes/images/replies/reshares exactly like author deletes).
 *   - setActive: ban (false) / unban (true). The JWT filter enforces
 *     isActive per request, so bans bite immediately. Self-change blocked
 *     (an admin must never lock themselves out).
 *   - setRole: promote/demote. Self-change blocked (prevents ending up
 *     with zero admins by accident).
 *   - broadcast: inbox row for every active user + optional instant push.
 */
package com.smacian.backend.service

import com.smacian.backend.dto.response.MessageResponse
import com.smacian.backend.entity.Notification
import com.smacian.backend.entity.enums.NotificationType
import com.smacian.backend.entity.enums.Role
import com.smacian.backend.exception.BadRequestException
import com.smacian.backend.exception.ForbiddenException
import com.smacian.backend.exception.ResourceNotFoundException
import com.smacian.backend.repository.CommentRepository
import com.smacian.backend.repository.NotificationRepository
import com.smacian.backend.repository.PostRepository
import com.smacian.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class AdminService(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val notificationRepository: NotificationRepository,
    private val pushService: PushService
) {

    // ====================================================================
    // MODERATION
    // ====================================================================

    @Transactional
    fun deletePost(postId: Long) {
        val post = postRepository.findById(postId)
            .orElseThrow { ResourceNotFoundException("Post not found") }
        postRepository.delete(post)
    }

    @Transactional
    fun deleteComment(commentId: Long) {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { ResourceNotFoundException("Comment not found") }
        commentRepository.delete(comment)
    }

    // ====================================================================
    // USER MANAGEMENT
    // ====================================================================

    @Transactional
    fun setActive(adminId: Long, userId: Long, active: Boolean): MessageResponse {

        if (adminId == userId) {
            throw ForbiddenException("You cannot change your own active status")
        }

        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        user.isActive = active
        userRepository.save(user)

        val state = if (active) "activated" else "deactivated"
        return MessageResponse(true, "User ${user.fullName} $state")
    }

    @Transactional
    fun setRole(adminId: Long, userId: Long, role: String?): MessageResponse {

        if (adminId == userId) {
            throw ForbiddenException("You cannot change your own role")
        }

        val target = Role.entries.firstOrNull { it.name.equals(role?.trim(), ignoreCase = true) }
            ?: throw BadRequestException("Role must be ADMIN or USER")

        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        user.role = target
        userRepository.save(user)

        return MessageResponse(true, "User ${user.fullName} is now ${target.name}")
    }

    // ====================================================================
    // BROADCAST (inbox rows now + optional push after commit)
    // ====================================================================

    @Transactional
    fun broadcast(title: String?, body: String?, push: Boolean): MessageResponse {

        val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw BadRequestException("Broadcast title is required")
        if (cleanTitle.length > 200) {
            throw BadRequestException("Broadcast title must be 200 characters or less")
        }
        val cleanBody = body?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw BadRequestException("Broadcast body is required")
        if (cleanBody.length > 500) {
            throw BadRequestException("Broadcast body must be 500 characters or less")
        }

        val ids = userRepository.findActiveUserIdsExcept(-1L)
        val rows = ids.map { id ->
            Notification().apply {
                this.recipient = userRepository.getReferenceById(id)
                this.type = NotificationType.BROADCAST
                this.title = cleanTitle
                this.body = cleanBody
                this.referenceId = null
                this.read = false
                this.actor = null
            }
        }
        notificationRepository.saveAll(rows)

        if (push && ids.isNotEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        try {
                            pushService.pushToUsers(ids, cleanTitle, cleanBody, NotificationType.BROADCAST, 0L)
                        } catch (e: Exception) {
                            // Push failures must never fail the broadcast itself.
                        }
                    }
                }
            )
        }

        return MessageResponse(true, "Broadcast sent to ${ids.size} users")
    }
}
