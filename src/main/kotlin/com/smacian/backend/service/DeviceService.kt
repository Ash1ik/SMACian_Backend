/*
 * DeviceService - Registers/unregisters Android FCM push tokens.
 *
 * One token = one device. Re-login on a shared device REASSIGNS the row
 * to the new user (tokens are globally unique). Logout deletes the row
 * so a signed-out device stops receiving that account's pushes.
 */
package com.smacian.backend.service

import com.smacian.backend.entity.UserDevice
import com.smacian.backend.exception.BadRequestException
import com.smacian.backend.exception.ResourceNotFoundException
import com.smacian.backend.repository.UserDeviceRepository
import com.smacian.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeviceService(
    private val deviceRepository: UserDeviceRepository,
    private val userRepository: UserRepository
) {

    // Idempotent: same token twice = harmless touch, not a duplicate
    // (unique constraint uk_user_devices_token backs this up on races).
    @Transactional
    fun registerToken(userId: Long, token: String?) {

        val cleanToken = token?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw BadRequestException("FCM token is required")

        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        val existing = deviceRepository.findByFcmToken(cleanToken).orElse(null)
        if (existing != null) {
            existing.user = user
            deviceRepository.save(existing)
            return
        }

        val device = UserDevice().apply {
            this.user = user
            this.fcmToken = cleanToken
        }
        deviceRepository.save(device)
    }

    // Idempotent: unknown token = 0 rows, still success.
    @Transactional
    fun unregisterToken(userId: Long, token: String?) {

        val cleanToken = token?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw BadRequestException("FCM token is required")

        deviceRepository.deleteByUserIdAndFcmToken(userId, cleanToken)
    }
}
