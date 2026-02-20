# Mini Numbers - Privacy-First Web Analytics Platform

## Overview

Mini Numbers is a **privacy-focused, minimalist web analytics platform** built with Kotlin and Ktor. It provides essential website traffic insights without compromising visitor privacy - a lightweight, self-hosted alternative to services like Google Analytics.

## Core Philosophy

**Privacy-First Approach:**
- No cookies or persistent tracking identifiers
- IP addresses are processed in-memory only (never stored)
- Visitor hashing uses daily-rotating salt (prevents cross-day tracking)
- No Personally Identifiable Information (PII) is stored
- Compliant with privacy regulations (GDPR-friendly design)

**Minimalist Design:**
- Tiny tracking script (< 2KB)
- Clean, focused feature set
- Fast data collection with minimal overhead
- SQLite or PostgreSQL database options

## Technical Architecture

### Backend Stack
- **Framework:** Ktor 3.4.0 (Kotlin web framework)
- **Runtime:** Kotlin JVM 2.3.0 on JDK 21
- **Database:** Exposed ORM with SQLite/PostgreSQL support
- **Server:** Netty (embedded)
- **Serialization:** kotlinx.serialization
- **Build Tool:** Gradle with Kotlin DSL

### Key Dependencies
- **MaxMind GeoIP2** - IP geolocation lookup (city/country)
- **UserAgentUtils** - Browser/OS/device detection
- **HikariCP** - Database connection pooling
- **Logback** - Logging framework

### Zero-Restart Architecture

**ServiceManager** - Centralized lifecycle management for hot-reload capability:

```kotlin
object ServiceManager {
    enum class State {
        UNINITIALIZED,  // Before setup complete
        INITIALIZING,   // Services being initialized
        READY,          // All services ready
        ERROR           // Initialization failed
    }

    // Initialize services with configuration
    fun initialize(config: AppConfig, logger: Logger): Boolean

    // Reload services with new configuration (no restart)
    fun reload(config: AppConfig, logger: Logger): Boolean

    // Check if services are ready
    fun isReady(): Boolean

    // Get current state
    fun getState(): State

    // Get last error (if any)
    fun getLastError(): Throwable?
}
```

**Initialization Flow:**
1. **Security Module** - Initialize `AnalyticsSecurity` with server salt
2. **Database** - Connect to SQLite or PostgreSQL via `DatabaseFactory`
3. **GeoIP Service** - Load MaxMind database (optional, non-fatal if missing)
4. **HTTP/CORS** - Configure allowed origins and headers
5. **Authentication** - Set up session-based auth with login page
6. **Routing** - Install data collection and admin endpoints

**Benefits:**
- **< 1 second transition** from setup to ready state
- **No JVM restart** - services initialize in-place
- **Thread-safe** - uses `@Synchronized` and `@Volatile`
- **Idempotent** - safe to call `initialize()` multiple times
- **Error resilient** - tracks state and last error for recovery

**ConfigLoader Hot-Reload:**
```kotlin
object ConfigLoader {
    // Check if setup is needed
    fun isSetupNeeded(): Boolean

    // Load configuration from environment
    fun load(): AppConfig

    // Reload configuration (clears cached values)
    fun reload(): AppConfig
}
```

**Unified Routing:**
- Both setup and normal routes coexist in same application
- Root `/` intelligently routes based on `ConfigLoader.isSetupNeeded()` and `ServiceManager.isReady()`
- Setup wizard accessible anytime at `/setup`
- Configuration changes take effect immediately via `reload()`

## Core Features

### 1. Data Collection
- **Real-time Tracking:** Lightweight JavaScript tracker (`tracker.js`)
- **Page Views:** Automatic pageview tracking
- **Heartbeat System:** 30-second intervals to measure time-on-page
- **SPA Support:** Detects route changes in Single Page Applications
- **Session Management:** Uses sessionStorage (no cookies)

### 2. Analytics Dashboard
Located at `/admin-panel`, provides comprehensive insights:

#### Basic Metrics
- Total page views
- Unique visitors (daily-rotated hashes)
- Top pages by traffic
- Last 10 visits with location

