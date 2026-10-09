/*
 * NotificationType - Why a notification exists.
 *
 * LIKE        = someone liked my post (referenceId = postId)
 * COMMENT     = someone commented on my post (referenceId = postId)
 * REPLY       = someone replied to my comment (referenceId = postId)
 * SHARE       = someone reshared my post (referenceId = the reshare postId)
 * BLOOD_MATCH = new blood request, fanned out to everyone (referenceId = bloodRequestId)
 * POST_CREATED = new feed post, fanned out to everyone (referenceId = postId)
 * BROADCAST   = admin announcement (reserved - no sender wired yet)
 */
package com.smacian.backend.entity.enums

enum class NotificationType {
    LIKE,
    COMMENT,
    REPLY,
    SHARE,
    BLOOD_MATCH,
    POST_CREATED,
    BROADCAST
}
