# Changelog

All notable changes to Mini Numbers will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Origin allowlist enforced on the admin API**: `ALLOWED_ORIGINS` now actually restricts cross-origin requests to `/admin/*` and `/api/*` (403 for unlisted origins). Requests whose `Origin` matches the dashboard's own host are always allowed, so the admin panel keeps working whatever the allowlist says. `AdminCorsGuard` existed before but had no call sites.
- **Rate limiting on admin and auth endpoints**: `/admin/*` and `/api/*` share the per-IP bucket configured by `RATE_LIMIT_PER_IP`; the credential endpoints (`/api/login`, `/api/token`, `/api/token/refresh`, `/api/password-reset`) use a stricter fixed bucket of 20 requests per IP per minute and 300 per minute overall, which caps the BCrypt work an unauthenticated client can force.
- **Role changes and deletions take effect immediately**: the user's role and active flag are re-read from the database on every authenticated request (session and JWT). A demoted admin loses write access on their next call; a deleted or deactivated user gets 401.
- **Dependabot** for Gradle, GitHub Actions and the Docker base images (`.github/dependabot.yml`).
- 4 integration tests covering the above (`AuthHardeningTest`).

### Changed

- **User-agent parsing** moved from `eu.bitwalker:UserAgentUtils` (LGPL-3.0, unmaintained since 2018) to `com.github.ua-parser:uap-java` (Apache-2.0). Browser, OS and device labels now use the same vocabulary as the demo data and the dashboard icons (`Chrome 120`, `macOS`, `Desktop` / `Mobile` / `Tablet` / `Bot`) instead of enum constants such as `CHROME 120`, `MAC_OS_X`, `COMPUTER`. Existing rows keep their old labels.
- **Password reset** compares the server salt in constant time (`MessageDigest.isEqual`).
- **Docker build** uses the Gradle wrapper instead of a separately pinned Gradle image, so Docker, CI and local builds run the same Gradle version.
- **Accessibility**: the create-project, onboarding, sign-out and delete-project dialogs now carry `role="dialog"`, `aria-modal` and `aria-labelledby` like the other modals.
- **`.env.example`** explains the accepted `ADMIN_PASSWORD` formats (plain text is hashed on first start; `$2a$`/`$2b$` hashes are stored as-is; `$2y$` is rejected).
- **Public docs**: `docs/configuration.md` now lists the real PostgreSQL variables (`DB_PG_HOST`, `DB_PG_PORT`, `DB_PG_NAME`, `DB_PG_USERNAME`, `DB_PG_PASSWORD`) and documents the SMTP variables for email reports.

### Fixed

- **Integration tests were silently skipping their bodies**: the analytics unit tests reloaded the `ServiceManager` singleton with a throwaway database whose only user was `test`; every integration test that ran afterwards failed to log in as `admin`, hit the five-failure lockout, and returned early through `if (login().status != OK) return@testApplication`. Not a single authenticated request was being exercised. The analytics tests now release the services in `@After`, tests run with `KTOR_DEVELOPMENT=true` so the session cookie is sent over plain HTTP, and the test JVM gets 1 GB of heap (each test app re-scans the classpath for the AsyncAPI plugin).
- `DataAnalysisUtilsTest` wrote its SQLite files to the repository root instead of `test-dbs/`.
- **Detekt gate is green again**: 12 findings that had accumulated unnoticed (CI never reached the detekt step while tests were failing) are fixed: unused catch parameters, two `IllegalStateException` throws replaced by `error()`, two overly broad catches narrowed, and the email-report and revenue routes split out of `adminFeatureRoutes` (cyclomatic complexity 123 → under the 120 threshold).

- **Test suite on a clean checkout**: the Gradle `test` task now creates the gitignored `test-dbs/` directory before running. Previously 41 of 296 tests failed on every fresh clone and on every CI run on `main`, because the SQLite test databases had nowhere to be written.
- **Time-of-day dependent test**: `DataAnalysisUtilsTest."generateContributionCalendar assigns intensity levels correctly"` inserted 40 events starting at "now minus one day", so a run after 23:20 spilled 8 of them past midnight into the next day. Timestamps are now anchored to midday.
- **HTML escaping of visitor-supplied strings (defense in depth)**: custom event names in the Raw Events modal and event names / traffic sources in the Revenue section are now passed through `Utils.escapeHtml()` like every other rendered field. Note: `/collect` already rejects `<` and `>` in event names and UTM fields (`InputValidator`), so this path was not exploitable through the tracker; the escaping protects against values that reach the database by other means.