#### Detailed Breakdowns (Top 10)
- **Browsers:** Chrome, Firefox, Safari, etc.
- **Operating Systems:** Windows, macOS, Linux, iOS, Android
- **Devices:** Desktop, Mobile, Tablet
- **Referrers:** Traffic sources
- **Geographic Data:** Countries and cities (via GeoIP2)

#### Advanced Analytics
- **Activity Heatmap:** 7 days × 24 hours traffic visualization
- **Peak Time Analysis:** Identifies busiest hours and days
- **Contribution Calendar:** GitHub-style 365-day activity grid with intensity levels
- **Time Series Data:** Trend charts with granular breakdowns (hourly/daily/weekly)
- **Comparison Reports:** Current vs. previous period metrics

#### Live Monitoring
- Real-time visitor feed (last 5 minutes)
- Live location tracking on interactive map

### 3. Multi-Project Support
- Manage multiple websites from one dashboard
- Each project has unique API key
- Per-project statistics and reports
- CRUD operations for projects

### 4. Time Period Filtering
Analytics available for:
- Last 24 hours
- Last 3 days
- Last 7 days (default)
- Last 30 days
- Last 365 days

### 5. Demo Data Generator
**Development & Testing Helper:**
- Located in admin panel header (shown when project selected)
- Generate realistic dummy analytics data
- Configurable event count (0-3000)
- Data characteristics:
  - **Paths:** Home, blog posts, products, docs, pricing, etc.
  - **Referrers:** Google, Twitter, Facebook, GitHub, Reddit, direct traffic
  - **Browsers:** Chrome, Firefox, Safari, Edge, Opera
  - **Operating Systems:** Windows, macOS, Linux, iOS, Android
  - **Devices:** Desktop, Mobile, Tablet
  - **Locations:** 10 countries with realistic cities
  - **Time spread:** Events distributed over last 30 days
  - **Privacy-compliant:** Uses proper visitor hashing and session IDs

**Usage:**
1. Select a project in admin panel
2. Click "Generate Demo Data" button
3. Enter desired event count (e.g., 500)
4. Click "Generate"
5. Dashboard refreshes with new data automatically

**Perfect for:**
- Testing dashboard features
- UI/UX demonstrations
- Performance testing
- Development without real traffic

## Database Schema

### Projects Table
```kotlin
- id: UUID (Primary Key)
- name: String (100 chars)
- domain: String (255 chars)
- apiKey: String (64 chars, unique)
```

### Events Table
```kotlin
- id: Long (Auto-increment)
- projectId: UUID (Foreign Key → Projects)
- visitorHash: String (64 chars) - Daily-rotated SHA-256 hash
- sessionId: String (64 chars)
- eventType: String (20 chars) - "pageview" or "heartbeat"
- path: String (512 chars)
- referrer: String? (512 chars, nullable)
- country: String? (100 chars, nullable)
- city: String? (100 chars, nullable)
- browser: String? (50 chars, nullable)
- os: String? (50 chars, nullable)
- device: String? (50 chars, nullable)
- duration: Int (seconds)
- timestamp: DateTime (indexed)

Indexes:
- idx_events_timestamp
- idx_events_project_timestamp
- idx_events_project_session
```

## API Endpoints

### Public Endpoints
- `GET /` - Intelligent redirect based on application state:
  - No configuration → redirects to `/setup`
  - Services ready → redirects to `/admin-panel`
  - Services failed → shows error message with recovery instructions
- `POST /collect` - Data collection endpoint (accepts page view events)
  - Requires API key via `X-Project-Key` header or `?key=` query parameter
  - Rate limited: configurable per IP and per API key
  - Returns `202 Accepted` on success, `429 Too Many Requests` if rate limit exceeded

### Setup Wizard Endpoints
**Note:** Accessible even when application is not fully configured

- `GET /setup` - Setup wizard static resources (HTML/CSS/JS)
- `GET /setup/api/status` - Check setup status and service readiness
  ```json
  {
    "setupNeeded": false,
    "servicesReady": true,
    "message": "Configuration complete and services ready"
  }
  ```
