---
title: Architecture
layout: default
nav_order: 8
---

# Architecture

A technical overview of how Mini Numbers is built, for developers and contributors who want to understand the codebase.

---

## Tech stack

| Layer                  | Technology                                |
|------------------------|-------------------------------------------|
| **Language**           | Kotlin 2.3.0                              |
| **Framework**          | Ktor 3.4.0                                |
| **Runtime**            | JVM (JDK 21)                              |
| **Server**             | Netty (embedded)                          |
| **Database**           | SQLite or PostgreSQL via Exposed ORM      |
| **Connection pooling** | HikariCP                                  |
| **Caching**            | Caffeine                                  |
| **Geolocation**        | MaxMind GeoIP2 (bundled offline database) |
| **User agent parsing** | Custom Regex-based parser                 |
| **Serialization**      | kotlinx.serialization                     |
| **Build tool**         | Gradle with Kotlin DSL                    |
| **Static analysis**    | Detekt                                    |

---

## Project structure

The project follows a standard Kotlin/JVM structure, with all code residing in the `se.onemanstudio` package.

```
mini-numbers/
├── src/main/kotlin/se/onemanstudio/
│   ├── Application.kt              # Entry point & Ktor module configuration
│   ├── Routing.kt                  # Main route installation
│   ├── WidgetRouting.kt            # Sparkline and public widget endpoints
│   ├── api/models/                 # Shared request/response DTOs
│   ├── config/                     # Configuration loading & validation logic
│   ├── core/                       # Auth, ServiceManager, and JWT services
│   ├── db/                         # Table schemas and DatabaseFactory
│   ├── middleware/                 # Rate limiting, Cache, and Input validation
│   ├── routing/                    # Modular API route definitions
│   │   ├── AdminAnalyticsRouting.kt # Reports and stats
│   │   ├── AdminFeatureRouting.kt   # Goals, Funnels, Webhooks
│   │   ├── AdminProjectRouting.kt   # Project CRUD
│   │   ├── AuthRouting.kt           # Login, Token, Password Reset
│   │   └── CollectionRouting.kt     # The /collect endpoint
│   ├── services/                   # Business logic (GeoIP, Email, Webhooks)
│   └── utils/                      # High-performance analytics aggregators
├── src/main/resources/
│   ├── static/                     # Dashboard frontend (Vanilla JS/CSS)
│   ├── setup/                      # Premium Setup Wizard (HTML5 Canvas)
│   ├── tracker/                    # tracker.js and minified versions
│   └── geo/                        # Bundled GeoIP database
└── src/test/kotlin/se/onemanstudio/ # Comprehensive test suite (288 tests)
```

---

## Application lifecycle

Mini Numbers uses a **ServiceManager** that handles startup and hot-reload without requiring restarts.

### Startup flow

1. **Configuration loaded** — Environment variables or `.env` file parsed.
2. **Security initialized** — Server salt, hash rotation, and JWT signing configured.
3. **Database connected** — SQLite or PostgreSQL initialized, tables created/updated.
4. **GeoIP loaded** — MaxMind database opened (optional, non-fatal if missing).
5. **Data retention started** — Background timer for automatic data purge.
6. **Unified Routing** — Public, Setup, and Admin routes installed.

### Zero-restart setup

The premium setup wizard can configure the entire application mid-session. The ServiceManager handles the reload of all internal components (DB, Cache, GeoIP) without killing the Ktor process.

---

## Data flow

### Tracking a page view

```
Visitor's browser
    ↓ (HTTP POST /collect)
Rate limiter (Caffeine-backed)
    ↓ (check IP and API Key limits)
Input validator (Regex-based sanitization)
    ↓
Privacy processor (Hash IP + UA + Salt)
    ↓
GeoIP lookup (Enforced by PRIVACY_MODE)
    ↓ (STANDARD: City/Country, STRICT: Country, PARANOID: None)
User agent parser (Extract Browser/OS/Device)
    ↓
Database insert (Exposed DSL)
    ↓
Cache invalidation (Invalidate current project's report cache)
Response → 204 No Content
```

---

## Caching strategy

| Cache            | Max entries | TTL        | Purpose                     |
|------------------|-------------|------------|-----------------------------|
| **Query cache**  | 500         | 30 seconds | Aggregated dashboard data   |
| **Widget cache** | 1,000       | 5 minutes  | Sparkline & widget SVG data |
| **GeoIP cache**  | 10,000      | 1 hour     | IP-to-location lookups      |
| **Rate limiter** | Dynamic     | 1 minute   | Request counting            |

The query cache is project-aware and automatically clears when new events arrive, ensuring data is always "Live."

---

## Authentication & Security

- **Multi-layered Auth**: Supports Session-based auth for the Dashboard and JWT for programmatic API access.
- **RBAC**: Roles for `ADMIN` (full access) and `VIEWER` (read-only, masked API keys).
- **Brute force protection**: Sliding window rate limiting on all sensitive endpoints.
- **CSP**: Strict Content Security Policy allowing only trusted CDNs and Google Fonts.

---

## Testing & Quality

288 tests verify the project across all layers (Unit, Integration, and E2E).

| Command                   | What it does                                  |
|---------------------------|-----------------------------------------------|
| `./gradlew test`          | Run the full test suite                       |
| `./gradlew detekt`        | Run static analysis (custom thresholds)       |
| `./gradlew compileKotlin` | Full type-check and compilation               |
| `./gradlew minifyTracker` | Minify the tracker.js script                  |

The build is verified against **JDK 21** and enforces clean code standards via **Detekt**.
