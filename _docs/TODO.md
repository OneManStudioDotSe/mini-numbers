# Mini Numbers - TODO list

**Last Updated**: February 27, 2026

---

## Phase 1: Security & foundation — COMPLETED

- [x] Remove all hardcoded credentials
- [x] Environment variable system with `.env` support
- [x] Input validation and sanitization
- [x] Rate limiting (per IP and per API key)
- [x] Configurable CORS
- [x] Interactive setup wizard (WordPress-style, zero-restart)
- [x] Demo data generator
- [x] Session-based authentication with login page
- [x] Comprehensive test suite (288 tests)
- [x] Code architecture restructuring (package-per-feature)
- [x] Configurable tracker endpoint
- [x] Bounce rate calculation and dashboard display
- [x] Tracker optimization (1.9KB source, 1.3KB minified, History API SPA detection, visibility-aware heartbeat)
- [x] Gradle minification task (`minifyTracker`)
- [x] Realistic demo data generator (multi-event sessions, bounce/engaged mix, auto-seeds goals/funnels/segments)
- [x] Custom event tracking with `MiniNumbers.track()` API and dashboard visualization
- [x] GeoIP database bundling (classpath fallback for fat JAR deployments)
- [x] Comprehensive deployment documentation (JAR, Docker, reverse proxy, SSL, backups)

---

## Phase 2: Easy deployment — COMPLETED

### Docker optimization
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

## Phase 3: Testing & quality — COMPLETED

### Additional tests
- [x] Analytics calculation tests (22 tests in DataAnalysisUtilsTest)
- [x] Admin endpoint integration tests (14 tests in AdminEndpointTest)
- [x] End-to-end tests (9 tests in TrackingWorkflowTest)
- [x] Health endpoint tests (6 tests in HealthEndpointTest)
- [x] Docker build and deployment tests (CI verifies Docker build + health check)

### CI/CD pipeline
- [x] GitHub Actions: test + build workflow (on push/PR to main)
- [x] GitHub Actions: Docker multi-platform publish to GHCR (on push/tag)
- [x] Code quality checks (Detekt static analysis with baseline, integrated in CI)
- [x] Extended test suite (288 tests, up from 166)

### Monitoring
- [x] Health check endpoint (`GET /health` — JSON status with servicesReady flag)
- [x] Metrics endpoint (`GET /metrics` — uptime, event counts, cache stats, privacy config)

---

## Phase 4: Documentation — COMPLETED

- [x] Installation guide (Docker, JAR, source) — in DEPLOYMENT.md
- [x] Configuration reference (all environment variables) — in DEPLOYMENT.md
- [x] Deployment guide (reverse proxy, SSL, backups, upgrading) — in DEPLOYMENT.md
- [x] Tracking integration guide (setup, SPA, custom events) — in DEPLOYMENT.md
- [x] API documentation — OpenAPI 3.0.3 spec at `/admin-panel/openapi.yaml`
- [x] Dashboard user guide — `_docs/DASHBOARD_GUIDE.md`
- [x] Privacy architecture explanation — `_docs/PRIVACY.md`
- [x] Contributing guidelines and code of conduct — `CONTRIBUTING.md` + `CODE_OF_CONDUCT.md`
- [x] License selection — MIT License (`LICENSE`)

---

## Phase 5: Polish & launch prep — COMPLETED

### UI/UX
- [x] Loading states and skeleton screens
- [x] Error handling improvements (global error handlers, API retry with exponential backoff, error states with retry)
- [x] Accessibility (ARIA labels, keyboard navigation, skip-to-content link, semantic HTML roles)
- [x] Empty state designs (contextual empty states for all charts, tables, and data sections)
- [x] Dashboard UI overhaul (merged filter bar, show more buttons, custom events breakdown, heatmap dates and color fix)
- [x] Project delete from sidebar with confirmation dialog
- [x] Enhanced demo data (10 custom event types, auto-seeds goals/funnels/segments)

### First-time experience
- [x] Onboarding flow after first login (modal with checklist, localStorage persistence)
- [x] In-app getting started checklist (4-step checklist: create project, install tracker, first visit, explore)

### Performance
- [x] Database query optimization (8 composite indexes)
- [x] Query result caching (Caffeine, 500 entries, 30s TTL, auto-invalidation)
- [x] GeoIP lookup caching (Caffeine, 10K entries, 1h TTL)
- [x] Frontend optimization (Promise.allSettled for parallel loads, debounced filter changes)

### Final review
- [x] Manual testing across browsers and devices — `_docs/TESTING_PLAN.md`
- [x] Security audit — `_docs/SECURITY_AUDIT.md`
- [x] Performance benchmarking — `_docs/PERFORMANCE.md`

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

## Phase 7: Post-launch features

### High priority (next 3 months)
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
- [x] UTM campaign tracking — source, medium, campaign breakdown with dashboard cards
- [x] Scroll depth tracking — threshold-based (25/50/75/100%) with distribution chart
- [x] Session duration & session count — top-level stat cards with period comparisons
- [x] Entry & exit pages — first/last page per session analysis
- [x] Outbound link & file download tracking — automatic detection with dashboard cards
- [x] Region/state geography — subdivision-level geolocation below country data
- [x] Real-time visitor count — live counter badge polling every 5 seconds
- [x] Conversion rate metric — top-level stat card (sessions with custom events / total)
- [x] Event properties — `MiniNumbers.track("name", { key: "value" })` with JSON storage
- [x] Email reports — SMTP service, scheduler, templates, admin UI, settings panel
- [x] Webhooks — event triggers (goal_conversion, traffic_spike), admin UI, setup guide, HMAC delivery
- [x] Revenue tracking — revenue aggregation, attribution by source/UTM, dashboard section, guide
- [ ] Retention and cohort analysis
- [ ] User journey visualization
- [ ] Plugin system
- [ ] Integrations (Slack, Discord, Zapier)

### Nice to have (future consideration)
- [ ] Hashed page path tracking — anonymize URL paths for additional privacy
- [ ] Cross-domain tracking — track users across multiple domains as single sessions
- [ ] Subdomain tracking — unified tracking across subdomains
- [ ] Public/shared dashboard links — read-only shareable analytics views
- [ ] Email/webhook alerts — threshold-based notifications (traffic spikes, goal completions)
- [ ] Data import — migrate from Plausible, Google Analytics, or CSV
- [ ] Annotations/event markers — mark deployments, campaigns, or events on time-series charts
- [ ] Page performance / Web Vitals — track LCP, FID, CLS, TTFB metrics
- [ ] A/B test tracking — variant assignment and conversion tracking
- [ ] Custom dashboards — user-configurable widget layouts
