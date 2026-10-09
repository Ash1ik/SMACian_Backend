/*
 * PushService - Sends FCM push notifications.
 *
 * Current push scope (product calls): new posts, likes, comments, replies,
 * shares, blood requests. Inbox rows cover like/comment/reply/share/blood;
 * new-post pushes are push-ONLY (the feed itself is the inbox for posts).
 *
 * FirebaseMessaging is injected via ObjectProvider: when no key is
 * configured the bean doesn't exist and every send is a logged no-op
 * (same disabled-without-key pattern as Brevo email).
 *
 * Fan-out: tokens for all target users are fetched in ONE query, sent in
 * chunks of 500 (FCM multicast limit), and dead tokens (UNREGISTERED /
 * INVALID_ARGUMENT) are pruned so the table never rots.
 *
 * CALLERS MUST invoke from after-commit (external HTTPS, never inside the
 * DB transaction) - see NewsfeedService/BloodRequestService.
 */
package com.smacian.backend.service

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import com.smacian.backend.entity.enums.NotificationType
import com.smacian.backend.repository.UserDeviceRepository
import com.smacian.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PushService(
    private val messagingProvider: ObjectProvider<FirebaseMessaging>,
    private val deviceRepository: UserDeviceRepository,
    private val userRepository: UserRepository
) {

    private val log = LoggerFactory.getLogger(PushService::class.java)

    // Tokens FCM declares permanently dead (safe to delete on sight).
    private val PRUNE_CODES = setOf(
        MessagingErrorCode.UNREGISTERED,
        MessagingErrorCode.INVALID_ARGUMENT
    )

    /*
     * Fan-out entry point: same recipient set as the inbox fan-out (every
     * active user except one). Own transaction - safe to call after-commit.
     */
    @Transactional
    fun pushBloodRequestToAllExcept(
        excludedUserId: Long,
        title: String,
        body: String,
        bloodRequestId: Long
    ) {
        pushToUsers(
            userRepository.findActiveUserIdsExcept(excludedUserId),
            title, body, NotificationType.BLOOD_MATCH, bloodRequestId
        )
    }

    /*
     * Single-recipient push (like/comment/reply/share). Own transaction.
     */
    @Transactional
    fun pushToUser(
        userId: Long,
        title: String,
        body: String,
        type: NotificationType,
        referenceId: Long
    ) {
        pushToUsers(listOf(userId), title, body, type, referenceId)
    }

    /*
     * New-post fan-out: everyone except the author. Own transaction.
     */
    @Transactional
    fun pushNewPostToAllExcept(
        excludedUserId: Long,
        title: String,
        body: String,
        postId: Long
    ) {
        pushToUsers(
            userRepository.findActiveUserIdsExcept(excludedUserId),
            title, body, NotificationType.POST_CREATED, postId
        )
    }

    /*
     * Core sender: tokens in ONE query, 500/chunk multicast, dead-token
     * pruning. data carries type+referenceId so the app can deep-link.
     */
    @Transactional
    fun pushToUsers(
        userIds: List<Long>,
        title: String,
        body: String,
        type: NotificationType,
        referenceId: Long
    ) {
        val messaging = messagingProvider.getIfAvailable()
        if (messaging == null) {
            log.warn("FCM not configured (FIREBASE_KEY_PATH unset) - skipping {} push for {}", type, referenceId)
            return
        }
        if (userIds.isEmpty()) return

        val tokens = try {
            deviceRepository.findFcmTokensByUserIds(userIds)
        } catch (e: Exception) {
            log.error("Push aborted: token lookup failed for {} {}", type, referenceId, e)
            return
        }
        if (tokens.isEmpty()) {
            log.info("Push skipped: no registered devices for {} {}", type, referenceId)
            return
        }

        val data = mapOf("type" to type.name, "referenceId" to referenceId.toString())
        var sent = 0
        tokens.chunked(500).forEach { chunk ->
            val message = MulticastMessage.builder()
                .addAllTokens(chunk)
                .setNotification(
                    Notification.builder().setTitle(title.take(200)).setBody(body.take(500)).build()
                )
                .putAllData(data)
                .build()
            try {
                val response = messaging.sendEachForMulticast(message)
                sent += response.successCount
                pruneDeadTokens(chunk, response)
            } catch (e: FirebaseMessagingException) {
                log.error("FCM multicast failed for {} {}", type, referenceId, e)
            }
        }
        log.info("Push done for {} {}: {}/{} delivered", type, referenceId, sent, tokens.size)
    }

    // Deletes tokens FCM reports as permanently dead (stale app installs).
    private fun pruneDeadTokens(
        chunk: List<String>,
        response: com.google.firebase.messaging.BatchResponse
    ) {
        val dead = response.responses.mapIndexedNotNull { index, sendResponse ->
            val code = sendResponse.exception?.messagingErrorCode
            if (!sendResponse.isSuccessful && code != null && code in PRUNE_CODES) chunk[index] else null
        }
        if (dead.isNotEmpty()) {
            deviceRepository.deleteByFcmTokenIn(dead)
            log.info("Pruned {} dead FCM tokens", dead.size)
        }
    }
}
