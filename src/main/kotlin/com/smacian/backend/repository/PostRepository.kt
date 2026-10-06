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
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PostRepository : JpaRepository<Post, Long> {

    /*
     * Newsfeed page with the author JOIN FETCHed - one query for posts +
     * authors instead of one lazy author SELECT per post. Pass a Pageable
     * WITHOUT sort (ordering is fixed in the query).
     */
    @Query(
        value = "SELECT p FROM Post p JOIN FETCH p.author ORDER BY p.createdAt DESC",
        countQuery = "SELECT COUNT(p) FROM Post p"
    )
    fun findFeedPage(pageable: Pageable): Page<Post>

    /*
     * Same, filtered to one author ("my posts" / user wall).
     */
    @Query(
        value = "SELECT p FROM Post p JOIN FETCH p.author a WHERE a.id = :authorId ORDER BY p.createdAt DESC",
        countQuery = "SELECT COUNT(p) FROM Post p WHERE p.author.id = :authorId"
    )
    fun findMyPostsPage(@Param("authorId") authorId: Long, pageable: Pageable): Page<Post>

    /*
     * Reshare counts for a whole page of posts in ONE query.
     * Returns (originalPostId, count) pairs; posts with zero reshares absent.
     * shareCount is DERIVED from real reshare rows - it can never drift
     * out of sync the way a stored counter did.
     */
    @Query("SELECT p.sharedFrom.id, COUNT(p) FROM Post p WHERE p.sharedFrom.id IN :ids GROUP BY p.sharedFrom.id")
    fun countResharesByPostIds(@Param("ids") ids: List<Long>): List<Array<Any>>

    /*
     * Reshare count for ONE post (single-post mapping).
     */
    @Query("SELECT COUNT(p) FROM Post p WHERE p.sharedFrom.id = :postId")
    fun countResharesByPostId(@Param("postId") postId: Long): Long

    /*
     * Reshare originals with authors in ONE query (for embedding in feed
     * responses without per-original lazy SELECTs).
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.id IN :ids")
    fun findAllWithAuthorByIds(@Param("ids") ids: List<Long>): List<Post>
}
