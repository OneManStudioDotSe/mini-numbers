package se.onemanstudio.core.models

/**
 * User roles for role-based access control.
 * ADMIN: Full access — read, write, user management.
 * VIEWER: Read-only access — dashboard, reports, exports.
 */
enum class UserRole {
    ADMIN,
    VIEWER
}
