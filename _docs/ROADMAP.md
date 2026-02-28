# Mini Numbers - Roadmap & Status

**Last Updated**: February 27, 2026

---

## Phase 1: Security & foundation -- COMPLETE

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

## Phase 2: Easy deployment -- COMPLETE

- [x] Production Dockerfile (multi-stage build, Alpine-based, non-root user, JVM container tuning)
- [x] docker-compose.yml (SQLite + PostgreSQL variants, volume mounts)
- [x] GHCR automated builds with version tagging (GitHub Actions)
- [x] Multi-platform builds (linux/amd64, linux/arm64)
- [x] Health check endpoint (`GET /health` -- JSON with uptime, version, service state)
- [x] Metrics endpoint (`GET /metrics` -- event counts, cache stats, privacy config)
- [x] GeoIP database bundled (filesystem + classpath fallback for fat JAR)
- [x] Deployment documentation (JAR, Docker, platforms, reverse proxy, SSL, systemd, backups)

---

## Phase 3: Testing & quality -- COMPLETE

- [x] Analytics calculation tests (22 tests in DataAnalysisUtilsTest)
- [x] Admin endpoint integration tests (14 tests in AdminEndpointTest)
- [x] End-to-end tests (9 tests in TrackingWorkflowTest)
- [x] Health endpoint tests (6 tests in HealthEndpointTest)
- [x] Docker build and deployment tests (CI verifies Docker build + health check)
- [x] GitHub Actions: test + build workflow (on push/PR to main)
- [x] GitHub Actions: Docker multi-platform publish to GHCR (on push/tag)
- [x] Code quality checks (Detekt static analysis with baseline, integrated in CI)
- [x] Extended test suite (288 tests, up from 166)

---

## Phase 4: Documentation -- COMPLETE

- [x] Installation guide (Docker, JAR, source) -- in DEPLOYMENT.md
- [x] Configuration reference (all environment variables) -- in DEPLOYMENT.md
- [x] Deployment guide (reverse proxy, SSL, backups, upgrading) -- in DEPLOYMENT.md
- [x] Tracking integration guide (setup, SPA, custom events) -- in DEPLOYMENT.md
- [x] API documentation -- OpenAPI 3.0.3 spec at `/admin-panel/openapi.yaml`
- [x] Dashboard user guide -- `_docs/DASHBOARD_GUIDE.md`
- [x] Privacy architecture explanation -- `_docs/PRIVACY.md`
- [x] Contributing guidelines and code of conduct -- `CONTRIBUTING.md` + `CODE_OF_CONDUCT.md`
- [x] License selection -- MIT License (`LICENSE`)

---

## Phase 5: Polish & launch prep -- COMPLETE

- [x] Loading states and skeleton screens
- [x] Error handling improvements (global error handlers, API retry with exponential backoff)
- [x] Accessibility (ARIA labels, keyboard navigation, skip-to-content link, semantic HTML roles)
- [x] Empty state designs (contextual empty states for all charts, tables, and data sections)
- [x] Dashboard UI overhaul (merged filter bar, show more buttons, custom events breakdown, heatmap dates)
- [x] Project delete from sidebar with confirmation dialog
- [x] Enhanced demo data (10 custom event types, auto-seeds goals/funnels/segments)
- [x] Onboarding flow after first login (modal with checklist, localStorage persistence)
- [x] Database query optimization (8 composite indexes)
- [x] Query result caching (Caffeine, 500 entries, 30s TTL, auto-invalidation)
- [x] GeoIP lookup caching (Caffeine, 10K entries, 1h TTL)
- [x] Frontend optimization (Promise.allSettled for parallel loads, debounced filter changes)
- [x] Manual testing across browsers and devices -- `_docs/TESTING_PLAN.md`
- [x] Security audit -- `_docs/SECURITY.md`
- [x] Performance benchmarking -- `_docs/PERFORMANCE.md`

---

## Phase 6: Feature parity -- COMPLETE

- [x] Custom event tracking -- name-based tracking with `MiniNumbers.track()` API
- [x] Conversion goals -- URL-based and event-based with conversion rate tracking
- [x] Basic funnels -- multi-step conversion tracking with drop-off analysis
- [x] API pagination -- backward-compatible `?page=&limit=` query parameters
- [x] Query result caching -- Caffeine cache with auto-invalidation
- [x] Standardized error responses -- `ApiError` model across all endpoints
- [x] OpenAPI documentation -- 3.0.3 spec documenting all endpoints
- [x] Configurable privacy -- hash rotation (1-8760h), 3 modes, data retention
- [x] User segments -- visual filter builder with AND/OR logic, segment analysis
- [x] UTM campaign tracking -- source, medium, campaign breakdown
- [x] Scroll depth tracking -- threshold-based (25/50/75/100%)
- [x] Session duration & session count -- stat cards with period comparisons
- [x] Entry & exit pages -- first/last page per session analysis
- [x] Outbound link & file download tracking -- automatic detection
- [x] Region/state geography -- subdivision-level geolocation
- [x] Real-time visitor count -- live counter badge polling every 5 seconds
- [x] Event properties -- `MiniNumbers.track("name", { key: "value" })` with JSON storage
- [x] Email reports -- SMTP service, scheduler, templates, admin UI, settings panel
- [x] Webhooks -- event triggers (goal_conversion, traffic_spike), admin UI, HMAC delivery
- [x] Revenue tracking -- aggregation, attribution by source/UTM, dashboard section

**Milestone**: v1.0.0-beta

---

## Phase 7: Launch

- [ ] Screenshots and demo GIFs
- [ ] GitHub repository setup (public, issue templates, discussions)
- [ ] Launch content (blog post, Show HN, Reddit, Product Hunt)
- [ ] GitHub release v1.0.0
- [ ] Announce on Hacker News, Reddit, Product Hunt, Dev.to
- [ ] Monitor and respond to feedback
- [ ] Comparison and tutorial content
- [ ] Community building (Discord or GitHub Discussions)

---

## Phase 8: Post-launch features

### High priority

- [ ] Retention and cohort analysis
- [ ] User journey visualization
- [ ] Plugin system
- [ ] Integrations (Slack, Discord, Zapier)
- [ ] Enterprise -- multi-user support with RBAC, SSO, white-labeling
- [ ] Mobile -- PWA support, optional native apps
- [ ] Customizable dashboards, saved reports, annotations

### Nice to have

- [ ] Hashed page path tracking -- anonymize URL paths for additional privacy
- [ ] Cross-domain tracking -- track users across multiple domains
- [ ] Subdomain tracking -- unified tracking across subdomains
- [ ] Public/shared dashboard links -- read-only shareable analytics views
- [ ] Email/webhook alerts -- threshold-based notifications
- [ ] Data import -- migrate from Plausible, Google Analytics, or CSV
- [ ] Annotations/event markers -- mark deployments/campaigns on time-series charts
- [ ] Page performance / Web Vitals -- track LCP, FID, CLS, TTFB
- [ ] A/B test tracking -- variant assignment and conversion tracking

---

## Risk mitigation

| Risk | Mitigation |
|------|------------|
| Security vulnerability | Security audit before launch, bug bounty, responsible disclosure |
| Slow adoption | Strong launch (HN, Reddit, Product Hunt), excellent docs |
| Maintenance burden | Build community early, automate testing/deployment |
| Feature gaps | Prioritize most-requested features, deliver incrementally |
| Competitors innovate faster | Focus on unique strengths (JVM, privacy, visuals) |
