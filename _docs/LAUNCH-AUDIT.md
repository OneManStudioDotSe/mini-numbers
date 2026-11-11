# Launch audit — 2026-11-11

What works, what gets fixed first, what is hidden for launch and what does not exist. It decides the scope of 1.6.0,
the first release meant for the public. Every **Ship** row has a passing test or a check that was driven by hand
on 2026-11-11 against the fat JAR built from `2d02626`.

**Verdicts:**
- **Ship**: works and is verified.
- **Fix, then ship**: works, with a confirmed defect to fix first (step 3 of the launch plan).
- **Labs**: hidden by default behind `LABS` (step 2).
- **Not built**: doesn't exist, and nothing may claim it does.
- **Fixed in 1.6.0**: was "Fix, then ship" or "Not built" on 2026-11-11 and has been fixed since; the notes keep the
  original finding.

## Status

Fixed after this audit (see the CHANGELOG, Unreleased): Quick Setup, widgets (now a lab, off), the labs switch
for the six Labs rows, the "Filter by" values, the country drill-down, the pinned globe, demo data only on the
demo project with a server-side limit, project delete reporting failures, Do Not Track / Global Privacy Control
(honoured), viewers seeing write controls, the dead Leaflet/chartjs-chart-geo loads, Google Fonts (Inter is
self-hosted), the Railway docs and the landing page's false claims. Android/iOS is roadmap idea #15.

Still open: the owner actions (rotate the leaked secrets on the live instance, refresh GeoLite2 with a MaxMind
download, enable Discussions) and the smaller notes at the end.

## Verification run (2026-11-11)

| Check | Result |
|---|---|
| `./gradlew test` (SQLite) | 355 tests, 0 failures |
| `./gradlew detekt` (JDK 21) | 0 issues |
| Playwright suite (`E2E_PORT=8090`) | 17/17 after a test fix, stable over 3 runs. `a11y.spec.js` scanned the dark theme while the theme-switch colour transition was still running and measured blended colours. It now waits for CSS transitions to finish. The real dark-theme contrast passes. |
| Scripted walk-through on the JAR | Login, demo project, all 8 sections in both themes (16 charts drawn), every period's stat cards equal the API's totals, globe, goal created in the UI, Raw events + CSV (500 rows), real tracker → `/collect` → events (pageview + custom), viewer role, logout. No 4xx/5xx responses and no console errors along the way. |
| Fresh install, no `.env` (port 8091) | `/` → `/setup`; `/admin-panel` and `/collect` answer 404 until setup is done. A 6-step wizard driven field by field writes `.env` and starts services without a restart; the new admin logs in; a second `/setup/api/save` gets 403. GeoIP falls back to the copy in the JAR. After a restart: `healthy`, login 200. |
| Production-mode headers (`KTOR_DEVELOPMENT=false`) | CSP, HSTS, `X-Frame-Options: DENY`, `nosniff` and Referrer-Policy are present. |
| Brute-force lockout | After 5 wrong passwords even the right one is refused. The lockout lives in memory, so a restart clears it. |

## Blockers before going live

1. **The setup wizard's "Quick Setup" button** (`setup/js/wizard.js:335`) sets the admin password to `admin123456`,
   turns on development mode (non-`Secure` cookie, no HSTS) and `ALLOWED_ORIGINS=*`, then submits by itself. The docs
   say it's for trying the app, but the button shows on every server. Whoever opens `/setup` on a public install
   could press it and leave the instance with a known password.
2. **Widgets expose stats to anyone.** An anonymous `GET /widget/toppages?key=<public tracking key>` returned 200 with the
   site's top pages. The key is in every tracked page's HTML. → Labs, off by default.
3. **Owner actions:** rotate the leaked secrets on the live instance, push the three local commits, refresh the GeoLite2
   file (from 2026-01-30), and enable Discussions.

## Feature table

### Install, setup, accounts

