# Changelog

All notable changes to Mini Numbers will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
