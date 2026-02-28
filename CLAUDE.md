# Mini Numbers - Privacy-first web analytics platform

## Overview

Mini Numbers is a **privacy-focused, minimalist web analytics platform** built with Kotlin and Ktor. It provides essential website traffic insights without compromising visitor privacy - a lightweight, self-hosted alternative to services like Google Analytics.

## Core philosophy

**Privacy-first approach:**
- No cookies or persistent tracking identifiers
- IP addresses are processed in-memory only (never stored)
- Configurable hash rotation (1-8760 hours, default 24h)
- Three privacy modes: STANDARD, STRICT (country-only geo), PARANOID (no geo/UA)
- No Personally Identifiable Information (PII) is stored
- Compliant with privacy regulations (GDPR-friendly design)
- Data retention policies with automatic purge

**Minimalist design:**
- Tiny tracking script (1.9KB source / 1.3KB minified)
- Clean, focused feature set
- Fast data collection with minimal overhead
- SQLite or PostgreSQL database options

## Technical architecture

### Backend stack
- **Framework:** Ktor 3.4.0 (Kotlin web framework)
- **Runtime:** Kotlin JVM 2.3.0 on JDK 21
- **Database:** Exposed ORM with SQLite/PostgreSQL support
- **Server:** Netty (embedded)
- **Serialization:** kotlinx.serialization
- **Caching:** Caffeine (query results + GeoIP lookups)
- **Build Tool:** Gradle with Kotlin DSL

### Key dependencies
- **MaxMind GeoIP2** - IP geolocation lookup (city/country)
- **UserAgentUtils** - Browser/OS/device detection
- **HikariCP** - Database connection pooling
- **Caffeine** - High-performance caching (query results, GeoIP, rate limiting)
- **Logback** - Logging framework

### Zero-restart architecture

**ServiceManager** - Centralized lifecycle management for hot-reload capability:

```kotlin
object ServiceManager {
    enum class State { UNINITIALIZED, INITIALIZING, READY, ERROR }

    fun initialize(config: AppConfig, logger: Logger): Boolean
    fun reload(config: AppConfig, logger: Logger): Boolean
    fun isReady(): Boolean
    fun getState(): State
    fun getUptimeSeconds(): Long
    fun getLastError(): Throwable?
}
```

**Initialization flow:**
1. **Security module** - Initialize `AnalyticsSecurity` with server salt and hash rotation hours
2. **Database** - Connect to SQLite or PostgreSQL via `DatabaseFactory`
3. **GeoIP service** - Load MaxMind database (optional, non-fatal if missing)
4. **Data retention** - Start auto-purge timer if retention days configured
5. **HTTP/CORS** - Configure allowed origins and headers
6. **Authentication** - Set up session-based auth with login page
7. **Routing** - Install data collection and admin endpoints

## Core features

### 1. Data collection
- **Real-time tracking:** Lightweight JavaScript tracker (`tracker.js`, 1.9KB source / 1.3KB minified)
- **Page views:** Automatic pageview tracking
- **Heartbeat system:** Configurable intervals (default 30s) to measure time-on-page (visibility-aware)
- **SPA support:** History API patching (`pushState`/`replaceState` + `popstate`), can be disabled
- **Custom events:** `MiniNumbers.track("event_name")` API for tracking custom interactions
- **Session management:** Uses sessionStorage (no cookies)
- **Build optimization:** Gradle `minifyTracker` task auto-generates `tracker.min.js`

### 2. Analytics dashboard
Located at `/admin-panel`, provides comprehensive insights:

#### Basic metrics
- Total page views with period comparison
- Unique visitors with period comparison
- Bounce rate with inverted comparison (lower = better)
- Top pages by traffic
- Recent visits with location

#### Detailed breakdowns
- **Browsers:** Chrome, Firefox, Safari, etc.
- **Operating systems:** Windows, macOS, Linux, iOS, Android
- **Devices:** Desktop, Mobile, Tablet
- **Referrers:** Traffic sources
- **Geographic data:** Countries and cities (via GeoIP2) with drill-down
- **Custom events:** Named event tracking with bar chart visualization