### Changed

- **Docker image publishing is gated on tests**: the `Docker Publish` workflow now runs `./gradlew test detekt` in a `test` job that the `docker` job depends on. Previously a red test run still published an image to GHCR.
- **`stats.db` removed from version control**: the development database is no longer tracked (it remains gitignored and on disk). It still exists in earlier commits; see `GOING_LIVE.md` item 1 for the rotation and history-purge steps.

---

## [1.2.1] - 2026-03-15

### Changed

- **Landing page design alignment**: Replaced the indigo/slate color scheme with the burnt amber/stone palette used by the admin panel, login page, and setup wizard — the entire application now shares a single consistent design system.
- **Landing page nav cleanup**: Removed the theme toggle button and login/dashboard button from the navigation bar on the main landing page and all sub-pages (contact, how-it-works, privacy-policy, setup-guide, terms).
- **Landing page link fixes**: All internal links across landing sub-pages changed from absolute `/landing/`-prefixed paths to relative paths (e.g. `how-it-works.html` instead of `/landing/how-it-works.html`), fixing broken navigation. CSS and JS references in sub-pages also corrected.
- **Footer link casing**: Footer link text changed from Title Case to sentence case (e.g. "Terms of Service" → "Terms of service").

---

## [1.2.0] - 2026-03-08

### Added

- **API key rotation**: New `POST /admin/projects/{id}/rotate-api-key` endpoint generates a fresh 64-character hex key, invalidates all related caches, and returns the new key immediately. A **Rotate key** button in the Settings modal surfaces this to the UI — the old key stops working the moment the rotation completes.
- **Retention preview**: New `GET /admin/projects/{id}/retention-preview?days=N` read-only endpoint returns the number of events that would be deleted and the date of the oldest event, so users can preview the impact before enabling auto-retention.
- **Tracker offline queue**: `tracker.js` now buffers failed `sendBeacon` events in `localStorage` under the key `mn_queue` (max 20 entries). The queue drains automatically on the next page load and whenever the browser fires the `online` event, closing the data-loss gap during brief network outages.
- **Focus trap utility**: `Utils.focusTrap` in `utils.js` traps Tab / Shift+Tab focus within open modals, closes on Escape (calling the provided callback), and restores focus to the triggering element on close. Wired to all five primary modals (demo-data, settings, raw-events, goals, funnels) via a `MutationObserver` release strategy so every close path is covered without duplicating teardown logic.
- **Modal accessibility**: `role="dialog"`, `aria-modal="true"`, and `aria-labelledby` attributes added to the five primary modals. All modal close buttons now carry `aria-label="Close"`.
- **OpenAPI spec completion**: 20+ previously undocumented endpoints now covered — JWT auth (`/api/token`, `/api/token/refresh`, `/api/password-reset`), user management (`/admin/users/*`), project utilities (`/admin/projects/{id}/rotate-api-key`, `/admin/projects/{id}/retention-preview`, `/admin/projects/{id}/realtime-count`, `/admin/projects/{id}/globe`, `/admin/projects/{id}/demo-data`), goal/funnel/segment DELETE + goal PUT, and all four widget endpoints. Added `securitySchemes` (sessionCookie + bearerAuth), top-level `security` declaration, and shared `Unauthorized` / `Forbidden` response components.
- **8 cross-project isolation tests**: New tests verify that analytics, goals, live feed, and segment endpoints for unknown project IDs return correct status codes; that `POST /collect` rejects API keys belonging to deleted projects; and that the rotate-key endpoint enforces authentication.
- **4 new documentation pages**: `docs/tracker-reference.md` (complete `tracker.js` API — all `data-*` attributes, `MiniNumbers.track()`, offline queue, SPA behavior), `docs/widgets.md` (widget embed reference with all configuration attributes), `docs/troubleshooting.md` (common issues guide — GeoIP, CORS, 401 loop, SQLite lock, Docker volume permissions), `docs/upgrading.md` (upgrade / migration guide for Docker and JAR deployments). All four pages added to the Jekyll navigation.

### Changed

