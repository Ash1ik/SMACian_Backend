/*
 * NotificationRepository - Data Access Layer for the Notification entity.
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface NotificationRepository : JpaRepository<Notification, Long> {

    /*
     * One user's notifications, newest first. Pass a Pageable WITHOUT sort
     * (ordering is fixed in the query). Recipient is JOIN FETCHed; actor is
     * LEFT JOIN FETCHed (nullable - inner join would drop legacy rows).
     */
    @Query(
        value = "SELECT n FROM Notification n JOIN FETCH n.recipient LEFT JOIN FETCH n.actor WHERE n.recipient.id = :userId ORDER BY n.createdAt DESC",
        countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.recipient.id = :userId"
    )
    fun findByRecipient(@Param("userId") userId: Long, pageable: Pageable): Page<Notification>

    /*
     * Badge count. Covered by the (recipient_id, created_at) index prefix.
     */
    fun countByRecipientIdAndReadFalse(recipientId: Long): Long

    /*
     * Mark-all-read in ONE statement. Returns rows updated.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipient.id = :userId AND n.read = false")
    fun markAllRead(@Param("userId") userId: Long): Int
}
