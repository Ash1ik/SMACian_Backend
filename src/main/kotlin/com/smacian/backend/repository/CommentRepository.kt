/*
 * CommentRepository - Data Access Layer for the Comment entity.
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.Comment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CommentRepository : JpaRepository<Comment, Long> {

    /*
     * SQL: SELECT * FROM comments WHERE post_id = ? ORDER BY created_at ASC
     * All comments (top-level + replies) for a post, oldest first.
     * The service assembles them into a nested tree.
     */
    fun findByPostIdOrderByCreatedAtAsc(postId: Long): List<Comment>

    /*
     * SQL: SELECT COUNT(*) FROM comments WHERE post_id = ?
     * Total comment count shown on the post (includes replies).
     */
    fun countByPostId(postId: Long): Long
}