- `GET /setup/api/generate-salt` - Generate cryptographically secure server salt
  ```json
  {
    "salt": "128-character-hex-string"
  }
  ```
- `POST /setup/api/save` - Save configuration and initialize services
  - Validates configuration server-side
  - Writes `.env` file atomically with backup
  - Reloads configuration and initializes services
  - **No restart required** - services ready in < 1 second

### Admin Panel (Session Auth Protected)
**Base Path:** `/admin`

#### Project Management
- `GET /admin/projects` - List all projects
- `POST /admin/projects` - Create new project
- `DELETE /admin/projects/{id}` - Delete project (cascade deletes events)

#### Analytics & Reports
- `GET /admin/projects/{id}/stats` - Basic statistics
- `GET /admin/projects/{id}/live` - Live visitor feed (last 5 minutes, max 20 entries)
- `GET /admin/projects/{id}/report?filter=7d` - Full analytics report
- `GET /admin/projects/{id}/report/comparison?filter=7d` - Comparison report with time series
- `GET /admin/projects/{id}/calendar` - 365-day contribution calendar

#### Static Assets
- `/admin-panel/*` - Serves static HTML/CSS/JS for admin interface

## Privacy Implementation

### Visitor Identification
```kotlin
// Visitor hash generation (core/AnalyticsSecurity.kt)
visitorHash = SHA256(ip + userAgent + projectId + serverSalt + currentDate)
```

**Privacy guarantees:**
1. IP address exists only in RAM during request processing
2. Hash salt rotates daily - same visitor = different hash each day
3. Cannot reverse-engineer the hash to get IP/user agent
4. No cross-day tracking possible

### Geolocation
- Uses MaxMind GeoLite2 database (offline lookup)
- Only stores country/city names (not coordinates)
- IP never touches the database

### User Agent Parsing
- Extracts browser/OS/device type server-side
- Raw user agent string is not stored

## Client-Side Tracking Script

### Installation
```html
<script
  async
  src="https://your-api-domain.com/admin-panel/tracker.js"
  data-project-key="YOUR_PROJECT_API_KEY">
</script>
```

### How It Works
1. **Session Management:** Generates random session ID in sessionStorage
2. **Initial Tracking:** Sends pageview on load
3. **Heartbeat:** Pings every 30 seconds for time-on-page metrics
4. **SPA Detection:** MutationObserver detects path changes
5. **Reliable Delivery:** Uses `navigator.sendBeacon()` API (non-blocking, survives page unload)

### Payload Structure
```json
{
  "path": "/blog/post-1",
  "referrer": "https://google.com",
  "sessionId": "abc123xyz789",
  "type": "pageview"
}
```

## Security Features

### Authentication
- Session-based authentication with dedicated login page (`/login`)
- Credentials configured via `ADMIN_USERNAME` and `ADMIN_PASSWORD` environment variables
- Login attempts tracked with `LoginAttempt` model
- Session management via `UserSession` with Ktor Sessions plugin

### CORS Configuration
- Configurable via `ALLOWED_ORIGINS` environment variable (comma-separated domains)
- Development mode (`KTOR_DEVELOPMENT=true`): allows all origins automatically
- Production mode: requires explicit origin whitelist
- Custom headers: `X-Project-Key`, `Authorization`, `Content-Type`

### API Key Validation
- Projects identified by unique API keys
- Keys can be sent via header (`X-Project-Key`) or query parameter (`?key=...`)

### Rate Limiting
- Per-IP address limiting (configurable, default: 1000 requests/minute)
- Per-API key limiting (configurable, default: 10000 requests/minute)
- Token bucket algorithm with in-memory Caffeine cache
- HTTP 429 responses with detailed rate limit information

### Input Validation & Sanitization
- Comprehensive validation for path, referrer, sessionId, and event type
- Character whitelisting to prevent XSS/SQL injection
- Control character removal and whitespace normalization
- All validation errors returned at once for faster debugging

## Frontend (Admin Panel)

### Technologies
- Vanilla JavaScript (no frameworks)
- CSS with custom properties (variables)
- Theme support (light/dark mode via `theme.js`)
- Chart visualization (`charts.js`)
- Interactive maps (`map.js`)

