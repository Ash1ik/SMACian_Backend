/*
 * PostLikeRepository - Data Access Layer for the PostLike entity.
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.PostLike
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PostLikeRepository : JpaRepository<PostLike, Long> {

    /*
     * SQL: SELECT COUNT(*) > 0 FROM post_likes WHERE post_id = ? AND user_id = ?
     * True when this user already liked this post.
     */
    fun existsByPostIdAndUserId(postId: Long, userId: Long): Boolean

    /*
     * SQL: SELECT COUNT(*) FROM post_likes WHERE post_id = ?
     * The like count shown on the post.
     */
    fun countByPostId(postId: Long): Long

    /*
     * SQL: DELETE FROM post_likes WHERE post_id = ? AND user_id = ?
     * Unlike. Returns rows deleted (0 = wasn't liked).
     */
    fun deleteByPostIdAndUserId(postId: Long, userId: Long): Long

    /*
     * Like counts for a whole page of posts in ONE query.
     * Returns (postId, count) pairs; posts with zero likes are absent.
     */
    @Query("SELECT l.post.id, COUNT(l) FROM PostLike l WHERE l.post.id IN :postIds GROUP BY l.post.id")
    fun countByPostIds(@Param("postIds") postIds: List<Long>): List<Array<Any>>

    /*
     * Which of the given posts the viewer liked - ONE query.
     */
    @Query("SELECT l.post.id FROM PostLike l WHERE l.post.id IN :postIds AND l.user.id = :userId")
    fun findLikedPostIds(@Param("postIds") postIds: List<Long>, @Param("userId") userId: Long): List<Long>
}
