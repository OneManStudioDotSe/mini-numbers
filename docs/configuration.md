---
title: Configuration
layout: default
nav_order: 3
---

# Configuration

Mini Numbers is configured through environment variables or a `.env` file. The setup wizard creates this file for you, but you can edit it manually at any time.

---

## Where is the configuration stored?

When you complete the setup wizard, it creates a `.env` file in the application directory. This file contains all your settings as environment variables.

You can also set these variables directly in your environment (useful for Docker and cloud deployments).

---

## Required settings

These must be set for the application to start:

| Setting | Description |
|---------|-------------|
| `ADMIN_PASSWORD` | Your admin panel password |
| `SERVER_SALT` | A secret key used for visitor privacy (minimum 32 characters). The setup wizard generates this for you |

---

## All settings

### Security

| Setting | Default | Description |
|---------|---------|-------------|
| `ADMIN_USERNAME` | `admin` | Username for the admin panel |
| `ADMIN_PASSWORD` | *(required)* | Password for the admin panel |
| `SERVER_SALT` | *(required)* | Secret key for visitor hashing. Must be at least 32 characters. Keep this secret — if you change it, all visitor history will reset |

### Database

| Setting | Default | Description |
|---------|---------|-------------|
| `DB_TYPE` | `SQLITE` | Database engine: `SQLITE` or `POSTGRESQL` |
| `DB_SQLITE_PATH` | `./stats.db` | Path to the SQLite database file |

For PostgreSQL, you'll also need to set the standard PostgreSQL connection variables (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`).

### Server

| Setting | Default | Description |
|---------|---------|-------------|
| `SERVER_PORT` | `8080` | Port the server listens on |
| `ALLOWED_ORIGINS` | *(empty)* | Comma-separated list of allowed website domains (e.g., `https://example.com,https://blog.example.com`). Required for production |
| `KTOR_DEVELOPMENT` | `false` | Set to `true` for local development (relaxes CORS restrictions) |

### Privacy

| Setting | Default | Description |
|---------|---------|-------------|
| `PRIVACY_MODE` | `STANDARD` | Privacy level (see below) |
| `HASH_ROTATION_HOURS` | `24` | How often visitor identifiers rotate, in hours. Range: 1–8760 (1 hour to 1 year) |
| `DATA_RETENTION_DAYS` | `0` | Automatically delete data older than this many days. Set to `0` to keep data forever |

#### Privacy modes explained

| Mode | Location data | Browser/device info | Best for |
|------|--------------|-------------------|----------|
| **Standard** | Country + city | Yes | Most websites — full insights while respecting privacy |
| **Strict** | Country only | Yes | Sites in privacy-sensitive regions |
| **Paranoid** | None | None | Maximum privacy — only page views and custom events |

### Rate limiting

| Setting | Default | Description |
|---------|---------|-------------|
| `RATE_LIMIT_PER_IP` | `1000` | Maximum requests per IP address per minute |
| `RATE_LIMIT_PER_API_KEY` | `10000` | Maximum requests per API key per minute |

### Tracker

| Setting | Default | Description |
|---------|---------|-------------|
| `TRACKER_HEARTBEAT_INTERVAL` | `30` | How often (in seconds) the tracker sends a "still here" signal. Used to measure time spent on page |
| `TRACKER_SPA_ENABLED` | `true` | Enable automatic tracking of page changes in single-page applications (React, Vue, etc.) |

### GeoIP

| Setting | Default | Description |
|---------|---------|-------------|
| `GEOIP_DATABASE_PATH` | `src/main/resources/geo/geolite2-city.mmdb` | Path to the MaxMind GeoIP database. A database is bundled with the application |

---

## Tracker script attributes

When adding the tracking script to your website, you can customize its behavior with HTML attributes:

```html
<script
  async
  src="https://your-domain.com/tracker/tracker.js"
  data-project-key="YOUR_API_KEY"
  data-heartbeat-interval="30000"
  data-disable-spa="false">
</script>
```

| Attribute | Default | Description |
|-----------|---------|-------------|
| `data-project-key` | *(required)* | Your project's API key |
| `data-api-endpoint` | Script's origin | Custom API endpoint URL (if your analytics server is on a different domain) |
| `data-heartbeat-interval` | `30000` | Heartbeat interval in milliseconds |
| `data-disable-spa` | `false` | Set to `"true"` to turn off automatic single-page app tracking |

---

## Example `.env` file

```bash
# Required
ADMIN_PASSWORD=your-strong-password-here
SERVER_SALT=your-random-32-character-string-here-abc123

# Database
DB_TYPE=SQLITE
DB_SQLITE_PATH=./stats.db

# Server
SERVER_PORT=8080
ALLOWED_ORIGINS=https://example.com,https://blog.example.com

# Privacy
PRIVACY_MODE=STANDARD
HASH_ROTATION_HOURS=24
DATA_RETENTION_DAYS=365
```

---

## Changing settings

1. **Edit the `.env` file** (or update your environment variables)
2. **Restart the application** for changes to take effect

Most settings can be changed at any time. However, changing the `SERVER_SALT` will reset all visitor identifiers, so historical visitor data won't connect to new visits.
