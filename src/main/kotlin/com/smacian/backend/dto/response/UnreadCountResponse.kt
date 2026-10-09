/*
 * UnreadCountResponse - Badge number for the app's notification icon.
 *
 * JSON: { "unreadCount": 3 }
 */
package com.smacian.backend.dto.response

data class UnreadCountResponse(
    val unreadCount: Long
)
