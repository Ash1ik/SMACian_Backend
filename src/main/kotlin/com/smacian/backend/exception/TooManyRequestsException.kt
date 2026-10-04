package com.smacian.backend.exception

/*
 * TooManyRequestsException - the caller is acting too fast (OTP resend
 * cooldown, too many wrong guesses). Mapped to HTTP 429.
 */
class TooManyRequestsException(message: String) : RuntimeException(message)
