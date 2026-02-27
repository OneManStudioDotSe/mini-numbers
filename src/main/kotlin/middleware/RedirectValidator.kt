package se.onemanstudio.middleware

/**
 * Validates redirect URLs against an allowlist to prevent open redirect attacks.
 * Only relative paths from a known set are permitted.
 */
object RedirectValidator {

    private val ALLOWED_PATHS = setOf("/setup", "/login", "/admin-panel")

    /**
     * Check if a redirect path is in the allowlist.
     * Only relative paths are accepted — absolute URLs are always rejected.
     */
    fun isAllowed(path: String): Boolean {
        // Reject absolute URLs and protocol-relative paths
        if (path.contains("://") || path.startsWith("//")) return false
        return path in ALLOWED_PATHS || path.startsWith("/admin-panel/")
    }

    /**
     * Return the requested path if allowed, otherwise fall back to a safe default.
     */
    fun safeRedirect(requested: String?, fallback: String = "/login"): String {
        return if (requested != null && isAllowed(requested)) requested else fallback
    }
}
