/*
 * ScheduledTasks - Runs automated background jobs.
 *
 * @EnableScheduling (in SmaCianApplication) activates the @Scheduled
 * annotations. This is production practice: expired OTP rows are cleaned
 * up periodically so the database stays fast and lean.
 */
package com.smacian.backend.config

import com.smacian.backend.service.BloodRequestService
import com.smacian.backend.service.OtpService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ScheduledTasks(
    private val otpService: OtpService,
    private val bloodRequestService: BloodRequestService
) {

    private val log = LoggerFactory.getLogger(ScheduledTasks::class.java)

    /*
     * Deletes all expired OTP records daily at 3:00 AM.
     *
     * Cron: second minute hour day-of-month month day-of-week
     *       0     0     3    *            *     ?
     */
    @Scheduled(cron = "0 0 3 * * ?")
    fun cleanupExpiredOtps() {
        log.info("Running scheduled cleanup of expired OTP codes")
        otpService.deleteExpiredOtps()
    }

    /*
     * Expires overdue blood requests daily at 4:00 AM (OPEN rows whose
     * needed_by passed become EXPIRED). Chose a scheduled job over a
     * lazy on-read check so listings never show stale OPEN rows between
     * reads, consistent with the OTP cleanup pattern above.
     *
     * Cron: 0 0 4 * * ?
     */
    @Scheduled(cron = "0 0 4 * * ?")
    fun expireOverdueBloodRequests() {
        log.info("Running scheduled expiry of overdue blood requests")
        val expired = bloodRequestService.expireOverdue()
        log.info("Expired {} overdue blood requests", expired)
    }
}