#### Advanced analytics
- **Activity heatmap:** 7 days x 24 hours traffic visualization
- **Peak time analysis:** Identifies busiest hours and days
- **Contribution calendar:** GitHub-style 365-day activity grid with intensity levels
- **Time series data:** Trend charts with granular breakdowns (hourly/daily/weekly)
- **Comparison reports:** Current vs. previous period metrics
- **Conversion goals:** URL-based and event-based goal tracking with conversion rates
- **Funnels:** Multi-step conversion tracking with drop-off analysis
- **User segments:** Visual filter builder with AND/OR logic

#### Live monitoring
- Real-time visitor feed (last 5 minutes)
- Live location tracking on interactive map

#### UI features
- Loading skeleton screens during data fetching
- ARIA labels and semantic HTML for accessibility
- Skip-to-content link for keyboard navigation
- Light/dark theme support
- CSV export for all data types
- Interactive maps with Leaflet

### 3. Multi-project support
- Manage multiple websites from one dashboard
- Each project has unique API key
- Per-project statistics, goals, funnels, and segments
- CRUD operations for projects

### 4. Privacy modes

| Mode | Geolocation | Browser/OS/device | Hash rotation |
|------|-------------|-------------------|---------------|
| STANDARD | Full (country + city) | Full | Configurable |
| STRICT | Country only | Full | Configurable |
| PARANOID | None | None | Configurable |

### 5. Performance
- **Query cache:** Caffeine-based, 500 entries, 30-second TTL, auto-invalidated on data changes
- **GeoIP cache:** 10,000 entries, 1-hour TTL
- **Database indexes:** 8 composite indexes for analytics query performance
- **Data retention:** Configurable auto-purge (background timer every 6 hours)

## Database schema

### Projects table
```kotlin
- id: UUID (Primary Key)
- name: String (100 chars)
- domain: String (255 chars)
- apiKey: String (64 chars, unique)
```

### Events table
```kotlin
- id: Long (Auto-increment)
- projectId: UUID (FK → Projects)
- visitorHash: String (64 chars)
- sessionId: String (64 chars)
- eventType: String (20 chars) - "pageview", "heartbeat", or "custom"
- eventName: String? (100 chars, nullable)
- path: String (512 chars)
- referrer: String? (512 chars, nullable)
- country: String? (100 chars, nullable)
- city: String? (100 chars, nullable)
- browser: String? (50 chars, nullable)
- os: String? (50 chars, nullable)
- device: String? (50 chars, nullable)
- duration: Int (seconds)
- timestamp: DateTime

Indexes: idx_events_timestamp, idx_events_project_timestamp, idx_events_project_session,
         idx_events_project_eventname, idx_events_project_visitor, idx_events_project_path,
         idx_events_project_type_ts, idx_events_project_country, idx_events_project_browser
```

### ConversionGoals table
```kotlin
- id: UUID (PK), projectId: UUID (FK), name, type (URL/EVENT), matchPattern, isActive, createdAt
```

### Funnels / FunnelSteps tables
```kotlin
- Funnels: id, projectId, name, createdAt
- FunnelSteps: id, funnelId (FK), stepOrder, name, type (URL/EVENT), matchPattern
```

### Segments table
```kotlin
- id: UUID (PK), projectId: UUID (FK), name, description, filtersJson (JSON array), createdAt, updatedAt
```

## API endpoints

### Public endpoints
- `GET /` - Intelligent redirect based on application state
- `GET /health` - Health check (uptime, version, state)
- `GET /metrics` - Application metrics (event counts, cache stats, privacy config)
- `GET /tracker/config` - Tracker configuration (heartbeat interval, SPA enabled)
- `POST /collect` - Data collection endpoint (rate limited, privacy mode aware)

### Setup wizard endpoints
- `GET /setup` - Setup wizard static resources
- `GET /setup/api/status` - Check setup status and service readiness
- `GET /setup/api/generate-salt` - Generate cryptographically secure server salt
- `POST /setup/api/save` - Save configuration and initialize services (no restart)

