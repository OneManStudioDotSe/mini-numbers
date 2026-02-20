# Mini Numbers - Security Audit

**Date**: February 20, 2026
**Scope**: Full application review

---

## 1. Authentication

### Implementation
- Session-based auth via Ktor Sessions plugin (`core/Security.kt`)
- BCrypt password hashing support (`$2a$`/`$2b$` prefixed hashes)
- Login attempt tracking with Caffeine cache (15-minute TTL)
- Brute force protection: 5 failed attempts triggers 15-minute lockout

### Findings
- **LOW**: Admin password stored as plain text in `.env` file. Recommend documenting that bcrypt hashing is supported and encouraged.
- **LOW**: Session cookie `secure` flag set to `false`. Acceptable for HTTP development; production deployments behind HTTPS should enable this.
- **INFO**: Session max age is 7 days. Appropriate for admin dashboard use.

---

## 2. Input Validation

### Implementation
- `InputValidator` class handles all `/collect` endpoint payloads
- Character whitelisting, length limits, control character removal
- 26 dedicated tests covering edge cases

### Findings
- **PASS**: All user-submitted data validated before processing
- **PASS**: Path length limited to 512 chars, event names to 100 chars
- **PASS**: Control characters stripped from all string inputs

---

## 3. XSS Prevention

### Implementation
- `Utils.escapeHtml()` used in goals and segments rendering
- HTML entity encoding via DOM textContent method

### Findings
- **FIXED**: `updateTables()` now uses `Utils.escapeHtml()` for `visit.path` and `visit.city`
- **FIXED**: `updateLiveFeed()` now uses `Utils.escapeHtml()` for `visit.path` and `visit.city`
- **LOW**: Project name in `loadProjects()` onclick handler uses `replace(/'/g, "\\'")` — partial mitigation. The name comes from the authenticated admin API, so risk is minimal.

---

## 4. SQL Injection

### Implementation
- Exposed ORM parameterizes all queries automatically
- No raw SQL strings detected in codebase

### Findings
- **PASS**: All database operations use parameterized queries via Exposed ORM

---

## 5. Rate Limiting

### Implementation
- Per-IP and per-API-key token bucket rate limiting
- In-memory Caffeine cache
- Configurable limits via environment variables

### Findings
- **PASS**: Default limits: 1000 req/min per IP, 10000 req/min per API key
- **INFO**: Rate limiting is in-memory only. Multi-instance deployments would need shared state (Redis). Acceptable for single-instance use.

---

## 6. CORS

### Implementation
- Configurable `ALLOWED_ORIGINS` environment variable
- Development mode (`KTOR_DEVELOPMENT=true`) relaxes CORS

### Findings
- **INFO**: Production deployments must explicitly set `ALLOWED_ORIGINS`
- **PASS**: CORS is properly restrictive in production mode

---

## 7. Privacy & Data Protection

### Implementation
- Visitor hashing with SHA-256 and configurable rotation (1-8760 hours)
- IP addresses never stored in database
- Three privacy modes: STANDARD, STRICT, PARANOID
- Data retention with automatic purge

### Findings
- **PASS**: Privacy implementation is robust and well-designed
- **INFO**: GeoLite2 database should be kept up to date for accuracy
- **PASS**: No PII stored in any database table

---

## 8. Dependencies

### Current Stack
- Ktor 3.4.0, Kotlin 2.3.0, JDK 21
- Exposed 0.56.0, HikariCP 5.0.1
- jBCrypt 0.4, Caffeine 3.1.8
- GeoIP2 5.0.1, UserAgentUtils 1.21

### Findings
- **INFO**: Recommend periodic dependency scanning with `./gradlew dependencyCheckAnalyze` (OWASP Dependency Check plugin)
- **INFO**: Detekt static analysis now integrated for code quality
- **PASS**: No known critical CVEs in current dependency versions

---

## 9. API Security

### Implementation
- All admin endpoints require session authentication
- API keys used for data collection (project-scoped)
- Standardized error responses (no stack traces leaked)

### Findings
- **PASS**: No stack traces or internal details in error responses
- **PASS**: API endpoints properly check authentication
- **INFO**: No CSRF tokens, but mitigated by JSON API (not form-based)

---

## Summary

| Category | Rating | Notes |
|----------|--------|-------|
| Authentication | Good | BCrypt, brute force protection, session management |
| Input Validation | Excellent | Comprehensive with 26 dedicated tests |
| XSS | Good | Fixed remaining unescaped outputs |
| SQL Injection | Excellent | ORM parameterization throughout |
| Rate Limiting | Good | Adequate for single-instance |
| CORS | Good | Configurable, restrictive by default |
| Privacy | Excellent | Industry-leading approach |
| Dependencies | Good | No known vulnerabilities |
| API Security | Good | Proper auth checks, no data leaks |

### Recommendations (Priority Order)

1. Document bcrypt password usage in deployment guide
2. Enable secure cookie flag for HTTPS deployments
3. Add OWASP Dependency Check to CI pipeline
4. Consider Content-Security-Policy headers
5. Add rate limiting to admin endpoints (currently only on `/collect`)
