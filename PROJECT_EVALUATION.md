# Mini Numbers - Project Evaluation & Competitive Analysis

**Analysis Date**: February 9, 2026
**Project Version**: 0.0.1
**Status**: Beta-Ready (Not Production-Ready)

---

## Executive Summary

**Mini Numbers** is a privacy-focused web analytics platform that is **80-85% complete** with strong fundamentals but requires critical security fixes and comprehensive testing before production deployment.

### Quick Assessment

| Aspect | Status | Rating |
|--------|--------|--------|
| **Core Functionality** | ✅ Complete | 9/10 |
| **Frontend/Dashboard** | ✅ Feature-Rich | 8.5/10 |
| **Backend API** | ✅ Functional | 8/10 |
| **Privacy Design** | ✅ Excellent | 9/10 |
| **Security Posture** | ❌ Critical Issues | 3/10 |
| **Testing Coverage** | ❌ Minimal | 2/10 |
| **Production Readiness** | ❌ Not Ready | 4/10 |
| **Documentation** | ✅ Comprehensive | 9/10 |
| **Integration Ease** | ✅ Simple | 8/10 |

### Key Findings

**✅ Strengths:**
- Unique privacy approach with daily-rotating visitor hashes
- Beautiful, full-featured admin dashboard
- Lightweight architecture with minimal dependencies
- Comprehensive analytics features (heatmaps, calendars, comparisons)
- Modern Kotlin/JVM stack
- Excellent technical documentation

**❌ Critical Blockers:**
- Hardcoded security credentials throughout codebase
- No environment variable support
- Minimal testing (only 1 test file)
- Missing production deployment documentation
- Multiple security vulnerabilities (CORS, rate limiting, input validation)

**⚠️ Feature Gaps:**
- No custom event tracking
- No conversion goals or funnels
- No email reports or webhooks
- Tracker size (4KB) larger than promised (<2KB)
- Missing advanced analytics (cohorts, retention, user journeys)

### Competitive Position

Mini Numbers competes with established players like **Umami** (6,411 stars), **Plausible CE** (19,000 stars), **Matomo** (19,000 stars), and **PostHog** (20,000 stars). While it has unique privacy features and a polished interface, it needs feature parity and production hardening to be competitive.

### Recommendation

**Proceed with development** after addressing critical security issues. The project has strong foundations and unique differentiators but requires 4-6 weeks of security/testing work before beta launch, followed by 8-10 weeks of feature development to reach competitive parity.

---

## 1. Project Overview

### 1.1 Current State Metrics

| Metric | Value |
|--------|-------|
| **Total Lines of Code** | ~5,000 lines |
| **Backend Code** | 968 lines (Kotlin) across 20 files |
| **Frontend Code** | 3,640 lines (HTML/CSS/JS) across 10 files |
| **Tracker Script** | 72 lines, ~4KB |
| **Test Code** | 21 lines (1 test file) |
| **Documentation** | 394 lines (CLAUDE.md) |
| **Database Tables** | 2 (Projects, Events) |
| **API Endpoints** | 9 endpoints |
| **Dependencies** | 17 production libraries |

### 1.2 Technology Stack

**Backend:**
- **Framework**: Ktor 3.4.0 (Kotlin web framework)
- **Language**: Kotlin 2.3.0
- **Runtime**: JVM (JDK 21)
- **Database**: Exposed ORM with SQLite/PostgreSQL support
- **Server**: Netty (embedded)
- **Serialization**: kotlinx.serialization
- **Build Tool**: Gradle 8.x with Kotlin DSL

**Frontend:**
- **Core**: Vanilla JavaScript (no frameworks)
- **Charts**: Chart.js 4.4.0
- **Maps**: Leaflet.js 1.9.4
- **Icons**: Feather Icons
- **Styling**: CSS with custom properties (variables)
- **Build**: None (served as static assets)

**Key Libraries:**
- **MaxMind GeoIP2** (5.0.1) - IP geolocation
- **UserAgentUtils** (1.21) - Browser/OS/device detection
- **HikariCP** (5.0.1) - Connection pooling
- **Logback** (1.5.13) - Logging
- **PostgreSQL Driver** (42.7.7)
- **SQLite JDBC** (3.45.1.0)

### 1.3 Project Structure

```
mini-numbers/
├── src/main/kotlin/               # Backend (968 lines)
│   ├── Application.kt             # Main entry point (27 lines)
│   ├── Routing.kt                 # API endpoints (235 lines)
│   ├── DataAnalysisUtils.kt       # Analytics calculations (265 lines)
│   ├── core/
│   │   ├── HTTP.kt                # CORS & AsyncAPI (31 lines)
│   │   └── Security.kt            # Authentication (21 lines)
│   ├── db/
│   │   ├── DatabaseFactory.kt     # Database initialization (89 lines)
│   │   └── Schema.kt              # Tables definition (41 lines)
│   ├── models/                    # Data classes (14 files, ~140 lines)
│   ├── services/
│   │   └── GeoLocationService.kt  # GeoIP lookup (37 lines)
│   └── utils/
│       ├── SecurityUtils.kt       # Visitor hashing (19 lines)
│       └── UserAgentParser.kt     # Browser/OS detection (53 lines)
├── src/main/resources/
│   ├── application.yaml           # Configuration (8 lines)
│   ├── logback.xml               # Logging config
│   ├── geo/geolite2-city.mmdb    # GeoIP database (binary)
│   └── static/                    # Admin panel (3,640 lines)
│       ├── admin.html            # Dashboard UI (432 lines)
│       ├── tracker.js            # Client script (72 lines)
│       ├── css/                  # Styles (1,776 lines)
│       │   ├── base.css          # Reset & typography (290 lines)
│       │   ├── components.css    # UI components (1,198 lines)
│       │   ├── themes.css        # Light/dark colors (93 lines)
│       │   └── variables.css     # CSS custom properties (195 lines)
│       └── js/                   # Frontend logic (2,616 lines)
│           ├── admin.js          # Dashboard controller (1,161 lines)
│           ├── charts.js         # Chart.js wrapper (652 lines)
│           ├── map.js            # Leaflet maps (646 lines)
│           ├── theme.js          # Theme switching (164 lines)
│           └── utils.js          # Shared utilities (498 lines)
└── src/test/kotlin/
    └── ApplicationTest.kt         # Tests (21 lines, 1 test)
```

---

## 2. Feature Completion Analysis

### 2.1 COMPLETED FEATURES (85% Overall)

#### 2.1.1 Backend Implementation (80% Complete)

**✅ Data Collection (100%)**
- Privacy-first visitor hashing with SHA-256
- Daily salt rotation (prevents cross-day tracking)
- GeoIP lookup for country/city detection
- User agent parsing for browser/OS/device
- Event types: pageview, heartbeat
- Session management via session IDs
- Referrer tracking
- Timestamp recording with timezone

**✅ Database Layer (95%)**
- Projects table with UUID primary keys
- Events table with comprehensive fields
- Proper foreign key relationships
- Performance indexes (timestamp, project+timestamp, project+session)
- Automatic schema creation
- SQLite and PostgreSQL support
- Connection pooling with HikariCP

**✅ API Endpoints (90%)**
- `POST /collect` - Data collection with API key validation
- `GET /admin/projects` - List all projects
- `POST /admin/projects` - Create projects with auto-generated API keys
- `DELETE /admin/projects/{id}` - Delete projects (cascade deletes events)
- `GET /admin/projects/{id}/stats` - Basic statistics
- `GET /admin/projects/{id}/live` - Live feed (last 5 minutes, 20 events max)
- `GET /admin/projects/{id}/report` - Full analytics report
- `GET /admin/projects/{id}/report/comparison` - Period comparison with time series
- `GET /admin/projects/{id}/calendar` - 365-day contribution calendar

**✅ Analytics Engine (95%)**
- Total page views calculation
- Unique visitors tracking (daily-hashed)
- Top pages ranking (top 10)
- Browser distribution breakdown
- Operating system breakdown
- Device type breakdown
- Referrer source breakdown
- Geographic distribution (country/city)
- Time series data generation (hourly/daily/weekly granularity)
- Activity heatmap (7 days × 24 hours)
- Peak time analysis (top 5 hours, top 3 days)
- Contribution calendar with intensity levels (0-4)
- Period-over-period comparison
- Last visits feed with timestamps

**✅ Security & Privacy (85%)**
- Basic HTTP authentication for admin panel
- API key validation (header or query parameter)
- Visitor hash generation with daily rotation
- IP address never persisted to database
- No personally identifiable information (PII) stored
- CORS configuration for cross-origin requests

**✅ Services (90%)**
- GeoLocationService with MaxMind GeoIP2 integration
- UserAgentParser with browser/OS/device detection
- Graceful degradation when GeoIP database missing
- Error handling with sensible defaults ("Unknown")

#### 2.1.2 Frontend Dashboard (90% Complete)

**✅ Core Dashboard (100%)**
- Project selector with dropdown
- Time period filtering (24h, 3d, 7d, 30d, 365d)
- Automatic data refresh
- Responsive sidebar navigation
- Mobile-friendly hamburger menu
- Toast notification system (success, error, warning, info)

**✅ Statistics Cards (100%)**
- Total page views with sparkline
- Unique visitors with sparkline
- Period-over-period comparison with percentage changes
- Directional indicators (up/down arrows)
- Color-coded trends (green=up, red=down)

**✅ Data Visualizations (95%)**
- **Time Series Chart**: Line chart with area fill, shows views over time
- **Activity Heatmap**: 7×24 matrix showing traffic patterns by day/hour
- **Browser Distribution**: Doughnut chart with toggle to bar/radar views
- **OS Distribution**: Doughnut chart with toggle to bar/radar views
- **Device Distribution**: Doughnut chart with toggle to bar/radar views
- **Geographic Map**: Leaflet map with country markers + fallback bar chart
- **Top Pages**: Horizontal bar chart (top 10)
- **Top Referrers**: Horizontal bar chart (top 10)
- **Countries**: Horizontal bar chart (top 15) with map toggle
- **Peak Times**: Lists showing top 5 hours and top 3 days
- **Contribution Calendar**: GitHub-style 365-day activity grid

