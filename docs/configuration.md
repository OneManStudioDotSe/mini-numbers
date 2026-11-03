---
title: Configuration
layout: default
nav_order: 5
---

# Configuration

Mini Numbers reads its settings from environment variables and from a `.env` file in the folder it runs from
(in Docker: the `/app/data` volume). The setup wizard writes that file for you. An environment variable always
wins over the same line in `.env`.

**To change a setting:** edit `.env` (or the variable in your hosting panel) and restart Mini Numbers.

Changing `SERVER_SALT` makes every visitor look new from then on — keep it secret and keep it the same.

---

## Required

| Setting | What it is |
|---------|------------|
| `ADMIN_PASSWORD` | Password for the first admin. Plain text is fine; it's stored hashed. |
| `SERVER_SALT` | Secret used to make visitor identifiers anonymous. At least 32 characters (`openssl rand -hex 64`). |

## Account and access

| Setting | Default | What it does |
|---------|---------|--------------|
| `ADMIN_USERNAME` | `admin` | Username of the first admin |
| `ALLOWED_ORIGINS` | *(empty = any)* | Websites allowed to call the **admin API** from a browser, comma-separated (`https://app.example.com`). The dashboard itself and the tracker always work, whatever is set here |
| `KTOR_DEVELOPMENT` | `false` | `true` lets you sign in over plain HTTP and turns off HSTS. Only for testing on your own computer or network |

## Server

| Setting | Default | What it does |
|---------|---------|--------------|
| `PORT` | — | Port to listen on. Hosting platforms set it; the Docker image sets `8080`. Wins over `SERVER_PORT` |
| `SERVER_PORT` | `8080` | Port to listen on when `PORT` isn't set (the wizard saves this one) |
| `TRUST_PROXY` | `false` | `true` when a reverse proxy or hosting platform sits in front, so the visitor's real IP is read from `X-Forwarded-For`. Leave it off when visitors connect directly, or they could fake their IP |
| `RATE_LIMIT_PER_IP` | `1000` | Requests per minute from one IP address |
| `RATE_LIMIT_PER_API_KEY` | `10000` | Requests per minute for one website (project key) |
| `TZ` | *(system; `UTC` in Docker)* | Time zone used for "today", the hour and day charts and the calendar. Individual timestamps are sent with this zone's offset, so the dashboard shows the right instant wherever you open it. Standard `TZ` names such as `Europe/Stockholm` |

Requests over a limit get HTTP 429 and the event is dropped.

## Privacy

| Setting | Default | What it does |
|---------|---------|--------------|
| `PRIVACY_MODE` | `STANDARD` | `STANDARD`, `STRICT` or `PARANOID` — what is stored, see [Privacy](privacy) |
| `HASH_ROTATION_HOURS` | `24` | After how many hours a returning visitor counts as new (1–8760) |
| `DATA_RETENTION_DAYS` | `0` | Delete events older than this many days; `0` keeps everything. Checked every 6 hours |

## Database

| Setting | Default | What it does |
|---------|---------|--------------|
| `DB_TYPE` | `SQLITE` | `SQLITE` or `POSTGRESQL` |
| `DB_SQLITE_PATH` | `./stats.db` | Database file (SQLite) |
| `DB_PG_HOST` | `localhost` | PostgreSQL host |
| `DB_PG_PORT` | `5432` | PostgreSQL port |
| `DB_PG_NAME` | `mini_numbers` | Database name |
| `DB_PG_USERNAME` | `postgres` | Database user |
| `DB_PG_PASSWORD` | *(required for PostgreSQL)* | Database password |
| `DB_PG_MAX_POOL_SIZE` | `3` | Connections kept open |

Tables are created and updated automatically at start-up.

## Email reports

Email reports stay switched off until `SMTP_HOST` and `SMTP_FROM` are set.

| Setting | Default | What it does |
|---------|---------|--------------|
| `SMTP_HOST` | — | Mail server |
| `SMTP_PORT` | `587` | Mail server port |
| `SMTP_USERNAME` | — | Login for the mail server |
| `SMTP_PASSWORD` | — | Password for the mail server |
| `SMTP_FROM` | — | Sender address on the reports |
| `SMTP_STARTTLS` | `true` | Encrypt the connection with STARTTLS |

## Location lookup

| Setting | Default | What it does |
|---------|---------|--------------|
| `GEOIP_DATABASE_PATH` | bundled file | Path to a MaxMind GeoLite2 City database. A copy built on 2026-01-30 is included (this product includes GeoLite2 data created by MaxMind, available from [maxmind.com](https://www.maxmind.com)); set this to use a newer download from MaxMind |

---

## Example `.env`

```bash
ADMIN_PASSWORD=a-long-password
SERVER_SALT=paste-the-output-of-openssl-rand-hex-64-here
PRIVACY_MODE=STANDARD
HASH_ROTATION_HOURS=24
DATA_RETENTION_DAYS=365
```

Everything else can stay on its default. Tracker options (heartbeat interval, single-page-app tracking) are set
on the tracking snippet, see [For developers](developers).
