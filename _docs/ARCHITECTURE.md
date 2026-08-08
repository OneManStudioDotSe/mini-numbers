# Mini Numbers - Architecture reference

Technical reference for the codebase: file layout, database schema, configuration
variables, API surface, and how the less obvious features work internally. This is
the "implementation details" companion to `CLAUDE.md` — when code changes, update
this file, not `CLAUDE.md`.

## Stack

Kotlin 2.3.0 + Ktor 3.4.0 on JDK 21, Exposed ORM (SQLite or PostgreSQL), Caffeine caching, Gradle Kotlin DSL. The frontend is vanilla JS/CSS — no bundler, no `package.json`, no build step.

## Tools (Figma, Sketch, Chrome, GitHub, MCPs)

**What's actually wired up today:**

- `gh` CLI + `git` for GitHub (PRs, issues, pushes).
- `./gradlew` for test/detekt/build.
- The `run` skill to launch and drive the app in a real browser before claiming a UI
  change works.
- `WebFetch` is allowlisted for competitor research and CDN/package version lookups.

**Direction, not yet in place** — call these out explicitly in the moment if you reach
for them, don't assume they're configured:

- **Figma** — for mocking layouts before implementing UI work that outgrows a CSS-variable
  tweak, and the Figma MCP tools to pull design context/screenshots into implementation.
  No design files or tokens exist yet; today's styling lives directly in CSS.
- **Chrome DevTools MCP** — for automated screenshot and console-error verification.
  Today, UI verification is manual (screenshot attached to the PR, per `CONTRIBUTING.md`).
- **Sketch** — treated as an equivalent external-mock tool if it's ever the one actually
  adopted instead of Figma.
- Other MCPs available in the broader environment (Slack, Atlassian, Amplitude, Google
  Drive, etc.) aren't part of this project's workflow — don't pre-wire them; name the
  specific one if a real need shows up.

## Definition of done

Turn the task into a verifiable goal before starting, not after: a bug fix means a failing test that reproduces it and then passes; a feature means stated success criteria you can check against when you're done.

Then, specifically for this project verify the following:

- [ ] `./gradlew test` and `./gradlew detekt` pass.
- [ ] Manually exercised in a real browser (`./gradlew run` or the `run` skill) — the golden path and the relevant edge cases, not just "it compiles."
- [ ] UI changes verified in both light and dark themes.
- [ ] Before/after screenshot attached for UI changes (manual today — see the Tools section in `_docs/ARCHITECTURE.md` for the aspirational automated path).
- [ ] No regression against the accessibility bar in `_docs/TESTING_PLAN.md` (WCAG AA, keyboard navigation).
- [ ] `_docs/ARCHITECTURE.md` updated for anything technical; `_docs/CHANGELOG.md` entry added for user-facing changes.
- [ ] Commit/PR message explains why, not what; PR title under 70 characters.

## Project structure