### Components
- Project selector
- Time period filters
- Real-time statistics cards
- Trend charts (time series)
- Top lists (pages, referrers, locations, etc.)
- Activity heatmap
- Contribution calendar
- Live visitor map

## Data Analysis Features

### Time Series Generation
```kotlin
generateTimeSeries(projectId, start, end, filter)
```
- Aggregates data by hour/day/week depending on timeframe
- Tracks views + unique visitors over time
- Sorted chronologically for trend visualization

### Heatmap Analytics
```kotlin
generateActivityHeatmap(projectId, cutoff)
```
- 7 days × 24 hours grid
- Shows traffic patterns by day of week and hour
- Enables peak time identification

### Peak Time Analysis
```kotlin
analyzePeakTimes(heatmapData)
```
- Top 5 busiest hours
- Top 3 busiest days
- Overall peak hour and day

### Contribution Calendar
```kotlin
generateContributionCalendar(projectId)
```
- GitHub-style 365-day activity grid
- 5-level intensity scale (0-4)
- Shows daily visits and unique visitors

## First-Time Setup

Mini Numbers features a **WordPress-style web-based setup wizard** that automatically launches when configuration is missing.

### Setup Wizard

**When it appears:**
- On first run (no `.env` file)
- When required configuration is incomplete

**How to trigger:**
1. Delete `.env` file (if exists)
2. Run: `./gradlew run`
3. Visit: `http://localhost:8080`

**Setup Process (5 Steps):**

1. **Security Configuration**
   - Admin username and password
   - Auto-generated cryptographic server salt (128-char hex)
   - Privacy notice and explanation

2. **Database Selection**
   - Visual radio cards: SQLite vs PostgreSQL
   - SQLite: Simple file-based (recommended for getting started)
   - PostgreSQL: Production-ready with connection pooling

3. **Server Configuration**
   - Port number (default: 8080)
   - CORS allowed origins (comma-separated)
   - Development mode toggle

4. **Advanced Settings**
   - GeoIP database path
   - Rate limiting configuration (per IP and per API key)
   - Sensible defaults provided

5. **Review & Confirm**
   - Summary of all settings
   - Passwords masked for security
   - One-click completion

**Quick Setup (Testing):**
- Click "⚡ Quick Setup (Testing)" button in wizard header
- Auto-fills all fields with sensible defaults
- One-click setup completion
- Perfect for rapid development setup

**⭐ Zero-Restart Architecture (NEW):**
After completing setup, the application **no longer requires a restart**! The configuration is:
1. Saved atomically to `.env` file (with `.env.backup`)
2. Reloaded instantly via `ConfigLoader.reload()`
3. Services initialized dynamically via `ServiceManager`
4. Admin panel accessible **within 1 second**
5. Seamless user experience - no waiting for JVM restart

**Technical Flow:**
- Frontend polls `/setup/api/status` every 500ms
- Backend initializes: Security → Database → GeoIP → Routing
- When `servicesReady: true`, automatic redirect to admin panel
- Total transition time: < 1 second (vs. 5-10 seconds with restart)

### Re-running Setup
To reconfigure the application:
```bash
rm .env
./gradlew run
```
Visit `http://localhost:8080` to start fresh setup.

## Running the Application

### Development Mode
```bash
./gradlew run
```
Server starts at: `http://localhost:8080`

**Unified Startup Architecture:**
- Application runs in **unified mode** - both setup and normal routes coexist
- Root `/` intelligently redirects based on state:
  - No configuration → redirects to `/setup` (setup wizard)
  - Configuration exists but services failed → shows error with recovery instructions
  - Services ready → redirects to `/admin-panel`
- **ServiceManager** tracks application state (UNINITIALIZED, INITIALIZING, READY, ERROR)
- Services can be reloaded without restart via `ServiceManager.reload()`

### Build Fat JAR
```bash
./gradlew buildFatJar
```
Executable JAR with all dependencies included.

### Docker
```bash
./gradlew buildImage
./gradlew publishImageToLocalRegistry
./gradlew runDocker
```

