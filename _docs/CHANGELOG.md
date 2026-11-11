# Changelog

All notable changes to Mini Numbers will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Labs.** Funnels, segments, revenue, webhooks, email reports and embeddable widgets are now experimental and off
  by default: their dashboard parts are hidden and their API answers 404. Turn any of them on with
  `LABS=funnels,webhooks,…` (see the configuration page). Nothing stored is removed.

### Security

- **Widgets are off by default.** With them on, anyone who copied the project key from a site's HTML could read
  that site's top pages and visitor counts. They now need `LABS=widgets`.
- **Quick Setup no longer uses a fixed password.** It generated `admin123456`, turned on development mode and
  submitted by itself. It now generates a strong password, shows it once on the review step with a copy button,
  turns on development mode only over plain HTTP, and waits for you to launch.
- The globe library and its textures are pinned to exact versions, and the script is checked with an SRI hash.

### Fixed

- The "Filter by" values read "(undefined)" instead of their counts.
- While a country was opened in the countries card, changing the period left the views chart, heatmap and the
  other charts after it on the old period. The country's regions now follow the period too, and switching
  project closes the country.
- **Generate demo data** was offered on every project, mixing sample visits into real numbers. It is now only on
  the demo project, and the server refuses more than 3,000 events or more than 365 days.
- Deleting a project reported success even when the server refused it.

### Changed

- **Light-theme colours now meet WCAG AA.** Success text is emerald-700 (`#047857`), danger text red-600
  (`#dc2626`) and muted text stone-500 (`#78716c`); the sidebar subtitle no longer fades to 70 % opacity. The
  dark theme keeps its green, gets red-400 (`#f87171`) for danger text and a lighter muted sidebar colour. The
  accessibility test now checks colour contrast in both themes.
- Exposed 1.5.1 → 1.6.0. No schema change; verified by starting on a database created by 1.5.0.
- The MaxMind GeoLite2 attribution is now also on the Docker image labels and the configuration page, with the
  build date of the bundled database (2026-01-30).

## [1.5.0] - 2026-11-03

The first tagged release (1.3.0 was published as source only), and the result of the November audit of
the working base: three critical, five high and six medium findings fixed, with the suite grown to
355 JVM and 17 browser tests.

**Upgrading**

- Everyone is signed out once: the session cookie is now signed, and sessions end on logout everywhere.
- SQLite now runs in WAL mode, so `stats.db-wal` and `stats.db-shm` appear next to the database. Back up with the
  service stopped or with `sqlite3 stats.db ".backup copy.db"`.
- Email reports created before this version have timezone UTC, so an existing "send at 8" report now goes out at
  08:00 UTC. Recreate it from the dialog to use your own zone.
- The users table gains a `sessions_valid_from` column, added automatically on first start.

### Security

- **Exposed 1.5.0 → 1.5.1**: fixes CVE-2026-108474 (SQL injection through string arguments).
- **PostgreSQL driver 42.7.13 → 42.7.14**: fixes CVE-2026-107314 (`requireAuth` silently not enforced) and a second advisory in the same release.
- Kotlin 2.4.20 → 2.4.21 (compiler, serialization plugin, test library).

### Added

- **Webhooks can be paused and resumed** from the webhooks dialog (`PUT /admin/projects/{id}/webhooks/{webhookId}` with `{"isActive": false}`); a paused webhook receives nothing until resumed. The flag existed but nothing could set it.
- **Users can be deactivated and reactivated** with `PUT /admin/users/{id}/active`. A deactivated user's session and tokens stop working on the next request, their refresh tokens are revoked, and they cannot sign in until reactivated. Deleting a user now revokes their refresh tokens too. An admin cannot demote, deactivate or delete their own account, so there is always an active admin left.
- **One-file install with automatic HTTPS**: `docker-compose.yml` now pulls the published image and runs Caddy in front of it. Point a domain at the server, put `DOMAIN=…` in `.env`, run `docker compose up -d`, and the certificate is obtained and renewed for you. The PostgreSQL compose file works the same way.
- **`TRUST_PROXY` setting**: behind a reverse proxy (Caddy, nginx, Railway, Render, Fly.io, server panels) set `TRUST_PROXY=true` and visitor IPs are read from `X-Forwarded-For`. The compose files, `fly.toml` and `render.yaml` set it.
- **Downloadable JAR**: tagging a version publishes `mini-numbers.jar` on the GitHub Release (`release.yml`), and the Docker image gets a `latest` tag on version tags.
- **systemd unit** in `deploy/mini-numbers.service` for running the JAR without Docker.
- **Install guide rewritten** in plain language (`docs/deployment.md`), with one path each for a VPS with Docker, server panels (Coolify, Dokploy, CapRover, Easypanel), Railway/Render/Fly.io, cPanel hosting, and plain Java. `_docs/DEPLOYMENT.md` now points to it instead of repeating it.