### Admin panel (session auth protected)

#### Project management
- `GET /admin/projects` - List projects (supports `?page=&limit=` pagination)
- `POST /admin/projects` - Create new project
- `DELETE /admin/projects/{id}` - Delete project (cascade deletes events, goals, funnels, segments)

#### Analytics & reports
- `GET /admin/projects/{id}/stats` - Basic statistics (cached)
- `GET /admin/projects/{id}/live` - Live visitor feed (last 5 minutes)
- `GET /admin/projects/{id}/report?filter=7d` - Full analytics report (cached)
- `GET /admin/projects/{id}/report/comparison?filter=7d` - Comparison report with time series (cached)
- `GET /admin/projects/{id}/calendar` - 365-day contribution calendar (cached)
- `GET /admin/projects/{id}/events` - Raw events with pagination, filtering, and sorting

#### Conversion goals
- `GET /admin/projects/{id}/goals` - List goals
- `POST /admin/projects/{id}/goals` - Create goal
- `PUT /admin/projects/{id}/goals/{goalId}` - Toggle goal active/inactive
- `DELETE /admin/projects/{id}/goals/{goalId}` - Delete goal
- `GET /admin/projects/{id}/goals/stats?filter=7d` - Goal statistics (cached)

#### Funnels
- `GET /admin/projects/{id}/funnels` - List funnels
- `POST /admin/projects/{id}/funnels` - Create funnel
- `DELETE /admin/projects/{id}/funnels/{funnelId}` - Delete funnel
- `GET /admin/projects/{id}/funnels/{funnelId}/analysis?filter=7d` - Funnel analysis

#### Segments
- `GET /admin/projects/{id}/segments` - List segments
- `POST /admin/projects/{id}/segments` - Create segment
- `DELETE /admin/projects/{id}/segments/{segmentId}` - Delete segment
- `GET /admin/projects/{id}/segments/{segmentId}/analysis?filter=7d` - Segment analysis

#### API documentation
- `GET /admin-panel/openapi.yaml` - OpenAPI 3.0.3 specification

All error responses use standardized `ApiError` format: `{ error, code, details[] }`.

## Privacy implementation

### Visitor identification
```kotlin
// Configurable epoch-hour-based rotation buckets
val epochHours = ChronoUnit.HOURS.between(epoch, now)
val rotationBucket = epochHours / hashRotationHours
visitorHash = SHA256(ip + userAgent + projectId + serverSalt + rotationBucket)
```

**Privacy guarantees:**
1. IP address exists only in RAM during request processing
2. Hash rotation is configurable (1 hour to 1 year)
3. Cannot reverse-engineer the hash to get IP/user agent
4. STRICT mode: only stores country (no city)
5. PARANOID mode: stores no geolocation or user agent data

## Client-side tracking script

### Installation
```html
<script
  async
  src="https://your-domain.com/tracker/tracker.js"
  data-project-key="YOUR_API_KEY"
  data-heartbeat-interval="30000"
  data-disable-spa="false">
</script>
```

### Custom events
```javascript
MiniNumbers.track("signup");
MiniNumbers.track("purchase");
```

### Configuration attributes
| Attribute | Default | Description |
|-----------|---------|-------------|
| `data-project-key` | Required | Project API key |
| `data-api-endpoint` | Script origin | Custom API endpoint |
| `data-heartbeat-interval` | `30000` | Heartbeat interval in ms |
| `data-disable-spa` | `false` | Set to `"true"` to disable SPA tracking |

## Configuration

