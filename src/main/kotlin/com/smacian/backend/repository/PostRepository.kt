/*
 * PostRepository - Data Access Layer for the Post entity.
 *
 * Feed ordering is newest-first (createdAt desc) via the Pageable sort
 * passed by the service - no custom query needed.
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.Post
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PostRepository : JpaRepository<Post, Long> {

    /*
     * SQL: SELECT * FROM posts ORDER BY created_at DESC LIMIT ? OFFSET ?
     * Used by the newsfeed (GET /api/feed).
     */
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Post>

    /*
     * SQL: SELECT * FROM posts WHERE author_id = ? ORDER BY created_at DESC
     * Used for "my posts" / a user's wall.
     */
    fun findByAuthorIdOrderByCreatedAtDesc(authorId: Long, pageable: Pageable): Page<Post>
}
