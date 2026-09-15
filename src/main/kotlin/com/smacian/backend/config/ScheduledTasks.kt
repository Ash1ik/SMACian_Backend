/*
 * ScheduledTasks - Runs automated background jobs.
 *
 * @EnableScheduling (in SmaCianApplication) activates the @Scheduled
 * annotations. This is production practice: expired OTP rows are cleaned
 * up periodically so the database stays fast and lean.
 */
package com.smacian.backend.config

import com.smacian.backend.service.OtpService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ScheduledTasks(
    private val otpService: OtpService
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
}