# Changelog

All notable changes to Mini Numbers will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.0.5] - 2026-02-20

### Added

- **API Enhancements**
  - Pagination for list endpoints with backward-compatible `?page=&limit=` query parameters
  - Query result caching with Caffeine (30-second TTL, 500 max entries, automatic invalidation on data changes)
  - Standardized error responses (`ApiError` model with error code, message, and details across all endpoints)
  - OpenAPI 3.0.3 specification at `/admin-panel/openapi.yaml` documenting all endpoints
- **Enhanced Privacy**
  - Configurable hash rotation period (1-8760 hours via `HASH_ROTATION_HOURS` env var, default 24)
  - Three privacy modes via `PRIVACY_MODE` env var: STANDARD (full data), STRICT (country-only geo), PARANOID (no geo/UA)
  - Data retention policies with auto-purge via `DATA_RETENTION_DAYS` env var (background timer every 6 hours)
- **Performance Optimization**
  - 5 new database indexes for analytics query performance (project+visitor, project+path, project+type+timestamp, project+country, project+browser)
  - GeoIP lookup cache with Caffeine (10,000 entries, 1-hour TTL)
  - Query result cache for dashboard endpoints (stats, reports, calendar, goal stats)
- **User Segments**
  - Visual filter builder with AND/OR logic operators
  - Filter fields: browser, OS, device, country, city, path, referrer, event type
  - Filter operators: equals, not_equals, contains, starts_with
  - Segment analysis endpoint with in-memory filter evaluation
  - Segment management modal (create, list, delete)
  - Segment cards rendered on dashboard with analyze button
- **Production Monitoring**
  - Health check endpoint (`GET /health`) with uptime, version, and service state
  - Metrics endpoint (`GET /metrics`) with event counts, cache statistics, and privacy configuration
- **Tracker Configuration**
  - Configurable heartbeat interval via `data-heartbeat-interval` attribute (default 30s)
  - SPA tracking toggle via `data-disable-spa="true"` attribute
  - Server-side tracker config endpoint (`GET /tracker/config`)
- **Dashboard UI Improvements**
  - Loading skeleton screens during data fetching (stat cards, chart containers)
  - Accessibility: ARIA labels on interactive elements, `role` attributes, skip-to-content link, semantic HTML (`<nav>`, `<main>`)
  - Keyboard navigation focus-visible styles
  - Segment section and management modal in admin panel
- **Docker Improvements**
  - JVM container tuning (`-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC`)
  - Configurable `JAVA_OPTS` environment variable

### Changed

- `AnalyticsSecurity` hash rotation now uses configurable epoch-hour-based buckets instead of fixed daily date strings
- `GeoLocationService` now caches lookups in Caffeine cache before hitting MaxMind database
- `ServiceManager` tracks uptime and manages data retention cleanup timer
- `/collect` endpoint applies privacy mode restrictions (PARANOID: no geo/UA, STRICT: country-only)
- Admin dashboard sidebar changed from `<div>` to semantic `<nav>` element

## [0.0.4] - 2026-02-20

### Added

- **Conversion Goals** — URL-based and event-based goal tracking with conversion rate analytics
  - Backend: `ConversionGoals` table, 5 goal API endpoints (CRUD + stats), conversion rate calculation with period comparison
  - Dashboard: Goal cards showing conversion rates with previous period comparison, goal management modal
  - Goal types: URL path match (pageview events) and custom event name match
  - Active/inactive toggle for goals without deletion
- **Basic Funnels** — Multi-step conversion tracking with drop-off analysis
  - Backend: `Funnels` and `FunnelSteps` tables, 4 funnel API endpoints (CRUD + analysis)
  - Dashboard: Horizontal funnel visualization with drop-off percentages and avg time between steps
  - Analysis: Session-based sequential step completion (events must occur in chronological order)
  - Funnel management modal with dynamic step builder (add/remove steps)
- **Expanded test suite** — 55 new tests (166 total, up from 111)
  - Analytics calculation tests (22 tests) — period calculations, report generation, bounce rate, heatmap, time series, contribution calendar
  - Admin endpoint integration tests (14 tests) — authentication, project CRUD, analytics endpoints
  - End-to-end tracking workflow tests (9 tests) — create project, collect events, verify analytics
  - Health endpoint tests (6 tests) — health check responses, JSON format, public access

### Fixed

- Mixed-type `mapOf` serialization failures in health, login, logout, rate limit, and update endpoints (replaced with `buildJsonObject`)
- Custom events query using `selectAll()` instead of explicit column selection (`COUNT(event_name)` not in result set)

## [0.0.3] - 2026-02-20

### Added

