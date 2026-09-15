/*
 * CloudinaryConfig - Creates the Cloudinary bean used to upload images.
 *
 * Cloudinary = free cloud image storage (free plan = 25GB).
 *
 * Values come from application.yml:
 *   cloudinary.cloud-name / api-key / api-secret
 *
 * Get them FREE at https://cloudinary.com/ (check your Dashboard).
 * Put real values in your environment variables, never hardcode them.
 */
package com.smacian.backend.config

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CloudinaryConfig(
    // @Value injects the settings from application.yml.
    @Value("\${cloudinary.cloud-name}") private val cloudName: String,
    @Value("\${cloudinary.api-key}") private val apiKey: String,
    @Value("\${cloudinary.api-secret}") private val apiSecret: String
) {

    /*
     * The Cloudinary client bean. Spring injects it anywhere we need it
     * (e.g. into CloudinaryService).
     */
    @Bean
    fun cloudinary(): Cloudinary =
        Cloudinary(
            ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true   // images served over https://
            )
        )
}