### Changed

- **Documentation rewritten and cut down.** The public docs are now eight short pages in plain language (install, getting started, dashboard, configuration, privacy, developers, troubleshooting, plus the comparison), checked line by line against the code. Wrong claims were removed or corrected: what Paranoid and Standard mode store, the session ID, `KTOR_DEVELOPMENT` and `ALLOWED_ORIGINS`, widget parameters and responses, Slack/Discord, "zero-restart config", SQLite WAL and test counts. Duplicated or historical files (`GOING_LIVE.md`, `GEMINI.md`, `_docs/TODO.md`, `EVALUATION.md`, `PRIVACY.md`, `DASHBOARD_GUIDE.md`, `SECURITY.md`, `DEPLOYMENT.md`, and six old docs pages) are gone; their open items live in `_docs/ROADMAP.md`, which now also ranks feature ideas. A short root `SECURITY.md` explains how to report vulnerabilities.
- **Comparison page refreshed** (`docs/comparison.html`, checked 2 Nov 2026): adds Liwan, Medama and Counterscale, shows Plausible as its self-hosted CE with cloud-only features marked, and lists what each tool needs to self-host and when it last released.
- The setup wizard's help text for Paranoid mode and development mode now says what they actually do; `.env.example` no longer enables development mode.
- Browser tests take `E2E_PORT` when port 8080 is busy, and include the dashboard's tracking snippet exactly as shown.
- `server.log` is no longer tracked in the repository and `*.log` files are ignored.
- **SQLite runs in WAL mode with a five-second busy timeout**, so dashboard reads no longer block incoming events and a brief lock waits instead of failing with "database is locked". Two companion files (`stats.db-wal`, `stats.db-shm`) now sit next to the database; the install guide's backup section says how to copy them safely.

### Fixed