```
mini-numbers/
├── src/main/kotlin/se/onemanstudio/
│   ├── Application.kt                    # Main entry point (unified mode)
│   ├── Routing.kt                        # Route tree assembly (mounts routing/*)
│   ├── WidgetRouting.kt                  # Embeddable widget endpoints
│   ├── api/models/
│   │   ├── ApiError.kt                   # Standardized error responses
│   │   ├── PaginatedResponse.kt          # Pagination wrapper
│   │   ├── LoginRequest.kt
│   │   ├── StatEntry.kt                  # Generic stat entry
│   │   ├── admin/                        # Admin-only request/response models
│   │   │   ├── DemoDataModels.kt
│   │   │   ├── EmailReportModels.kt
│   │   │   ├── FunnelModels.kt
│   │   │   ├── GoalModels.kt
│   │   │   ├── PasswordResetRequest.kt
│   │   │   ├── RevenueModels.kt
│   │   │   ├── SegmentModels.kt
│   │   │   ├── UserModels.kt
│   │   │   └── WebhookModels.kt
│   │   ├── collection/
│   │   │   └── PageViewPayload.kt        # /collect request body
│   │   ├── dashboard/                    # Report/analytics response models
│   │   │   ├── ActivityCell.kt, ComparisonReport.kt, ContributionCalendar.kt,
│   │   │   ├── ContributionDay.kt, GlobeModels.kt, PeakTimeAnalysis.kt,
│   │   │   ├── ProjectReport.kt, ProjectStats.kt, RawEvent.kt,
│   │   │   ├── RawEventsResponse.kt, TimeSeriesPoint.kt, TopPage.kt, VisitSnippet.kt
│   │   └── widget/
│   │       └── WidgetModels.kt
│   ├── config/
│   │   ├── ConfigLoader.kt               # Environment variable loader
│   │   ├── ConfigurationException.kt
│   │   └── models/                       # AppConfig, DatabaseConfig, EmailConfig,
│   │                                      # GeoIPConfig, PrivacyConfig, RateLimitConfig,
│   │                                      # SecurityConfig, ServerConfig, TrackerConfig
│   ├── core/
│   │   ├── AnalyticsSecurity.kt          # Visitor hashing (configurable rotation)
│   │   ├── HTTP.kt                       # CORS & content negotiation
│   │   ├── JwtService.kt                 # Access/refresh token issuance & verification
│   │   ├── Security.kt                   # Session auth & login page
│   │   ├── ServiceManager.kt             # Lifecycle management, uptime, retention timer
│   │   ├── WidgetAuth.kt                 # API-key auth for /widget/* endpoints
│   │   └── models/                       # LoginAttempt, UserRole, UserSession
│   ├── db/
│   │   ├── DatabaseFactory.kt            # Database initialization, table creation
│   │   ├── Events.kt                     # Events table (9 indexes)
│   │   ├── Projects.kt
│   │   ├── Users.kt                      # RBAC user accounts
│   │   ├── RefreshTokens.kt              # JWT refresh token store
│   │   ├── ConversionGoals.kt
│   │   ├── Funnels.kt / FunnelSteps.kt
│   │   ├── Segments.kt
│   │   ├── Webhooks.kt                   # Webhooks + WebhookDeliveries tables
│   │   ├── EmailReports.kt               # Scheduled report configs
│   │   └── ResetDatabase.kt
│   ├── middleware/
│   │   ├── AdminCorsGuard.kt             # Per-origin allowlist for /admin — see Known Gaps
│   │   ├── InputValidator.kt
│   │   ├── QueryCache.kt                 # Caffeine query cache (30s TTL, 500 entries)
│   │   ├── RateLimiter.kt                # Only wired into /collect — see Known Gaps
│   │   ├── RedirectValidator.kt          # Open-redirect protection
│   │   ├── RoleGuard.kt                  # admin/viewer role enforcement
│   │   ├── WidgetCache.kt                # 60s cache for /widget/* responses
│   │   └── models/                       # RateLimitBucket, RateLimitResult, RateLimitStatus
│   ├── routing/
│   │   ├── AdminAnalyticsRouting.kt      # report, comparison, calendar, events
│   │   ├── AdminFeatureRouting.kt        # goals, funnels, segments, webhooks,
│   │   │                                 # email-reports, revenue
│   │   ├── AdminProjectRouting.kt        # project CRUD, stats, live, globe, demo-data
│   │   ├── AdminUserRouting.kt           # user CRUD, role management
│   │   ├── AuthRouting.kt                # login, logout, JWT issuance, password reset
│   │   ├── CollectionRouting.kt          # POST /collect
│   │   ├── PublicRouting.kt              # health, metrics, tracker config/script
│   │   └── RoutingUtils.kt               # shared helpers (period parsing, UUID parsing)
│   ├── services/
│   │   ├── EmailService.kt               # SMTP sending (Jakarta Mail)
│   │   ├── GeoLocationService.kt         # GeoIP lookup (with Caffeine cache)
│   │   ├── UserAgentParser.kt            # Browser/OS/device detection
│   │   ├── WebhookService.kt             # HMAC signing + delivery
│   │   └── WebhookTrigger.kt             # Fires webhook events from collection path
│   ├── setup/                            # Setup wizard backend + models
│   └── utils/
│       ├── ConversionAnalysisUtils.kt    # Goal & funnel calculations
│       ├── DataAnalysisUtils.kt          # Core analytics calculations
│       └── RevenueAnalysisUtils.kt       # Revenue totals, breakdown, attribution
├── src/main/resources/
│   ├── geo/geolite2-city.mmdb            # GeoIP database
│   ├── setup/                            # Setup wizard frontend
│   ├── tracker/tracker.js / tracker.min.js
│   └── static/                           # Admin panel frontend (admin.html, css/, js/)
└── src/test/kotlin/                      # 296 tests
```

## Database schema

### Projects

```kotlin
id: UUID (PK), name: String(100), domain: String(255), apiKey: String(64, unique)
```

### Events