All configuration via `.env` file or environment variables:

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `ADMIN_PASSWORD` | Yes | — | Admin panel password |
| `SERVER_SALT` | Yes | — | Server salt for visitor hashing (min 32 chars) |
| `ADMIN_USERNAME` | No | `admin` | Admin panel username |
| `DB_TYPE` | No | `SQLITE` | Database type (`SQLITE` or `POSTGRESQL`) |
| `DB_SQLITE_PATH` | No | `./stats.db` | SQLite database file path |
| `SERVER_PORT` | No | `8080` | Server port |
| `KTOR_DEVELOPMENT` | No | `false` | Development mode (relaxes CORS) |
| `ALLOWED_ORIGINS` | No | — | Comma-separated allowed CORS origins |
| `RATE_LIMIT_PER_IP` | No | `1000` | Max requests per IP per minute |
| `RATE_LIMIT_PER_API_KEY` | No | `10000` | Max requests per API key per minute |
| `HASH_ROTATION_HOURS` | No | `24` | Hash rotation period (1-8760) |
| `PRIVACY_MODE` | No | `STANDARD` | Privacy mode: STANDARD, STRICT, PARANOID |
| `DATA_RETENTION_DAYS` | No | `0` | Auto-delete events older than N days (0 = disabled) |
| `TRACKER_HEARTBEAT_INTERVAL` | No | `30` | Default heartbeat interval in seconds |
| `TRACKER_SPA_ENABLED` | No | `true` | Enable SPA tracking by default |
| `GEOIP_DATABASE_PATH` | No | `src/main/resources/geo/geolite2-city.mmdb` | GeoIP database path |

## Testing

288 tests covering critical functionality, edge cases, and error scenarios.

```bash
./gradlew test
```

**Test organization:**

```
src/test/kotlin/
├── ApplicationTest.kt
├── analytics/
│   └── DataAnalysisUtilsTest.kt     (22 tests) — analytics calculations
├── core/
│   ├── SecurityUtilsTest.kt         (13 tests) — visitor hashing, rotation
│   └── ServiceManagerTest.kt        (13 tests) — service lifecycle
├── middleware/
│   └── InputValidatorTest.kt        (26 tests) — input validation
├── services/
│   └── UserAgentParserTest.kt       (22 tests) — UA parsing
└── integration/
    ├── AdminEndpointTest.kt         (14 tests) — admin API
    ├── CollectEndpointTest.kt       (19 tests) — data collection
    ├── HealthEndpointTest.kt        (6 tests) — health endpoint
    ├── SetupWizardTest.kt           (10 tests) — setup wizard
    └── TrackingWorkflowTest.kt      (9 tests) — end-to-end
```

Test databases are written to the `test-dbs/` directory (gitignored).

## Project structure

