/*
 * UserDeviceRepository - Data Access Layer for the UserDevice entity.
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.UserDevice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserDeviceRepository : JpaRepository<UserDevice, Long> {

    fun findByFcmToken(fcmToken: String): Optional<UserDevice>

    /*
     * All push tokens owned by the given users - ONE query (push fan-out).
     */
    @Query("SELECT d.fcmToken FROM UserDevice d WHERE d.user.id IN :userIds")
    fun findFcmTokensByUserIds(@Param("userIds") userIds: List<Long>): List<String>

    fun deleteByUserIdAndFcmToken(userId: Long, fcmToken: String): Long

    fun deleteByFcmTokenIn(fcmTokens: List<String>): Long
}
