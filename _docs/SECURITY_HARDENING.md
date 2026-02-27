# Security Hardening Guide

Comprehensive security reference for Mini Numbers. Covers all implemented security measures, configuration best practices, and deployment checklists.

---

## 1. Authentication

### Dual authentication system

Mini Numbers supports two authentication methods simultaneously:

**Session-based auth** (for the browser admin panel):
- Cookie: `mini_numbers_session`
- `HttpOnly`, `SameSite=Strict`, `Secure` (in production)
- 7-day max age with 4-hour inactivity timeout
- Session rotation on every login (prevents fixation attacks)

**JWT auth** (for programmatic API access):
- `POST /api/token` — exchange credentials for access + refresh tokens
- Access tokens: 15-minute expiry, signed with HMAC-SHA256
- Refresh tokens: 7-day expiry, single-use with rotation
- Signing key derived from `SERVER_SALT` (no additional secrets needed)

### Brute force protection

- 5 failed login attempts = 15-minute lockout per username
- Lockout state tracked with Caffeine cache (in-memory, auto-expires)

### Password storage

- BCrypt (cost factor 12) — plaintext passwords are rejected
- Passwords validated server-side only; never logged or returned in API responses

---

## 2. Refresh Token Rotation

Refresh tokens use a **family-based rotation** scheme to detect replay attacks:

1. Each login creates a new token family
2. On refresh, the old token is revoked and a new one issued in the same family
3. If a revoked token is reused (replay attack), the request is rejected
4. Tokens are stored as SHA-256 hashes — raw tokens never persist on disk

**Database table:** `refresh_tokens` (columns: `id`, `token_hash`, `family`, `expires_at`, `revoked_at`, `replaced_by`)

---

## 3. Role-Based Access Control (RBAC)

Two roles:

| Role | Read access | Write access | User management |
|------|-------------|--------------|-----------------|
| **admin** | All endpoints | Create/update/delete projects, goals, funnels, segments, webhooks | Yes |
| **viewer** | All GET endpoints | None | No |

- Roles stored in `users` table
- Checked via `call.requireRole(UserRole.ADMIN)` guard on write endpoints
- Works with both session auth and JWT (role embedded in JWT `role` claim)

### User management endpoints (admin-only)

- `GET /admin/users` — list users
- `POST /admin/users` — create user
- `PUT /admin/users/{id}/role` — change role
- `DELETE /admin/users/{id}` — delete user

---

## 4. Password Reset

Since Mini Numbers is self-hosted, password reset uses **server salt verification** instead of email:

```
POST /api/password-reset
{
  "serverSalt": "<your SERVER_SALT from .env>",
  "newPassword": "new-secure-password"
}
```

- Only the server operator has access to `SERVER_SALT`
- Password is BCrypt-hashed before storage
- All refresh tokens are invalidated on reset
- Minimum 8 characters enforced

---

## 5. CORS Policy

**Public endpoints** (`/collect`, `/widget/*`, `/tracker/*`):
- `anyHost()` — required because tracking scripts run on third-party sites
- `allowCredentials = false` — session cookies are never sent cross-origin

**Admin endpoints** (`/admin/*`):
- Origin validated against `ALLOWED_ORIGINS` configuration
- Requests with unknown `Origin` header are rejected with 403
- Same-origin requests (no `Origin` header) are always allowed

### Configuration

```env
# Development (allow all)
ALLOWED_ORIGINS=*

# Production (restrict to specific domains)
ALLOWED_ORIGINS=https://analytics.example.com,https://admin.example.com
```

---

## 6. Rate Limiting

| Endpoint group | Limit | Strategy |
|---------------|-------|----------|
| `/collect` | 1000 req/min per IP, 10000 req/min per API key | Token bucket |
| `/widget/*` | Same as /collect | Token bucket |
| `/admin/*` | 200 req/min per IP | Token bucket |
| Login | 5 failed attempts = 15min lockout | Counter + lockout |
| `/api/password-reset` | Inherits admin rate limit | Token bucket |

- Buckets auto-expire after 5 minutes of inactivity
- Rate limit state stored in-memory (Caffeine cache)
- Returns `429 Too Many Requests` with `ApiError` body when exceeded

---

## 7. Input Validation & Sanitization

All user input is validated before processing:

- **Whitelist-based regex** patterns for paths, URLs, session IDs, event names
- **Length limits** matching database schema (e.g., path: 512 chars, event name: 100 chars)
- **Control character removal** and whitespace normalization
- **26 dedicated tests** covering edge cases

### SQL injection prevention

All database queries use Exposed ORM with parameterized expressions (`eq`, `less`, etc.). No raw SQL string concatenation exists in the codebase.

### XSS prevention

- Content Security Policy (CSP) header restricts resource loading
- HTML entity encoding via `Utils.escapeHtml()` in dashboard
- `X-Content-Type-Options: nosniff` prevents MIME sniffing
- `X-Frame-Options: DENY` prevents clickjacking

---

## 8. Cookie Security

| Attribute | Value | Purpose |
|-----------|-------|---------|
| `HttpOnly` | `true` | Prevents JavaScript access (XSS mitigation) |
| `Secure` | `true` in production | Ensures HTTPS-only transmission |
| `SameSite` | `Strict` | Prevents CSRF attacks |
| `Path` | `/` | Scoped to entire application |
| `MaxAge` | 7 days | Absolute session lifetime |