- **Custom event tracking** — Full-stack implementation with `MiniNumbers.track("name")` public API
  - Backend: `eventName` column, validation rules, custom event type, demo data generation
  - Dashboard: Dedicated "Custom Events" bar chart card, raw events filter, CSV export
  - Tracker: `window.MiniNumbers.track()` API exposed from tracking script IIFE
  - Validation: Event name regex (`a-zA-Z0-9_-. `), max 100 chars, required for custom type only
  - Tests: 8 new test cases for custom event validation (111 total)
- **GeoIP database bundling** — Classpath fallback for fat JAR deployments
  - Tries filesystem path first (dev mode), then classpath resource extraction to temp file
  - GeoIP works out of the box with `java -jar` without any user configuration
- **Comprehensive deployment documentation** — Full guide for production deployment
  - Installation methods: Fat JAR with systemd, source build, Docker (multi-stage)
  - Configuration reference: All environment variables from ConfigLoader
  - Reverse proxy: Nginx and Caddy configuration examples
  - SSL/HTTPS: Let's Encrypt with certbot and Caddy auto-HTTPS
  - Backup & recovery: SQLite and PostgreSQL strategies
  - Upgrading and troubleshooting guides
  - Docker Compose configurations for SQLite and PostgreSQL setups
- **Database schema evolution** — `createMissingTablesAndColumns` for non-destructive column additions

### Changed

- **Tracker script** — Internal `track()` renamed to `send(type, eventName)` to support custom event payloads
- **Demo data generator** — Now includes custom events (~15% of engaged session events)
  - Event names: signup, download, purchase, newsletter_subscribe, share, contact_form
- **Raw events table** — Shows event name in badge for custom events (e.g., "custom: signup")
- **Dashboard badges** — New color scheme: primary (pageview), secondary (heartbeat), accent (custom)

### Fixed

- Heartbeat not resuming after tab visibility change (referenced renamed function)

## [0.0.2] - 2026-02-17

### Added

- **Code architecture restructuring** — Package-per-feature organization with clean separation of concerns
- **Session-based authentication** — Dedicated login page replacing Basic HTTP Auth
- **Zero-restart setup flow** — Services initialize dynamically in < 1 second after setup
- **Service lifecycle management** — Centralized state tracking with hot-reload support
- **Comprehensive test suite** — 103 tests covering security, validation, lifecycle, and integration
- **Web-based setup wizard** — Interactive 5-step WordPress-style configuration
- **Demo data generator** — Generate realistic analytics data for testing (0-3000 events)
- **Environment-based configuration** — `.env` file support with validation and sensible defaults
- **Input validation & sanitization** — Protection against XSS, SQL injection, and malformed input
- **Rate limiting** — Per-IP and per-API key limits with configurable thresholds
- **Smart CORS configuration** — Auto-detect development vs production mode
- **Database reset** — `./gradlew reset` command for development convenience
- **Configurable tracker endpoint** — Runtime configuration via `data-api-endpoint` attribute
- **PostgreSQL support** — Alongside SQLite, with HikariCP connection pooling
- **Comprehensive logging** — Configuration, security, database, and error events

### Changed

- **Authentication** upgraded from Basic HTTP Auth to session-based with login page
- **Codebase** restructured to package-per-feature architecture
- **Setup wizard** no longer requires server restart (< 1 second transition)
- **Configuration** supports hot-reload without restart
- **BREAKING**: Admin credentials must be set via environment variables
- **BREAKING**: Server salt must be set via environment variable (min 32 chars)
- **BREAKING**: CORS requires explicit configuration in production
- **BREAKING**: Application fails fast if required configuration is missing

### Removed

- All hardcoded credentials and configuration
- Basic HTTP Authentication
- Monolithic file structure (split into focused modules)

### Security

- Rate limiting to prevent abuse and DDoS attacks
- Input validation to prevent injection attacks
- String sanitization to remove control characters
- Strong server salt requirement (min 32 characters)
- Secure CORS configuration by default in production
- Error handling without exposing sensitive details

### Fixed

- Missing input validation for all user-facing fields
- Missing rate limiting on data collection endpoint
- CORS accepting requests from any origin in production
- Application starting with weak or missing security configuration
- Hardcoded tracker endpoint requiring manual updates

## [0.0.1] - 2026-02-03

### Added

- Initial release with core analytics functionality
- Privacy-first approach with daily-rotating visitor hashes
- Multi-project support with unique API keys
- Real-time dashboard (page views, visitors, heatmap, calendar, live feed, charts)
- SQLite database support
- Lightweight tracking script (< 2KB, no cookies, SPA support)
- GeoIP-based location tracking
- User-agent parsing for browser/OS/device detection
- Basic HTTP authentication for admin panel

---

## Contributors

- Initial development by OneManStudio
- Security hardening, custom events, and documentation assisted by Claude Code (Anthropic)
