/*
 * UserRepository - Data Access Layer for the User entity.
 *
 * A Repository interface handles ALL database operations for our entity.
 * By extending JpaRepository, we get these methods FOR FREE:
 *   - save(entity)     → INSERT or UPDATE
 *   - findById(id)     → SELECT by primary key
 *   - findAll()        → SELECT all rows
 *   - delete(entity)   → DELETE
 *   - count()          → total rows
 *
 * Custom methods are defined by NAMING only - Spring Data JPA generates
 * the SQL from the method name (called "Query Methods").
 */
package com.smacian.backend.repository

import com.smacian.backend.entity.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<User, Long> {

    /*
     * SQL: SELECT * FROM users WHERE email = ?
     * Returns Optional so we don't null-check manually.
     */
    fun findByEmail(email: String): Optional<User>

    /*
     * SQL: SELECT * FROM users WHERE phone = ?
     */
    fun findByPhone(phone: String): Optional<User>

    /*
     * SQL: SELECT COUNT(*) FROM users WHERE email = ?
     * Returns true if the email is already taken.
     */
    fun existsByEmail(email: String): Boolean

    /*
     * SQL: SELECT COUNT(*) FROM users WHERE phone = ?
     */
    fun existsByPhone(phone: String): Boolean

    // Uniqueness checks that ignore the user's own row (for profile edits).
    fun existsByEmailAndIdNot(email: String, id: Long): Boolean

    fun existsByPhoneAndIdNot(phone: String, id: Long): Boolean

    /*
     * People search: case-insensitive ILIKE on full name OR designation.
     * Only active users are returned; sorted by updatedAt desc (via Pageable sort).
     */
    @Query(
        "SELECT u FROM User u " +
            "WHERE u.isActive = true AND (" +
            "  :q = '' " +
            "  OR lower(concat(u.firstName, ' ', u.lastName)) LIKE lower(concat('%', :q, '%')) " +
            "  OR lower(coalesce(u.designation, '')) LIKE lower(concat('%', :q, '%'))" +
            ")"
    )
    fun searchPeople(@Param("q") q: String, pageable: Pageable): Page<User>
}