### Testing

Mini Numbers includes a comprehensive test suite with **103 tests** covering critical functionality, edge cases, and error scenarios.

```bash
./gradlew test
```

**Test Coverage:**
- **91 tests passing** - Core functionality validated
- **12 tests** - Integration tests requiring specific database state

**Test Organization:**

```
src/test/kotlin/
├── ApplicationTest.kt              # Application tests
├── core/
│   ├── SecurityUtilsTest.kt       (13 tests) ✓
│   │   • Visitor hash generation and uniqueness
│   │   • Daily rotation validation
│   │   • Initialization and reinitialization
│   │   • Thread safety verification
│   │
│   └── ServiceManagerTest.kt      (13 tests) ✓
│       • Service initialization and lifecycle
│       • Configuration hot-reload
│       • Error handling and recovery
│       • State transitions (UNINITIALIZED → READY)
│       • Idempotent initialization
│
├── middleware/
│   └── InputValidatorTest.kt      (26 tests) ✓
│       • PageView payload validation
│       • Path, referrer, sessionId validation
│       • Event type validation (pageview, heartbeat)
│       • Input sanitization (control chars, whitespace)
│       • Length limits and null byte handling
│
├── services/
│   └── UserAgentParserTest.kt     (22 tests) ✓
│       • Browser detection (Chrome, Firefox, Safari, Edge, Opera)
│       • OS detection (Windows, macOS, Linux, iOS, Android)
│       • Device detection (Desktop, Mobile, Tablet)
│       • Edge cases (bots, long strings, special characters)
│       • Thread safety verification
│
└── integration/
    ├── CollectEndpointTest.kt     (19 tests)
    │   • API key validation (header & query param)
    │   • Payload validation and sanitization
    │   • Rate limiting enforcement
    │   • Error responses (400, 404, 429)
    │   • Valid event types (pageview, heartbeat)
    │
    └── SetupWizardTest.kt          (10 tests)
        • Setup status endpoint
        • Salt generation
        • Configuration validation
        • Save endpoint behavior
        • Error handling
```

**What's Tested:**

1. **Security & Privacy:**
   - Visitor hash uniqueness across IPs, user agents, projects
   - Daily hash rotation (prevents cross-day tracking)
   - Thread-safe hash generation under concurrent load

2. **Input Validation:**
   - XSS/SQL injection prevention (via validation rules)
   - Path length limits (512 chars)
   - Referrer length limits (512 chars)
   - Session ID format validation
   - Event type validation

3. **Service Lifecycle:**
   - Zero-restart configuration reload
   - Service initialization (Security → Database → GeoIP)
   - Error recovery and graceful degradation
   - State management and transitions

4. **Data Collection:**
   - Valid payload acceptance
   - Invalid payload rejection
   - API key authentication
   - Missing field detection

5. **Edge Cases:**
   - Empty strings and null values
   - Very long inputs (10,000+ chars)
   - Special characters and Unicode
   - Concurrent requests
   - Control characters and null bytes

**Test Quality:**
- **Fast execution:** < 20 seconds for full suite
- **Isolated:** Tests don't interfere with each other
- **Deterministic:** Consistent results across runs
- **Documented:** Clear test names and comments

## Configuration

### Application Config (`application.yaml`)
```yaml
ktor:
  development: true
  application:
    modules:
      - se.onemanstudio.ApplicationKt.module
  deployment:
    port: 8080
```

### Database Initialization
```kotlin
DatabaseFactory.init() // Sets up SQLite/PostgreSQL connection
```

### GeoIP Database
- Located at: `src/main/resources/geo/geolite2-city.mmdb`
- MaxMind GeoLite2 City database (update periodically)

## Completed Features ✅

1. **Security & Configuration:**
   - ✅ Environment variable system (`.env` file support)
   - ✅ Interactive setup wizard (WordPress-style)
   - ✅ Session-based authentication with login page
   - ✅ Server salt via environment variables
   - ✅ Configurable CORS (allowed origins)
   - ✅ Rate limiting (per IP and per API key)
   - ✅ Input validation & sanitization
   - ✅ Zero-restart configuration hot-reload

