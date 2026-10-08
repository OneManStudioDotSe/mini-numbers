# Mini Numbers - Roadmap & Status

**Last Updated**: October 8, 2026 (v1.3.0, pre-release)

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

## Phase 6.5: Revival (October 2026) -- COMPLETE

Everything between the March docs and a releasable tree. See `_docs/CHANGELOG.md` 1.3.0 and `GOING_LIVE.md`.

- [x] Audit closed: origin allowlist wired, rate limits on admin and auth, live role checks, constant-time compare, LGPL dependency replaced, docs drift fixed
- [x] Test suite made honest (it had been skipping every authenticated test), PostgreSQL CI job, Playwright browser suite (13 tests)
- [x] Three tracker defects found by the browser suite and fixed (MIME type, beacon rejection, offline queue)
- [x] Page-view counting corrected (heartbeats and custom events were counted as views)
- [x] Dependencies current: Kotlin 2.4, Ktor 3.6, Gradle 9.8, Exposed 1.5, HikariCP 7, password4j
- [x] Competitor evaluation rewritten for October 2026
- [x] AI assistant referrals card and privacy posture chip
- [x] Release prep: version 1.3.0, screenshots, issue templates, Code of Conduct contact

## Phase 7: Launch

- [x] Screenshots (README and docs)
- [x] GitHub issue templates
- [ ] Rotate the credentials that were committed in `stats.db`, then purge `stats.db` and the GeoLite2 file from history (force-push, owner approval)
- [ ] Decide GeoLite2 distribution (download-on-start with a licence key, recommended) and add the attribution to the image
- [ ] Light-theme colour contrast (16 nodes; palette decision)
- [ ] Enable GitHub Discussions
- [ ] Push, watch the first CI run with the PostgreSQL and browser jobs, fix what it surfaces
- [ ] Tag `v1.3.0` → GHCR image via Docker Publish
- [ ] Launch content built on the niche: single JAR, SQLite, configurable privacy, honest AI-traffic view (Show HN, Reddit r/selfhosted, Dev.to)
- [ ] Monitor and respond to feedback

---

## Phase 8: Post-launch features

### High priority (table stakes in the 2026 market, in this order)

- [ ] Annotations on the time series, with auto-detected change points to confirm (needs one small table)
- [ ] User journeys as a privacy-aware river (aggregates to sections in PARANOID mode)
- [ ] Bot filter v2: datacenter ASN exclusion and flood detection, with a "filtered" count instead of silent drops
- [ ] Read-only MCP server over the existing API
- [ ] Web vitals as a tracker opt-in (three nullable columns)
- [ ] Retention and cohort analysis
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
