/*
 * PageController - Serves the Terms of Service and Privacy Policy pages.
 *
 * The mobile app (AppNavHost.kt) calls:
 *   GET /api/terms    → returns our Terms of Service HTML page
 *   GET /api/privacy  → returns our Privacy Policy HTML page
 *
 * These endpoints are PUBLIC.
 *
 * The HTML files live in src/main/resources/static/ and get packaged
 * inside the built JAR. We read them from the classpath and return with
 * the "text/html" content type so the browser renders them.
 */
package com.smacian.backend.controller

import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PageController {

    // GET /api/terms - returns the Terms of Service page.
    @GetMapping(value = ["/api/terms"], produces = [MediaType.TEXT_HTML_VALUE])
    fun termsOfService(): ResponseEntity<String> = renderPage("static/terms.html")

    // GET /api/privacy - returns the Privacy Policy page.
    @GetMapping(value = ["/api/privacy"], produces = [MediaType.TEXT_HTML_VALUE])
    fun privacyPolicy(): ResponseEntity<String> = renderPage("static/privacy.html")

    /*
     * Reads an HTML file from the classpath and returns it as a response.
     * ClassPathResource reads files packaged inside the JAR.
     */
    private fun renderPage(path: String): ResponseEntity<String> {
        val resource = ClassPathResource(path)
        val html = resource.inputStream.readBytes().toString(Charsets.UTF_8)
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html)
    }
}