2. **Testing:**
   - ✅ Comprehensive test suite (103 tests)
   - ✅ Unit tests (AnalyticsSecurity, UserAgentParser, InputValidator)
   - ✅ Integration tests (/collect endpoint, setup wizard)
   - ✅ ServiceManager lifecycle tests
   - ✅ Thread safety verification

3. **Code Architecture:**
   - ✅ Package-per-feature organization
   - ✅ Configuration split into dedicated models (config/models/)
   - ✅ Database schema split (Events.kt, Projects.kt)
   - ✅ API models organized under api/models/
   - ✅ Setup models organized under setup/models/
   - ✅ Middleware layer (InputValidator, RateLimiter)
   - ✅ Core domain layer (AnalyticsSecurity, ServiceManager, HTTP, Security)

4. **Demo & Development:**
   - ✅ Demo data generator (0-3000 realistic events)
   - ✅ Quick setup button for rapid testing
   - ✅ Atomic .env file writing with backup

## TODOs & Production Considerations

1. **Performance:**
   - [ ] Optimize GeoIP lookup speed (add caching)
   - [ ] Add caching layer for frequent queries (Redis optional)
   - [ ] Implement database query optimization (additional indexes)
   - [ ] Query result caching for dashboard

2. **Deployment:**
   - [ ] Create production Dockerfile (multi-stage build)
   - [ ] docker-compose.yml for easy deployment
   - [ ] Health check endpoint improvements
   - [ ] Metrics endpoint (Prometheus format)

3. **Features:**
   - [ ] Add data export (CSV/JSON)
   - [ ] Implement data retention policies
   - [ ] Add email reports (scheduled)
   - [ ] Custom event tracking
   - [ ] Conversion goals and funnels
   - [ ] User journey visualization

4. **Documentation:**
   - [ ] Deployment guide
   - [ ] API documentation
   - [ ] Privacy policy template
   - [ ] Contributing guidelines

## Project Structure

