# Mini Numbers - Gap Analysis & Recommendations

**Version**: 1.0
**Date**: February 9, 2026

---

## Table of Contents

1. [Critical Priority (Before Any Launch)](#1-critical-priority-before-any-launch)
2. [High Priority (Feature Parity with Competitors)](#2-high-priority-feature-parity-with-competitors)
3. [Medium Priority (Competitive Advantages)](#3-medium-priority-competitive-advantages)
4. [Low Priority (Future Enhancements)](#4-low-priority-future-enhancements)

---

## 1. Critical Priority (Before Any Launch)

**Timeline**: 4-6 weeks | **Risk Level**: CRITICAL

### 1.1 Security Hardening (Week 1-2)

#### a. Move All Credentials to Environment Variables

**Current State**: Hardcoded credentials throughout codebase

**Required Changes:**
- `ADMIN_USERNAME` and `ADMIN_PASSWORD` (Security.kt)
- `SERVER_SALT` (SecurityUtils.kt)
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `GEOIP_DATABASE_PATH`

**Implementation**: Use Ktor's environment config or external config file

**Example Configuration:**
```kotlin
data class SecurityConfig(
    val adminUsername: String = System.getenv("ADMIN_USERNAME")
        ?: throw IllegalStateException("ADMIN_USERNAME required"),
    val adminPassword: String = System.getenv("ADMIN_PASSWORD")
        ?: throw IllegalStateException("ADMIN_PASSWORD required"),
    val serverSalt: String = System.getenv("SERVER_SALT")
        ?: throw IllegalStateException("SERVER_SALT required"),
    val allowedOrigins: List<String> = System.getenv("ALLOWED_ORIGINS")
        ?.split(",") ?: emptyList()
)
```

#### b. Implement Proper CORS

**Current State**: `anyHost()` allows all origins (Security.kt:28)

**Required Changes:**
- Replace `anyHost()` with configurable origins
- Environment variable: `ALLOWED_ORIGINS` (comma-separated list)
- Default: reject all (fail-secure)

**Example:**
```kotlin
install(CORS) {
    val allowedOrigins = System.getenv("ALLOWED_ORIGINS")?.split(",") ?: emptyList()
    allowedOrigins.forEach { allowHost(it) }
    allowHeader(HttpHeaders.ContentType)
    allowHeader("X-Project-Key")
    allowMethod(HttpMethod.Post)
}
```

#### c. Add Rate Limiting

**Current State**: No rate limiting on /collect endpoint

**Required Changes:**
- Implement rate limiting middleware for `/collect` endpoint
- Configure limits: 100 requests/minute per API key
- Environment variables: `RATE_LIMIT_REQUESTS`, `RATE_LIMIT_WINDOW`
- Return 429 (Too Many Requests) when exceeded

**Implementation Strategy:**
- Use in-memory cache (ConcurrentHashMap) for request counts
- Key: API key + time window bucket
- Sliding window algorithm
- Clean up expired entries periodically

#### d. Input Validation & Sanitization

**Current State**: Minimal validation in /collect endpoint

**Required Changes:**
- Validate all payload fields (path, referrer, sessionId)
- Enforce size limits: path (512 chars), referrer (512 chars)
- Sanitize inputs to prevent injection attacks
- Return 400 (Bad Request) with validation errors

**Validation Rules:**
- `path`: Required, max 512 chars, must start with '/'
- `referrer`: Optional, max 512 chars, valid URL or empty
- `sessionId`: Required, alphanumeric, 20-40 chars
- `type`: Required, enum (pageview, heartbeat)
- `browser`, `os`, `device`: Optional, max 128 chars each

#### e. Security Audit

**Required Actions:**
- SQL injection testing (Exposed should handle, but verify)
- XSS vulnerability testing
- CSRF protection review
- Authentication bypass testing
- Dependency vulnerability scan (`./gradlew dependencyCheckAnalyze`)

**Tools:**
- OWASP ZAP or Burp Suite
- Snyk or GitHub Dependabot
- Manual code review

---

### 1.2 Comprehensive Testing (Week 2-4)

#### a. Unit Tests (Target: 80% coverage)

**Current State**: Only 1 test file (21 lines), <5% coverage

**Required Tests:**

**SecurityUtils tests:**
- `generateVisitorHash()` - various inputs, edge cases
- Daily salt rotation verification
- Hash uniqueness across projects
- Hash consistency within same day

**UserAgentParser tests:**
- `parseBrowser()` - known user agents, edge cases
- `parseOS()` - various operating systems
- `parseDevice()` - mobile, tablet, desktop detection
- Null/empty user agent handling

**DataAnalysisUtils tests:**
- Time period calculations
- Time series aggregation
- Heatmap generation
- Contribution calendar generation
- Peak time analysis
- Top items calculation

**Model serialization tests:**
- PageViewPayload serialization/deserialization
- Project model validation
- Error response formatting

#### b. Integration Tests

**Required Tests:**

**POST /collect:**
- Valid payload with all fields
- Valid payload with minimal fields
- Missing API key → 401
- Invalid API key → 401
- Missing required fields → 400
- Invalid field values → 400
- Rate limiting enforcement → 429

**Authentication:**
- Valid credentials → 200
- Invalid username → 401
- Invalid password → 401
- Missing credentials → 401

**Project CRUD:**
- GET /admin/projects → list all
- POST /admin/projects → create, validate
- DELETE /admin/projects/{id} → cascade deletion
- DELETE non-existent project → 404

**Analytics endpoints:**
- GET /admin/projects/{id}/report → all time filters
- Empty data handling
- Large dataset (10k+ events)
- GET /admin/projects/{id}/live → real-time updates
- GET /admin/projects/{id}/calendar → date ranges
- GET /admin/projects/{id}/export-csv → CSV format

#### c. Security Tests

**Required Tests:**
- Authentication bypass attempts
- CORS violation attempts (unauthorized origins)
- SQL injection attempts (parameterized queries should prevent)
- XSS payload attempts in path/referrer fields
- Rate limiting enforcement (exceed limits)
- Input validation edge cases (very long strings, special chars)

#### d. Database Tests

**Required Tests:**
- Schema creation and migration
- Foreign key constraints (cascade delete)
- Index performance (explain analyze)
- Transaction rollbacks
- Concurrent write handling (SQLite single-writer lock)
- PostgreSQL vs SQLite parity

#### e. End-to-End Tests

**Required Tests:**
- Full tracking workflow:
  1. Load tracker.js
  2. Send pageview event
  3. Verify in database
  4. Check dashboard display
- Project CRUD operations via UI
- Dashboard data visualization
- CSV export functionality
- Filter and search operations
- Theme switching persistence

---

### 1.3 Production Configuration (Week 3-5)

#### a. Environment Variable System

**Create Config data classes:**

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
    val adminUsername: String = System.getenv("ADMIN_USERNAME")
        ?: throw IllegalStateException("ADMIN_USERNAME required"),
    val adminPassword: String = System.getenv("ADMIN_PASSWORD")
        ?: throw IllegalStateException("ADMIN_PASSWORD required"),
    val serverSalt: String = System.getenv("SERVER_SALT")
        ?: throw IllegalStateException("SERVER_SALT required"),
    val allowedOrigins: List<String> = System.getenv("ALLOWED_ORIGINS")
        ?.split(",") ?: emptyList()
)

data class ServerConfig(
    val port: Int = System.getenv("SERVER_PORT")?.toInt() ?: 8080,
    val host: String = System.getenv("SERVER_HOST") ?: "0.0.0.0"
)

data class GeoIPConfig(
    val databasePath: String = System.getenv("GEOIP_DATABASE_PATH")
        ?: "./data/geolite2-city.mmdb",
    val autoDownload: Boolean = System.getenv("GEOIP_AUTO_DOWNLOAD")
        ?.toBoolean() ?: true
)
```

#### b. Docker Production Configuration

**Create production Dockerfile:**
- Multi-stage build (build stage + runtime stage)
- Base: `eclipse-temurin:21-jre-alpine` (minimal size)
- Non-root user for security
- Health check support
- Optimized layers

**Example Dockerfile:**
```dockerfile
FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN ./gradlew shadowJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appuser && adduser -S appuser -G appuser
USER appuser
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Docker Compose configuration:**
```yaml
version: '3.8'
services:
  mini-numbers:
    build: .
    ports:
      - "8080:8080"
    environment:
      - ADMIN_USERNAME=${ADMIN_USERNAME}
      - ADMIN_PASSWORD=${ADMIN_PASSWORD}
      - SERVER_SALT=${SERVER_SALT}
      - ALLOWED_ORIGINS=${ALLOWED_ORIGINS}
    volumes:
      - ./data:/app/data
    restart: unless-stopped
```

#### c. Deployment Documentation

**Required Documentation:**

**README.md**: Quick start for development
**DEPLOYMENT.md**: Production deployment guide
- Docker deployment (step-by-step)
- Binary deployment (fat JAR)
- Systemd service setup
- Reverse proxy configuration (nginx, Apache, Caddy)
- SSL/TLS setup with Let's Encrypt
- Environment variable reference
- Backup procedures
- Upgrading guide

**DATABASE.md**: Database setup
- PostgreSQL recommended for production
- SQLite for single-server
- Schema migrations
- Backup/restore procedures

**UPGRADING.md**: Version upgrade procedures
- Breaking changes
- Migration scripts
- Rollback procedures

#### d. Monitoring & Logging

**Structured logging:**
- JSON logging format
- Correlation IDs for request tracing
- Log levels configurable via environment (`LOG_LEVEL`)
- Sensitive data redaction (passwords, API keys)

**Health check endpoint:**
```kotlin
get("/health") {
    val dbHealthy = checkDatabaseConnection()
    val geoipHealthy = checkGeoIPDatabase()

    if (dbHealthy && geoipHealthy) {
        call.respond(HttpStatusCode.OK, mapOf(
            "status" to "healthy",
            "database" to "connected",
            "geoip" to "loaded"
        ))
    } else {
        call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
            "status" to "unhealthy",
            "database" to if (dbHealthy) "connected" else "disconnected",
            "geoip" to if (geoipHealthy) "loaded" else "missing"
        ))
    }
}
```

**Metrics endpoint (optional):**
- GET /metrics - Prometheus format
- Request counts by endpoint
- Response times (histogram)
- Error rates
- Database connection pool stats

---

### 1.4 Tracker Optimization (Week 4-6)

#### a. Reduce Tracker Size (<2KB)

**Current**: 72 lines, ~4KB
**Target**: <50 lines, <2KB

**Optimization strategies:**
- Remove whitespace and comments (use minifier)
- Minify variable names
- Use shorter function expressions
- Remove unnecessary error handling
- Use ternary operators instead of if-else
- Combine initialization logic

**Example minified structure:**
```javascript
(function(){const s=document.currentScript,k=s.getAttribute('data-project-key'),
e="https://api.example.com/collect";if(!k)return;let i=sessionStorage.getItem('ma_sid');
if(!i){i=Math.random().toString(36).slice(2,15);sessionStorage.setItem('ma_sid',i);}
const t=(type='pageview')=>{const p=JSON.stringify({path:location.pathname,
referrer:document.referrer||null,sessionId:i,type});navigator.sendBeacon?
navigator.sendBeacon(e+"?key="+k,p):fetch(e,{method:'POST',body:p,
headers:{'Content-Type':'application/json','X-Project-Key':k},keepalive:true});};
t();setInterval(()=>t('heartbeat'),30000);let l=location.pathname;
['pushState','replaceState'].forEach(m=>{const o=history[m];history[m]=function(){
o.apply(this,arguments);if(l!==location.pathname){l=location.pathname;t();}};});
addEventListener('popstate',()=>{if(l!==location.pathname){l=location.pathname;t();}});})();
```

**Build process integration:**
- Use `terser` or similar minifier
- Automated in Gradle build
- Generate `tracker.min.js`
- Source maps for debugging

#### b. Improve SPA Detection

**Current**: MutationObserver (inefficient)
**Better**: History API events

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

#### c. Configuration System

**Build-time endpoint substitution:**
```bash
./gradlew buildTracker -Pendpoint=https://analytics.example.com
```

**OR Runtime configuration (preferred):**
```html
<script
  src="https://your-server.com/tracker.js"
  data-project-key="YOUR_KEY"
  data-api-endpoint="https://your-server.com">
</script>
```

**Additional configuration options:**
- `data-heartbeat-interval="30000"` - Customize heartbeat
- `data-spa="false"` - Disable SPA detection
- `data-track-hash="true"` - Track hash-based routing

---

## 2. High Priority (Feature Parity with Competitors)

**Timeline**: 8-10 weeks | **Risk Level**: HIGH

### 2.1 Custom Event Tracking (Week 1-3)

**Backend Changes:**
- Add `event_name` field to Events table (nullable for backward compatibility)
- Add `event_properties` field (JSON) to Events table
- Update /collect endpoint to accept event_name and properties
- Add event breakdown endpoint: `GET /admin/projects/{id}/events`
- Event aggregation and statistics

**Frontend Changes:**
- Event tracking API in tracker.js:
```javascript
window.miniNumbers = {
    track: (eventName, properties = {}) => {
        const payload = JSON.stringify({
            path: window.location.pathname,
            referrer: document.referrer || null,
            sessionId: sessionId,
            type: 'event',
            eventName: eventName,
            properties: properties
        });
        // Send event
    }
};

// Usage example:
miniNumbers.track('button_click', { button_id: 'signup', label: 'Sign Up' });
```

- Event breakdown UI in dashboard (table + chart)
- Event filtering and search
- Event property display

**Documentation:**
- Custom event tracking guide
- Example use cases (button clicks, form submissions, video plays)
- Best practices for event naming

---

### 2.2 Conversion Goals (Week 3-5)

**Backend Changes:**
- Add goals table:
  - id, project_id, name, goal_type (url, event), target_value, created_at
- Add goal tracking logic in /collect endpoint
- Add goal conversion endpoint: `GET /admin/projects/{id}/goals`
- Calculate conversion rates (conversions / total visitors)

**Frontend Changes:**
- Goal management UI (create, edit, delete)
- Goal type selection:
  - **URL-based**: Match by path (exact or pattern)
  - **Event-based**: Match by event name
- Conversion rate display in dashboard
- Goal funnel visualization (multi-step goals)
- Goal comparison (compare multiple goals)

**Example Goals:**
- URL Goal: "/thank-you" (purchase completion)
- Event Goal: "video_completed" (video watch completion)
- Funnel Goal: "/cart" → "/checkout" → "/thank-you"

---

### 2.3 Basic Funnels (Week 5-7)

**Backend Changes:**
- Add funnels table:
  - id, project_id, name, steps (JSON array), created_at
- Add funnel analysis endpoint: `GET /admin/projects/{id}/funnels/{funnel_id}/analysis`
- Calculate drop-off rates at each step
- Identify bottlenecks
- Time between steps analysis

**Frontend Changes:**
- Funnel creation UI (visual step builder)
- Funnel visualization:
  - Vertical flow chart with drop-off rates
  - Conversion rate by step
  - Average time between steps
- Funnel comparison (compare time periods)

**Example Funnel:**
1. Homepage → 100% (1000 visitors)
2. Product Page → 60% (600 visitors) - 40% drop-off
3. Add to Cart → 30% (300 visitors) - 50% drop-off
4. Checkout → 20% (200 visitors) - 33% drop-off
5. Purchase → 15% (150 visitors) - 25% drop-off

---

### 2.4 API Enhancements (Week 7-9)

#### a. Pagination

**Implementation:**
- Add pagination to all list endpoints
- Query parameters: `?page=1&limit=50`
- Return pagination metadata:
```json
{
  "data": [...],
  "pagination": {
    "total": 1000,
    "page": 1,
    "limit": 50,
    "pages": 20
  }
}
```
- Default limit: 50, max limit: 1000

#### b. Query Result Caching

**Implementation:**
- Implement Redis or in-memory caching
- Cache frequently accessed data (top pages, browser stats)
- TTL: 5 minutes for real-time data, 1 hour for historical data
- Cache invalidation on new data
- Cache key structure: `project:{id}:report:{period}:{hash}`

#### c. Error Responses

**Standardized error format:**
```json
{
    "error": {
        "code": "INVALID_API_KEY",
        "message": "The provided API key is invalid",
        "details": {
            "key": "abc123"
        }
    }
}
```

**Error codes:**
- `INVALID_API_KEY` - API key not found
- `MISSING_FIELD` - Required field missing
- `INVALID_FIELD` - Field value invalid
- `RATE_LIMIT_EXCEEDED` - Too many requests
- `UNAUTHORIZED` - Authentication failed
- `NOT_FOUND` - Resource not found
- `INTERNAL_ERROR` - Server error

#### d. API Documentation

**OpenAPI 3.0 specification:**
- Generate from code or write manually
- Interactive docs (Swagger UI or Redoc)
- Code examples in multiple languages (curl, JavaScript, Python)
- Rate limiting documentation
- Authentication documentation
- Host at `/api-docs`

---

### 2.5 Email Reports (Week 9-10)

**Backend Changes:**
- Add email_reports table:
  - id, project_id, email, frequency (daily, weekly, monthly), enabled, created_at
- Add email sending service (SMTP or third-party like SendGrid)
- Scheduled job for report generation (cron or Quartz)
- Report template (HTML email with charts as images)

**Frontend Changes:**
- Email report configuration UI
- Frequency selection (daily at 9am, weekly on Monday, monthly on 1st)
- Email address input with validation
- Report preview
- Test email functionality ("Send test email now")
- Enable/disable toggle

**Email Report Contents:**
- Time period summary (vs previous period)
- Total views, unique visitors
- Top 5 pages
- Top 5 referrers
- Top 3 browsers, OS, devices
- Geographic breakdown (top 5 countries)
- Link to full dashboard

---

## 3. Medium Priority (Competitive Advantages)

**Timeline**: 12-16 weeks | **Risk Level**: MEDIUM

### 3.1 Webhooks & Integrations (Week 1-3)

**Backend Changes:**
- Add webhooks table:
  - id, project_id, url, events (JSON array), enabled, secret, created_at
- Add webhook delivery system (async job queue)
- Retry logic for failed deliveries (exponential backoff: 1s, 2s, 4s, 8s, 16s)
- Webhook signature (HMAC-SHA256) for security
- Delivery log (last 100 deliveries per webhook)

**Frontend Changes:**
- Webhook management UI (create, edit, delete, enable/disable)
- Event selection checkboxes:
  - `pageview` - New pageview
  - `custom_event` - Custom event tracked
  - `goal_conversion` - Goal achieved
- URL input with validation
- Secret key generation
- Delivery log and retry UI
- Webhook testing tool ("Send test payload")

**Integrations:**
- **Slack notifications**: Webhook to Slack channel
- **Discord notifications**: Webhook to Discord channel
- **Zapier support**: Generic webhook for Zapier triggers
- **Custom integrations**: Generic webhook for any service

**Webhook Payload Example:**
```json
{
  "event_type": "goal_conversion",
  "project_id": "abc123",
  "timestamp": "2026-02-09T12:34:56Z",
  "data": {
    "goal_name": "Purchase",
    "visitor_hash": "def456",
    "path": "/thank-you",
    "referrer": "https://example.com",
    "country": "United States"
  },
  "signature": "sha256=..."
}
```

---

### 3.2 Enhanced Privacy Features (Week 3-5)

#### a. Configurable Hash Rotation

**Options:**
- Hourly rotation
- Daily rotation (default, current behavior)
- Weekly rotation
- Never (persistent hash) - for users wanting cross-day tracking

**Environment variable**: `VISITOR_HASH_ROTATION=daily`

**Implementation:**
- Modify `SecurityUtils.generateVisitorHash()` to use configurable rotation
- Ensure backward compatibility with existing hashes

#### b. Privacy Mode Levels

**Strict Mode:**
- No geolocation lookup
- No user agent parsing
- IP never used (not even for hashing)
- Random visitor ID (truly anonymous)

**Balanced Mode (default):**
- Geolocation + user agent
- IP for hashing only (never stored)
- Daily hash rotation

**Detailed Mode:**
- Store more granular data
- Requires user consent (display notice)
- Optional session duration analysis
- Optional bounce rate calculation

**Configuration**: `PRIVACY_MODE=balanced`

#### c. Data Anonymization Options

**PII Scrubbing:**
- Automatic removal of email addresses from paths
- Removal of access tokens from query strings
- Configurable regex patterns for custom PII

**IP Address Masking:**
- Last octet zeroed (192.168.1.0 instead of 192.168.1.123)
- Option for full anonymization (no geolocation)

**Data Retention:**
- Automatic deletion after N days (configurable)
- Default: 365 days
- Environment variable: `DATA_RETENTION_DAYS=365`

---

### 3.3 Performance Optimization (Week 5-7)

#### a. Database Indexing

**Add indexes on frequently queried columns:**
```sql
CREATE INDEX idx_events_path ON events(path);
CREATE INDEX idx_events_browser ON events(browser);
CREATE INDEX idx_events_os ON events(os);
CREATE INDEX idx_events_device ON events(device);
CREATE INDEX idx_events_country ON events(country);
CREATE INDEX idx_events_project_created ON events(project_id, created_at);
```

**Composite indexes for common query patterns:**
```sql
CREATE INDEX idx_events_project_type_created
ON events(project_id, type, created_at);
```

**Analyze query performance:**
- Use `EXPLAIN ANALYZE` to identify slow queries
- Optimize N+1 queries
- Use database-level aggregations instead of in-memory

#### b. Query Optimization

**Strategies:**
- Use window functions for time series
- Use materialized views for expensive calculations
- Parallel query execution where possible
- Batch inserts for events
- Connection pooling (HikariCP already implemented)

#### c. Caching Layer

**Redis for distributed caching:**
- Cache analytics results (5-minute TTL)
- Cache geo IP lookups (1-hour TTL)
- Cache-Control headers for static assets
- Conditional requests (ETag, Last-Modified)

#### d. GeoIP Optimization

**Strategies:**
- Implement IP range caching (CIDR blocks)
- In-memory LRU cache for recent lookups (10,000 entries)
- Async geolocation (don't block request)
- Fallback to no geolocation if lookup fails

---

### 3.4 User Segments (Week 7-9)

**Backend Changes:**
- Add segments table:
  - id, project_id, name, filters (JSON), created_at
- Segment filtering logic:
  - Combine multiple conditions (AND/OR)
  - Filter types: country, browser, OS, device, referrer, path pattern
- Segment comparison endpoint

**Frontend Changes:**
- Segment builder UI (visual filter builder)
- Combine filters with AND/OR logic
- Save and manage segments
- Compare segments side-by-side (table + charts)

**Example Segments:**
- "Mobile Users from US" (device:mobile AND country:US)
- "Chrome Users" (browser:Chrome)
- "Organic Traffic" (referrer contains google.com OR bing.com)
- "Blog Readers" (path starts with /blog)

---

## 4. Low Priority (Future Enhancements)

**Timeline**: 16+ weeks | **Risk Level**: LOW

### 4.1 Advanced Analytics (Week 1-8)

#### a. User Journey Visualization

**Features:**
- Sankey diagram showing user paths through site
- Most common journeys (top 10)
- Drop-off points identification
- Entry and exit pages

#### b. Retention Analysis

**Features:**
- Cohort-based retention (first visit date)
- Retention curves (Day 1, 7, 14, 30, 90)
- Churn prediction
- Retention by segment

#### c. Cohort Analysis

**Features:**
- Behavioral cohorts (users who did X)
- Time-based cohorts (users who visited in timeframe Y)
- Cohort comparison
- Cohort metrics (avg views per cohort, conversion rate)

---

### 4.2 UI Enhancements (Week 9-12)

#### a. Customizable Dashboards

**Features:**
- Drag-and-drop widget arrangement
- Widget selection (show/hide)
- Multiple dashboard layouts (overview, traffic, conversions)
- Saved dashboard configurations per user

#### b. Saved Reports

**Features:**
- Save custom report configurations
- Quick access to frequent reports
- Report templates (weekly summary, monthly traffic, conversion funnel)
- Share report links

#### c. Collaborative Features

**Features:**
- Share dashboard links (public or private with token)
- Annotations on charts (mark events, releases)
- Comments on data points
- Activity feed (who viewed what)

---

### 4.3 Mobile App (Week 13-16)

#### a. Progressive Web App (PWA)

**Features:**
- Offline support (service worker)
- Install prompt (add to home screen)
- Push notifications for alerts (optional)
- Mobile-optimized charts (touch gestures)

#### b. Native Mobile Apps (Optional)

**iOS app (Swift/SwiftUI):**
- Native iOS interface
- Push notifications
- Widgets (summary metrics)

**Android app (Kotlin/Jetpack Compose):**
- Native Android interface
- Push notifications
- Widgets (summary metrics)

---

### 4.4 Enterprise Features (Week 16+)

#### a. Multi-User Support

**Features:**
- User accounts table
- Role-based access control:
  - **Admin**: Full access, manage users
  - **Viewer**: Read-only access
  - **Analyst**: View + export, no project management
- Per-project permissions
- Activity audit log

#### b. Team Management

**Features:**
- Organization/team hierarchy
- Shared projects across team
- Team analytics (usage metrics)
- Team member invitation

#### c. SSO Integration

**Features:**
- SAML 2.0 support
- OAuth 2.0 integration (Google, GitHub, Azure AD)
- LDAP/Active Directory
- JWT token support

#### d. White-Labeling

**Features:**
- Custom branding (logo, colors, favicon)
- Custom domain support (analytics.yourdomain.com)
- Remove "Powered by Mini Numbers" footer
- Custom email templates

---

## Summary

### Priority Matrix

| Priority | Timeline | Features | Risk | Impact |
|----------|----------|----------|------|--------|
| **Critical** | Weeks 1-6 | Security, Testing, Production Config, Tracker Optimization | 🔴 Critical | 🟢 Blocks Launch |
| **High** | Weeks 7-16 | Custom Events, Goals, Funnels, API Enhancements, Email Reports | 🟠 High | 🟢 Feature Parity |
| **Medium** | Weeks 17-24 | Webhooks, Privacy Features, Performance, Segments | 🟡 Medium | 🟡 Competitive Edge |
| **Low** | Weeks 24+ | Advanced Analytics, UI Enhancements, Mobile, Enterprise | 🟢 Low | 🟡 Future Growth |

### Estimated Effort

| Category | Tasks | Estimated Time | Complexity |
|----------|-------|----------------|------------|
| **Security Hardening** | 5 tasks | 2 weeks | Medium |
| **Testing** | 50+ tests | 3 weeks | High |
| **Production Config** | 4 tasks | 2 weeks | Medium |
| **Tracker Optimization** | 3 tasks | 1 week | Low |
| **Custom Events** | 3 tasks | 3 weeks | Medium |
| **Goals** | 2 tasks | 2 weeks | Medium |
| **Funnels** | 2 tasks | 2 weeks | High |
| **API Enhancements** | 4 tasks | 2 weeks | Medium |
| **Email Reports** | 2 tasks | 1 week | Low |

### Total Estimated Timeline

- **Critical Priority**: 6 weeks (must complete before launch)
- **High Priority**: 10 additional weeks (feature parity)
- **Medium Priority**: 8 additional weeks (competitive advantages)
- **Low Priority**: 16+ additional weeks (future growth)

**Total to Production-Ready**: 6-8 weeks
**Total to Feature Parity**: 16-18 weeks
**Total to Competitive Leader**: 24-32 weeks

---

**Document Metadata:**
- **Version**: 1.0
- **Date**: February 9, 2026
- **Lines**: ~432
- **Source**: PROJECT_EVALUATION.md (Section 5)