```kotlin
id: Long (auto-increment), projectId: UUID (FK), visitorHash: String(64), sessionId: String(64)
eventType: String(20) - "pageview" | "heartbeat" | "custom"
eventName: String?(100), path: String(512), referrer: String?(512)
country: String?(100), city: String?(100)
browser: String?(50), os: String?(50), device: String?(50)
utmSource/utmMedium/utmCampaign/utmTerm/utmContent: String?
scrollDepth: Int?, targetUrl: String?(512), properties: String? (JSON, custom event payload)
duration: Int (seconds), timestamp: DateTime

Indexes: idx_events_timestamp, idx_events_project_timestamp, idx_events_project_session,
         idx_events_project_eventname, idx_events_project_visitor, idx_events_project_path,
         idx_events_project_type_ts, idx_events_project_country, idx_events_project_browser
```

### Users

```kotlin
id: UUID (PK), username: String(100, unique), passwordHash: String(255)
role: String(20) default "viewer" - "admin" | "viewer"
isActive: Boolean default true, createdAt: DateTime
```

### RefreshTokens

```kotlin
id: UUID (PK), username: String(100), tokenHash: String(64, unique) - SHA-256, never raw
family: String(64) - rotation-replay detection, expiresAt: DateTime
revokedAt: DateTime?, replacedBy: UUID? (successor in rotation chain), createdAt: DateTime
```

### ConversionGoals

```kotlin
id: UUID (PK), projectId: UUID (FK), name, type (URL/EVENT), matchPattern, isActive, createdAt
```

### Funnels / FunnelSteps

```kotlin
Funnels: id, projectId, name, createdAt
FunnelSteps: id, funnelId (FK), stepOrder, name, type (URL/EVENT), matchPattern
```

### Segments

```kotlin
id: UUID (PK), projectId: UUID (FK), name, description, filtersJson (JSON array), createdAt, updatedAt
```

### Webhooks / WebhookDeliveries

```kotlin
Webhooks: id (PK), projectId (FK), url(1024), secret(128) - HMAC-SHA256 signing key
          events(500) - comma-separated e.g. "goal_conversion,traffic_spike", isActive, createdAt
WebhookDeliveries: id (PK), webhookId (FK), eventType(50), payload (text), responseCode?, responseBody?
          attempt (default 1), status (pending/success/failed), createdAt, deliveredAt?
```

### EmailReports

```kotlin
id: UUID (PK), projectId: UUID (FK), recipientEmail(320)
schedule: String(20) default "WEEKLY" - DAILY | WEEKLY | MONTHLY
sendHour: Int (0-23), sendDay: Int (1=Mon for weekly, 1-28 for monthly), timezone(50) default "UTC"
subjectTemplate(500), headerText?(500), footerText?(500)
includeSections(500) default "overview,pages,referrers,geo,events"
isActive, lastSentAt?, createdAt
```

## Configuration reference

All configuration via `.env` file or environment variables.

### Security (required)

| Variable          | Default | Description                                              |
| ----------------- | ------- | -------------------------------------------------------- |
| `ADMIN_PASSWORD`  | —       | Admin panel password (required)                          |
| `SERVER_SALT`     | —       | Server salt for visitor hashing, min 32 chars (required) |
| `ADMIN_USERNAME`  | `admin` | Admin panel username                                     |
| `ALLOWED_ORIGINS` | —       | Comma-separated CORS allowlist (`*` allows all)          |

### Database

| Variable              | Default        | Description                                              |
| --------------------- | -------------- | -------------------------------------------------------- |
| `DB_TYPE`             | `SQLITE`       | `SQLITE` or `POSTGRESQL`                                 |
| `DB_SQLITE_PATH`      | `./stats.db`   | SQLite database file path                                |
| `DB_PG_HOST`          | `localhost`    | PostgreSQL host                                          |
| `DB_PG_PORT`          | `5432`         | PostgreSQL port                                          |
| `DB_PG_NAME`          | `mini_numbers` | PostgreSQL database name                                 |
| `DB_PG_USERNAME`      | `postgres`     | PostgreSQL username                                      |
| `DB_PG_PASSWORD`      | —              | PostgreSQL password (required when `DB_TYPE=POSTGRESQL`) |
| `DB_PG_MAX_POOL_SIZE` | `3`            | HikariCP maximum pool size (PostgreSQL only)             |

> These are the actual variable names read by `ConfigLoader.loadDatabaseConfig()`.
> Earlier docs referenced `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` (no `_PG_`
> prefix) and a `mininumbers` default name — both were wrong and have been corrected here.

### Server / privacy / tracker

