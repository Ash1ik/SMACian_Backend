package com.smacian.backend.exception

/*
 * AuthenticationRequiredException - thrown when an endpoint needs a logged-in
 * user but the security context holds none (e.g. CurrentUser.getUserId() on
 * an anonymous request). Mapped to HTTP 401 (NOT 500 - the server is fine,
 * the caller just needs to log in).
 */
class AuthenticationRequiredException(message: String = "Authentication required. Please log in.") :
    RuntimeException(message)
