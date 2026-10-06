/*
 * HttpCache - Tiny helper for conditional image responses.
 *
 * matchesEtag() honors the If-None-Match header (single tag, list, or "*")
 * so stream endpoints can return 304 NOT MODIFIED instead of re-sending
 * bytes the client already caches (Coil/OkHttp revalidate automatically).
 */
package com.smacian.backend.util

object HttpCache {

    fun matchesEtag(ifNoneMatch: String?, etag: String): Boolean {
        if (ifNoneMatch.isNullOrBlank()) return false
        val header = ifNoneMatch.trim()
        if (header == "*") return true
        return header.split(",").any { it.trim() == etag }
    }
}
