/*
 * AdminSeeder - Promotes the first platform admin on every boot.
 *
 * Reads ADMIN_EMAIL (env / local-run.ps1). If a user with that email exists
 * and isn't ADMIN yet, promotes them. Idempotent and self-healing: on a
 * fresh database it no-ops until the owner registers, then promotes on the
 * next restart. Empty ADMIN_EMAIL = disabled.
 */
package com.smacian.backend.config

import com.smacian.backend.entity.enums.Role
import com.smacian.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AdminSeeder(
    @Value("\${ADMIN_EMAIL:}") private val adminEmail: String,
    private val userRepository: UserRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(AdminSeeder::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val email = adminEmail.trim().lowercase()
        if (email.isEmpty()) {
            return
        }
        val user = userRepository.findByEmail(email).orElse(null)
        if (user == null) {
            log.warn("ADMIN_EMAIL {} has no account yet - register first, then restart", email)
            return
        }
        if (user.role != Role.ADMIN) {
            user.role = Role.ADMIN
            userRepository.save(user)
            log.info("Promoted {} to ADMIN", email)
        }
    }
}