| Variable                     | Default                                     | Description                                         |
| ---------------------------- | ------------------------------------------- | --------------------------------------------------- |
| `SERVER_PORT`                | `8080`                                      | Server port                                         |
| `KTOR_DEVELOPMENT`           | `false`                                     | Development mode (relaxes CORS)                     |
| `RATE_LIMIT_PER_IP`          | `1000`                                      | Max requests per IP per minute                      |
| `RATE_LIMIT_PER_API_KEY`     | `10000`                                     | Max requests per API key per minute                 |
| `HASH_ROTATION_HOURS`        | `24`                                        | Hash rotation period (1-8760)                       |
| `PRIVACY_MODE`               | `STANDARD`                                  | `STANDARD`, `STRICT`, or `PARANOID`                 |
| `DATA_RETENTION_DAYS`        | `0`                                         | Auto-delete events older than N days (0 = disabled) |
| `TRACKER_HEARTBEAT_INTERVAL` | `30`                                        | Default heartbeat interval in seconds               |
| `TRACKER_SPA_ENABLED`        | `true`                                      | Enable SPA tracking by default                      |
| `GEOIP_DATABASE_PATH`        | `src/main/resources/geo/geolite2-city.mmdb` | GeoIP database path                                 |

### Email reports (SMTP)

| Variable        | Default | Description                                                              |
| --------------- | ------- | ------------------------------------------------------------------------ |
| `SMTP_HOST`     | —       | SMTP server host; leaving unset disables Email Reports entirely          |
| `SMTP_PORT`     | `587`   | SMTP server port                                                         |
| `SMTP_USERNAME` | —       | SMTP auth username                                                       |
| `SMTP_PASSWORD` | —       | SMTP auth password                                                       |
| `SMTP_FROM`     | —       | From address; required alongside `SMTP_HOST` for the feature to activate |
| `SMTP_STARTTLS` | `true`  | Use STARTTLS                                                             |

`EmailConfig.isConfigured()` requires both `SMTP_HOST` and `SMTP_FROM` to be set — if either
is missing, Email Reports endpoints return 400 ("SMTP is not configured").

## API endpoints reference

### Public

- `GET /` — Intelligent redirect based on application state
- `GET /health` — Health check (uptime, version, state)
- `GET /metrics` — Application metrics (event counts, cache stats, privacy config)
- `GET /favicon.ico`
- `GET /tracker/config` — Tracker configuration (heartbeat interval, SPA enabled)
- `GET /tracker/tracker.min.js` — Minified tracker script
- `POST /collect` — Data collection endpoint (rate limited, privacy mode aware)

### Setup wizard

- `GET /setup`, `GET /setup/api/status`, `GET /setup/api/generate-salt`, `POST /setup/api/save`

### Auth / users (session cookie or JWT)

- `POST /api/login` / `POST /api/logout` — Session-based auth
- `POST /api/token` / `POST /api/token/refresh` — JWT access + refresh token pair
- `POST /api/password-reset` — Invalidates all sessions for the user
- `GET /admin/me` — Current authenticated user info
- `GET /admin/users`, `POST /admin/users`, `PUT /admin/users/{userId}/role`, `DELETE /admin/users/{userId}` — admin only

### Projects

- `GET /admin/projects` (paginated), `POST /admin/projects`, `POST /admin/projects/{id}` (update),
  `DELETE /admin/projects/{id}` (cascades events/goals/funnels/segments/webhooks/email-reports)
- `POST /admin/projects/{id}/rotate-api-key`
- `POST /admin/projects/{id}/demo-data`
- `GET /admin/projects/{id}/retention-preview?days=N`

### Analytics & reports

- `GET /admin/projects/{id}/stats`, `GET /admin/projects/{id}/live`, `GET /admin/projects/{id}/realtime-count`
- `GET /admin/projects/{id}/globe`
- `GET /admin/projects/{id}/report?filter=7d`, `GET /admin/projects/{id}/report/comparison?filter=7d`
- `GET /admin/projects/{id}/calendar`
- `GET /admin/projects/{id}/events` (paginated, filterable, sortable)

### Conversion goals / funnels / segments

- `GET|POST /admin/projects/{id}/goals`, `PUT|DELETE /admin/projects/{id}/goals/{goalId}`, `GET /admin/projects/{id}/goals/stats?filter=7d`
- `GET|POST /admin/projects/{id}/funnels`, `DELETE /admin/projects/{id}/funnels/{funnelId}`, `GET /admin/projects/{id}/funnels/{funnelId}/analysis?filter=7d`
- `GET|POST /admin/projects/{id}/segments`, `DELETE /admin/projects/{id}/segments/{segmentId}`, `GET /admin/projects/{id}/segments/{segmentId}/analysis?filter=7d`

### Webhooks

