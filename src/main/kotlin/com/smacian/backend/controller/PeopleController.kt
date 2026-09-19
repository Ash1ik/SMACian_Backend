/*
 * PeopleController - Endpoints to browse OTHER users (all Bearer required).
 *
 *   GET /api/users?search=&page=0&size=20   -> paged people list
 *   GET /api/users/{id}                     -> public profile of one user
 *
 * SecurityConfig already requires authentication for anything under
 * /api/users, so both endpoints need a valid JWT.
 */
package com.smacian.backend.controller

import com.smacian.backend.dto.response.PagedResponse
import com.smacian.backend.dto.response.PeopleListItemResponse
import com.smacian.backend.dto.response.PublicUserResponse
import com.smacian.backend.service.UserService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class PeopleController(
    private val userService: UserService
) {

    // ====================================================================
    // 1. PEOPLE SEARCH (paged) - ?search=&page=0&size=20
    // ====================================================================
    // Case-insensitive match on full name OR designation.
    // Sorted by updatedAt desc. Returns with a default empty query = first 20.

    @GetMapping
    fun searchPeople(
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PagedResponse<PeopleListItemResponse> =
        userService.searchPeople(search ?: "", page, size)

    // ====================================================================
    // 2. PUBLIC PROFILE - GET /api/users/{id}
    // ====================================================================
    // Full profile for another user. Email/phone are hidden. 404 if the
    // user is not found or inactive.

    @GetMapping("/{id}")
    fun getPublicProfile(@PathVariable id: Long): PublicUserResponse =
        userService.getPublicProfile(id)
}