/*
 * CommentRepository - Data Access Layer for the Comment entity.
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.Comment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    /*
     * Comment counts for a whole page of posts in ONE query.
     * Returns (postId, count) pairs; posts with zero comments are absent.
     */
    @Query("SELECT c.post.id, COUNT(c) FROM Comment c WHERE c.post.id IN :postIds GROUP BY c.post.id")
    fun countByPostIds(@Param("postIds") postIds: List<Long>): List<Array<Any>>
}
