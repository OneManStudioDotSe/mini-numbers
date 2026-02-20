# Mini Numbers - TODO List

**Last Updated**: February 20, 2026

---

## Priority Guide

- **CRITICAL** — Must complete before any public release
- **HIGH** — Essential for launch quality
- **MEDIUM** — Production confidence
- **POLISH** — Final touches

---

## Phase 1: Security & Foundation — COMPLETED

- [x] Remove all hardcoded credentials
- [x] Environment variable system with `.env` support
- [x] Input validation and sanitization
- [x] Rate limiting (per IP and per API key)
- [x] Configurable CORS
- [x] Interactive setup wizard (WordPress-style, zero-restart)
- [x] Demo data generator
- [x] Session-based authentication with login page
- [x] Comprehensive test suite (166 tests)
- [x] Code architecture restructuring (package-per-feature)
- [x] Configurable tracker endpoint
- [x] Bounce rate calculation and dashboard display
- [x] Tracker optimization (1.9KB source, 1.3KB minified, History API SPA detection, visibility-aware heartbeat)
- [x] Gradle minification task (`minifyTracker`)
- [x] Realistic demo data generator (multi-event sessions, bounce/engaged mix)
- [x] Custom event tracking with `MiniNumbers.track()` API and dashboard visualization
- [x] GeoIP database bundling (classpath fallback for fat JAR deployments)
- [x] Comprehensive deployment documentation (JAR, Docker, reverse proxy, SSL, backups)

---

## Phase 2: Easy Deployment — COMPLETED

### Docker Optimization
- [x] Production Dockerfile in repo (multi-stage build, Alpine-based, non-root user, JVM container tuning)
- [x] docker-compose.yml in repo (SQLite + PostgreSQL variants, volume mounts)
- [x] GHCR automated builds with version tagging (GitHub Actions)
- [x] Multi-platform builds (linux/amd64, linux/arm64)
- [x] Health check endpoint (`GET /health` — JSON status with uptime, version, service state)
- [x] Metrics endpoint (`GET /metrics` — event counts, cache stats, privacy config)

### Other
- [x] GeoIP database bundled — works from filesystem and classpath (fat JAR)
- [x] Tracker size optimization (<2KB minified) — 1.9KB source, 1.3KB minified
- [x] Deployment documentation (JAR, Docker, platforms, reverse proxy, SSL, systemd, backups)

---

## Phase 3: Testing & Quality

### Additional Tests
- [x] Analytics calculation tests (22 tests in DataAnalysisUtilsTest)
- [x] Admin endpoint integration tests (14 tests in AdminEndpointTest)
- [x] End-to-end tests (9 tests in TrackingWorkflowTest)
- [x] Health endpoint tests (6 tests in HealthEndpointTest)
- [ ] Docker build and deployment tests

### CI/CD Pipeline
- [x] GitHub Actions: test + build workflow (on push/PR to main)
- [x] GitHub Actions: Docker multi-platform publish to GHCR (on push/tag)
- [ ] Code quality checks (linting, static analysis, vulnerability scanning)

### Monitoring
- [x] Health check endpoint (`GET /health` — JSON status with servicesReady flag)
- [x] Metrics endpoint (`GET /metrics` — uptime, event counts, cache stats, privacy config)

---

## Phase 4: Documentation

- [x] Installation guide (Docker, JAR, source) — in DEPLOYMENT.md
- [x] Configuration reference (all environment variables) — in DEPLOYMENT.md
- [x] Deployment guide (reverse proxy, SSL, backups, upgrading) — in DEPLOYMENT.md
- [x] Tracking integration guide (setup, SPA, custom events) — in DEPLOYMENT.md
- [x] API documentation — OpenAPI 3.0.3 spec at `/admin-panel/openapi.yaml`
- [ ] Dashboard user guide
- [ ] Privacy architecture explanation
- [ ] Contributing guidelines and code of conduct
- [ ] License selection (MIT recommended)

---

## Phase 5: Polish & Launch Prep

### UI/UX
- [x] Loading states and skeleton screens
- [ ] Error handling improvements
- [x] Accessibility (ARIA labels, keyboard navigation, skip-to-content link, semantic HTML roles)
- [ ] Empty state designs

### First-Time Experience
- [ ] Onboarding flow after first login
- [ ] In-app getting started checklist

### Performance
- [x] Database query optimization (8 composite indexes)
- [x] Query result caching (Caffeine, 500 entries, 30s TTL, auto-invalidation)
- [x] GeoIP lookup caching (Caffeine, 10K entries, 1h TTL)
- [ ] Frontend optimization (lazy loading, debouncing)

### Final Review
- [ ] Manual testing across browsers and devices
- [ ] Security audit
- [ ] Performance benchmarking

---

## Phase 6: Launch

- [ ] Screenshots and demo GIFs
- [ ] GitHub repository setup (public, issue templates, discussions)
- [ ] Launch content (blog post, Show HN, Reddit, Product Hunt)
- [ ] GitHub release v1.0.0
- [ ] Announce on Hacker News, Reddit, Product Hunt, Dev.to
- [ ] Monitor and respond to feedback
- [ ] Comparison and tutorial content
- [ ] Community building (Discord or GitHub Discussions)

---

## Phase 7: Post-Launch Features

### High Priority (Next 3 months)
- [x] Custom event tracking — name-based tracking with `MiniNumbers.track()` API and dashboard card
- [x] Conversion goals — URL-based and event-based goals with conversion rate tracking and management UI
- [x] Basic funnels — multi-step conversion tracking with drop-off analysis and time between steps
- [x] API pagination — backward-compatible `?page=&limit=` query parameters
- [x] Query result caching — Caffeine cache with auto-invalidation
- [x] Standardized error responses — `ApiError` model across all endpoints
- [x] OpenAPI documentation — 3.0.3 spec documenting all endpoints
- [x] Configurable privacy — hash rotation (1-8760h), 3 modes (STANDARD/STRICT/PARANOID), data retention
- [x] User segments — visual filter builder with AND/OR logic, segment analysis
- [x] Configurable tracker — heartbeat interval and SPA tracking toggle
- [ ] Email reports
- [ ] Webhooks
- [ ] Retention and cohort analysis
- [ ] User journey visualization
- [ ] Plugin system
- [ ] Integrations (Slack, Discord, Zapier)
