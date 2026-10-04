/*
 * PostImageRepository - Data Access Layer for the PostImage entity.
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.PostImage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PostImageRepository : JpaRepository<PostImage, Long> {

    /*
     * IDs only, no BYTEA - used by feed mapping (only the id feeds the URL).
     */
    @Query("SELECT pi.id FROM PostImage pi WHERE pi.post.id = :postId ORDER BY pi.sortOrder ASC")
    fun findIdsByPostIdOrdered(@Param("postId") postId: Long): List<Long>

    /*
     * (postId, imageId) pairs for a whole page of posts in ONE query.
     * Ordered by sort_order; the service groups per post preserving order.
     * No BYTEA loaded.
     */
    @Query("SELECT pi.post.id, pi.id FROM PostImage pi WHERE pi.post.id IN :postIds ORDER BY pi.sortOrder ASC")
    fun findImageIdsByPostIds(@Param("postIds") postIds: List<Long>): List<Array<Any>>

    /*
     * Delete ALL images of a post in ONE statement (no SELECT, no BYTEA).
     * Used by post edit (replace/clear) and relies on no orphan cleanup
     * since rows are removed directly. Returns rows deleted.
     */
    fun deleteByPostId(postId: Long): Long

    /*
     * SQL: SELECT COUNT(*) FROM post_images WHERE post_id = ?
     * Used by post edit to enforce the text-or-image invariant.
     */
    fun countByPostId(postId: Long): Long
}
