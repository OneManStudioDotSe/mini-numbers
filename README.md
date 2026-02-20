# Mini Numbers

A **privacy-first, minimalist web analytics platform** built with Kotlin and Ktor. Track your website traffic without compromising visitor privacy — a lightweight, self-hosted alternative to Google Analytics.

## Why Mini Numbers?

- **No cookies** — No consent banners needed
- **No PII stored** — IP addresses are never persisted
- **Configurable hash rotation** — Adjustable from 1 hour to 1 year
- **Three privacy modes** — STANDARD, STRICT, PARANOID
- **Self-hosted** — Complete data ownership
- **Lightweight** — ~1.3KB tracking script (minified)
- **GDPR-friendly** — Privacy by design

## Features

### Analytics
- Real-time analytics dashboard with time series charts
- Multi-project support with unique API keys
- Activity heatmap (7 days x 24 hours)
- GitHub-style contribution calendar (365 days)
- Live visitor feed with geographic data
- Browser, OS, device, and referrer breakdowns
- Custom event tracking (`MiniNumbers.track("event")`)
- Bounce rate with period comparison

### Conversion Tracking
- Conversion goals (URL-based and event-based)
- Multi-step funnels with drop-off analysis
- User segments with visual AND/OR filter builder

### Privacy & Performance
- Configurable hash rotation (1-8760 hours)
- Three privacy modes: STANDARD, STRICT (country-only), PARANOID (no geo/UA)
- Data retention policies with automatic purge
- Query result caching (Caffeine)
- GeoIP lookup caching
- 8 database indexes for fast analytics queries

### Dashboard UI
- Light/dark theme support
- Loading skeleton screens
- Accessibility (ARIA labels, keyboard navigation)
- CSV data export
- Interactive maps with Leaflet
- 6 chart types with view toggles

### API
- OpenAPI 3.0.3 documentation
- Pagination for all list endpoints
- Standardized error responses
- Health check and metrics endpoints

## Quick Start

### 1. Run the application

```bash
./gradlew run
```

### 2. Complete the setup wizard

Visit `http://localhost:8080` — the web-based setup wizard will guide you through:

1. **Security** — Admin credentials and server salt (auto-generated)
2. **Database** — SQLite (simple) or PostgreSQL (production)
3. **Server** — Port, CORS origins, development mode
4. **Advanced** — GeoIP path, rate limiting
5. **Review** — Confirm and save

No restart required — the app transitions seamlessly to the admin panel.

### 3. Add the tracking script

```html
<script
  async
  src="https://your-domain.com/admin-panel/tracker.js"
  data-project-key="YOUR_API_KEY">
</script>
```

#### Optional attributes

```html
<script
  async
  src="https://your-domain.com/admin-panel/tracker.js"
  data-project-key="YOUR_API_KEY"
  data-heartbeat-interval="30000"
  data-disable-spa="false">
</script>
```

### 4. Track custom events

```javascript
MiniNumbers.track("signup");
MiniNumbers.track("purchase");
```

## Docker

```bash
docker build -t mini-numbers .
docker run -p 8080:8080 mini-numbers
```

Or with Docker Compose:

```bash
docker compose up -d
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Ktor 3.4.0 |
| Language | Kotlin 2.3.0 (JDK 21) |
| Database | SQLite or PostgreSQL (Exposed ORM) |
| Server | Netty (embedded) |
| Caching | Caffeine |
| GeoIP | MaxMind GeoLite2 |
| Build | Gradle with Kotlin DSL |

## Building & Running

| Task | Description |
|------|-------------|
| `./gradlew run` | Run the server (development) |
| `./gradlew test` | Run the test suite (166 tests) |
| `./gradlew build` | Build everything |
| `./gradlew buildFatJar` | Build executable JAR with all dependencies |
| `./gradlew reset` | Reset database and re-seed demo data |

## Configuration

Configuration is managed via `.env` file (created by the setup wizard) or environment variables:

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
| `HASH_ROTATION_HOURS` | No | `24` | Hash rotation period in hours (1-8760) |
| `PRIVACY_MODE` | No | `STANDARD` | Privacy mode: `STANDARD`, `STRICT`, `PARANOID` |
| `DATA_RETENTION_DAYS` | No | `0` | Auto-delete events older than N days (0 = disabled) |
| `TRACKER_HEARTBEAT_INTERVAL` | No | `30` | Default heartbeat interval in seconds |
| `TRACKER_SPA_ENABLED` | No | `true` | Enable SPA tracking by default |

## Testing

```bash
./gradlew test
```

166 tests covering security, input validation, service lifecycle, analytics calculations, data collection, admin endpoints, and end-to-end tracking workflows. See [CLAUDE.md](CLAUDE.md) for detailed test organization.

## API Documentation

OpenAPI 3.0.3 specification available at `/admin-panel/openapi.yaml` when the application is running.

Key endpoints:
- `GET /health` — Health check
- `GET /metrics` — Application metrics
- `POST /collect` — Data collection
- `GET /admin/projects` — Project management
- `GET /admin/projects/{id}/report/comparison` — Analytics reports
- `GET /admin/projects/{id}/goals/stats` — Conversion goal stats
- `GET /admin/projects/{id}/segments` — User segments

## Documentation

- [CLAUDE.md](CLAUDE.md) — Full technical architecture and API reference
- [_docs/CHANGELOG.md](_docs/CHANGELOG.md) — Version history
- [_docs/ROADMAP.md](_docs/ROADMAP.md) — Development roadmap
- [_docs/TODO.md](_docs/TODO.md) — Task tracking
- [_docs/COMPETITIVE_ANALYSIS.md](_docs/COMPETITIVE_ANALYSIS.md) — Competitive analysis

## License

[MIT](LICENSE)
