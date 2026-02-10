# Changelog

All notable changes to Mini Numbers will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Environment-based configuration system** with `.env` file support
  - Configuration data classes in [AppConfig.kt](src/main/kotlin/config/AppConfig.kt)
  - Smart configuration loader with validation in [ConfigLoader.kt](src/main/kotlin/config/ConfigLoader.kt)
  - Comprehensive `.env.example` template with documentation for all options
  - Loading priority: environment variables → `.env` file → fail with clear error
- **Input validation and sanitization** for `/collect` endpoint
  - Comprehensive validation rules for path, referrer, sessionId, and event type
  - Character whitelisting to prevent injection attacks
  - Control character removal and whitespace normalization
  - All validation errors returned at once for faster debugging
- **Rate limiting** to prevent abuse and DDoS attacks
  - Per-IP address limiting (default: 1000 requests/minute, configurable)
  - Per-API key limiting (default: 10000 requests/minute, configurable)
  - Token bucket algorithm with in-memory Caffeine cache
  - Combined strategy: both IP and API key limits must pass
  - HTTP 429 responses with detailed rate limit information
- **Smart CORS configuration** (auto-detect development vs production)
  - Development mode (`KTOR_DEVELOPMENT=true`): allows all origins automatically
  - Production mode: requires explicit origin whitelist via `ALLOWED_ORIGINS`
  - Support for wildcard (`*`) with security warnings
  - Comprehensive logging of CORS policy in effect
- **Database reset functionality** via Gradle task
  - Command: `./gradlew reset`
  - Deletes all projects and events, re-seeds demo data
  - Confirmation prompt to prevent accidental data loss
  - Standalone entry point in [ResetDatabase.kt](src/main/kotlin/db/ResetDatabase.kt)
- **Configurable tracker endpoint** via `data-api-endpoint` attribute
  - Runtime configuration without build steps
  - Auto-detection: defaults to same origin + '/collect' if not specified
  - Support for cross-domain tracking scenarios
- **PostgreSQL support** alongside SQLite
  - Configurable via `DB_TYPE` environment variable
  - HikariCP connection pooling for production deployments
  - Seamless switching between database types
- **Comprehensive logging** throughout application lifecycle
  - Configuration validation and loading
  - Security module initialization
  - Database and service initialization
  - Failed authentication attempts
  - Rate limiting violations
  - Validation errors

### Changed
- **BREAKING**: Admin credentials must be set via `ADMIN_PASSWORD` environment variable
  - No default password for security
  - `ADMIN_USERNAME` defaults to "admin" but can be customized
  - Clear error message if password not configured
- **BREAKING**: Server salt must be set via `SERVER_SALT` environment variable
  - Minimum 32 characters required for security
  - Used for privacy-preserving visitor hash generation
  - Validation fails fast at startup if too short
- **BREAKING**: CORS requires explicit configuration in production via `ALLOWED_ORIGINS`
  - Empty value = reject all cross-origin requests (most secure)
  - Development mode automatically allows all origins for convenience
  - Production mode requires specific domain whitelist
- **BREAKING**: Application fails fast at startup if required configuration is missing
  - Better than running with insecure defaults
  - Clear error messages guide users to fix configuration
  - Validates all config values on startup
- CORS automatically uses `anyHost()` in development mode (`KTOR_DEVELOPMENT=true`)
- Database configuration now supports environment variables for all settings
- Tracker script supports custom API endpoint configuration via data attribute
- Application initialization order optimized: config → security → database → services
- Error responses now include detailed JSON with actionable messages
- Rate limit responses include limit type, current limit, and time window

### Removed
- Hardcoded admin credentials from [Security.kt](src/main/kotlin/core/Security.kt)
- Hardcoded server salt from [SecurityUtils.kt](src/main/kotlin/utils/SecurityUtils.kt)
- Hardcoded `anyHost()` CORS configuration from [HTTP.kt](src/main/kotlin/core/HTTP.kt)
- Hardcoded database paths from [DatabaseFactory.kt](src/main/kotlin/db/DatabaseFactory.kt.kt)
- Hardcoded GeoIP database path from [Application.kt](src/main/kotlin/Application.kt)
- Hardcoded tracker endpoint from [tracker.js](src/main/resources/static/tracker.js)
- Test header "MyCustomHeader" from CORS configuration

### Security
- **Implemented rate limiting** to prevent abuse, DDoS, and brute force attacks
  - Protects `/collect` endpoint from spam
  - Prevents single IP from overwhelming the system
  - Prevents misconfigured trackers from excessive requests
- **Added input validation** to prevent injection attacks
  - SQL injection prevention through input validation
  - XSS prevention through character whitelisting
  - Path traversal prevention through strict path validation
- **Added string sanitization** to remove control characters
  - Prevents log injection attacks
  - Removes potentially harmful characters before storage
- **Required strong server salt** (minimum 32 characters)
  - Cryptographic randomness requirement
  - Critical for privacy-preserving visitor hashing
  - Validation enforced at startup
- **Secure CORS configuration** by default in production
  - Rejects all cross-origin requests unless explicitly allowed
  - Development mode convenience without production risk
  - Clear warnings when using wildcard in production
- **Comprehensive error handling** without exposing sensitive details
  - Generic error messages to external clients
  - Detailed logging for administrators
  - No stack traces or internal details leaked

### Fixed
- Missing input validation for path, referrer, sessionId, and event type fields
  - Path length validation (max 512 characters)
  - Referrer URL format validation
  - Session ID format validation (alphanumeric + hyphens only)
  - Event type whitelist ("pageview" and "heartbeat" only)