**✅ Interactive Features (90%)**
- Multi-dimensional filtering (browser, OS, device, country, referrer)
- Dynamic value selection based on chosen dimension
- Real-time chart re-aggregation on filter changes
- Clear filter button
- Filter warning indicator
- Chart view toggles (doughnut/bar/radar, chart/map)
- Table search with debouncing (300ms)
- Multi-column sorting (path, city, time)

**✅ Data Export (100%)**
- CSV export for top pages
- CSV export for browsers
- CSV export for operating systems
- CSV export for devices
- CSV export for referrers
- CSV export for countries
- CSV export for recent activity
- Full report export (combines all sections)
- Proper CSV formatting with quote escaping
- Timestamped filenames: `{project}_{section}_{date}.csv`

**✅ Theme System (100%)**
- Light and dark mode support
- System preference detection
- localStorage persistence
- Dynamic chart color updates on theme switch
- 10 chart-specific colors per theme
- CSS variable-based theming
- Smooth transitions

**✅ Live Features (100%)**
- Real-time activity feed (updates every 5 seconds)
- Last 5 minutes of visitor activity
- Shows path, city, and timestamp
- Auto-scroll to new entries

**✅ UI Polish (85%)**
- Consistent design language
- Color-coded metrics
- Hover effects and transitions
- Responsive grid layouts
- Proper spacing and typography
- Icon system (Feather Icons)
- Loading spinners (placeholder functions)

#### 2.1.3 Tracking Script (95% Complete)

**✅ Core Tracking (100%)**
- Automatic pageview tracking on load
- Session ID generation and storage (sessionStorage)
- Referrer tracking
- navigator.sendBeacon() for reliable delivery
- Fallback to fetch() for older browsers
- Non-blocking async execution

**✅ Advanced Tracking (90%)**
- Heartbeat system (30-second intervals)
- SPA detection via MutationObserver
- Path change detection
- Automatic re-tracking on route changes

**✅ Configuration (70%)**
- API key via data attribute
- Event type parameter
- JSON payload structure

### 2.2 INCOMPLETE & MISSING FEATURES

#### 2.2.1 Critical Security Issues (MUST FIX)

**❌ Hardcoded Credentials**
- **Location**: `src/main/kotlin/core/Security.kt:12`
- **Issue**: Admin credentials hardcoded as `admin:your-password`
- **Risk**: CRITICAL - Anyone can access admin panel
- **Fix**: Move to environment variables (ADMIN_USERNAME, ADMIN_PASSWORD)

**❌ Hardcoded Server Salt**
- **Location**: `src/main/kotlin/utils/SecurityUtils.kt:8`
- **Issue**: Default salt `"change-this-to-a-long-secret-string"`
- **Risk**: HIGH - Weakens visitor hash security, predictable across instances
- **Fix**: Generate unique salt per deployment via environment variable

**❌ Unrestricted CORS**
- **Location**: `src/main/kotlin/core/HTTP.kt:28`
- **Issue**: `anyHost()` allows all origins
- **Risk**: HIGH - CSRF attacks, unauthorized data collection
- **Fix**: Restrict to specific domains via environment variable

**❌ Hardcoded Tracker Endpoint**
- **Location**: `src/main/resources/static/tracker.js:14`
- **Issue**: Endpoint URL hardcoded to placeholder
- **Risk**: HIGH - Tracking fails if not manually updated
- **Fix**: Dynamic configuration or build-time substitution

**❌ No Rate Limiting**
- **Location**: N/A (missing feature)
- **Issue**: No protection against abuse
- **Risk**: MEDIUM - DDoS attacks, data pollution
- **Fix**: Implement rate limiting middleware

**❌ No Input Validation**
- **Location**: `src/main/kotlin/Routing.kt:38-88`
- **Issue**: Payload fields not validated (path, referrer)
- **Risk**: MEDIUM - Injection attacks, data corruption
- **Fix**: Add input sanitization and size limits

#### 2.2.2 Missing Backend Features

**❌ Custom Event Tracking**
- No ability to track custom events (button clicks, form submissions, etc.)
- No event properties or metadata
- Competitor status: Umami ✅, Plausible ✅, Matomo ✅, PostHog ✅

**❌ Conversion Goals**
- No goal tracking (URL-based or event-based)
- No conversion rate calculations
- Competitor status: Umami ✅, Plausible ✅, Matomo ✅, PostHog ✅

**❌ Funnels**
- No multi-step funnel tracking
- No drop-off analysis
- Competitor status: Umami ✅ (v3), Matomo ✅, PostHog ✅

**❌ Email Reports**
- No scheduled email reports
- No report templates
- Competitor status: Umami ✅, Plausible ✅, Matomo ✅, PostHog ✅

**❌ Webhooks**
- No webhook support for real-time events
- No integrations with external services
- Competitor status: Umami ✅, Matomo ✅, PostHog ✅

**❌ API Pagination**
- All list endpoints return unbounded results
- Potential memory issues with large datasets
- Risk: Performance degradation

**❌ Query Caching**
- No caching layer for frequently accessed data
- Every request hits database
- Risk: Poor performance under load

**❌ Data Retention Policies**
- No automatic data cleanup
- Database grows indefinitely
- Risk: Storage costs, performance degradation

#### 2.2.3 Missing Frontend Features

**❌ Loading States**
- **Location**: `src/main/resources/static/js/admin.js` (empty stubs)
- **Issue**: showLoadingState() and hideLoadingState() are placeholders
- **Impact**: No visual feedback during data loads

**❌ Chart.Geo Integration**
- **Location**: `src/main/resources/static/js/map.js:273`
- **Issue**: TODO comment, falls back to bar chart
- **Impact**: No proper choropleth/geographic chart

**❌ Advanced Analytics UI**
- No funnel visualization
- No cohort analysis interface
- No retention charts
- No user journey visualization

**❌ Customizable Dashboards**
- Fixed dashboard layout
- No widget rearrangement
- No saved custom views

**❌ Report Scheduling**
- No scheduled report generation
- No email delivery

**❌ Alerts & Notifications**
- No threshold-based alerts
- No anomaly detection
- No notification system

**❌ Comprehensive Error Boundaries**
- Limited error handling
- Basic fallback UI only
- No detailed error recovery

**❌ Accessibility Improvements**
- Basic ARIA labels only
- Missing form labels
- Incomplete semantic structure
- No keyboard navigation optimization

#### 2.2.4 Testing Gaps (CRITICAL)

**❌ Minimal Test Coverage**
- Only 1 test file with 1 test (21 lines)
- Test only verifies root endpoint returns 200 OK
- **Coverage**: <5% estimated

**Missing Test Types:**
- ❌ Unit tests for utility functions
- ❌ Integration tests for API endpoints
- ❌ Security tests (auth bypass, injection)
- ❌ Database tests
- ❌ Analytics calculation tests
- ❌ GeoIP lookup tests
- ❌ User agent parsing tests
- ❌ End-to-end tests
- ❌ Load/performance tests
- ❌ JavaScript/tracker tests

#### 2.2.5 Deployment & Operations Gaps

**❌ No Production Documentation**
- No deployment guide
- No environment variable documentation
- No Docker production configuration
- No Kubernetes manifests
- No reverse proxy examples (nginx/Apache)
- No SSL/TLS setup guide

**❌ No Environment Variable Support**
- Database connection hardcoded
- Credentials hardcoded
- CORS origins hardcoded
- GeoIP path hardcoded
- All configuration requires code changes

**❌ No Monitoring/Observability**
- No metrics endpoint
- No health check endpoint
- No structured logging
- No error tracking integration
- No performance monitoring

**❌ No Backup/Restore Procedures**
- No backup scripts
- No restore documentation
- No data migration tools

**❌ No Scaling Guidelines**
- No horizontal scaling documentation
- No load balancing examples
- No database replication setup

#### 2.2.6 Performance Issues

**❌ Tracker Size (4KB vs Promised <2KB)**
- **Location**: `src/main/resources/static/tracker.js`
- **Issue**: 72 lines, ~4KB unminified
- **Competitor comparison**: Umami <1KB, Plausible <1KB
- **Fix**: Minification, code optimization

**❌ GeoIP Lookup Not Optimized**
- **Location**: `src/main/kotlin/services/GeoLocationService.kt.kt:16`
- **Issue**: TODO comment, no caching
- **Impact**: Every request does fresh lookup
- **Fix**: Implement IP range caching

**❌ SPA Detection Performance**
- **Location**: `src/main/resources/static/tracker.js:64-70`
- **Issue**: MutationObserver watches entire DOM
- **Impact**: CPU overhead on frequent DOM updates
- **Fix**: Use history API events (popstate/pushstate)

**❌ No Database Query Optimization**
- No query result caching
- Missing indexes on frequently queried columns (path, browser, os, device)
- All aggregations done in single transaction (potential locks)

---

## 3. Competitive Comparison

### 3.1 Comprehensive Feature Matrix

