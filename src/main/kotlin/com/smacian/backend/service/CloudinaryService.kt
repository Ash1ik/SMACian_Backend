/*
 * CloudinaryService - Handles all image uploading to Cloudinary.
 *
 * Three jobs:
 *   1. Validate the file (image type, size ≤ 5MB)
 *   2. Upload it to Cloudinary cloud storage
 *   3. Return the public URL to store in our database
 *
 * Cloudinary returns URLs like:
 *   https://res.cloudinary.com/<cloud>/image/upload/v1234567/avatars/user1_j3k9s2.jpg
 *
 * The app just loads the URL to display the image - Cloudinary stores it.
 * If you later switch providers (e.g. AWS S3), only THIS file changes.
 */
package com.smacian.backend.service

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import com.smacian.backend.exception.BadRequestException
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class CloudinaryService(
    // Cloudinary bean is injected automatically from CloudinaryConfig.
    private val cloudinary: Cloudinary
) {

    // Only these image types are allowed.
    private val allowedTypes = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

    // Maximum file size: 5MB (matches application.yml multipart setting).
    private val MAX_FILE_SIZE = 5L * 1024 * 1024 // 5MB in bytes

    /*
     * Uploads a profile/cover photo for a user.
     *
     * @param file       the image uploaded by the app
     * @param userId     the owner of the photo
     * @param folderName "avatars" or "covers" in Cloudinary
     *
     * @return the public Cloudinary URL of the uploaded image
     */
    fun uploadImage(file: MultipartFile, userId: Long, folderName: String): String {

        // ===== Step 1: Validate BEFORE uploading =====
        validateFile(file)

        return try {
            // ===== Step 2: Upload to Cloudinary =====
            val uploadResult = cloudinary.uploader().upload(
                file.bytes, // the image bytes
                ObjectUtils.asMap(
                    "folder", folderName,   // store under /avatars or /covers
                    "public_id", "${userId}_${System.currentTimeMillis()}", // unique name
                    "overwrite", true,
                    // Limit the image to 800px to save free-tier space.
                    "transformation", ObjectUtils.asMap("width", 800, "crop", "limit")
                )
            )

            // Cloudinary returns a map; "secure_url" is the https URL.
            uploadResult["secure_url"] as String

        } catch (e: java.io.IOException) {
            // File couldn't be read from the request.
            throw BadRequestException("Could not read the uploaded file. Please try again")
        } catch (e: Exception) {
            // Cloudinary server rejected the upload (wrong keys, network...).
            throw BadRequestException("Image upload failed. Please check your Cloudinary settings and try again")
        }
    }

    /*
     * Validates the uploaded file:
     *   1. Not empty
     *   2. Is an allowed image type
     *   3. Not larger than 5MB
     */
    private fun validateFile(file: MultipartFile) {

        // 1. Not empty
        if (file.isEmpty) {
            throw BadRequestException("No file was uploaded. Please select an image")
        }

        // 2. Allowed image type
        val contentType = file.contentType
        if (contentType == null || contentType !in allowedTypes) {
            throw BadRequestException("Only JPG, PNG, WEBP or GIF images are allowed")
        }

        // 3. Size limit
        if (file.size > MAX_FILE_SIZE) {
            throw BadRequestException("File size must be less than 5MB")
        }
    }

    /*
     * Generates a default avatar (initials) when the user skips uploading
     * a photo during registration.
     *
     * Uses the free service https://ui-avatars.com which generates
     * initials avatars on the fly - no storage needed.
     *
     * Example for "Ahmed Khan":
     *   https://ui-avatars.com/api/?name=Ahmed+Khan&background=random&size=200
     */
    fun getDefaultAvatarUrl(firstName: String, lastName: String): String {
        val name = "$firstName+$lastName"
        return "https://ui-avatars.com/api/?name=$name&background=random&size=200"
    }
}