```
mini-numbers/
├── _docs/                       # Project documentation
│   ├── CHANGELOG.md
│   ├── COMPETITIVE_ANALYSIS.md
│   ├── GAP_ANALYSIS.md
│   ├── MARKET_VIABILITY.md
│   ├── PROJECT_EVALUATION.md
│   ├── PROJECT_STATUS.md
│   ├── ROADMAP.md
│   └── TODO.md
├── src/main/kotlin/
│   ├── Application.kt           # Main entry point (unified mode)
│   ├── Routing.kt               # API endpoints & admin routes
│   ├── DataAnalysisUtils.kt     # Analytics calculations
│   ├── api/models/              # API response/request models
│   │   ├── PageViewPayload.kt   # Data collection payload
│   │   ├── ProjectReport.kt     # Full analytics report
│   │   ├── ProjectStats.kt      # Basic project statistics
│   │   ├── RawEvent.kt          # Raw event data
│   │   ├── RawEventsResponse.kt # Paginated raw events
│   │   ├── StatEntry.kt         # Generic stat entry
│   │   ├── VisitSnippet.kt      # Live visitor snippet
│   │   └── dashboard/           # Dashboard-specific models
│   │       ├── ActivityCell.kt
│   │       ├── ComparisonReport.kt
│   │       ├── ContributionCalendar.kt
│   │       ├── ContributionDay.kt
│   │       ├── PeakTimeAnalysis.kt
│   │       ├── TimeSeriesPoint.kt
│   │       └── TopPage.kt
│   ├── config/                  # Configuration system
│   │   ├── ConfigLoader.kt      # Environment variable loader
│   │   ├── ConfigurationException.kt
│   │   └── models/              # Config data classes (split)
│   │       ├── AppConfig.kt     # Root config (composes others)
│   │       ├── DatabaseConfig.kt
│   │       ├── GeoIPConfig.kt
│   │       ├── RateLimitConfig.kt
│   │       ├── SecurityConfig.kt
│   │       └── ServerConfig.kt
│   ├── core/                    # Core domain logic
│   │   ├── AnalyticsSecurity.kt # Visitor hashing (reinitializable)
│   │   ├── HTTP.kt              # CORS & content negotiation
│   │   ├── Security.kt          # Session auth & login page
│   │   ├── ServiceManager.kt    # Lifecycle management
│   │   └── models/
│   │       ├── LoginAttempt.kt  # Login attempt tracking
│   │       └── UserSession.kt   # Session data class
│   ├── db/                      # Database layer
│   │   ├── DatabaseFactory.kt   # Database initialization
│   │   ├── Events.kt            # Events table schema
│   │   ├── Projects.kt          # Projects table schema
│   │   └── ResetDatabase.kt     # Database reset utility
│   ├── middleware/              # Request processing middleware
│   │   ├── InputValidator.kt    # Input validation & sanitization
│   │   ├── RateLimiter.kt       # Rate limiting logic
│   │   └── models/
│   │       ├── RateLimitBucket.kt
│   │       ├── RateLimitResult.kt
│   │       └── RateLimitStatus.kt
│   ├── services/                # External service integrations
│   │   ├── GeoLocationService.kt.kt  # GeoIP lookup
│   │   └── UserAgentParser.kt   # Browser/OS/device detection
│   └── setup/                   # Setup wizard
│       ├── SetupRouting.kt      # Setup endpoints
│       ├── SetupValidation.kt   # Server-side validation
│       └── models/              # Setup DTOs (split)
│           ├── DatabaseSetupDTO.kt
│           ├── ErrorResponse.kt
│           ├── GeoIPSetupDTO.kt
│           ├── RateLimitSetupDTO.kt
│           ├── SaltResponse.kt
│           ├── SaveResponse.kt
│           ├── ServerSetupDTO.kt
│           ├── SetupConfigDTO.kt
│           ├── SetupStatusResponse.kt
│           └── ValidationResult.kt
├── src/main/resources/
│   ├── application.yaml         # Ktor configuration
│   ├── logback.xml             # Logging config
│   ├── geo/
│   │   └── geolite2-city.mmdb  # GeoIP database
│   ├── setup/                   # Setup wizard frontend
│   │   ├── wizard.html
│   │   ├── css/wizard.css
│   │   └── js/wizard.js
│   └── static/                  # Admin panel frontend
│       ├── admin.html
│       ├── tracker.js           # Client tracking script
│       ├── login/
│       │   └── login.html       # Login page
│       ├── css/
│       │   ├── base.css
│       │   ├── components.css
│       │   ├── themes.css
│       │   └── variables.css
│       └── js/
│           ├── admin.js         # Main admin logic
│           ├── charts.js        # Chart visualization
│           ├── map.js           # Interactive maps
│           ├── settings.js      # Settings panel
│           ├── theme.js         # Light/dark theme
│           └── utils.js         # Shared utilities
└── src/test/kotlin/             # Test suite (103 tests)
    ├── ApplicationTest.kt       # Application tests
    ├── core/
    │   ├── SecurityUtilsTest.kt # Privacy/hashing tests (13)
    │   └── ServiceManagerTest.kt # Service lifecycle tests (13)
    ├── middleware/
    │   └── InputValidatorTest.kt # Validation tests (26)
    ├── services/
    │   └── UserAgentParserTest.kt # UA parsing tests (22)
    └── integration/
        ├── CollectEndpointTest.kt # Data collection tests (19)
        └── SetupWizardTest.kt     # Setup wizard tests (10)
```

## Use Cases

Mini Numbers is ideal for:
- Personal websites and blogs
- Privacy-conscious organizations
- Projects requiring GDPR compliance
- Self-hosted analytics solutions
- Developers wanting lightweight alternative to Google Analytics
- Sites with ethical data handling requirements

## Key Differentiators

1. **No Cookie Consent Required** - No cookies, no consent banners
2. **Zero PII Storage** - IP addresses never persisted
3. **Self-Hosted** - Complete data ownership
4. **Lightweight** - Minimal JavaScript footprint
5. **Real-Time** - Live visitor monitoring
6. **Multi-Project** - Manage multiple sites from one instance
7. **Open Source** - Transparent, auditable code