- **Dark mode WCAG contrast**: `--color-text-muted` in the `[data-theme="dark"]` block changed from `#94a3b8` to `#a8b8cc`, raising the contrast ratio above the WCAG AA minimum of 4.5:1 on dark-themed backgrounds.
- **Filter warning visible state**: `.filter-warning` now renders with a translucent warning-coloured background, a matching border, `border-radius`, and padding. Previously it was styled with text colour and flex layout only, making it nearly invisible against the dashboard background.
- **Stat card responsive grid**: `.grid-cols-4` now explicitly collapses to a 2-column layout at ≤900 px and a 1-column layout at ≤480 px, preventing overflow on mobile devices.
- **Goals, funnels, segments pagination**: All three list endpoints now accept optional `?page=&limit=` query parameters and return a `PaginatedResponse` wrapper when provided. Omitting the parameters preserves the existing flat-array response for backward compatibility.
- **Demo data loading state**: The "Generate" confirmation button is disabled and shows "Generating…" during the API call, then restored in the `finally` block — matching the loading-state pattern used by other async buttons in the codebase.
- **Public documentation expanded**: `docs/features.md` — added full sections for Webhooks, Email Reports, Revenue Tracking, User Management / RBAC, and Embeddable Widgets. `docs/index.md` — expanded "What can you track?" with UTM campaigns, scroll depth, outbound links, file downloads, and revenue. `docs/configuration.md` — added rate-limiting behavior section and PostgreSQL-specific environment variable table (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_PG_MAX_POOL_SIZE`). `docs/dashboard-guide.md` — added Rotate API Key instructions under Project Settings.

---

## [1.1.1] - 2026-03-08

### Changed

- **Filter bar redesign**: The date range text is now displayed in a large, bold, primary-coloured style to make the selected period immediately prominent. The "Filter by" dropdown group is pushed to the far right end of the bar via `margin-left: auto`. The vertical divider between the two sections has been removed.
- **Raw Events modal**: The Path column is now 20% wider and the Location column is 15% wider to reduce truncation. The event-type and sort dropdowns are always displayed side-by-side (removed `flex-wrap: wrap`). Column widths are now defined with a `<colgroup>` for consistent layout.
- **Overview stat cards**: Removed the decorative set_3 background illustration images that had been added to the four primary stat cards. The cards are now clean and uncluttered.
- **Empty and error states**: All chart, table, and list containers now display a contextual set_3 illustration above the message text when empty or when an error occurs. Error states use a research illustration; each data section uses a purpose-matched icon (pages, geography, funnels, goals, etc.).
- **"vs previous period" label**: Wrapped in a dedicated `.comparison-period-label` span, styled at `font-size-xs`, `font-weight-normal`, and `color-text-muted` — visually subordinate to the percentage value.
- **Table row hover color**: `--table-row-hover` now maps to `--color-bg-hover` (light: `#e2e8f0`, dark: `#475569`) in both themes, making hover clearly distinguishable from the even-row zebra stripe (`--color-bg-tertiary`).
- **Header layout**: Removed `flex-wrap: wrap` from `.dashboard-header`. The h1 now truncates with `text-overflow: ellipsis` instead of wrapping to a second line. The right-side controls div uses `flex-shrink: 0`.
- **Header buttons**: "Generate demo data" and "Raw events" buttons now carry `white-space: nowrap; flex-shrink: 0` to prevent text wrapping or compression in narrow viewports.
- **Theme toggle**: Corrected visual rendering — added `display: inline-flex`, `overflow: hidden` (clips slider to pill shape in Safari), `flex-shrink: 0`, and `line-height: 1` on the slider circle to properly center the Remixicon sun/moon icon.
- **App icon**: Logo in the sidebar header is 20% larger (52 px) and horizontally centered. The sidebar header now uses `text-align: center` to align the subtitle text as well.
- **Demo project button**: The "Demo project" button in the sidebar footer is automatically hidden after `loadProjects()` detects a project named "Demo project" already exists. It reappears when the list is empty.

### Fixed

- **Demo data generation (500 error)**: `seedDemoGoalsFunnelsSegments` was executing all database operations (three `selectAll`/`count` queries and three sets of `insert` statements) outside any Exposed `transaction {}` block. This caused an `IllegalStateException: No transaction in context` at runtime, which the route handler surfaced as a 500. Fixed by converting the function to an expression body using `= transaction { … }`.

---

## [1.1.0] - 2026-03-05

### Added
- **Premium Setup Flow**: Animated "Constellation" background using HTML5 Canvas, glassmorphism UI, and streamlined step-by-step configuration.
- **Privacy Modes**: Introduced STANDARD, STRICT, and PARANOID modes to control data collection granularity.
- **Unified State Management**: Dashboard-wide handling for Loading (skeletons), Empty (helpful hints), and Error (retry mechanisms) states.
- **Enhanced Time Analysis**: Merged Heatmap and 365-day Calendar with a Zoom in/out feature and Peak Activity cards.
- **New Summary Cards**: Busiest Day, Peak Hour, and Top Month metrics added to the dashboard.
- **Dark Mode Support**: Full theme-awareness for the setup flow, login page, and background animations.
- **JWT Authentication**: Programmatic API access now supported alongside traditional session-based auth.

### Changed
- **Modular Routing Architecture**: Massive `Routing.kt` refactored into 7 feature-based modules under `se.onemanstudio.routing`.
- **Package Restructuring**: Unified project structure under the `se.onemanstudio` package for better convention compliance.
- **Dashboard Grid**: Standardized all primary and revenue stat cards into a consistent 4-column layout.
- **Icon Bar Charts**: Switched Referrers, Countries, and Custom Events to a premium proportional bar style.
- **Raw Events Viewer**: Improved data formatting with monospace paths, country flags, and intelligent timestamping.
- **Landing Page Separation**: Moved the landing page outside the main source code for easier standalone deployment. Sub-page links use relative paths for portability.

### Fixed
- **Tracker Leak**: Fixed a memory/network leak in `tracker.js` related to visibility change heartbeats.
- **401 Fallback**: Corrected authentication logic to properly fall back to `.env` credentials when the database is empty.
- **Config Sync**: Ensured `.env` changes are picked up immediately by the application without a restart.
- **Security**: Masked API keys for `VIEWER` roles to prevent unauthorized data spoofing.
- **CSP Headers**: Updated Content Security Policy to allow Google Fonts and trusted CDNs.
- **Static Analysis**: Resolved all `detekt` warnings, including line lengths, generic exceptions, and unused parameters.

## [1.0.0-beta] - 2026-02-27

### Added

- **Webhooks System**
  - Real-time HTTP POST notifications for goal conversions and traffic spikes
  - HMAC-SHA256 signed deliveries with configurable secret per webhook
  - Admin panel UI with CRUD, delivery log, test delivery, and setup guide
  - Webhook trigger engine with Caffeine-cached active webhooks per project
  - 3-attempt retry with exponential backoff for failed deliveries
  - OpenAPI documentation for all webhook endpoints

- **Email Reports**
  - Scheduled HTML analytics reports via SMTP (Daily / Weekly / Monthly)
  - SMTP service with Jakarta Mail, async delivery, and hourly scheduler
  - Customizable templates: subject line, header/footer text, section selection
  - Admin panel with SMTP status indicator, setup guide, and report management
  - Support for Gmail, SendGrid, Mailgun, Amazon SES, and self-hosted SMTP

- **Revenue Tracking & Attribution**
  - Revenue tracking via existing `MiniNumbers.track()` API with `revenue` property
  - Revenue aggregation engine: total revenue, AOV, revenue per visitor, with period comparison
  - Revenue by event breakdown (purchases, subscriptions, etc.)
  - Revenue attribution by referrer source and UTM campaign
  - Dashboard revenue section with stat cards, bar charts, and attribution table
  - In-dashboard guide with setup examples, framework integrations, and best practices
  - Demo data generator enhanced with realistic revenue events

- **Landing Page**
  - Integrated landing page served at `/landing/` via Ktor static resources
  - Root `/` redirects to landing page when services are ready
  - Particle canvas hero background with mouse-reactive animation
  - Gradient text effects and floating trust badges
  - Revenue Intelligence showcase section with animated mockup
  - Updated content: 288 tests, v1.0.0-beta version, 2026 copyright

### Changed

- Test suite expanded from 250 to 288 tests (18 new for webhooks, email, revenue)
- Demo data generator now produces revenue-bearing purchase events
- Root URL redirects to landing page instead of login