```
mini-numbers/
├── _docs/                           # Project documentation
│   ├── CHANGELOG.md                 # Version history
│   ├── DASHBOARD_GUIDE.md           # Dashboard user guide
│   ├── DEPLOYMENT.md                # Deployment & operations guide
│   ├── EVALUATION.md                # Competitive analysis & market viability
│   ├── PERFORMANCE.md               # Performance benchmarking guide
│   ├── PRIVACY.md                   # Privacy architecture explanation
│   ├── ROADMAP.md                   # Development roadmap & task tracking
│   ├── SECURITY.md                  # Security architecture & audit
│   └── TESTING_PLAN.md              # Manual testing plan
├── .github/workflows/               # CI/CD
│   ├── build.yml                    # Test + build + Detekt + Docker verify
│   └── docker-publish.yml           # Docker multi-platform publish
├── config/detekt/                   # Static analysis config
│   └── detekt.yml                   # Detekt rules (relaxed for existing code)
├── CODE_OF_CONDUCT.md               # Contributor Covenant v2.1
├── CONTRIBUTING.md                  # Contributing guidelines
├── Dockerfile                       # Multi-stage production build
├── docker-compose.yml               # SQLite deployment
├── docker-compose.postgres.yml      # PostgreSQL deployment
├── LICENSE                          # MIT License
├── src/main/kotlin/
│   ├── Application.kt              # Main entry point (unified mode)
│   ├── Routing.kt                  # API endpoints & admin routes
│   ├── DataAnalysisUtils.kt        # Analytics calculations
│   ├── ConversionAnalysisUtils.kt  # Goal & funnel calculations
│   ├── api/models/                 # API response/request models
│   │   ├── ApiError.kt             # Standardized error responses
│   │   ├── PaginatedResponse.kt    # Pagination wrapper
│   │   ├── SegmentModels.kt        # Segment filter/response models
│   │   ├── GoalModels.kt           # Goal request/response models
│   │   ├── FunnelModels.kt         # Funnel request/response models
│   │   ├── PageViewPayload.kt      # Data collection payload
│   │   ├── ProjectReport.kt        # Full analytics report
│   │   ├── ProjectStats.kt         # Basic project statistics
│   │   ├── RawEvent.kt             # Raw event data
│   │   ├── RawEventsResponse.kt    # Paginated raw events
│   │   ├── StatEntry.kt            # Generic stat entry
│   │   ├── VisitSnippet.kt         # Live visitor snippet
│   │   └── dashboard/              # Dashboard-specific models
│   ├── config/                     # Configuration system
│   │   ├── ConfigLoader.kt         # Environment variable loader
│   │   ├── ConfigurationException.kt
│   │   └── models/                 # Config data classes
│   │       ├── AppConfig.kt        # Root config (composes others)
│   │       ├── DatabaseConfig.kt
│   │       ├── GeoIPConfig.kt
│   │       ├── PrivacyConfig.kt    # Hash rotation, modes, retention
│   │       ├── RateLimitConfig.kt
│   │       ├── SecurityConfig.kt
│   │       ├── ServerConfig.kt
│   │       └── TrackerConfig.kt    # Heartbeat interval, SPA toggle
│   ├── core/                       # Core domain logic
│   │   ├── AnalyticsSecurity.kt    # Visitor hashing (configurable rotation)
│   │   ├── HTTP.kt                 # CORS & content negotiation
│   │   ├── Security.kt             # Session auth & login page
│   │   ├── ServiceManager.kt       # Lifecycle management + uptime + retention timer
│   │   └── models/
│   ├── db/                         # Database layer
│   │   ├── DatabaseFactory.kt      # Database initialization
│   │   ├── Events.kt               # Events table (8 indexes)
│   │   ├── Projects.kt             # Projects table
│   │   ├── ConversionGoals.kt      # Goals table
│   │   ├── Funnels.kt              # Funnels table
│   │   ├── FunnelSteps.kt          # Funnel steps table
│   │   ├── Segments.kt             # Segments table (JSON filters)
│   │   └── ResetDatabase.kt        # Database reset utility
│   ├── middleware/                  # Request processing middleware
│   │   ├── InputValidator.kt       # Input validation & sanitization
│   │   ├── QueryCache.kt           # Caffeine query cache (30s TTL, 500 entries)
│   │   ├── RateLimiter.kt          # Rate limiting logic
│   │   └── models/
│   ├── services/                   # External service integrations
│   │   ├── GeoLocationService.kt.kt  # GeoIP lookup (with Caffeine cache)
│   │   └── UserAgentParser.kt      # Browser/OS/device detection
│   └── setup/                      # Setup wizard
│       ├── SetupRouting.kt
│       ├── SetupValidation.kt
│       └── models/
├── src/main/resources/
│   ├── application.yaml
│   ├── geo/
│   │   └── geolite2-city.mmdb      # GeoIP database
│   ├── setup/                      # Setup wizard frontend
│   └── static/                     # Admin panel frontend
│       ├── admin.html
│       ├── tracker.js              # Client tracking script (1.9KB)
│       ├── tracker.min.js          # Minified tracker (1.3KB, auto-generated)
│       ├── openapi.yaml            # OpenAPI 3.0.3 specification
│       ├── login/login.html
│       ├── css/                    # base, components, themes, variables
│       └── js/                     # admin, charts, goals, segments, map, settings, theme, utils
├── src/test/kotlin/                # Test suite (288 tests)
└── test-dbs/                       # Test database files (gitignored)
```

## Running the application

### Development
```bash
./gradlew run
```

### Build fat JAR
```bash
./gradlew buildFatJar
```

### Docker
```bash
docker build -t mini-numbers .
docker run -p 8080:8080 mini-numbers
```

### Testing
```bash
./gradlew test
```

## License

MIT License. See [LICENSE](LICENSE) for details.
