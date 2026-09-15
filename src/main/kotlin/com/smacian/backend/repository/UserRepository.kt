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
import org.springframework.data.jpa.repository.JpaRepository
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
}