- `GET /admin/projects/{id}/webhooks` — list
- `POST /admin/projects/{id}/webhooks` — create (returns the signing secret once)
- `DELETE /admin/projects/{id}/webhooks/{webhookId}`
- `GET /admin/projects/{id}/webhooks/{webhookId}/deliveries` — delivery log
- `POST /admin/projects/{id}/webhooks/{webhookId}/test` — sends a synthetic test payload

### Email reports

- `GET /admin/projects/{id}/email-reports`, `POST /admin/projects/{id}/email-reports`
- `PUT /admin/projects/{id}/email-reports/{reportId}`, `DELETE /admin/projects/{id}/email-reports/{reportId}`
- `POST /admin/projects/{id}/email-reports/{reportId}/test` — sends a test email immediately
- `GET /admin/smtp/status` — whether SMTP is configured

### Revenue

- `GET /admin/projects/{id}/revenue?filter=7d` — totals, transactions, AOV, revenue/visitor, prior-period comparison
- `GET /admin/projects/{id}/revenue/breakdown?filter=7d` — revenue grouped by event name
- `GET /admin/projects/{id}/revenue/attribution?filter=7d` — revenue by traffic source, with conversion rate

### Embeddable widgets (public, key-authenticated, cached 60s)

- `GET /widget/realtime?key=<KEY>`, `GET /widget/pageviews?key=<KEY>&scope=site&filter=7d`
- `GET /widget/toppages?key=<KEY>&filter=7d&limit=5`, `GET /widget/sparkline?key=<KEY>`

### API documentation

- `GET /admin-panel/openapi.yaml` — OpenAPI 3.0.3 spec (all endpoints)

All error responses use standardized `ApiError` format: `{ error, code, details[] }`.

## Feature notes

### Webhooks

Per-project outbound HTTP notifications (`db/Webhooks.kt`, `services/WebhookService.kt`,
`services/WebhookTrigger.kt`). Each webhook has its own HMAC-SHA256 secret; payloads are
signed and deliveries logged to `WebhookDeliveries` (status, response code/body, attempt
count) for debugging. Triggered from the collection path (`WebhookTrigger`) on events such
as goal conversions. The admin UI's `webhooks.js` renders the config + delivery log modal.

### Email reports

Scheduled recurring analytics emails per project (`db/EmailReports.kt`,
`services/EmailService.kt` via Jakarta Mail over SMTP). Schedule is DAILY/WEEKLY/MONTHLY
with a configurable send hour/day and timezone; `includeSections` controls which report
sections are rendered into the email. Disabled entirely unless `SMTP_HOST` and `SMTP_FROM`
are set — see Configuration reference above. `email-reports.js` is the admin UI modal.

### Revenue tracking

Not a separate tracked field — revenue is parsed out of custom event `properties` JSON.
Client usage:

```js
MiniNumbers.track("purchase", { revenue: 29.99, currency: "USD" });
```

`RevenueAnalysisUtils` scans `Events.properties` for `"revenue": <number>` and aggregates
totals, per-event breakdown, and per-traffic-source attribution (UTM campaign → UTM source
→ referrer domain → "Direct", from the session's first pageview). `revenue.js` renders this
in the admin UI.

### Onboarding checklist

A first-run checklist modal (`#onboarding-modal` in `admin.html`) walks a new admin through
initial setup steps (e.g. create a project, install the tracker). Driven client-side by
`admin.js`.

## Known implementation gaps

These are real, current gaps between documented/intended behavior and what the code does —
tracked here so they don't get silently assumed as "already fixed." See `GOING_LIVE.md` for
the full pre-launch audit this was extracted from.

- **`AdminCorsGuard` is unwired**: implements per-origin allowlist checking for `/admin/*`
  but has zero call sites. The actual CORS policy (`core/HTTP.kt`) uses `anyHost()` for
  every route, so `ALLOWED_ORIGINS` does not currently restrict admin API access.
- **Rate limiting only covers `/collect`**: `RateLimiter` is not applied to `/api/*` or
  `/admin/*` — login/token/password-reset endpoints have no request-volume throttling
  beyond the per-username lockout (5 fails → 15 min).
- **ARIA dialog attributes are incomplete**: `create-project-modal`, `onboarding-modal`,
  `logout-modal`, and `delete-project-modal` in `admin.html` lack `role="dialog"` /
  `aria-modal`. Only a subset of `.modal` elements have them set.
- **Role/session changes don't revoke live sessions**: demoting or deleting a user doesn't
  invalidate their existing session cookie or in-flight JWT until natural expiry.
