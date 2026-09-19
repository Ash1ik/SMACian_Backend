/*
 * PagedResponse - Standard pagination wrapper for list endpoints.
 *
 * JSON: { "content": [...], "page": 0, "size": 20,
 *         "totalElements": 42, "totalPages": 3 }
 */
package com.smacian.backend.dto.response

data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)