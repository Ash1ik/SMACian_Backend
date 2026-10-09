/*
 * FirebaseConfig - Initializes the Firebase Admin SDK for FCM push.
 *
 * Credentials come from a service-account JSON file whose path is given by
 * FIREBASE_KEY_PATH (local: .runtime/firebase-key.json, git-ignored).
 * When unset, NO bean is registered and PushService silently skips sending
 * (same disabled-without-key pattern as Brevo email).
 */
package com.smacian.backend.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream

@Configuration
class FirebaseConfig(
    @Value("\${FIREBASE_KEY_PATH:}") private val keyPath: String
) {

    private val log = LoggerFactory.getLogger(FirebaseConfig::class.java)

    // Registered ONLY when a key path is configured (SpEL compares the
    // resolved value). NOTE: use FORWARD slashes in the path even on
    // Windows (D:/...) - backslashes break SpEL string parsing.
    // PushService injects via ObjectProvider and no-ops when absent.
    @Bean
    @ConditionalOnExpression("'\${FIREBASE_KEY_PATH:}' != ''")
    fun firebaseMessaging(): FirebaseMessaging {
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(FileInputStream(keyPath)))
            .build()
        val app = try {
            FirebaseApp.initializeApp(options)
        } catch (e: IllegalStateException) {
            // Already initialized (e.g. test restarts in one JVM).
            FirebaseApp.getInstance()
        }
        log.info("FirebaseApp initialized (push ENABLED)")
        return FirebaseMessaging.getInstance(app)
    }
}