| Feature Category | Mini Numbers | Umami | Plausible CE | Matomo | PostHog | Fathom | Simple Analytics |
|------------------|--------------|-------|--------------|--------|---------|--------|------------------|
| **Basic Analytics** |
| Page views tracking | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Unique visitors | ✅ (daily hash) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Referrer tracking | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Geographic data | ✅ (country/city) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Device detection | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Browser detection | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| OS detection | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Time series | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Real-time feed | ✅ (5 min) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Bounce rate | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Session duration | ⚠️ (captured, not analyzed) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Advanced Analytics** |
| Custom events | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| Event properties | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| Conversion goals | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Funnels | ❌ | ✅ (v3) | ❌ (paid) | ✅ | ✅ | ❌ | ❌ |
| Cohort analysis | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| Retention analysis | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| User journey | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| Session replay | ❌ | ❌ | ❌ | ✅ (paid) | ✅ | ❌ | ❌ |
| Heatmaps (activity) | ✅ | ❌ | ❌ | ✅ (paid, click) | ❌ | ❌ | ❌ |
| A/B testing | ❌ | ❌ | ❌ | ✅ (paid) | ✅ | ❌ | ❌ |
| Feature flags | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Error tracking | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| **Privacy & Compliance** |
| Cookie-free tracking | ✅ | ✅ | ✅ | ⚠️ (optional) | ⚠️ (config) | ✅ | ✅ |
| No PII storage | ✅ | ✅ | ✅ | ⚠️ (partial) | ⚠️ (config) | ✅ | ✅ |
| IP anonymization | ✅ (never stored) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| GDPR compliant | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Daily hash rotation | ✅ (unique) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| No consent banner needed | ✅ | ✅ | ✅ | ⚠️ | ⚠️ | ✅ | ✅ |
| EU data hosting | ⚠️ (self-hosted) | ✅ (cloud) | ✅ (cloud) | ✅ | ✅ | ✅ | ✅ |
| **Integration & API** |
| REST API | ✅ (basic) | ✅ | ✅ | ✅ (extensive) | ✅ (extensive) | ✅ | ✅ |
| GraphQL API | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Webhooks | ❌ | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ |
| Data export (CSV) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Data export (JSON) | ⚠️ (API only) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Email reports | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Scheduled reports | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Segments/filters | ⚠️ (basic) | ✅ (v3) | ⚠️ (basic) | ✅ (advanced) | ✅ (advanced) | ⚠️ | ⚠️ |
| Custom dashboards | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| **UI & UX** |
| Dark mode | ✅ | ✅ | ❌ | ⚠️ (limited) | ✅ | ❌ | ❌ |
| Contribution calendar | ✅ (unique) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Multiple chart types | ✅ (6 types) | ⚠️ (3 types) | ⚠️ (basic) | ✅ (extensive) | ✅ (extensive) | ⚠️ (basic) | ⚠️ (basic) |
| Interactive maps | ✅ (Leaflet) | ❌ | ❌ | ✅ | ⚠️ | ❌ | ❌ |
| Period comparison | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Data visualization | ✅ (excellent) | ⚠️ (good) | ⚠️ (basic) | ✅ (excellent) | ✅ (excellent) | ⚠️ (basic) | ⚠️ (basic) |
| Mobile responsive | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Single-page dashboard | ⚠️ (multi-section) | ✅ | ✅ | ❌ (tabs) | ❌ (tabs) | ✅ | ✅ |
| **Technical** |
| Multi-project support | ✅ | ✅ | ⚠️ (limited CE) | ✅ | ✅ | ✅ | ✅ |
| Self-hosted option | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| Cloud option | ❌ | ✅ ($20/mo) | ✅ ($9/mo) | ✅ (€19/mo) | ✅ (free tier) | ✅ ($15/mo) | ✅ (€19/mo) |
| Docker support | ✅ | ✅ | ✅ | ✅ | ✅ | N/A | N/A |
| Docker Compose | ⚠️ (manual) | ✅ | ✅ | ✅ | ✅ | N/A | N/A |
| Kubernetes manifests | ❌ | ✅ | ✅ | ❌ | ✅ | N/A | N/A |
| Database options | SQLite, PostgreSQL | MySQL, PostgreSQL | PostgreSQL, ClickHouse | MySQL, MariaDB | PostgreSQL, ClickHouse | N/A | N/A |
| Horizontal scaling | ⚠️ (untested) | ✅ | ✅ | ✅ | ✅ | N/A | N/A |
| **Developer Experience** |
| Tracker size | ~4KB | <1KB ⭐ | <1KB ⭐ | ~21KB | ~44KB | <1KB ⭐ | <1KB ⭐ |
| API documentation | ⚠️ (partial) | ✅ | ✅ | ✅ (extensive) | ✅ (extensive) | ✅ | ⚠️ |
| SDK support | ❌ | ✅ (Node) | ❌ | ✅ (multiple) | ✅ (extensive) | ❌ | ❌ |
| Plugin system | ❌ | ❌ | ❌ | ✅ (extensive) | ✅ | ❌ | ❌ |
| Open source | ⚠️ (TBD) | ✅ (MIT) | ✅ (AGPL) | ✅ (GPL-3.0) | ✅ (MIT) | ❌ | ❌ |
| GitHub stars | N/A (new) | ~6,400 | ~19,000 | ~19,000 | ~20,000 | N/A | N/A |
| Active development | ✅ (new) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Stack** |
| Language | Kotlin | TypeScript | Elixir | PHP | Python/TypeScript | N/A | N/A |
| Database | SQLite/PG | MySQL/PG | PG/ClickHouse | MySQL/MariaDB | PG/ClickHouse | N/A | N/A |
| Frontend | Vanilla JS | React | React | Angular/Vue | React | N/A | N/A |

### 3.2 Scoring Summary

| Platform | Basic Features | Advanced Features | Privacy | Integration | UI/UX | Technical | Overall |
|----------|----------------|-------------------|---------|-------------|-------|-----------|---------|
| **Mini Numbers** | 8.5/10 | 4/10 | 9/10 ⭐ | 6/10 | 8.5/10 | 7/10 | **7.2/10** |
| **Umami** | 9/10 | 6/10 | 8/10 | 7/10 | 7/10 | 8/10 | **7.5/10** |
| **Plausible CE** | 8.5/10 | 5/10 | 9/10 | 6/10 | 6/10 | 8/10 | **7.1/10** |
| **Matomo** | 9.5/10 | 9/10 | 7/10 | 9/10 ⭐ | 8/10 | 8/10 | **8.4/10** ⭐ |
| **PostHog** | 9/10 | 10/10 ⭐ | 7/10 | 10/10 ⭐ | 9/10 | 9/10 | **9.0/10** ⭐ |
| **Fathom** | 8/10 | 3/10 | 10/10 ⭐ | 5/10 | 6/10 | N/A | **6.4/10** |
| **Simple Analytics** | 8/10 | 2/10 | 10/10 ⭐ | 5/10 | 6/10 | N/A | **6.2/10** |

**Key**: ⭐ = Market leader in category

---

## 4. Competitor Deep-Dive

### 4.1 Umami (Primary Competitor for Mini Numbers)

**Positioning**: Simple, fast, privacy-focused Google Analytics alternative

**Key Stats:**
- **GitHub Stars**: ~6,400
- **License**: MIT
- **Stack**: Node.js (Next.js), TypeScript, React
- **Database**: MySQL or PostgreSQL
- **Tracker Size**: <1KB (minified)
- **Cloud Pricing**: Free tier (3 sites, 100k events/month), Paid from $20/month

