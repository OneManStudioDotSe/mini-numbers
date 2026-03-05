package se.onemanstudio.core.models

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Login attempt tracker for brute-force protection
 * Stores failed attempt count and lockout timestamp per username
 */
data class LoginAttempt(
    val failedAttempts: AtomicInteger = AtomicInteger(0),
    val lockoutUntil: AtomicLong = AtomicLong(0)
)
