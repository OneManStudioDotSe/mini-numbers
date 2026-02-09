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
- `GET /` - Health check ("Hello World!")
- `POST /collect` - Data collection endpoint (accepts page view events)

### Admin Panel (Basic Auth Protected)
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
// Visitor hash generation (SecurityUtils.kt)
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
- Basic HTTP Authentication for admin panel
- Credentials: `admin:your-password` (TODO: Move to environment variables)

### CORS Configuration
- Allows all origins (TODO: Restrict in production)
- Custom headers: `X-Project-Key`, `Authorization`, `Content-Type`

### API Key Validation
- Projects identified by unique API keys
- Keys can be sent via header (`X-Project-Key`) or query parameter (`?key=...`)

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

## Running the Application

### Development Mode
```bash
./gradlew run
```
Server starts at: `http://localhost:8080`

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
```bash
./gradlew test
```

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

## TODOs & Production Considerations

1. **Security:**
   - [ ] Move admin credentials to environment variables
   - [ ] Update `SERVER_SALT` in SecurityUtils.kt
   - [ ] Restrict CORS to specific domains
   - [ ] Add rate limiting

2. **Performance:**
   - [ ] Optimize GeoIP lookup speed
   - [ ] Add caching layer for frequent queries
   - [ ] Implement database query optimization

3. **Configuration:**
   - [ ] Update tracker.js endpoint URL
   - [ ] Configure database connection (PostgreSQL for production)
   - [ ] Set up proper logging levels

4. **Features:**
   - [ ] Add data export (CSV/JSON)
   - [ ] Implement data retention policies
   - [ ] Add email reports
   - [ ] Custom event tracking

## Project Structure

```
mini-numbers/
├── src/main/kotlin/
│   ├── Application.kt           # Main entry point
│   ├── Routing.kt               # API endpoints & admin routes
│   ├── DataAnalysisUtils.kt     # Analytics calculations
│   ├── core/
│   │   ├── HTTP.kt              # CORS & AsyncAPI setup
│   │   └── Security.kt          # Authentication config
│   ├── db/
│   │   ├── DatabaseFactory.kt   # Database initialization
│   │   └── Schema.kt            # Projects & Events tables
│   ├── models/                  # Data classes
│   │   ├── PageViewPayload.kt
│   │   ├── ProjectReport.kt
│   │   ├── ProjectStats.kt
│   │   ├── StatEntry.kt
│   │   ├── VisitSnippet.kt
│   │   └── dashboard/           # Dashboard-specific models
│   ├── services/
│   │   └── GeoLocationService.kt # GeoIP lookup
│   └── utils/
│       ├── SecurityUtils.kt     # Visitor hashing
│       └── UserAgentParser.kt   # Browser/OS detection
├── src/main/resources/
│   ├── application.yaml         # Ktor configuration
│   ├── logback.xml             # Logging config
│   ├── geo/
│   │   └── geolite2-city.mmdb  # GeoIP database
│   └── static/                  # Admin panel frontend
│       ├── admin.html
│       ├── tracker.js           # Client tracking script
│       ├── css/
│       └── js/
└── src/test/kotlin/
    └── ApplicationTest.kt       # Unit tests
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
