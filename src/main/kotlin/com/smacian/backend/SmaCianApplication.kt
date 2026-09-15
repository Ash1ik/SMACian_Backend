/*
 * SmaCianApplication - The main entry point of our Spring Boot app.
 *
 * When this runs, Spring Boot:
 *   1. Starts the embedded Tomcat web server
 *   2. Connects to the database
 *   3. Scans for components in com.smacian.backend and below
 *
 * That's why this file must be in the ROOT package - so Spring finds
 * every controller, service and repository in the project.
 *
 * Kotlin notes:
 *   - No need for the old Java `public static void main` boilerplate -
 *     Kotlin uses a top-level main function inside a class with
 *     @JvmStatic or the idiomatic companion approach shown below.
 *   - @EnableScheduling activates our scheduled background jobs
 *     (e.g. daily cleanup of expired OTP codes).
 */
package com.smacian.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.SpringApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SmaCianApplication

// Top-level main function - the JVM entry point.
// runApplication() is Kotlin's idiomatic alternative to SpringApplication.run().
fun main(args: Array<String>) {
    SpringApplication.run(SmaCianApplication::class.java, *args)
}