| Feature | Verdict | Evidence | Notes |
|---|---|---|---|
| Docker image + compose with Caddy (HTTPS) | Ship | CI: Docker health check and volume persistence check (`build.yml`) | No CI job runs the compose stacks themselves; GO-LIVE covers it on the server |
| Release JAR + systemd unit | Ship | `release.yml`; fresh-install run above | |
| Fly / Render | Ship | configs match `/app/data` and `PORT` | Fly stops idle machines, which pauses scheduled jobs (already in the docs) |
| Railway | Fixed in 1.6.0 | — | `railway.json` builds from the Dockerfile, but the docs deploy the GHCR image; it declares no volume and no `TRUST_PROXY` |
| Setup wizard (field by field) | Ship | `SetupWizardTest` (17), `SetupValidationTest` (29), fresh-install run | It writes `GEOIP_DATABASE_PATH=src/main/resources/...`; harmless because of the fallback to the copy in the JAR |
| Quick Setup button | Fixed in 1.6.0 | fresh-install run | Blocker 1 |
| Login / logout / session cookie | Ship | `AuthHardeningTest` (18), `auth.spec.js`, walk-through | Logout ends sessions on every device |
| JWT tokens + refresh rotation | Ship | `PrivacyRbacAndCacheTest` | |
| Brute-force lockout, credential rate limit | Ship | `AuthHardeningTest`, curl run | |
| Password change (`/api/password-reset`) | Ship (API only) | `AuthHardeningTest` | Only for the `.env` admin; no UI or email. Docs should call it a password change |
| Extra users + viewer role | Ship (API only) | `PrivacyRbacAndCacheTest`, walk-through | No user screen. Viewers saw "New project" and other write buttons that answered 403; hidden since 1.6.0 (`auth.spec.js`). Launch as single-admin |
| Self-service signup, OAuth/OIDC, 2FA | Not built | — | Roadmap ideas #10, #11 |

### Collection and privacy

| Feature | Verdict | Evidence | Notes |
|---|---|---|---|
| Tracker: pageviews, SPA, heartbeats, scroll, outbound, downloads, custom events, offline queue | Ship | `tracker.spec.js` (6), walk-through | |
| `/collect` validation + rate limits | Ship | `CollectEndpointTest` (17), `InputValidatorTest` (31), `xss.spec.js` | |
| Privacy modes (Standard / Strict / Paranoid) + chip | Ship | `PrivacyRbacAndCacheTest`, `dashboard.spec.js` | |
| Visitor-hash rotation | Ship | `SecurityUtilsTest` (unit only) | No end-to-end test across a rotation window (reads the wall clock) |
| Data retention purge | Ship (untested) | — | No test; check `DATA_RETENTION_DAYS` on your own server before advertising it |
| Do Not Track / Global Privacy Control | Fixed in 1.6.0 | `tracker.spec.js` | Found ignored; both are now honoured (opt out with `data-respect-privacy-signals="false"`) |
| Android / iOS apps | Not built | — | Roadmap idea #15. | No SDK or app fields. A native app could call `POST /collect` by hand, but its User-Agent is parsed as "Unknown" and nothing documents it. Say "websites" everywhere |

### Dashboard

| Feature | Verdict | Evidence | Notes |
|---|---|---|---|
| Stat cards, sparklines, vs-previous-period | Ship | walk-through (card = API for 24h/3d/30d/365d), `DataAnalysisUtilsTest` (26) | |
| AI referrals card | Ship | `AiTrafficTest`, `dashboard.spec.js` | |
| Views over time, top/entry/exit pages, scroll, outbound/downloads, referrers, UTM | Ship | walk-through | `utmTerm`/`utmContent` are stored but never shown |
| Countries chart + city drill-down | Fixed in 1.6.0 | walk-through | While drilled into a country, a period change leaves the time series and the charts after it stale (24h kept the 7d series: 8 points instead of 4). `admin.js:1099` returns early |
| Globe | Fixed in 1.6.0 | walk-through (renders) | `globe.gl` and the `three-globe` textures load from jsdelivr with no version and no SRI hash (`globe.js:33,95,235`) |
| Browsers / OS / devices (doughnut/bar/radar) | Ship | walk-through | |
| Heatmap, peak times, 365-day calendar | Ship | walk-through | |
| Custom events | Ship | walk-through | Event properties aren't shown anywhere |
| "Filter by" bar | Fixed in 1.6.0 | walk-through | Every value reads "Firefox (undefined)" (`admin.js:1555` uses `item.count`; the field is `value`). Only narrows that dimension's own chart, as its warning says |
| Real-time feed + counter | Ship | walk-through | For the demo project it shows random fake data |
| Goals | Ship | walk-through (UI create, API 201), `GoalsFunnelsSegmentsTest` | No edit in the UI, although `PUT` exists |
| Raw events + CSV export | Ship | `export.spec.js`, walk-through | CSV only (no JSON). The raw-events type filter lacks scroll/outbound/download |
| Project create / rename / rotate key / delete | Fixed in 1.6.0 | `dashboard.spec.js`, `AdminEndpointTest` (23) | Delete ignores a failed response. The "Loading..." rename race didn't reproduce (the name comes from cache first); low priority |
| Demo project + "Generate demo data" | Fixed in 1.6.0 | walk-through | The button shows on real projects too and also seeds goals/funnels/segments there; the server doesn't cap `count` |
| Onboarding checklist | Ship | — | Only "create project" ever ticks; the other three steps are static |
| Theme toggle, light and dark | Ship | `a11y.spec.js` (axe WCAG AA, both themes), screenshots | |
| Settings dialog (formats, heatmap colours) | Ship | walk-through | Saving always re-sends the project name |

