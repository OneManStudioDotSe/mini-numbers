---
title: Architecture
layout: default
nav_order: 8
---

# Architecture

A technical overview of how Mini Numbers is built, for developers and contributors who want to understand the codebase.

---

## Tech stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin 2.3.0 |
| **Framework** | Ktor 3.4.0 |
| **Runtime** | JVM (JDK 21) |
| **Server** | Netty (embedded) |
| **Database** | SQLite or PostgreSQL via Exposed ORM |
| **Connection pooling** | HikariCP |
| **Caching** | Caffeine |
| **Geolocation** | MaxMind GeoIP2 (bundled offline database) |
| **User agent parsing** | UserAgentUtils |
| **Serialization** | kotlinx.serialization |
| **Build tool** | Gradle with Kotlin DSL |
| **Static analysis** | Detekt |

---

## Project structure

```
mini-numbers/
├── src/main/kotlin/
│   ├── Application.kt              # Entry point
│   ├── Routing.kt                  # All API endpoints
│   ├── DataAnalysisUtils.kt        # Analytics calculations
│   ├── ConversionAnalysisUtils.kt  # Goal & funnel logic
│   ├── api/models/                 # Request/response data classes
│   ├── config/                     # Configuration loading
│   ├── core/                       # Security, auth, service lifecycle
│   ├── db/                         # Database tables and setup
│   ├── middleware/                  # Validation, caching, rate limiting
│   ├── services/                   # GeoIP and user agent services
│   └── setup/                      # Setup wizard backend
├── src/main/resources/
│   ├── static/                     # Dashboard frontend (HTML/CSS/JS)
│   ├── setup/                      # Setup wizard frontend
│   └── geo/                        # Bundled GeoIP database
├── src/test/kotlin/                # Test suite (288 tests)
├── docs/                           # GitHub Pages documentation
├── _docs/                          # Internal project docs
├── Dockerfile                      # Multi-stage production build
└── docker-compose.yml              # Docker deployment
```

---

## Application lifecycle

Mini Numbers uses a **ServiceManager** that handles startup and hot-reload without requiring restarts.

### Startup flow

1. **Configuration loaded** — Environment variables or `.env` file parsed
2. **Security initialized** — Server salt and hash rotation configured
3. **Database connected** — SQLite or PostgreSQL initialized, tables created
4. **GeoIP loaded** — MaxMind database opened (optional, non-fatal if missing)
5. **Data retention started** — Background timer for automatic data purge
6. **HTTP configured** — CORS, content negotiation, session auth
7. **Routes installed** — Data collection and admin API endpoints registered

### Zero-restart setup

The setup wizard can configure and initialize the application without a restart. The ServiceManager coordinates this:

```
Setup Wizard → Save Config → Initialize Services → Ready
```

This means users can go from first launch to a working dashboard without touching config files or restarting the server.

---

## Data flow

### Tracking a page view

```
Visitor's browser
    ↓ (HTTP POST /collect)
Rate limiter
    ↓ (check limits)
Input validator
    ↓ (sanitize payload)
Privacy processor
    ↓ (hash IP, apply privacy mode)
GeoIP lookup (in memory)
    ↓ (country/city from IP, then discard IP)
User agent parser
    ↓ (extract browser/OS/device, discard raw UA)
Database insert
    ↓ (store anonymized event)
Cache invalidation
    ↓ (clear stale query cache)
Response → 204 No Content
```

### Serving a dashboard report

```
Admin dashboard
    ↓ (GET /admin/projects/{id}/report)
Session auth check
    ↓ (verify login session)
Query cache lookup
    ↓ (return cached result if fresh)
Database query
    ↓ (aggregated analytics via indexed queries)
Cache store (30-second TTL)
    ↓
JSON response → Dashboard
```

---

## Database schema

### Core tables

**Events** — The primary data table. Every page view, heartbeat, and custom event is stored here.

| Column | Type | Description |
|--------|------|-------------|
| id | Long | Auto-incrementing primary key |
| projectId | UUID | Which project this event belongs to |
| visitorHash | String | Anonymous visitor identifier |
| sessionId | String | Random session identifier |
| eventType | String | `pageview`, `heartbeat`, or `custom` |
| eventName | String? | Name for custom events |
| path | String | Page URL path |
| referrer | String? | Where the visitor came from |
| country | String? | Visitor's country |
| city | String? | Visitor's city |
| browser | String? | Browser name |
| os | String? | Operating system |
| device | String? | Device type |
| duration | Int | Time on page in seconds |
| timestamp | DateTime | When the event occurred |

**8 composite indexes** ensure fast query performance across all analytics dimensions.

### Supporting tables

- **Projects** — Website projects with name, domain, and API key
- **ConversionGoals** — URL or event-based goals with match patterns
- **Funnels / FunnelSteps** — Multi-step conversion funnels
- **Segments** — User segments with JSON filter definitions

---

## Caching strategy

Mini Numbers uses Caffeine for three caching layers:

| Cache | Max entries | TTL | Purpose |
|-------|------------|-----|---------|
| **Query cache** | 500 | 30 seconds | Dashboard report results |
| **GeoIP cache** | 10,000 | 1 hour | IP-to-location lookups |
| **Rate limiter** | Per-config | 1 minute | Request counting per IP/key |

The query cache is automatically invalidated when new data is collected, ensuring the dashboard always shows fresh results.

---

## Authentication

- **Session-based auth** with secure cookies (HttpOnly, Secure, SameSite=Strict)
- **Brute force protection** — 5 failed attempts triggers a 15-minute lockout
- **Session timeout** — 4-hour inactivity timeout, 7-day maximum age
- **Public endpoints** (`/collect`, `/health`, `/tracker/*`) require no authentication
- **Admin endpoints** (`/admin/*`) require an active session

---

## API design

All API endpoints follow consistent patterns:

- **JSON** request and response bodies
- **Standardized errors** with code, message, and optional details
- **Pagination** via `?page=&limit=` query parameters
- **Cache headers** on report endpoints
- **Rate limiting** on all endpoints

The full API is documented with an **OpenAPI 3.0.3** specification available at `/admin-panel/openapi.yaml`.

---

## Testing

288 tests organized by layer:

| Category | Tests | What's tested |
|----------|-------|--------------|
| Security | 13 | Visitor hashing, hash rotation |
| Service lifecycle | 13 | ServiceManager states and transitions |
| Input validation | 26 | Sanitization, edge cases, XSS prevention |
| User agent parsing | 22 | Browser/OS/device detection |
| Analytics | 22 | Calculations, aggregations |
| Data collection | 19 | `/collect` endpoint behavior |
| Admin API | 14 | Project management, reports |
| Health endpoint | 6 | Health check responses |
| Setup wizard | 10 | Configuration flow |
| End-to-end | 9 | Full tracking workflows |

Run the full suite:

```bash
./gradlew test
```

---

## Build system

| Command | What it does |
|---------|-------------|
| `./gradlew run` | Start the development server |
| `./gradlew test` | Run all tests |
| `./gradlew build` | Build everything |
| `./gradlew buildFatJar` | Create a standalone JAR with all dependencies |
| `./gradlew detekt` | Run static analysis |
| `./gradlew minifyTracker` | Minify the tracking script |

The Docker build uses a multi-stage process: build the fat JAR in a Gradle container, then copy it into a minimal JRE runtime image.
