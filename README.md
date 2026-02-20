# Mini Numbers

A **privacy-first, minimalist web analytics platform** built with Kotlin and Ktor. Track your website traffic without compromising visitor privacy — a lightweight, self-hosted alternative to Google Analytics.

## Why Mini Numbers?

- **No cookies** — No consent banners needed
- **No PII stored** — IP addresses are never persisted
- **Daily-rotating visitor hashes** — Prevents cross-day tracking
- **Self-hosted** — Complete data ownership
- **Lightweight** — < 2KB tracking script
- **GDPR-friendly** — Privacy by design

## Features

- Real-time analytics dashboard with time series charts
- Multi-project support with unique API keys
- Activity heatmap (7 days x 24 hours)
- GitHub-style contribution calendar (365 days)
- Live visitor feed with geographic data
- Browser, OS, device, and referrer breakdowns
- SPA support via MutationObserver
- GeoIP-based country/city detection (MaxMind GeoLite2)
- Light/dark theme support
- Demo data generator for testing

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

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Ktor 3.4.0 |
| Language | Kotlin 2.3.0 (JDK 21) |
| Database | SQLite or PostgreSQL (Exposed ORM) |
| Server | Netty (embedded) |
| GeoIP | MaxMind GeoLite2 |
| Build | Gradle with Kotlin DSL |

## Building & Running

| Task | Description |
|------|-------------|
| `./gradlew run` | Run the server (development) |
| `./gradlew test` | Run the test suite (103 tests) |
| `./gradlew build` | Build everything |
| `./gradlew buildFatJar` | Build executable JAR with all dependencies |
| `./gradlew buildImage` | Build Docker image |
| `./gradlew runDocker` | Run using local Docker image |
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

## Testing

```bash
./gradlew test
```

103 tests covering security, input validation, service lifecycle, data collection, and edge cases. See [CLAUDE.md](CLAUDE.md) for detailed test organization.

## Documentation

- [CLAUDE.md](CLAUDE.md) — Full technical architecture and API reference
- [_docs/CHANGELOG.md](_docs/CHANGELOG.md) — Version history
- [_docs/ROADMAP.md](_docs/ROADMAP.md) — Development roadmap
- [_docs/TODO.md](_docs/TODO.md) — Task tracking

## License

All rights reserved. See LICENSE for details.