### Hidden for launch (Labs)

| Feature | Verdict | Evidence | Why hidden |
|---|---|---|---|
| Embeddable widgets | Labs | one status-code test | Blocker 2; no UI |
| Funnels | Labs | API: `GoalsFunnelsSegmentsTest` | No UI test, no edit |
| Segments | Labs | API: `GoalsFunnelsSegmentsTest` | No UI test, no edit; delete ignores a failed response |
| Revenue | Labs | `RevenueAnalysisUtilsTest` (8), CSV column | No endpoint test |
| Webhooks | Labs | CRUD: `WebhookEndpointTest` (6) | Delivery, signing and retries untested; retries lost on restart |
| Email reports | Labs | `EmailReportEndpointTest` (9), rendering | Scheduler and real send untested; needs SMTP |

### Public demo, landing, docs

| Item | Verdict | Notes |
|---|---|---|
| Public demo instance | Not built | Roadmap M4. Launch with screenshots + "try it locally" instead |
| Landing page (`landing/`) | Fixed in 1.6.0 | Claims v1.3.0, "327 tests", a 5-step wizard (it has 6), Slack/Discord webhooks, Stripe/Shopify/WooCommerce; marks Plausible self-hosted "Paid" and Matomo cookies "Required"; snippet uses `tracker.js` instead of `tracker.min.js`; loads Remixicon from a CDN |
| Docs site (`docs/`) | Ship | Accurate on what exists (viewer role, no user screen, demo data) |

## Third-party requests from the dashboard

Seen during the walk-through. Every visit sends the admin's IP to these hosts:

- `cdn.jsdelivr.net`: Chart.js 4.4.0, chartjs-chart-matrix 2.0.1, Remixicon 4.6.0 (pinned, with SRI), and **globe.gl + three-globe textures (unpinned)**
- `unpkg.com`: **chartjs-chart-geo 4.3.6 and Leaflet 1.9.4, both dead code** (`createMap` draws a bar chart; `createLeafletMap` is never called)
- `fonts.googleapis.com` / `fonts.gstatic.com`: Google Fonts

For a privacy-first product, self-hosting these (or at least dropping the dead ones and Google Fonts) is worth a
follow-up. None of them touch visitors of tracked sites; only the admin's own browser.

## Smaller notes (not blocking)

- `admin.js` defines `loadContributionCalendar`, `loadGoalsAndFunnels` and `loadSegments` twice; the later ones win.
- The globe "Test" button and the real-time "Demo" button are always hidden.
- `DatabaseFactory.kt:36` says empty databases are seeded with demo data; they aren't.
- `TRACKER_HEARTBEAT_INTERVAL` / `TRACKER_SPA_ENABLED` do nothing (already in the roadmap).
- `_docs/TESTING_PLAN.md` still says the light theme fails contrast on 16 elements; fixed in `7f0a463`.
- A sticky section header can cover the geo back button when the card sits at the top edge of the viewport.
- About 1,400 leftover databases in `test-dbs/` (gitignored, safe to delete locally).
