# Mini Numbers - Roadmap & Status

**Last Updated**: March 8, 2026 (v1.2.0)

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
- [x] Empty/error state illustrations — all panels now render a contextual set_3 PNG illustration; error states use a research icon
- [x] Dashboard UI overhaul (merged filter bar, show more buttons, custom events breakdown, heatmap dates)
- [x] Filter bar redesign — date range text large and bold (xl, 700), "Filter by" group pushed to far right with `margin-left: auto`
- [x] Header layout fix — title ellipsis-truncates instead of wrapping; right-side controls never compress or wrap
- [x] Theme toggle fixed — pill overflow clipping, Remixicon icon correctly centered
- [x] Table row hover color — visually distinct from zebra stripe in both light and dark themes
- [x] "vs previous period" label — `xs` size, muted color, normal weight; subordinate to percentage value
- [x] Overview stat cards — decorative background illustrations removed; clean card layout
- [x] Raw Events modal — wider Path (31%) and Location (18%) columns; filter dropdowns always side-by-side
- [x] Sidebar app icon — 20% larger, centered; sidebar header text-aligned center
- [x] Demo project button — auto-hidden in sidebar when project list already contains "Demo project"
- [x] Header action buttons — `white-space: nowrap; flex-shrink: 0` prevents text wrapping at any viewport width
- [x] Bug fix: demo data generation 500 error — `seedDemoGoalsFunnelsSegments` missing `transaction {}` wrapper
- [x] Project delete from sidebar with confirmation dialog
- [x] Dark mode WCAG AA contrast — `--color-text-muted` lightened from `#94a3b8` to `#a8b8cc` on dark backgrounds
- [x] Filter warning visible state — background, border, border-radius, and padding added to `.filter-warning`
- [x] Stat card mobile grid — `.grid-cols-4` collapses to 2 columns ≤900 px and 1 column ≤480 px
- [x] Demo data loading state — "Generating…" spinner state on confirm button during API call
- [x] Modal accessibility — `role="dialog"`, `aria-modal`, `aria-labelledby` on all five primary modals; `aria-label="Close"` on all close buttons
- [x] Focus trap utility — `Utils.focusTrap` with Tab/Shift+Tab trapping, Escape key, and focus restoration; wired to all dialog modals via MutationObserver
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
- [x] API key rotation -- `POST /admin/projects/{id}/rotate-api-key` endpoint + "Rotate key" button in Settings modal
- [x] Retention preview -- `GET /admin/projects/{id}/retention-preview?days=N` read-only preview before enabling auto-retention
- [x] Goals / funnels / segments pagination -- `?page=&limit=` support, backward-compatible flat-array fallback
- [x] Tracker offline queue -- `mn_queue` localStorage buffer (max 20), drains on page load and `online` event
- [x] OpenAPI spec completion -- 20+ missing endpoints documented; JWT auth, user management, widget endpoints, securitySchemes, shared error responses
- [x] Cross-project isolation tests -- 8 new integration tests verifying auth boundaries and project isolation
- [x] Documentation: tracker reference, widget embed guide, troubleshooting guide, upgrading guide (4 new public doc pages)
- [x] Documentation: features.md, index.md, configuration.md, dashboard-guide.md expanded to cover all implemented features

**Milestone**: v1.0.0-beta → v1.2.0

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

| Risk                        | Mitigation                                                       |
|-----------------------------|------------------------------------------------------------------|
| Security vulnerability      | Security audit before launch, bug bounty, responsible disclosure |
| Slow adoption               | Strong launch (HN, Reddit, Product Hunt), excellent docs         |
| Maintenance burden          | Build community early, automate testing/deployment               |
| Feature gaps                | Prioritize most-requested features, deliver incrementally        |
| Competitors innovate faster | Focus on unique strengths (JVM, privacy, visuals)                |
