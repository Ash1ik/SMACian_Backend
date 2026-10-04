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
     * Full metadata for ONE post, no BYTEA: (id, width, height, sortOrder),
     * upload order. Used by single-post mapping.
     */
    @Query(
        "SELECT pi.id, pi.width, pi.height, pi.sortOrder FROM PostImage pi " +
        "WHERE pi.post.id = :postId ORDER BY pi.sortOrder ASC"
    )
    fun findMetadataByPostIdOrdered(@Param("postId") postId: Long): List<Array<Any>>

    /*
     * Metadata for a WHOLE page of posts in ONE query, no BYTEA:
     * (postId, id, width, height, sortOrder), ordered by sort_order.
     * The service groups per post preserving upload order.
     */
    @Query(
        "SELECT pi.post.id, pi.id, pi.width, pi.height, pi.sortOrder FROM PostImage pi " +
        "WHERE pi.post.id IN :postIds ORDER BY pi.sortOrder ASC"
    )
    fun findMetadataByPostIds(@Param("postIds") postIds: List<Long>): List<Array<Any>>

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