- **Timestamps were off by hours when the server and the viewer were in different time zones.** The API sent bare wall-clock times that the browser read as its own local time. Every timestamp now carries the server's UTC offset, so the dashboard, Raw events and exports show the right instant anywhere. Hour and day buckets (charts, heatmap, calendar) stay in the server's zone, which the compose files now expose as `TZ`; the contribution calendar no longer mixes UTC and local dates when drawing its grid.
- **A goal-conversion webhook fired on every matching event**, so a visitor reloading the thank-you page produced a call each time. It now fires once per goal per session, which is how the conversion stats count it.
- **A failing webhook endpoint blocked other deliveries**: retries slept on one of the two delivery threads. Retries are now scheduled 5 and 10 seconds later without holding a thread. They are still kept in memory, so a restart drops retries that were waiting.
- Deleting a webhook from the dialog showed "Failed to delete webhook" after the dead dashboard-section renderer was removed, although the delete had succeeded.
- **Email reports went out at the server's hour, not the report's**, ignored the section choices, rendered project names, page paths and custom text without escaping, and a test send counted as that day's scheduled send. The scheduler now evaluates "send at 8" in the report's own timezone (the dialog sends the admin's browser zone, and unknown zones are rejected), only the chosen sections are rendered (an events table was missing), all text is escaped, and test sends no longer block the scheduled one.
- **`/health` answered 200 even when services had failed to start, and never touched the database.** It now returns 503 `unhealthy` in that case or when a database probe fails, 200 `healthy` with `"database":"ok"` once everything runs, and 200 `ok` while the setup wizard is still needed. The documented response matches what is sent.
- **Signing out only cleared the cookie in that browser**: a copy of the cookie kept working until its 4-hour limit, and a password reset claimed to have ended all sessions while cookies and access tokens stayed valid. The users table now records a per-user cut-off (`sessions_valid_from`, added automatically on upgrade) that logout and password reset set; every cookie issued before it is rejected, so signing out ends the session on every device. The reset also revokes only that account's refresh tokens instead of everyone's, and says what it did.
- **The 4-hour session lifetime never applied.** The cookie did not store its creation time (the serializer omits properties left at their default), so every request read it as "just created" and a session lasted the cookie's full 7 days. The creation time is now always written; a cookie older than 4 hours is rejected.
- **A refresh token presented twice now revokes its whole family.** Rotation marked the used token but left its successor valid, so a stolen copy only produced a 401 while the thief's chain stayed alive.
- **A deactivated account whose username matched `ADMIN_USERNAME` could still sign in** with the `.env` password, because the login fell back to the config credentials whenever no *active* database user matched. The fallback now applies only when the username has no database row at all.
- **`/admin/me` and `/admin/privacy` sat outside the admin guard**, so they skipped the `ALLOWED_ORIGINS` check and the admin rate limit. They are inside it now (and reachable at `/api/me` and `/api/privacy` for token users). `/admin/me` reports the role the account has now rather than the one stored in the cookie or token at login, and `/admin/privacy` reads the running configuration instead of re-reading `.env`.
- **Deleting a project left its webhooks, webhook deliveries and email reports behind** on SQLite, and failed on PostgreSQL. The delete now removes them too, and SQLite runs with foreign keys enforced, so an incomplete cascade fails loudly instead of orphaning rows. `./gradlew reset` clears every table.
- **The revenue section of the exported CSV report was always empty**, because the export read a field the revenue module never sets. It now lists total revenue, transactions, average order value, revenue per visitor, and the per-event and per-source breakdowns. Text cells in the report and the raw-events export are quoted, so a path or label with a comma or quote no longer breaks a row.
- **Raw-event exports stopped at 1 000 rows without saying so.** The limit is 10 000, and the dashboard shows a warning when a project has more events than that.
- **Projects at the end of a long sidebar list could not be clicked**: the footer with the Demo, New project and Sign out buttons was laid over the list, and the space reserved for it was sized for one button. The footer now sticks to the bottom and the list scrolls above it.
- Dashboard code that targeted elements which do not exist in the page (a regions chart, a globe visitor counter, webhook and email-report sections) is gone; the dialogs that replaced those sections are unchanged. A duplicate `hideLoadingState` was removed.
- **A visitor could inject markup into the dashboard through the referrer**, and an admin could do the same through project, goal, funnel and segment names, webhook URLs and report recipients. `Utils.escapeHtml` now escapes quotes as well, the toast shows its message as text, the Leaflet country popup and the remaining unescaped fields are escaped, the project/goal/funnel/segment row buttons no longer build inline `onclick` handlers from names, and `/collect` rejects referrers containing whitespace, quotes, angle brackets or backticks (browsers percent-encode those, so no real referrer is lost). A browser test plants a script-carrying name and checks that it renders as text and that the row actions still work.
- **The session cookie could be forged.** It was stored as readable JSON and only checked against the users table, so anyone who knew a username could write a cookie by hand and get in without the password. The cookie is now signed with HMAC-SHA256 using a key derived from `SERVER_SALT`; unsigned or edited cookies are rejected. Everyone is signed out once after upgrading.
- **API tokens issued right after the setup wizard were rejected until a restart**, and the cookie signer would have had the same problem: both keys were fixed when the server started, before the wizard had produced a salt. They are now looked up per request.
- **The "Create project" dialog showed a tracking key that did not exist.** Since March the dialog generated its own key while the server generated another and kept that one, so the snippet a new user copied never collected anything and "Go to dashboard" did nothing. The dialog now shows the key the server stored, the misleading "pre-generated key" field on step 1 is gone, and the browser test loads a page with the exact snippet shown and checks that the page view arrives.
- **`POST /admin/projects` now returns the new project's `id` and `apiKey`** instead of an empty 201. Fifteen integration tests (webhooks, email reports, revenue) had been reading the id from that body, getting nothing, and returning early as if they had passed.
- **Tests no longer exit early.** 97 checks of the form `if (login failed) return` across five test files are now assertions, so a broken login or project creation fails the build instead of hiding.
- **The tracking snippet recorded nothing when Mini Numbers runs on a different domain from the website** (the usual setup). Without `data-api-endpoint`, the tracker sent events to the website's own address instead of the Mini Numbers server. It now defaults to the server it was loaded from; snippets already on websites pick this up automatically, because the script is served by Mini Numbers.
- **Visitor counts behind a proxy**: every visitor used to get the proxy's IP address, so unique visitors collapsed to one, countries were missing and the per-IP rate limit acted as a global one. Fixed with `TRUST_PROXY=true` (see above).
- **Docker installs forgot their setup**: the wizard saved its settings in `/app/.env`, outside the data volume, so after an upgrade or restart on Fly/Render/Railway the wizard came back with a new salt. The container now runs from `/app/data`, so settings and database live in the same volume.
- **`PORT` and `SERVER_PORT` were ignored**: the server always listened on 8080. It now listens on `PORT` (set by hosting platforms), then `SERVER_PORT`, then 8080, and the startup log shows the real address.
- **Silent login loop over plain HTTP**: signing in on an `http://` address other than `localhost` succeeded but the browser dropped the secure-only cookie and sent you back to the login page. The page now explains that the site needs HTTPS.
- **The JVM now receives `docker stop`**, so shutdown runs cleanly; the image no longer stores the JAR twice.
- **Setup wizard rendered without styling on a fresh install**: the shared `/css`, `/js` and `/assets` files were only served once setup was complete, so the wizard's stylesheets, theme script and logo returned 404 before it. They are now served in every state. Covered by a test that requests every local asset the wizard links to with only the setup routes installed.

---

## [1.3.0] - 2026-10-08

### Added

- **AI assistant referrals card**: the Overview now has a stat card counting page views referred by AI assistants (ChatGPT, Claude, Perplexity, Gemini, Copilot, Grok, Meta AI, Mistral, DeepSeek, You.com, Poe, Phind, Duck.ai), with the share of all referred visits, the top assistants, and the usual previous-period comparison. Classification happens at report time from the stored referrer host (`utils/AiTrafficUtils.kt`), so nothing new is stored and historical data is covered. Report JSON gains `aiReferrals`, `aiReferralVisits` and `referredVisits`. The demo generator now includes assistant referrers. Only referrals are covered: AI crawlers never execute the tracker, so a crawler view would be empty by construction.
- **Privacy posture chip**: a chip next to the date range reads "Standard · visitors forgotten after 1 day" (mode and rotation window from the new read-only `GET /admin/privacy`) and opens a dialog listing, in plain words, what this instance stores per page view, what it never stores, and when it forgets (hash rotation, retention). Adapts to STRICT and PARANOID.
- **Origin allowlist enforced on the admin API**: `ALLOWED_ORIGINS` now actually restricts cross-origin requests to `/admin/*` and `/api/*` (403 for unlisted origins). Requests whose `Origin` matches the dashboard's own host are always allowed, so the admin panel keeps working whatever the allowlist says. `AdminCorsGuard` existed before but had no call sites.
- **Rate limiting on admin and auth endpoints**: `/admin/*` and `/api/*` share the per-IP bucket configured by `RATE_LIMIT_PER_IP`; the credential endpoints (`/api/login`, `/api/token`, `/api/token/refresh`, `/api/password-reset`) use a stricter fixed bucket of 20 requests per IP per minute and 300 per minute overall, which caps the BCrypt work an unauthenticated client can force.
- **Role changes and deletions take effect immediately**: the user's role and active flag are re-read from the database on every authenticated request (session and JWT). A demoted admin loses write access on their next call; a deleted or deactivated user gets 401.
- **Dependabot** for Gradle, GitHub Actions and the Docker base images (`.github/dependabot.yml`).
- 4 integration tests covering the above (`AuthHardeningTest`).
- 8 integration tests in `PrivacyRbacAndCacheTest`: each privacy mode's stored field set (STANDARD keeps browser/OS/device, STRICT drops city/region/coordinates, PARANOID stores nothing but the page view), viewer role read-only enforcement and API-key masking, the JWT issue → Bearer → refresh → replay-rejection flow, widget key authentication, stats-cache invalidation on collect and isolation between projects, and the 2048-character limit on custom-event properties.
- **Browser test suite** in `e2e/` (Playwright, 12 tests, Chrome): login and sign-out, project creation through the UI with the tracking snippet, demo-data dashboard with the Raw events dialog (focus trap, Escape, focus return) in both themes with screenshots, phone-width sidebar, the real `tracker.min.js` on a fake cross-site page (initial page view with UTM, SPA navigation, custom events with properties, heartbeats, offline queue and drain, outbound links and downloads), and axe WCAG 2 A/AA scans of the login page and dashboard in both themes. Runs in CI against the fat JAR; report and screenshots are uploaded as artifacts.
- **Comparison page** (`docs/comparison.html`): a standalone, interactive feature-by-feature comparison against seven competitors with the full feature list and the 1.3.0 notes, linked from the README and the docs index. `_docs/TODO.md` lists every open item with its blocker.
- **PostgreSQL CI job**: `Build & Test` now also runs the whole suite against a PostgreSQL 16 service container (`test-postgres` job), so the second supported database is exercised on every push for the first time.

### Changed

- **Dependencies brought up to date** (minor/patch): Kotlin 2.3.0 → 2.4.20, Ktor 3.4.0 → 3.6.0, Gradle 9.3.0 → 9.8.0, kotlin-asyncapi-ktor 3.1.3 → 3.2.4, logback 1.5.32 → 1.6.5, PostgreSQL driver 42.7.10 → 42.7.13, geoip2 5.0.2 → 5.2.0, Caffeine 3.2.3 → 3.3.0, Jakarta Mail 2.1.3 → 2.1.5, Angus Mail 2.0.3 → 2.0.5. **Held back on purpose**: `sqlite-jdbc` stays at 3.47.2.0 because with 3.53.x the driver's JDBC metadata no longer reports SQLite primary keys the way Exposed 0.56 expects, so `createMissingTablesAndColumns()` emits `ALTER TABLE … ADD PRIMARY KEY`, which SQLite rejects and which makes service initialisation fail on every database, new or existing. It will move together with the Exposed 1.x upgrade. Exposed and jBCrypt are deliberately left for their own changes.
- **Exposed 0.56.0 → 1.5.0** and **sqlite-jdbc 3.47.2.0 → 3.53.4.0** (the driver bump that had to wait for this). Mechanical migration: imports move from `org.jetbrains.exposed.sql.*` to `org.jetbrains.exposed.v1.core.*` (tables, column types, operators) and `org.jetbrains.exposed.v1.jdbc.*` (`Database`, `SchemaUtils`, `transaction`, `selectAll`/`insert`/`update`/`delete`); the comparison operators are top-level functions now, so every file that builds a `where` clause imports the core package; `ExposedSQLException` lives in `org.jetbrains.exposed.v1.exceptions`. Table columns switch from `uuid()` to `javaUUID()` because `uuid()` now yields Kotlin's `Uuid` type; the SQL type is unchanged, so existing databases open as before. Verified by booting the upgraded JAR against a database created by the old version (7,004 events intact).
- **jBCrypt 0.4 → password4j 1.8.4** (Apache-2.0, maintained) behind `core/Passwords.kt`. Existing `$2a$` hashes verify unchanged; `$2y$` hashes from Apache `htpasswd` are accepted now instead of locking the admin out.
- **HikariCP 5.0.1 → 7.1.0**. The pool is PostgreSQL-only and configured with core options whose behaviour is unchanged across the two majors; the Postgres path is not covered by automated tests yet (planned CI job), only the pool-initialisation failure path is.
- **User-agent parsing** moved from `eu.bitwalker:UserAgentUtils` (LGPL-3.0, unmaintained since 2018) to `com.github.ua-parser:uap-java` (Apache-2.0). Browser, OS and device labels now use the same vocabulary as the demo data and the dashboard icons (`Chrome 120`, `macOS`, `Desktop` / `Mobile` / `Tablet` / `Bot`) instead of enum constants such as `CHROME 120`, `MAC_OS_X`, `COMPUTER`. Existing rows keep their old labels.
- **Password reset** compares the server salt in constant time (`MessageDigest.isEqual`).
- **Docker build** uses the Gradle wrapper instead of a separately pinned Gradle image, so Docker, CI and local builds run the same Gradle version.
- **Accessibility**: the create-project, onboarding, sign-out and delete-project dialogs now carry `role="dialog"`, `aria-modal` and `aria-labelledby` like the other modals.
- **`.env.example`** explains the accepted `ADMIN_PASSWORD` formats (plain text is hashed on first start; `$2a$`/`$2b$` hashes are stored as-is; `$2y$` is rejected).
- **Version reported consistently**: `/health` and the API docs said `1.0.0` while the build said `1.0.0-beta`; both now read a single `AppInfo.VERSION` (1.3.0). README, landing page and docs carry the real test counts and new screenshots generated from a demo project (`e2e/tests/screenshots.spec.js`). GitHub issue templates and a private reporting path for the Code of Conduct added.
- **Tracker size claim corrected**: README, docs and landing page said 1.3 KB; the script is 4.9 KB minified and 1.9 KB gzipped, so they now say 1.9 KB (gzipped). The build's "minifier" only strips comments and whitespace.
- **Public docs**: `docs/configuration.md` now lists the real PostgreSQL variables (`DB_PG_HOST`, `DB_PG_PORT`, `DB_PG_NAME`, `DB_PG_USERNAME`, `DB_PG_PASSWORD`) and documents the SMTP variables for email reports.

### Fixed

- **Page views were counted as every event**: the dashboard's total page views, top pages, referrers, browsers, countries, time series, activity heatmap and contribution calendar counted heartbeats (one per 30 s while a tab is open), custom, scroll, outbound and download events as page views, so a five-minute visit showed as about ten views and the dashboard disagreed with the embeddable widgets, which filtered correctly. View-based metrics now count `pageview` events only; sessions, duration and bounce still use every event. Expect lower, correct numbers after upgrading.
- **Startup-time column defaults**: five `datetime` columns were declared with a literal default evaluated when the server started, which Exposed 1.5 then tried to alter on every boot (a warning on SQLite, an `ALTER COLUMN` on PostgreSQL). They use `clientDefault` now; every insert already sets them explicitly.
- **The tracker could not run in any browser**: `/tracker/tracker.min.js` was sent as raw bytes with no content type (`application/octet-stream`), and because every response also carries `X-Content-Type-Options: nosniff`, browsers refused to execute it on customer sites. It is now served as `application/javascript`. Found by the new browser suite; see `GOING_LIVE.md` for the full note.
- **Every real tracker beacon was rejected with 400**: `navigator.sendBeacon` posts as `text/plain`, which the JSON content negotiation on `/collect` did not accept ("Invalid request body format"). The endpoint now decodes the body text directly, whatever the Content-Type, and ignores unknown keys so older or newer tracker builds are never rejected.
- **The offline queue never engaged during an outage**: `sendBeacon` returns `true` even with no network and the browser then drops the request, so the queue, which only triggered on a refused beacon, was never used. The tracker now queues while `navigator.onLine` is false and drains on the `online` event, which is what the 1.2.0 notes claimed.
- **Dashboard accessibility (axe, WCAG 2 A/AA)**: the "Filter by" select had no accessible name, the project list was a `listbox` whose children were not options, and the eight dashboard sections carried `aria-expanded` on `<section>` elements where it is not allowed (the attribute now lives only on the header buttons; the section state is `data-expanded`). 16 colour-contrast failures in the light theme remain; see `GOING_LIVE.md`.
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
