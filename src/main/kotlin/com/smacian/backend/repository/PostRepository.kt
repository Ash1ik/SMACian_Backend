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
import org.springframework.data.jpa.repository.Modifying
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
     * Atomic share-counter bump. Concurrent shares can never lose
     * increments (no read-modify-write). Returns rows updated (0 = no post).
     */
    @Modifying
    @Query("UPDATE Post p SET p.shareCount = p.shareCount + 1 WHERE p.id = :postId")
    fun incrementShareCount(@Param("postId") postId: Long): Int
}
