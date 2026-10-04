/*
 * ImageProcessing - Single place for upload image validation + optimization.
 *
 * Plain Kotlin object (no Spring service, no new infrastructure):
 *   1. Sniff the REAL type from magic bytes (never trust the filename or
 *      the client-provided Content-Type alone - both are attacker-controlled).
 *   2. Require the sniffed type to match the claimed Content-Type.
 *   3. Decode with ImageIO (also rejects truncated/corrupt files -
 *      ImageIO.read returns null or throws on garbage).
 *   4. Downscale to fit within 1600x1600, keeping aspect ratio. Smaller
 *      images pass through untouched (never upscale).
 *   5. Re-encode (JPEG quality ~0.82, PNG default) to strip metadata bloat.
 *
 * Format policy (honest, no extra dependencies):
 *   - JPEG/PNG/GIF are decoded by the JDK's ImageIO: full pipeline above.
 *     GIFs pass through UNCHANGED (resizing would kill animation); only
 *     their dimensions are recorded.
 *   - WebP has no JDK decoder: magic-byte validated, stored as-is, and
 *     width/height stay null (recorded when a decoder is added).
 *
 * Memory: files are processed SEQUENTIALLY by the caller (never parallel),
 * one decoded bitmap at a time (~10MB at 1600px). Safe inside the 384m heap.
 */
package com.smacian.backend.util

import com.smacian.backend.exception.BadRequestException
import org.springframework.web.multipart.MultipartFile
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

object ImageProcessing {

    const val MAX_WIDTH = 1600
    const val MAX_HEIGHT = 1600

    // Output JPEG quality: 0.82 keeps photos visually clean while roughly
    // halving the file size vs. typical phone-camera originals.
    private const val JPEG_QUALITY = 0.82f

    // Decoded formats we fully handle (see policy above).
    private val DECODED_TYPES = setOf("image/jpeg", "image/png", "image/gif")

    /*
     * Result of processing one upload: bytes to STORE (not the original),
     * the effective content type, and dimensions (null when unknown).
     */
    data class ProcessedImage(
        val bytes: ByteArray,
        val contentType: String,
        val width: Int?,
        val height: Int?
    )

    /*
     * Full pipeline for one uploaded file. Throws BadRequestException
     * (→ HTTP 400) on anything suspicious or unreadable.
     */
    fun process(file: MultipartFile): ProcessedImage {

        if (file.isEmpty) {
            throw BadRequestException("Empty file uploaded. Please select an image")
        }

        val raw = file.bytes
        val sniffed = sniffType(raw)
            ?: throw BadRequestException("File is not a supported image (JPG, PNG, WEBP or GIF)")

        // Claimed type must match the actual bytes (blocks polyglot uploads:
        // e.g. an .exe renamed to .jpg with Content-Type image/jpeg).
        val claimed = (file.contentType ?: "").lowercase().substringBefore(";").trim()
        if (claimed != sniffed) {
            throw BadRequestException("File content does not match its type. Please upload a real image")
        }

        // WebP: validated above, stored as-is (no JDK decoder for dimensions).
        if (sniffed == "image/webp") {
            return ProcessedImage(bytes = raw, contentType = sniffed, width = null, height = null)
        }

        // Decode (null return / exception = corrupt or truncated file).
        val decoded: BufferedImage = try {
            ImageIO.read(ByteArrayInputStream(raw))
                ?: throw BadRequestException("Image is corrupted or unreadable")
        } catch (e: BadRequestException) {
            throw e
        } catch (e: Exception) {
            throw BadRequestException("Image is corrupted or unreadable")
        }

        val width = decoded.width
        val height = decoded.height
        if (width <= 0 || height <= 0) {
            throw BadRequestException("Image is corrupted or unreadable")
        }

        // GIF: keep original bytes (animation), record dimensions only.
        if (sniffed == "image/gif") {
            return ProcessedImage(bytes = raw, contentType = sniffed, width = width, height = height)
        }

        // Downscale to fit 1600x1600 (aspect preserved, never upscale)...
        // PNG keeps alpha; JPEG is flattened (JPEG has no alpha channel and
        // the writer rejects ARGB input).
        val scaled = downscaleIfNeeded(decoded, width, height, keepAlpha = (sniffed == "image/png"))

        // ...and re-encode to shed metadata bloat.
        val out = reencode(scaled, sniffed)
        return ProcessedImage(bytes = out, contentType = sniffed, width = scaled.width, height = scaled.height)
    }

    /*
     * Magic-byte sniffing. Returns the MIME type or null when unknown.
     * JPEG: FF D8 FF | PNG: 89 50 4E 47 | GIF: "GIF8" | WebP: RIFF....WEBP
     */
    fun sniffType(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        fun u(i: Int) = bytes[i].toInt() and 0xFF
        // JPEG
        if (u(0) == 0xFF && u(1) == 0xD8 && u(2) == 0xFF) return "image/jpeg"
        // PNG
        if (u(0) == 0x89 && u(1) == 0x50 && u(2) == 0x4E && u(3) == 0x47) return "image/png"
        // GIF ("GIF87a" / "GIF89a")
        if (u(0) == 0x47 && u(1) == 0x49 && u(2) == 0x46 && u(3) == 0x38) return "image/gif"
        // WebP ("RIFF" + 4 size bytes + "WEBP")
        if (u(0) == 0x52 && u(1) == 0x49 && u(2) == 0x46 && u(3) == 0x46 &&
            u(8) == 0x57 && u(9) == 0x45 && u(10) == 0x42 && u(11) == 0x50
        ) return "image/webp"
        return null
    }

    /*
     * Fit within MAX_WIDTH x MAX_HEIGHT, aspect ratio preserved.
     * Returns the ORIGINAL instance when already small enough (no copy).
     * keepAlpha=false flattens onto white (for JPEG output, which has no
     * alpha channel and whose writer rejects ARGB input).
     */
    fun downscaleIfNeeded(image: BufferedImage, width: Int, height: Int, keepAlpha: Boolean): BufferedImage {
        val scale = minOf(1.0, MAX_WIDTH.toDouble() / width, MAX_HEIGHT.toDouble() / height)
        val outType = if (keepAlpha && image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB
        else BufferedImage.TYPE_INT_RGB
        // No scaling needed: still normalize the type (cheap, keeps the
        // JPEG writer happy even for small images with alpha).
        val targetW = (width * scale).toInt().coerceAtLeast(1)
        val targetH = (height * scale).toInt().coerceAtLeast(1)
        if (scale >= 1.0 && image.type == outType) return image

        val out = BufferedImage(targetW, targetH, outType)
        val g = out.createGraphics()
        try {
            if (!keepAlpha) {
                g.color = java.awt.Color.WHITE
                g.fillRect(0, 0, targetW, targetH)
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(image, 0, 0, targetW, targetH, null)
        } finally {
            g.dispose()
        }
        return out
    }

    /*
     * Re-encode: JPEG with explicit quality, PNG with defaults (drops
     * EXIF/metadata, usually smaller than camera originals).
     */
    fun reencode(image: BufferedImage, mimeType: String): ByteArray {
        val format = if (mimeType == "image/png") "png" else "jpeg"
        val out = ByteArrayOutputStream()

        if (format == "png") {
            ImageIO.write(image, "png", out)
            return out.toByteArray()
        }

        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            writer.output = ImageIO.createImageOutputStream(out)
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = JPEG_QUALITY
            }
            writer.write(null, IIOImage(image, null, null), params)
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }
}
