/*
 * CommentLikeRepository - Data Access Layer for the CommentLike entity.
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.CommentLike
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CommentLikeRepository : JpaRepository<CommentLike, Long> {

    /*
     * True when this user already liked this comment.
     */
    fun existsByCommentIdAndUserId(commentId: Long, userId: Long): Boolean

    /*
     * Like count for ONE comment (single-comment mapping).
     */
    fun countByCommentId(commentId: Long): Long

    /*
     * Like counts for a WHOLE tree in ONE query.
     * Returns (commentId, count) pairs; zero-like comments are absent.
     */
    @Query("SELECT l.comment.id, COUNT(l) FROM CommentLike l WHERE l.comment.id IN :ids GROUP BY l.comment.id")
    fun countByCommentIds(@Param("ids") ids: List<Long>): List<Array<Any>>

    /*
     * Which of the given comments the viewer liked - ONE query.
     */
    @Query("SELECT l.comment.id FROM CommentLike l WHERE l.comment.id IN :ids AND l.user.id = :userId")
    fun findLikedCommentIds(@Param("ids") ids: List<Long>, @Param("userId") userId: Long): List<Long>

    /*
     * Unlike. Returns rows deleted (0 = wasn't liked).
     */
    fun deleteByCommentIdAndUserId(commentId: Long, userId: Long): Long
}
