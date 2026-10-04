/*
 * PostLikeRepository - Data Access Layer for the PostLike entity.
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.PostLike
import org.springframework.data.jpa.repository.JpaRepository
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
}
