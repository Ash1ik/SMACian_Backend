/*
 * PostImageRepository - Data Access Layer for the PostImage entity.
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.PostImage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PostImageRepository : JpaRepository<PostImage, Long> {

    /*
     * SQL: SELECT * FROM post_images WHERE post_id = ? ORDER BY sort_order ASC
     * Returns a post's images in upload order.
     */
    fun findByPostIdOrderBySortOrderAsc(postId: Long): List<PostImage>
}