**Inactivity timeout:** Sessions are invalidated after 4 hours of inactivity, even if the cookie hasn't expired.

---

## 9. Webhook Security

Outbound webhooks are signed with HMAC-SHA256:

```
X-MiniNumbers-Signature: sha256=<hex-encoded HMAC>
X-MiniNumbers-Event: goal_conversion
```

**Verification on the receiving end:**

```python
import hmac, hashlib

def verify_webhook(payload, signature, secret):
    expected = "sha256=" + hmac.new(
        secret.encode(), payload.encode(), hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(expected, signature)
```

- Each webhook has a unique auto-generated secret (shown once at creation)
- Webhook URLs must use HTTPS
- Delivery retries: 3 attempts with exponential backoff (5s, 10s, 15s)
- Delivery history stored in `webhook_deliveries` table

---

## 10. Data Isolation (Application-Level RLS)

All admin endpoints validate that the requested resource belongs to the authenticated project:

- Funnel analysis: `(Funnels.id eq funnelId) and (Funnels.projectId eq projectId)`
- Funnel deletion: ownership verified before deleting steps
- Goals, segments: compound `WHERE` clauses include `projectId`
- Webhook operations: verified against project ownership

---

## 11. Privacy Architecture

| Mode | Geolocation | Browser/OS/device | Hash rotation |
|------|-------------|-------------------|---------------|
| STANDARD | Country + city | Full | Configurable |
| STRICT | Country only | Full | Configurable |
| PARANOID | None | None | Configurable |

- IP addresses are **never stored** — processed in-memory only
- Visitor identification via rotating SHA-256 hash: `SHA256(ip + ua + projectId + salt + rotationBucket)`
- Hash rotation configurable from 1 hour to 1 year
- Data retention: automatic purge of events older than N days

---

## 12. Security Headers

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline' ...
Referrer-Policy: strict-origin-when-cross-origin
Strict-Transport-Security: max-age=31536000; includeSubDomains  (production only)
```

---

## 13. Error Handling

- **Global error handler** (StatusPages plugin) catches all unhandled exceptions
- Client receives generic `ApiError` JSON — no stack traces, internal paths, or SQL details
- Server logs full exception details for debugging
- Standardized error format: `{ "error": "...", "code": "...", "details": [...] }`

---

## 14. Redirect Safety

All internal redirects are validated against a hardcoded allowlist:
- `/setup`, `/login`, `/admin-panel`
- Absolute URLs and protocol-relative paths (`//`) are rejected
- `RedirectValidator.safeRedirect()` falls back to `/login` on invalid input

---

## 15. Global Error Handler

The StatusPages plugin provides centralized error handling:

| Exception | Status | Client message |
|-----------|--------|----------------|
| `SerializationException` | 400 | "Invalid request format" |
| `Throwable` (catch-all) | 500 | "An unexpected error occurred" |
| Status 404 | 404 | "Resource not found" |

Full stack traces are logged server-side only.

---

## 16. Deployment Security Checklist

Before deploying to production:

- [ ] Set `KTOR_DEVELOPMENT=false` in `.env`
- [ ] Use a strong `ADMIN_PASSWORD` (BCrypt-hashed, min 12 characters)
- [ ] Generate a cryptographically secure `SERVER_SALT` (`openssl rand -hex 64`)
- [ ] Set specific `ALLOWED_ORIGINS` (not `*`)
- [ ] Enable HTTPS (reverse proxy with Nginx/Caddy or cloud load balancer)
- [ ] Verify `cookie.secure = true` is active (automatic when `KTOR_DEVELOPMENT=false`)
- [ ] Set appropriate `DATA_RETENTION_DAYS` for your jurisdiction
- [ ] Configure firewall rules — only expose port 8080 via reverse proxy
- [ ] Use Docker with the provided `Dockerfile` (runs as non-root `analytics` user)
- [ ] Mount the SQLite database file as a Docker volume for persistence
- [ ] Set up regular backups of the database file or PostgreSQL database
- [ ] Review `PRIVACY_MODE` setting for your compliance requirements
- [ ] Keep dependencies updated — run `./gradlew dependencies --scan` periodically
- [ ] Add OWASP Dependency Check to CI pipeline

---

## 17. Incident Response

### Compromised admin credentials

1. Reset password: `POST /api/password-reset` with server salt
2. All refresh tokens are automatically invalidated
3. Active sessions expire within 4 hours (inactivity timeout)

### Suspected token theft

1. Delete all refresh tokens: `DELETE FROM refresh_tokens WHERE username = 'admin'`
2. Rotate the `SERVER_SALT` in `.env` — this invalidates all JWT access tokens
3. Restart the application to pick up the new salt

### API key compromise

1. Delete the compromised project via admin panel or API
2. Create a new project with a fresh API key
3. Update the tracking script on affected websites

---

## 18. Future Considerations

- **OWASP Dependency Check** — add to CI pipeline for automated vulnerability scanning
- **CSP nonce-based scripts** — replace `unsafe-inline` with nonce-based script loading
- **Exposed ORM 1.x migration** — major version bump with R2DBC support
- **HikariCP 7.x** — major version with breaking API changes, evaluate when stable
- **Multi-factor authentication** — TOTP-based 2FA for admin accounts
- **Audit logging** — persistent log of admin actions (who changed what, when)
- **IP allowlisting** — restrict admin access to specific IP ranges
- **Webhook retry queue** — move from in-thread retry to persistent job queue (e.g., Quartz)