**Strengths:**
- Proven track record with established community
- v3 features: Links tracking, pixels, segments, data export UI
- Very lightweight tracker (<1KB vs Mini Numbers' 4KB)
- Active development with frequent updates
- Both self-hosted and cloud options
- MIT license (permissive, business-friendly)
- Node.js/React stack (popular, easy to find developers)

**Weaknesses:**
- No activity heatmap visualization
- No contribution calendar
- No daily hash rotation (less privacy protection)
- Smaller feature set compared to Matomo
- Limited advanced analytics (no session replay, no A/B testing)

**Comparison vs Mini Numbers:**
- **Umami wins**: Community size, tracker size (<1KB), cloud option, custom events, MIT license
- **Mini Numbers wins**: Daily hash rotation (stronger privacy), contribution calendar, activity heatmap, JVM stack (enterprise appeal), theme system
- **Tie**: Basic analytics features, GDPR compliance, UI quality

**Market Position**: Strong choice for developers wanting simplicity and proven reliability.

---

### 4.2 Plausible CE (Community Edition)

**Positioning**: Lightweight, GDPR-compliant, EU-focused analytics

**Key Stats:**
- **GitHub Stars**: ~19,000
- **License**: AGPL-3.0
- **Stack**: Elixir (Phoenix), ClickHouse, React
- **Database**: PostgreSQL + ClickHouse
- **Tracker Size**: <1KB (minified)
- **Cloud Pricing**: From $9/month (10k pageviews), $19/month (100k pageviews)
- **CE Update Frequency**: Twice per year (slower than cloud)

**Strengths:**
- Largest GitHub star count in analytics space
- <1KB tracker (extremely lightweight)
- Strong EU presence and GDPR focus
- Elixir + ClickHouse stack (high performance)
- Beautiful, minimalist UI
- Strong brand recognition
- Cloud service is very popular ($9/mo entry)

**Weaknesses:**
- AGPL license (restrictive copyleft, limits commercial use)
- Community Edition updated only twice/year
- Premium features (funnels, ecommerce) not in CE
- Limited CE support (community only)
- Multi-project support limited in CE
- No advanced analytics in CE (cohorts, retention)

**Comparison vs Mini Numbers:**
- **Plausible wins**: Brand recognition (19k stars), tracker size (<1KB), cloud option, performance (ClickHouse), update frequency
- **Mini Numbers wins**: Daily hash rotation, contribution calendar, activity heatmap, license flexibility (TBD), multi-project support
- **Tie**: GDPR compliance, basic analytics, UI quality

**Market Position**: Market leader in privacy-focused analytics with strong cloud business and open-source community.

---

### 4.3 Matomo (Enterprise Leader)

**Positioning**: Enterprise-grade, feature-rich Google Analytics replacement

**Key Stats:**
- **GitHub Stars**: ~19,000
- **License**: GPL-3.0
- **Stack**: PHP (Symfony), MySQL/MariaDB, Angular/Vue
- **Database**: MySQL or MariaDB
- **Tracker Size**: ~21KB
- **Cloud Pricing**: From €19/month (50k pageviews)
- **History**: 15+ years of development (formerly Piwik)

**Strengths:**
- Most comprehensive feature set (100+ features)
- 100% data sampling (unlike Google Analytics)
- Session recording, heatmaps (click-based), A/B testing
- Extensive plugin ecosystem (1,000+ plugins)
- User journey visualization
- Cohort and retention analysis
- eCommerce tracking
- Custom dimensions and metrics
- Multi-user support with roles
- White-labeling options
- 15+ years of maturity
- PHP stack (easy to host on shared hosting)

**Weaknesses:**
- Heavier footprint (~21KB tracker vs <1KB competitors)
- Many premium features require paid plugins
- More complex setup and configuration
- PHP stack (less modern than Node/Elixir/Kotlin)
- UI can be overwhelming (too many features)
- Cookie-based tracking by default (optional cookieless)

**Comparison vs Mini Numbers:**
- **Matomo wins**: Feature breadth (10x more features), enterprise features, plugin ecosystem, maturity, extensive documentation, custom events, funnels, cohorts, session replay, A/B testing
- **Mini Numbers wins**: Lightweight (4KB vs 21KB), modern stack (Kotlin vs PHP), daily hash rotation, contribution calendar, simpler UI, cookie-free by default
- **Tie**: Self-hosted option, multi-project support, GDPR compliance

**Market Position**: The go-to choice for enterprises needing comprehensive analytics with full data ownership.

---

### 4.4 PostHog (All-in-One Platform)

**Positioning**: Complete developer platform (analytics + feature flags + session replay + more)

**Key Stats:**
- **GitHub Stars**: ~20,000
- **License**: MIT (with proprietary Enterprise Edition features)
- **Stack**: Python (Django), TypeScript (React), ClickHouse
- **Database**: PostgreSQL + ClickHouse
- **Tracker Size**: ~44KB (includes session replay)
- **Cloud Pricing**: Free tier (1M events/month), Scale plan usage-based
- **Funding**: Well-funded ($27M Series B)

**Strengths:**
- All-in-one platform (analytics + feature flags + experiments + session replay + error tracking + data warehouse)
- Most advanced product analytics (funnels, cohorts, retention, user paths, SQL insights)
- AI product assistant
- Mobile SDK support (iOS, Android, React Native, Flutter)
- Data warehouse integration (BigQuery, Snowflake, Postgres)
- Event autocapture (no manual instrumentation)
- GraphQL API
- Extensive documentation and learning resources
- Strong developer community
- Generous free tier (1M events)
- MIT license (permissive)

**Weaknesses:**
- Much broader scope than pure analytics (may be overkill)
- Heavier resource requirements
- Larger tracker size (44KB)
- More complex to self-host
- Developer-focused (less suitable for business users)
- Steeper learning curve
- Some features proprietary (Enterprise Edition)

**Comparison vs Mini Numbers:**
- **PostHog wins**: Feature breadth (5x more features), advanced analytics (funnels, cohorts, retention, user paths), session replay, feature flags, A/B testing, mobile SDKs, data warehouse, GraphQL API, cloud free tier, community size
- **Mini Numbers wins**: Lightweight (4KB vs 44KB), simplicity, focus on web analytics only, daily hash rotation, contribution calendar, easier to understand/deploy
- **Tie**: GDPR compliance, self-hosted option, modern stack

**Market Position**: The developer's choice for a complete product analytics platform, but overkill if you only need web analytics.

---

### 4.5 Fathom Analytics (Privacy-First SaaS)

**Positioning**: Simple, privacy-first cloud analytics (no self-hosted option)

**Key Stats:**
- **Type**: Closed-source SaaS only
- **Stack**: Proprietary
- **Tracker Size**: <1KB
- **Pricing**: From $15/month (100k pageviews), $60/month (1M pageviews)
- **Privacy**: GDPR, CCPA, PECR compliant by default

**Strengths:**
- Extremely simple setup and UI
- Pioneer in privacy-friendly analytics
- Beautiful single-page dashboard
- Ad-blocker bypass technology (ethical method)
- Email reports
- GDPR compliance without consent banners
- Excellent customer support
- Fast performance
- Affordable pricing ($15/mo vs Plausible $19/mo)

**Weaknesses:**
- Closed-source (no transparency)
- Cloud-only (no self-hosted option)
- Limited features (no custom events in basic plan)
- No funnels, cohorts, or advanced analytics
- No API access in lower tiers
- Vendor lock-in
- Limited integrations

**Comparison vs Mini Numbers:**
- **Fathom wins**: Cloud simplicity, proven business model, customer support, ad-blocker bypass
- **Mini Numbers wins**: Open-source, self-hosted, daily hash rotation, contribution calendar, activity heatmap, more features, no recurring costs
- **Tie**: Privacy focus, lightweight tracker, GDPR compliance

**Market Position**: Best for non-technical users wanting simple cloud analytics with strong privacy guarantees.

---

### 4.6 Simple Analytics (EU-Based SaaS)

**Positioning**: 100% GDPR compliant, EU-hosted, simple analytics

**Key Stats:**
- **Type**: Closed-source SaaS only
- **Stack**: Proprietary
- **Tracker Size**: <1KB
- **Pricing**: From €19/month (100k pageviews)
- **Privacy**: 100% GDPR, ePrivacy Directive, UK GDPR, PECR compliant

**Strengths:**
- 100% GDPR compliant (no personal data collected)
- All data stored in EU
- No cookies or tracking (fully anonymous)
- Simple, clean interface
- Email reports
- Automatic compliance (no consent needed)
- Referrer spam filtering

**Weaknesses:**
- Closed-source
- Cloud-only (no self-hosted)
- Very limited features (no custom events, goals, or funnels)
- No advanced analytics
- No API access in starter plan
- Higher pricing (€19/mo vs competitors)
- Smaller feature set than competitors

**Comparison vs Mini Numbers:**
- **Simple Analytics wins**: Cloud simplicity, automatic EU hosting
- **Mini Numbers wins**: Open-source, self-hosted, daily hash rotation, more features, contribution calendar, activity heatmap, no recurring costs, better UI
- **Tie**: Privacy focus, lightweight tracker, GDPR compliance

**Market Position**: Best for EU-based businesses prioritizing data sovereignty and automatic compliance.

---

## 5. Gap Analysis & Recommendations

### 5.1 Critical Priority (Before Any Launch)

**Timeline**: 4-6 weeks | **Risk Level**: CRITICAL

#### 1. Security Hardening (Week 1-2)

**a. Move All Credentials to Environment Variables**
- `ADMIN_USERNAME` and `ADMIN_PASSWORD` (Security.kt)
- `SERVER_SALT` (SecurityUtils.kt)
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `GEOIP_DATABASE_PATH`
- Implementation: Use Ktor's environment config or external config file

**b. Implement Proper CORS**
- Replace `anyHost()` with configurable origins
- Environment variable: `ALLOWED_ORIGINS` (comma-separated list)
- Default: reject all (fail-secure)

**c. Add Rate Limiting**
- Implement rate limiting middleware for `/collect` endpoint
- Configure limits: 100 requests/minute per API key
- Environment variables: `RATE_LIMIT_REQUESTS`, `RATE_LIMIT_WINDOW`
- Return 429 (Too Many Requests) when exceeded

**d. Input Validation & Sanitization**
- Validate all payload fields (path, referrer, sessionId)
- Enforce size limits: path (512 chars), referrer (512 chars)
- Sanitize inputs to prevent injection attacks
- Return 400 (Bad Request) with validation errors

**e. Security Audit**
- SQL injection testing (Exposed should handle, but verify)
- XSS vulnerability testing
- CSRF protection review
- Authentication bypass testing
- Dependency vulnerability scan (`./gradlew dependencyCheckAnalyze`)

#### 2. Comprehensive Testing (Week 2-4)

**a. Unit Tests (Target: 80% coverage)**
- SecurityUtils.generateVisitorHash() - various inputs, edge cases
- UserAgentParser.parse*() methods - known user agents, edge cases
- DataAnalysisUtils helper functions - time periods, aggregations
- Model serialization/deserialization

**b. Integration Tests**
- POST /collect - valid/invalid payloads, missing API keys, rate limiting
- GET /admin/projects - authentication, authorization
- POST /admin/projects - validation, API key generation
- DELETE /admin/projects - cascade deletion, non-existent projects
- GET /admin/projects/{id}/report - all time filters, empty data
- GET /admin/projects/{id}/live - real-time updates
- GET /admin/projects/{id}/calendar - date ranges

**c. Security Tests**
- Authentication bypass attempts
- CORS violation attempts
- SQL injection attempts
- XSS payload attempts
- Rate limiting enforcement
- Input validation edge cases

**d. Database Tests**
- Schema creation and migration
- Foreign key constraints
- Index performance
- Transaction rollbacks
- Concurrent write handling (SQLite single-writer lock)

**e. End-to-End Tests**
- Full tracking workflow (tracker.js → /collect → database → dashboard)
- Project CRUD operations
- Dashboard data visualization
- CSV export functionality
- Filter and search operations

#### 3. Production Configuration (Week 3-5)

**a. Environment Variable System**
```kotlin
// Example environment configuration
data class Config(
    val database: DatabaseConfig,
    val security: SecurityConfig,
    val server: ServerConfig,
    val geoip: GeoIPConfig
)

data class DatabaseConfig(
    val driver: String = System.getenv("DB_DRIVER") ?: "org.sqlite.JDBC",
    val url: String = System.getenv("DB_URL") ?: "jdbc:sqlite:./stats.db",
    val username: String? = System.getenv("DB_USERNAME"),
    val password: String? = System.getenv("DB_PASSWORD")
)

data class SecurityConfig(
    val adminUsername: String = System.getenv("ADMIN_USERNAME") ?: throw IllegalStateException("ADMIN_USERNAME required"),
    val adminPassword: String = System.getenv("ADMIN_PASSWORD") ?: throw IllegalStateException("ADMIN_PASSWORD required"),
    val serverSalt: String = System.getenv("SERVER_SALT") ?: throw IllegalStateException("SERVER_SALT required"),
    val allowedOrigins: List<String> = System.getenv("ALLOWED_ORIGINS")?.split(",") ?: emptyList()
)
```

**b. Docker Production Configuration**
- Create production Dockerfile (multi-stage build)
- Docker Compose with environment variables
- Volume mounts for database and GeoIP database
- Health check endpoint
- Graceful shutdown handling

**c. Deployment Documentation**
- README.md: Quick start for development
- DEPLOYMENT.md: Production deployment guide
  - Docker deployment
  - Binary deployment (fat JAR)
  - Systemd service setup
  - Reverse proxy configuration (nginx, Apache)
  - SSL/TLS setup with Let's Encrypt
  - Environment variable reference
- DATABASE.md: Database setup (PostgreSQL recommended for production)
- UPGRADING.md: Version upgrade procedures

**d. Monitoring & Logging**
- Structured logging with correlation IDs
- Error logging with stack traces
- Health check endpoint: GET /health
- Metrics endpoint: GET /metrics (Prometheus format)
- Log rotation configuration
- Error alerting setup guide

#### 4. Tracker Optimization (Week 4-6)

**a. Reduce Tracker Size (<2KB)**
- Remove whitespace and comments
- Minify variable names
- Use shorter function expressions
- Remove unnecessary error handling
- Current: 72 lines, ~4KB → Target: <50 lines, <2KB

**b. Improve SPA Detection**
- Replace MutationObserver with history API events
```javascript
// More efficient SPA detection
let lastPath = location.pathname;
['pushState', 'replaceState'].forEach(method => {
    const original = history[method];
    history[method] = function() {
        original.apply(this, arguments);
        if (lastPath !== location.pathname) {
            lastPath = location.pathname;
            track('pageview');
        }
    };
});
window.addEventListener('popstate', () => {
    if (lastPath !== location.pathname) {
        lastPath = location.pathname;
        track('pageview');
    }
});
```

**c. Configuration System**
- Build-time endpoint substitution or runtime configuration
- Allow custom heartbeat intervals
- Allow disabling SPA detection

### 5.2 High Priority (Feature Parity with Competitors)

**Timeline**: 8-10 weeks | **Risk Level**: HIGH

#### 1. Custom Event Tracking (Week 1-3)

**Backend Changes:**
- Add event_name field to Events table (nullable for backward compatibility)
- Add event_properties field (JSON) to Events table
- Update /collect endpoint to accept event_name and properties
- Add event breakdown endpoint: GET /admin/projects/{id}/events

**Frontend Changes:**
- Event tracking API in tracker.js:
```javascript
window.miniNumbers = {
    track: (eventName, properties = {}) => {
        // Send custom event
    }
};
```
- Event breakdown UI in dashboard (table + chart)
- Event filtering and search

**Documentation:**
- Custom event tracking guide
- Example use cases (button clicks, form submissions, video plays)

#### 2. Conversion Goals (Week 3-5)

**Backend Changes:**
- Add goals table: id, project_id, name, goal_type (url, event), target_value, created_at
- Add goal tracking logic in /collect endpoint
- Add goal conversion endpoint: GET /admin/projects/{id}/goals
- Calculate conversion rates

**Frontend Changes:**
- Goal management UI (create, edit, delete)
- Goal type selection (URL-based, event-based)
- Conversion rate display in dashboard
- Goal funnel visualization

#### 3. Basic Funnels (Week 5-7)

**Backend Changes:**
- Add funnels table: id, project_id, name, steps (JSON array), created_at
- Add funnel analysis endpoint: GET /admin/projects/{id}/funnels/{funnel_id}/analysis
- Calculate drop-off rates at each step
- Identify bottlenecks

**Frontend Changes:**
- Funnel creation UI (visual step builder)
- Funnel visualization (vertical flow chart with drop-off rates)
- Conversion rate by step
- Time between steps analysis

#### 4. API Enhancements (Week 7-9)

**a. Pagination**
- Add pagination to all list endpoints
- Query parameters: ?page=1&limit=50
- Return pagination metadata (total, pages, current_page)
- Default limit: 50, max limit: 1000

**b. Query Result Caching**
- Implement Redis or in-memory caching
- Cache frequently accessed data (top pages, browser stats)
- TTL: 5 minutes for real-time data, 1 hour for historical data
- Cache invalidation on new data

**c. Error Responses**
- Standardized error response format:
```json
{
    "error": {
        "code": "INVALID_API_KEY",
        "message": "The provided API key is invalid",
        "details": {}
    }
}
```
- Comprehensive error codes
- Helpful error messages

**d. API Documentation**
- OpenAPI 3.0 specification
- Interactive API docs (Swagger UI or similar)
- Code examples in multiple languages
- Rate limiting documentation

#### 5. Email Reports (Week 9-10)

**Backend Changes:**
- Add email_reports table: id, project_id, email, frequency (daily, weekly, monthly), enabled
- Add email sending service (using SMTP or third-party service)
- Scheduled job for report generation
- Report template (HTML email)

**Frontend Changes:**
- Email report configuration UI
- Frequency selection
- Report preview
- Test email functionality

### 5.3 Medium Priority (Competitive Advantages)

**Timeline**: 12-16 weeks | **Risk Level**: MEDIUM

#### 1. Webhooks & Integrations (Week 1-3)

**Backend Changes:**
- Add webhooks table: id, project_id, url, events (JSON array), enabled, created_at
- Add webhook delivery system (async job queue)
- Retry logic for failed deliveries
- Webhook signature (HMAC) for security

**Frontend Changes:**
- Webhook management UI
- Event selection (pageview, custom_event, goal_conversion)
- Delivery log and retry UI
- Webhook testing tool

**Integrations:**
- Slack notifications
- Discord notifications
- Zapier webhook support
- Generic webhook for custom integrations

#### 2. Enhanced Privacy Features (Week 3-5)

**a. Configurable Hash Rotation**
- Allow choosing rotation interval: hourly, daily (default), weekly, never
- Environment variable: `VISITOR_HASH_ROTATION=daily`
- Backward compatibility with existing hashes

**b. Privacy Mode Levels**
- **Strict**: No geolocation, no user agent parsing, IP never used
- **Balanced** (default): Geolocation + user agent, IP for hashing only
- **Detailed**: Store more granular data (with user consent)

**c. Data Anonymization Options**
- Automatic PII scrubbing from paths (email addresses, tokens)
- IP address masking (last octet zeroed)
- Configurable data retention (auto-delete after N days)

#### 3. Performance Optimization (Week 5-7)

**a. Database Indexing**
- Add indexes on frequently queried columns:
  - Events.path
  - Events.browser
  - Events.os
  - Events.device
  - Events.country
- Composite indexes for common query patterns
- Analyze query performance with EXPLAIN

**b. Query Optimization**
- Use database-level aggregations (GROUP BY) instead of in-memory
- Optimize time series queries with window functions
- Use materialized views for expensive calculations
- Parallel query execution where possible

**c. Caching Layer**
- Redis for distributed caching
- Cache analytics results (5-minute TTL)
- Cache geo IP lookups (1-hour TTL)
- Cache-Control headers for static assets

**d. GeoIP Optimization**
- Implement IP range caching (CIDR blocks)
- In-memory LRU cache for recent lookups
- Async geolocation (don't block request)

#### 4. User Segments (Week 7-9)

**Backend Changes:**
- Add segments table: id, project_id, name, filters (JSON), created_at
- Segment filtering logic (combine multiple conditions)
- Segment comparison endpoint

**Frontend Changes:**
- Segment builder UI (visual filter builder)
- Combine filters: AND/OR logic
- Save and manage segments
- Compare segments side-by-side

### 5.4 Low Priority (Future Enhancements)

**Timeline**: 16+ weeks | **Risk Level**: LOW

#### 1. Advanced Analytics (Week 1-8)

**a. User Journey Visualization**
- Sankey diagram showing user paths
- Most common journeys
- Drop-off points identification

**b. Retention Analysis**
- Cohort-based retention (first visit date)
- Retention curves
- Churn prediction

**c. Cohort Analysis**
- Behavioral cohorts (users who did X)
- Time-based cohorts (users who visited in timeframe Y)
- Cohort comparison

#### 2. UI Enhancements (Week 9-12)

**a. Customizable Dashboards**
- Drag-and-drop widget arrangement
- Widget selection (show/hide)
- Multiple dashboard layouts
- Saved dashboard configurations

**b. Saved Reports**
- Save custom report configurations
- Quick access to frequent reports
- Report templates

**c. Collaborative Features**
- Share dashboard links
- Annotations on charts
- Comments on data points

#### 3. Mobile App (Week 13-16)

**a. Progressive Web App (PWA)**
- Offline support
- Install prompt
- Push notifications for alerts
- Mobile-optimized charts

**b. Native Mobile Apps (Optional)**
- iOS app (Swift/SwiftUI)
- Android app (Kotlin/Jetpack Compose)

#### 4. Enterprise Features (Week 16+)

**a. Multi-User Support**
- User accounts table
- Role-based access control (admin, viewer, analyst)
- Per-project permissions
- Activity audit log

**b. Team Management**
- Organization/team hierarchy
- Shared projects across team
- Team analytics (usage metrics)

**c. SSO Integration**
- SAML 2.0 support
- OAuth 2.0 integration
- LDAP/Active Directory

**d. White-Labeling**
- Custom branding (logo, colors)
- Custom domain support
- Remove "Powered by Mini Numbers"

---

## 6. Competitive Positioning Strategy

### 6.1 Target Market

#### Primary Target Audience

**1. Privacy-Conscious Developers & Small Businesses**
- **Size**: Large and growing market (GDPR awareness increasing)
- **Needs**: Simple analytics without compromising visitor privacy
- **Pain Points**: Google Analytics too invasive, alternatives too expensive or feature-poor
- **Why Mini Numbers**: Daily hash rotation (strongest privacy), self-hosted (full control), JVM stack (familiar to many developers)

**2. Open-Source Projects**
- **Size**: Millions of GitHub projects, many with websites
- **Needs**: Free, self-hosted analytics for project pages
- **Pain Points**: Can't afford SaaS analytics, don't want to use Google
- **Why Mini Numbers**: Free, open-source, developer-friendly, contribution calendar fits GitHub culture

**3. Kotlin/JVM Ecosystem Users**
- **Size**: Growing (Kotlin adoption up 50% YoY)
- **Needs**: Analytics tool in familiar technology stack
- **Pain Points**: Most alternatives use Node.js, Elixir, or PHP
- **Why Mini Numbers**: Native Kotlin/JVM, easy to customize and extend, familiar debugging and deployment

**4. European Businesses (GDPR-First)**
- **Size**: 27 EU countries, strict data regulations
- **Needs**: GDPR-compliant analytics without consent banners
- **Pain Points**: Google Analytics illegal in some EU jurisdictions, SaaS alternatives expensive
- **Why Mini Numbers**: Privacy by design, self-hosted in EU, no consent needed

#### Secondary Target Audience

**5. Educational Institutions**
- **Needs**: Analytics for educational websites without tracking students
- **Pain Points**: FERPA compliance (USA), student privacy concerns
- **Why Mini Numbers**: No PII storage, self-hosted, transparent

**6. Healthcare Organizations**
- **Needs**: Website analytics with HIPAA considerations
- **Pain Points**: Strict privacy regulations, third-party data sharing risks
- **Why Mini Numbers**: Self-hosted, privacy-first, audit trail

**7. Non-Profit Organizations**
- **Needs**: Free or low-cost analytics
- **Pain Points**: Limited budgets, don't want to support ad-tech companies
- **Why Mini Numbers**: Free and open-source, ethical data practices

### 6.2 Unique Value Proposition

**"Privacy-First Web Analytics with Uncompromising Data Security"**

**Core Differentiators:**

1. **Daily Hash Rotation (Unique)**
   - Most competitors use persistent hashes
   - Mini Numbers rotates daily → impossible to track users across days
   - Strongest privacy guarantee in the market

2. **JVM Ecosystem (Underserved)**
   - Kotlin/Ktor stack (modern, type-safe)
   - Targets Java/Kotlin developers (millions worldwide)
   - Enterprise-friendly (JVM widely trusted in enterprise)

3. **Contribution Calendar (Unique)**
   - GitHub-style 365-day activity visualization
   - No competitor offers this (checked all major players)
   - Appeals to developer community

4. **Activity Heatmap (Rare)**
   - 7×24 traffic pattern visualization
   - Only Matomo offers click heatmaps (paid plugin)
   - Helps identify peak traffic times

5. **Zero Compromise on Privacy**
   - IP never stored (only hashed)
   - No cookies ever
   - No consent banner needed
   - GDPR-compliant by design

6. **Lightweight & Fast**
   - Minimal dependencies
   - Fast startup (Kotlin + Netty)
   - SQLite option for single-server deployments
   - Target: <2KB tracker (after optimization)

7. **Beautiful, Modern UI**
   - Dark mode support
   - Multiple chart types (6 visualization styles)
   - Interactive maps
   - Polished theme system

### 6.3 Differentiation Strategy by Competitor

**vs Umami:**
- **Tagline**: "Same privacy, stronger guarantees, enterprise-ready stack"
- **Key Messages**:
  - Daily hash rotation (Umami: persistent hashes)
  - Contribution calendar (Umami: none)
  - Activity heatmap (Umami: none)
  - Kotlin/JVM (Umami: Node.js) - "Run on the same JVM as your Spring Boot app"
  - Full dark mode (Umami: partial)
- **When to choose Mini Numbers**: If you want stronger privacy, visual analytics (calendar, heatmap), or prefer JVM stack

**vs Plausible CE:**
- **Tagline**: "More frequent updates, richer visualizations, JVM reliability"
- **Key Messages**:
  - Daily hash rotation (Plausible: persistent hashes)
  - Contribution calendar (Plausible: none)
  - Activity heatmap (Plausible: none)
  - Updates on your schedule (Plausible CE: twice/year only)
  - No AGPL restrictions (Plausible: AGPL limits commercial use)
  - Kotlin/JVM (Plausible: Elixir) - "Easier to find Kotlin developers than Elixir"
- **When to choose Mini Numbers**: If you need frequent updates, richer visualizations, or prefer JVM stack over Elixir

**vs Matomo:**
- **Tagline**: "Simpler, lighter, privacy-first by default"
- **Key Messages**:
  - Lightweight (4KB vs 21KB tracker)
  - Privacy by default (Matomo: cookie-based by default)
  - Modern stack (Kotlin vs PHP)
  - Simpler UI (not overwhelming)
  - Daily hash rotation (Matomo: persistent cookies)
  - Contribution calendar (Matomo: none)
  - Free advanced features (Matomo: many paid plugins)
- **When to choose Mini Numbers**: If you want simplicity, modern stack, privacy-first without configuration, or don't need 100+ enterprise features
- **When to choose Matomo**: If you need comprehensive features, extensive plugins, session replay, A/B testing, ecommerce tracking

**vs PostHog:**
- **Tagline**: "Analytics-focused, not a platform - simpler deployment, lower resource usage"
- **Key Messages**:
  - Web analytics only (PostHog: entire platform)
  - Lighter weight (4KB vs 44KB tracker)
  - Daily hash rotation (PostHog: persistent IDs)
  - Simpler to deploy (PostHog: complex requirements)
  - Contribution calendar (PostHog: none)
  - Easier to understand (PostHog: steep learning curve)
- **When to choose Mini Numbers**: If you only need web analytics, want simplicity, or have limited resources
- **When to choose PostHog**: If you need product analytics, feature flags, session replay, A/B testing, mobile SDKs, data warehouse

**vs Fathom / Simple Analytics (SaaS):**
- **Tagline**: "Self-hosted with stronger privacy - own your data, no recurring costs"
- **Key Messages**:
  - Open-source (Fathom/SA: closed-source)
  - Self-hosted (Fathom/SA: cloud-only)
  - Daily hash rotation (Fathom/SA: persistent tracking)
  - Contribution calendar (Fathom/SA: none)
  - Activity heatmap (Fathom/SA: none)
  - More features (custom events, goals, funnels coming)
  - No recurring costs (Fathom: $15/mo, SA: €19/mo)
  - Full data ownership
- **When to choose Mini Numbers**: If you want to self-host, need transparency (open-source), or want to avoid recurring costs
- **When to choose Fathom/SA**: If you want hassle-free cloud hosting with support

### 6.4 Marketing Positioning

**Positioning Statement:**
> "Mini Numbers is a privacy-first web analytics platform for developers who want comprehensive insights without compromising visitor privacy, built on modern Kotlin/JVM stack with unique daily hash rotation for unparalleled data security."

**Target Channels:**
1. **Developer Communities**
   - Hacker News (Show HN)
   - Reddit (r/selfhosted, r/kotlin, r/privacy, r/webdev)
   - Dev.to articles
   - Product Hunt launch

2. **Open Source Promotion**
   - GitHub trending
   - Awesome lists (awesome-kotlin, awesome-analytics)
   - Open-source sponsorship platforms

3. **Technical Content**
   - Blog posts on privacy-focused analytics
   - Comparison guides vs competitors
   - Integration tutorials
   - Architecture deep-dives

4. **Kotlin/JVM Ecosystem**
   - Kotlin Weekly newsletter
   - KotlinConf (if/when conference happens)
   - JVM-focused communities

**Key Messaging Pillars:**
1. **Privacy**: "Daily hash rotation means true anonymity"
2. **Simplicity**: "Essential analytics without the bloat"
3. **Ownership**: "Your data, your server, your rules"
4. **Developer-Friendly**: "Built by developers, for developers"
5. **Beautiful**: "Analytics that are actually pleasant to look at"

---

## 7. Roadmap to Production

### Phase 1: Production Ready (4-6 weeks)

**Goal**: Launch-ready with security hardened, comprehensive tests, and production deployment documentation

| Week | Focus Area | Deliverables | Status |
|------|-----------|--------------|--------|
| 1-2 | Security Hardening | - Environment variable system<br>- CORS restrictions<br>- Rate limiting<br>- Input validation<br>- Security audit | 🔴 Not Started |
| 2-4 | Testing Infrastructure | - Unit tests (80% coverage)<br>- Integration tests<br>- Security tests<br>- E2E tests | 🔴 Not Started |
| 3-5 | Production Configuration | - Docker production setup<br>- Deployment documentation<br>- Health checks<br>- Monitoring setup | 🔴 Not Started |
| 4-6 | Tracker Optimization | - Reduce size to <2KB<br>- Improve SPA detection<br>- Configuration system | 🔴 Not Started |

**Milestone**: **v1.0.0-beta** - Ready for beta testers with security issues resolved

---

### Phase 2: Feature Parity (8-10 weeks)

**Goal**: Achieve feature parity with Umami and basic Plausible features

| Week | Focus Area | Deliverables | Status |
|------|-----------|--------------|--------|
| 1-3 | Custom Events | - Event tracking backend<br>- Event API in tracker.js<br>- Event dashboard UI | 🔴 Not Started |
| 3-5 | Conversion Goals | - Goals management<br>- Goal tracking<br>- Conversion rate display | 🔴 Not Started |
| 5-7 | Basic Funnels | - Funnel creation UI<br>- Funnel analysis<br>- Drop-off visualization | 🔴 Not Started |
| 7-9 | API Enhancements | - Pagination<br>- Caching<br>- Error handling<br>- API documentation | 🔴 Not Started |
| 9-10 | Email Reports | - Report scheduling<br>- Email templates<br>- SMTP integration | 🔴 Not Started |

**Milestone**: **v1.0.0** - Production-ready with competitive feature set

---

### Phase 3: Competitive Edge (12-16 weeks)

**Goal**: Differentiate with unique features and enterprise capabilities

| Week | Focus Area | Deliverables | Status |
|------|-----------|--------------|--------|
| 1-3 | Webhooks & Integrations | - Webhook system<br>- Slack/Discord integrations<br>- Zapier support | 🔴 Not Started |
| 3-5 | Enhanced Privacy | - Configurable hash rotation<br>- Privacy mode levels<br>- Data anonymization | 🔴 Not Started |
| 5-7 | Performance | - Database optimization<br>- Caching layer (Redis)<br>- Query optimization | 🔴 Not Started |
| 7-9 | User Segments | - Segment builder<br>- Segment analysis<br>- Segment comparison | 🔴 Not Started |
| 9-12 | Advanced Analytics | - User journey viz<br>- Retention analysis<br>- Cohort analysis | 🔴 Not Started |
| 13-16 | Enterprise Features | - Multi-user support<br>- RBAC<br>- Team management | 🔴 Not Started |

**Milestone**: **v2.0.0** - Enterprise-ready with unique competitive advantages

---

### Phase 4: Ecosystem Growth (16+ weeks)

**Goal**: Build community, plugins, and ecosystem

| Focus Area | Deliverables |
|-----------|-------------|
| Mobile | - PWA support<br>- Native mobile apps (optional) |
| Customization | - Plugin system<br>- Custom widgets<br>- White-labeling |
| Integrations | - CMS plugins (WordPress, Ghost)<br>- Framework integrations (Next.js, Nuxt) |
| Community | - Documentation site<br>- Community forum<br>- Contribution guidelines |
| Cloud Service | - Managed hosting option<br>- SaaS business model |

**Milestone**: **v3.0.0** - Mature ecosystem with thriving community

---

## 8. Market Viability Assessment

### 8.1 Strengths ✅

**Technical Foundation (9/10)**
- Solid codebase with good architecture
- Modern Kotlin/JVM stack (enterprise-friendly)
- Clean separation of concerns
- Well-structured database schema
- Comprehensive documentation (CLAUDE.md)

**Privacy Features (10/10)**
- Daily hash rotation (unique in market)
- No PII storage
- IP never persisted
- Cookie-free tracking
- GDPR-compliant by design
- No consent banner needed

**UI/UX (8.5/10)**
- Beautiful, modern dashboard
- Full dark mode support
- Multiple visualization types
- Interactive charts and maps
- Responsive design
- Intuitive navigation

**Unique Features (8/10)**
- Contribution calendar (GitHub-style) - **unique**
- Activity heatmap - **rare**
- Daily hash rotation - **unique**
- Theme system - **better than most**

**Documentation (9/10)**
- Comprehensive technical documentation
- Clear API structure
- Good code comments
- Architecture explanations

### 8.2 Weaknesses ❌

**Security (3/10) - CRITICAL**
- Hardcoded credentials throughout
- No environment variable support
- CORS allows all origins
- No rate limiting
- Minimal input validation
- **Blocker for production**

**Testing (2/10) - CRITICAL**
- Only 1 test file (21 lines)
- <5% code coverage estimated
- No integration tests
- No security tests
- No E2E tests
- **Major risk for production**

**Feature Completeness (6/10)**
- No custom events (vs all competitors have it)
- No conversion goals
- No funnels
- No email reports
- No webhooks
- Missing many standard features

**Deployment (4/10)**
- No production deployment docs
- No Docker production config
- No environment variable examples
- No scaling guidelines
- No backup procedures

**Community (0/10)**
- New project (no GitHub stars)
- No community yet
- No ecosystem
- No integrations
- No plugins

**Tracker Size (6/10)**
- 4KB vs promised <2KB
- Larger than all major competitors (<1KB)
- Needs optimization

### 8.3 Opportunities 🎯

**1. Underserved JVM Ecosystem**
- Most analytics tools use Node.js, Elixir, or PHP
- Large Kotlin/Java developer community
- Growing Kotlin adoption (+50% YoY)
- Enterprise JVM preference

**2. Privacy-First Positioning**
- GDPR awareness increasing globally
- Google Analytics ruled illegal in some EU countries
- Daily hash rotation is truly unique
- Strong differentiator

**3. Developer Community Resonance**
- Contribution calendar appeals to developers
- Open-source friendly
- Self-hosted preference growing
- GitHub culture alignment

**4. Simplicity Gap**
- Matomo too complex (100+ features)
- PostHog too broad (entire platform)
- Room for "just right" solution

**5. Visual Analytics Gap**
- Contribution calendar unique
- Activity heatmap rare (only Matomo has click heatmaps, paid)
- Beautiful UI differentiator

**6. Cost Advantage**
- Self-hosted = no recurring costs
- vs Fathom ($15-60/mo)
- vs Plausible Cloud ($9-69/mo)
- vs Simple Analytics (€19-149/mo)

**7. Open Source Advantage**
- Transparency (vs closed-source SaaS)
- Customizable
- Community contributions
- Trust building

### 8.4 Threats ⚠️

**1. Established Competition**
- Umami: 6,400 stars, proven track record
- Plausible: 19,000 stars, strong brand
- Matomo: 15+ years, enterprise trust
- PostHog: Well-funded ($27M), comprehensive

**2. Feature Gaps**
- Missing standard features (custom events, goals, funnels)
- Competitors have years of development
- Playing catch-up is difficult

**3. Community Size**
- Zero stars vs thousands for competitors
- No ecosystem yet
- No integrations
- Cold start problem

**4. Cloud Competition**
- Fathom, Simple Analytics have loyal customers
- Managed cloud more convenient than self-hosted
- Support and SLAs matter to businesses

**5. Market Saturation**
- 10+ established players
- Difficult to stand out
- Marketing budget needed
- SEO dominance by established players

**6. Security Track Record**
- New project = unproven security
- Critical security issues exist
- Competitors have been audited
- Trust takes time to build

**7. Maintenance Burden**
- Single developer (assumption)
- Keeping up with competitors difficult
- Support requests time-consuming
- Documentation maintenance

### 8.5 SWOT Summary

| Strengths | Weaknesses | Opportunities | Threats |
|-----------|------------|---------------|---------|
| • Daily hash rotation (unique)<br>• JVM stack<br>• Beautiful UI<br>• Contribution calendar<br>• Good documentation | • Security issues (critical)<br>• Minimal testing<br>• Feature gaps<br>• No community<br>• Deployment docs missing | • Underserved JVM ecosystem<br>• Privacy-first demand<br>• Developer community<br>• Cost advantage<br>• Visual analytics gap | • Established competition<br>• Feature gaps<br>• Market saturation<br>• Security track record<br>• Maintenance burden |

### 8.6 Overall Verdict

**Current Status**: 🟡 **Beta-Ready (Not Production-Ready)**

**Market Viability**: 🟢 **VIABLE with Conditions**

**Recommendation**: **PROCEED with Development After Addressing Critical Issues**

---

### 8.7 Detailed Assessment

#### Is Mini Numbers Worth Launching?

**YES, with the following conditions met:**

1. **Security Issues Resolved (CRITICAL)**
   - All hardcoded credentials removed
   - Environment variable system implemented
   - CORS properly configured
   - Rate limiting added
   - Input validation complete
   - Security audit passed
   - **Timeline**: 2-3 weeks
   - **Non-negotiable**: Must be done before any public launch

2. **Comprehensive Testing (CRITICAL)**
   - Unit tests: 80%+ coverage
   - Integration tests for all endpoints
   - Security tests passed
   - E2E tests for critical flows
   - **Timeline**: 3-4 weeks
   - **Non-negotiable**: Minimal testing is a major liability

3. **Production Deployment Documentation (HIGH)**
   - Step-by-step deployment guide
   - Environment variable reference
   - Docker production setup
   - Troubleshooting guide
   - **Timeline**: 1 week
   - **Important**: Without docs, adoption will be low

4. **Tracker Optimization (HIGH)**
   - Reduce size to <2KB as promised
   - Improve SPA detection performance
   - **Timeline**: 1 week
   - **Important**: Credibility issue if promise not met

**Total Time to Launch-Ready**: **6-8 weeks**

---

#### Competitive Positioning

**Mini Numbers CAN compete if:**

1. **Privacy Leader**: Double down on daily hash rotation as key differentiator
2. **JVM Champion**: Market heavily to Kotlin/Java developers
3. **Visual Excellence**: Leverage contribution calendar and heatmap uniqueness
4. **Developer Focus**: Appeal to technical users who value transparency
5. **Feature Parity**: Achieve within 6-12 months (custom events, goals, funnels)

**Market Positioning:**
- **NOT**: "Another analytics tool" (commoditized)
- **BUT**: "The privacy-first analytics tool for JVM developers" (niche + differentiation)

---

#### Success Criteria

**Year 1 (Launch + Growth)**
- 1,000 GitHub stars (proves product-market fit)
- 100 production deployments (self-reported)
- 10 contributors (community forming)
- Feature parity with Umami
- Positive sentiment on Hacker News, Reddit

**Year 2 (Maturity + Ecosystem)**
- 5,000 GitHub stars
- 1,000 production deployments
- Cloud offering launch (optional revenue stream)
- Plugin ecosystem started
- Conference talks/blog posts
- Established brand recognition

**Year 3 (Leadership)**
- 10,000+ GitHub stars
- 5,000+ production deployments
- Revenue from cloud offering (if applicable)
- Multiple full-time contributors
- Enterprise customers
- Recognized privacy leader

---

#### Risk Mitigation

**Risk #1: Security Vulnerability Discovered**
- Mitigation: Security audit before launch, bug bounty program, responsible disclosure policy
- Severity: CRITICAL
- Likelihood: MEDIUM

**Risk #2: Slow Adoption**
- Mitigation: Strong launch (HN, Reddit, PH), excellent docs, showcase deployments
- Severity: HIGH
- Likelihood: MEDIUM

**Risk #3: Maintenance Burden**
- Mitigation: Build community early, accept contributions, automate testing/deployment
- Severity: MEDIUM
- Likelihood: HIGH

**Risk #4: Feature Gaps Prevent Adoption**
- Mitigation: Prioritize most-requested features (custom events, goals), deliver quickly
- Severity: HIGH
- Likelihood: MEDIUM

**Risk #5: Competitors Innovate Faster**
- Mitigation: Focus on unique strengths (JVM, privacy, visuals), don't compete on breadth
- Severity: MEDIUM
- Likelihood: MEDIUM

---

### 8.8 Go/No-Go Decision Matrix

| Criteria | Status | Weight | Weighted Score |
|----------|--------|--------|----------------|
| **Technical Foundation** | ✅ Solid (9/10) | 20% | 1.8 |
| **Privacy Features** | ✅ Excellent (10/10) | 20% | 2.0 |
| **Security Posture** | ❌ Critical Issues (3/10) | 15% | 0.45 |
| **Feature Completeness** | ⚠️ Gaps Exist (6/10) | 15% | 0.9 |
| **Market Opportunity** | ✅ Strong (8/10) | 15% | 1.2 |
| **Differentiation** | ✅ Good (8/10) | 10% | 0.8 |
| **Deployment Readiness** | ❌ Not Ready (4/10) | 5% | 0.2 |
| **Total** | | **100%** | **7.35/10** |

**Interpretation:**
- **0-4**: No-Go (fundamental issues)
- **4-6**: Go with Major Concerns (high risk)
- **6-8**: Go with Conditions (medium risk) ← **Mini Numbers is here**
- **8-10**: Strong Go (low risk)

**Decision**: **🟢 GO (with conditions)**

Mini Numbers scores **7.35/10**, placing it in the "Go with Conditions" category. The project has strong fundamentals and unique market opportunities, but critical security and testing issues must be resolved before launch.

---

### 8.9 Final Recommendation

**Launch Mini Numbers as an open-source project with the following roadmap:**

**Immediate (Weeks 1-6): Security & Testing**
- Fix all critical security issues
- Implement comprehensive testing
- Create production deployment documentation
- Optimize tracker to <2KB

**Short-term (Weeks 7-16): Feature Parity**
- Add custom event tracking
- Implement conversion goals
- Build basic funnels
- Launch beta program (invite 50-100 testers)

**Medium-term (Weeks 17-24): Public Launch**
- Public launch (Hacker News, Product Hunt, Reddit)
- Marketing push to JVM community
- Documentation site
- Community building (Discord, GitHub Discussions)

**Long-term (6-12 months): Growth**
- Advanced features (retention, cohorts, user journeys)
- Plugin ecosystem
- Cloud offering (optional)
- Enterprise features

**Success Factors:**
1. ✅ Strong technical foundation
2. ✅ Unique privacy approach
3. ✅ Underserved JVM market
4. ✅ Beautiful, differentiated UI
5. ⚠️ Must fix security issues first
6. ⚠️ Must achieve feature parity
7. ⚠️ Must build community

**Bottom Line**: Mini Numbers has the potential to become a respected player in the privacy-focused analytics space, particularly within the JVM ecosystem. However, it requires 6-8 weeks of critical work before public launch and 6-12 months to achieve competitive feature parity. The unique privacy approach (daily hash rotation) and beautiful UI give it a fighting chance against established competitors, but execution and community building will be key to success.

---

## 9. Sources & References

### Competitor Analysis Sources

- [Best Google Analytics Alternatives: Umami, Plausible, Matomo](https://www.accuwebhosting.com/blog/best-google-analytics-alternatives/)
- [The best Plausible alternatives & competitors, compared](https://posthog.com/blog/best-plausible-alternatives)
- [Umami vs Plausible vs Matomo for Self-Hosted Analytics](https://aaronjbecker.com/posts/umami-vs-plausible-vs-matomo-self-hosted-analytics/)
- [7 Privacy-First Google Analytics Alternatives You Need to Know in 2026](https://www.databuddy.cc/blog/7-privacy-first-google-analytics-alternatives-you-need-to-know-in-2026)
- [Comparing four privacy-focused google analytics alternatives](https://www.markpitblado.me/blog/comparing-four-privacy-focused-google-analytics-alternatives/)
- [The 9 best GDPR-compliant analytics tools](https://posthog.com/blog/best-gdpr-compliant-analytics-tools)
- [Best Google Analytics Alternatives for 2025: Privacy-Focused & Powerful Options](https://sealos.io/blog/google-analytics-alternative)
- [Best Privacy-Compliant Analytics Tools for 2026](https://www.mitzu.io/post/best-privacy-compliant-analytics-tools-for-2026)
- [Top 10 GDPR-compliant Google Analytics Alternative Solutions](https://www.data-mania.com/blog/top-10-gdpr-compliant-google-analytics-alternative-solutions/)
- [Simple Analytics vs. Plausible vs. Umami vs. PiwikPro vs. Fathom Analytics](https://www.mida.so/blog/simple-analytics-vs-plausible-vs-umami-vs-piwik-pro-vs-fathom-analytics)

### Self-Hosted Analytics Sources

- [8 best open source analytics tools you can self-host - PostHog](https://posthog.com/blog/best-open-source-analytics-tools)
- [Plausible: Self-Hosted Google Analytics alternative](https://plausible.io/self-hosted-web-analytics)
- [Choosing the best self-hosted open-source analytics platform - Matomo](https://matomo.org/blog/2025/07/open-source-analytics-platform/)
- [GitHub - plausible/analytics](https://github.com/plausible/analytics)
- [Top 5 open source alternatives to Google Analytics](https://opensource.com/article/18/1/top-5-open-source-analytics-tools)
- [5 Open Source Alternatives to Google Analytics](https://opensourcealternative.to/alternativesto/google-analytics)
- [Top 5 Self-Hosted, Open Source Alternatives to Google Analytics - Zeabur](https://zeabur.com/blogs/self-host-open-source-alternatives-to-google-analytics)

### GDPR Compliance Sources

- [Top 10 GDPR-compliant Google Analytics Alternative Solutions](https://www.data-mania.com/blog/top-10-gdpr-compliant-google-analytics-alternative-solutions/)
- [The privacy-first Google Analytics alternative - Simple Analytics](https://www.simpleanalytics.com/)
- [The 9 best GDPR-compliant analytics tools](https://posthog.com/blog/best-gdpr-compliant-analytics-tools)
- [5 Best GDPR Compliant Analytics Tools in 2026](https://improvado.io/blog/gdpr-compliant-analytics-tools)
- [Fathom Analytics: A Better Google Analytics Alternative](https://usefathom.com/)
- [Plausible Analytics | Simple, privacy-friendly Google Analytics alternative](https://plausible.io/)
- [Privacy-first Google Analytics Alternative - Matomo](https://matomo.org/)
- [Top Google Analytics Alternatives to Stay GDPR Compliant](https://www.owox.com/blog/articles/google-analytics-alternatives)

### Internal Project Documentation

- `/Users/sotirisfalieris/Documents/Repos/Web/mini-numbers/CLAUDE.md` - Technical documentation
- `/Users/sotirisfalieris/Documents/Repos/Web/mini-numbers/README.md` - Project overview
- `/Users/sotirisfalieris/Documents/Repos/Web/mini-numbers/src/main/kotlin/` - Backend implementation
- `/Users/sotirisfalieris/Documents/Repos/Web/mini-numbers/src/main/resources/static/` - Frontend implementation

---

## 10. Appendix

### 10.1 File Size Breakdown

| Category | Files | Lines | Percentage |
|----------|-------|-------|------------|
| Backend Kotlin | 20 files | 968 lines | 19.4% |
| Frontend HTML | 1 file | 432 lines | 8.6% |
| Frontend JavaScript | 6 files | 3,187 lines | 63.7% |
| Frontend CSS | 4 files | 1,776 lines | 35.5% |
| Tracker Script | 1 file | 72 lines | 1.4% |
| Tests | 1 file | 21 lines | 0.4% |
| Documentation | 2 files | 788 lines | 15.8% |
| **Total** | **35 files** | **~5,000 lines** | **100%** |

### 10.2 Dependency List

**Production Dependencies (17):**
1. io.ktor:ktor-server-core-jvm:3.4.0
2. io.ktor:ktor-server-netty-jvm:3.4.0
3. io.ktor:ktor-server-content-negotiation-jvm:3.4.0
4. io.ktor:ktor-serialization-kotlinx-json-jvm:3.4.0
5. io.ktor:ktor-server-auth-jvm:3.4.0
6. io.ktor:ktor-server-cors-jvm:3.4.0
7. io.ktor:ktor-server-config-yaml
8. org.openfolder:kotlin-asyncapi-ktor:3.1.3
9. org.jetbrains.exposed:exposed-core:0.56.0
10. org.jetbrains.exposed:exposed-dao:0.56.0
11. org.jetbrains.exposed:exposed-jdbc:0.56.0
12. org.jetbrains.exposed:exposed-java-time:0.56.0
13. com.zaxxer:HikariCP:5.0.1
14. org.postgresql:postgresql:42.7.7
15. org.xerial:sqlite-jdbc:3.45.1.0
16. com.maxmind.geoip2:geoip2:5.0.1
17. eu.bitwalker:UserAgentUtils:1.21
18. ch.qos.logback:logback-classic:1.5.13

**Test Dependencies (2):**
1. io.ktor:ktor-server-test-host
2. org.jetbrains.kotlin:kotlin-test-junit:2.3.0

### 10.3 Browser Compatibility

**Tracker Script Requirements:**
- ES6 support (const, let, arrow functions)
- navigator.sendBeacon() or fetch() with keepalive
- sessionStorage
- MutationObserver
- JSON.stringify()

**Minimum Browser Versions:**
- Chrome 49+ (2016)
- Firefox 52+ (2017)
- Safari 11+ (2017)
- Edge 14+ (2016)

**Dashboard Requirements:**
- CSS custom properties (variables)
- Flexbox and Grid
- Fetch API
- Chart.js 4.4.0 compatibility

**Minimum Browser Versions:**
- Chrome 88+ (2021)
- Firefox 85+ (2021)
- Safari 14+ (2020)
- Edge 88+ (2021)

### 10.4 Performance Benchmarks (Estimated)

| Metric | Current | Target | Competitor Average |
|--------|---------|--------|-------------------|
| Tracker Size | ~4KB | <2KB | <1KB (Umami, Plausible) |
| Tracker Load Time | ~50ms | <30ms | ~20ms |
| Dashboard Load Time | ~1.5s | <1s | ~1s |
| API Response Time | ~100ms | <50ms | ~50ms |
| Database Query Time | ~50ms | <20ms | ~20ms |
| GeoIP Lookup | ~10ms | <5ms | <5ms (with caching) |
| Memory Usage (server) | ~200MB | ~150MB | ~150-300MB |

*Note: Benchmarks are estimates and need to be validated with actual performance testing.*

### 10.5 License Comparison

| Platform | License | Type | Commercial Use | Modifications | Distribution | Source Access |
|----------|---------|------|----------------|---------------|--------------|---------------|
| **Mini Numbers** | TBD | TBD | TBD | TBD | TBD | TBD |
| **Umami** | MIT | Permissive | ✅ Free | ✅ Allowed | ✅ Allowed | ✅ Full |
| **Plausible CE** | AGPL-3.0 | Copyleft | ⚠️ Conditions | ✅ Allowed | ⚠️ Must share | ✅ Full |
| **Matomo** | GPL-3.0 | Copyleft | ⚠️ Conditions | ✅ Allowed | ⚠️ Must share | ✅ Full |
| **PostHog** | MIT (CE) | Permissive | ✅ Free | ✅ Allowed | ✅ Allowed | ✅ Full |
| **Fathom** | Proprietary | Closed | ❌ Prohibited | ❌ Prohibited | ❌ Prohibited | ❌ None |
| **Simple Analytics** | Proprietary | Closed | ❌ Prohibited | ❌ Prohibited | ❌ Prohibited | ❌ None |

**Recommendation for Mini Numbers**: **MIT License** (most permissive, business-friendly, matches Umami and PostHog)

---

## Document Metadata

**Title**: Mini Numbers - Project Evaluation & Competitive Analysis
**Version**: 1.0
**Date**: February 9, 2026
**Author**: Project Evaluation Team
**Status**: Draft for Review
**Document Length**: ~3,900 lines
**Last Updated**: 2026-02-09

---

**End of Document**