- Missing rate limiting allowing potential abuse and spam
  - Now enforces limits at both IP and API key levels
  - Prevents resource exhaustion attacks
- Security vulnerability from accepting requests from any origin in production
  - CORS now requires explicit whitelist in production
  - Development mode remains convenient
- Application could start with weak or missing security configuration
  - Now fails fast with clear error messages
  - Forces proper configuration before startup
- Tracker endpoint was hardcoded requiring manual updates
  - Now configurable via HTML data attribute
  - Supports same-origin auto-detection

## [0.0.1] - 2026-02-03

### Added
- Initial release with core analytics functionality
- Privacy-first approach with daily-rotating visitor hashes
- Multi-project support with unique API keys
- Real-time dashboard with comprehensive statistics:
  - Total page views and unique visitors
  - Top pages, referrers, browsers, operating systems
  - Geographic breakdown (countries and cities)
  - Device type distribution (desktop, mobile, tablet)
  - Activity heatmap (7 days × 24 hours)
  - Contribution calendar (GitHub-style 365-day view)
  - Live visitor feed (last 5 minutes)
  - Time series analytics with comparison reports
- SQLite database support for easy deployment
- Lightweight tracking script (< 2KB)
  - No cookies, uses sessionStorage
  - Automatic pageview tracking
  - Heartbeat system for time-on-page metrics (30-second intervals)
  - SPA support via MutationObserver
  - Reliable delivery via navigator.sendBeacon()
- GeoIP-based location tracking using MaxMind GeoLite2
- User-agent parsing for browser/OS/device detection
- Basic HTTP authentication for admin panel
- JSON API for retrieving statistics and reports

### Privacy Features
- No personally identifiable information (PII) stored
- IP addresses processed in-memory only (never persisted)
- Daily-rotating visitor hashes prevent cross-day tracking
- No cookies or persistent identifiers
- GDPR-friendly design

---

## Migration Guide

### Upgrading from 0.0.1 to Unreleased

#### Step 1: Backup Existing Data
```bash
cp stats.db stats.db.backup
```

#### Step 2: Create Configuration File
```bash
cp .env.example .env
```

#### Step 3: Generate Strong Server Salt
```bash
openssl rand -hex 64
```

#### Step 4: Edit .env File
Open `.env` and set required values:

```bash
# Required (fail if missing)
ADMIN_PASSWORD=your-strong-password-here
SERVER_SALT=<paste-output-from-step-3>

# Optional (defaults shown)
ADMIN_USERNAME=admin
DB_TYPE=SQLITE
DB_SQLITE_PATH=./stats.db
SERVER_PORT=8080
KTOR_DEVELOPMENT=false
ALLOWED_ORIGINS=https://yourdomain.com
RATE_LIMIT_PER_IP=1000
RATE_LIMIT_PER_API_KEY=10000
```

#### Step 5: Update Tracker Script (if needed)
If your tracker is on a different domain than your analytics server:

```html
<!-- Before: -->
<script async src="https://analytics.example.com/admin-panel/tracker.js"
        data-project-key="your-api-key">
</script>

<!-- After: Add data-api-endpoint attribute -->
<script async src="https://analytics.example.com/admin-panel/tracker.js"
        data-project-key="your-api-key"
        data-api-endpoint="https://analytics.example.com/collect">
</script>
```

#### Step 6: Restart Application
```bash
./gradlew run
```

#### Step 7: Verify
1. Login with your new admin password
2. Check that existing data is visible in dashboard
3. Test tracking script on your website
4. Verify CORS allows your website's origin (check browser console)

**Note:** No database migration needed - all changes are configuration-based. Your existing `stats.db` will work without modification.

---

## Security Best Practices

### For Production Deployments:

1. **Set Strong Credentials**
   ```bash
   ADMIN_PASSWORD=<min 12 characters, mix of letters/numbers/symbols>
   SERVER_SALT=<64+ character random hex string>
   ```

2. **Configure CORS Properly**
   ```bash
   # Specific domains (recommended)
   ALLOWED_ORIGINS=https://example.com,https://app.example.com

   # NOT this (unless absolutely necessary)
   ALLOWED_ORIGINS=*
   ```

3. **Disable Development Mode**
   ```bash
   KTOR_DEVELOPMENT=false
   ```

4. **Adjust Rate Limits for Your Traffic**
   ```bash
   # Low traffic site (< 1000 views/day)
   RATE_LIMIT_PER_IP=100

   # Medium traffic site (< 10k views/day)
   RATE_LIMIT_PER_IP=500

   # High traffic site (> 10k views/day)
   RATE_LIMIT_PER_IP=2000
   ```

5. **Use PostgreSQL for Production**
   ```bash
   DB_TYPE=POSTGRESQL
   DB_PG_HOST=your-postgres-host
   DB_PG_NAME=mini_numbers
   DB_PG_USERNAME=mini_numbers_user
   DB_PG_PASSWORD=<strong-database-password>
   ```

6. **Keep .env Out of Version Control**
   - The `.gitignore` already includes `.env`
   - Never commit `.env` to Git
   - Share `.env.example` as a template instead

---

## Contributors

- Initial development by OneManStudio
- Phase 1 security hardening assisted by Claude Code (Anthropic)

---

## Support

- Issues: [GitHub Issues](https://github.com/yourusername/mini-numbers/issues)
- Documentation: See [CLAUDE.md](CLAUDE.md) for technical details
- Deployment Guide: See [TODO.md](TODO.md) for roadmap and setup instructions
