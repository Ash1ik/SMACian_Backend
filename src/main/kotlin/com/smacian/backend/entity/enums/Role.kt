/*
 * Role - Account privilege level.
 *
 * USER  = normal member (everyone starts here)
 * ADMIN = platform manager: delete any content, activate/deactivate users,
 *         send broadcasts, promote other admins (@PreAuthorize hasRole).
 * Stored as the enum name; granted as ROLE_* in JwtAuthenticationFilter.
 */
package com.smacian.backend.entity.enums

enum class Role {
    USER,
    ADMIN
}
