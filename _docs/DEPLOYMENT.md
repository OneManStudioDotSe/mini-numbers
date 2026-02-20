# Mini Numbers - Deployment Guide

## Prerequisites

- **Java 21** (JDK) — Amazon Corretto, Eclipse Temurin, or any OpenJDK 21 distribution
- **512 MB RAM** minimum (1 GB recommended)
- **~100 MB disk** for the application + GeoIP database
- A domain name with DNS pointing to your server (for production)

### Verify Java

```bash
java -version
# Should show: openjdk version "21.x.x"
```

If not installed:

```bash
# Ubuntu/Debian
sudo apt install openjdk-21-jdk

# Amazon Linux / RHEL
sudo yum install java-21-amazon-corretto

# macOS (Homebrew)
brew install openjdk@21
```

---

## Installation

### Option 1: Fat JAR (Recommended)

Build a self-contained JAR with all dependencies included:

```bash
git clone https://github.com/user/mini-numbers.git
cd mini-numbers
./gradlew buildFatJar
```

The JAR is created at `build/libs/mini-numbers-all.jar`.

Copy it to your server:

```bash
scp build/libs/mini-numbers-all.jar user@yourserver:/opt/mini-numbers/
```

Run it:

```bash
java -jar /opt/mini-numbers/mini-numbers-all.jar
```

### Option 2: Build from Source

```bash
git clone https://github.com/user/mini-numbers.git
cd mini-numbers
./gradlew run
```

### Option 3: Docker

#### Using the Ktor Gradle Plugin

The Ktor Gradle plugin includes built-in Docker support:

```bash
# Build Docker image
./gradlew buildImage

# Publish to local Docker registry
./gradlew publishImageToLocalRegistry

# Run the Docker container
./gradlew runDocker
```

#### Using a Custom Dockerfile

For more control over the build, create a `Dockerfile` in the project root:

```dockerfile
# Stage 1: Build
FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY src ./src
RUN gradle buildFatJar --no-daemon

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S analytics && adduser -S analytics -G analytics

# Copy the fat JAR from build stage
COPY --from=build /app/build/libs/*-all.jar app.jar

# Create data directory for SQLite and backups
RUN mkdir -p /app/data /app/backups && chown -R analytics:analytics /app

USER analytics

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and run:

```bash
# Build the image
docker build -t mini-numbers .

# Run with inline environment variables
docker run -d \
  --name mini-numbers \
  -p 8080:8080 \
  -v mini-numbers-data:/app/data \
  -e ADMIN_USERNAME=admin \
  -e ADMIN_PASSWORD=your-secure-password \
  -e SERVER_SALT=$(openssl rand -hex 64) \
  -e DB_TYPE=SQLITE \
  -e DB_SQLITE_PATH=/app/data/stats.db \
  mini-numbers

# Or run with an env file
docker run -d \
  --name mini-numbers \
  -p 8080:8080 \
  -v mini-numbers-data:/app/data \
  --env-file .env \
  mini-numbers
```

#### Docker Compose

Create a `docker-compose.yml` for easy deployment:

**SQLite setup (simplest):**

```yaml
services:
  mini-numbers:
    build: .
    container_name: mini-numbers
    restart: unless-stopped
    ports:
      - "8080:8080"
    volumes:
      - analytics-data:/app/data
    env_file:
      - .env
    environment:
      - DB_TYPE=SQLITE
      - DB_SQLITE_PATH=/app/data/stats.db
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8080/"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 10s

volumes:
  analytics-data:
```

**PostgreSQL setup (production):**

```yaml
services:
  mini-numbers:
    build: .
    container_name: mini-numbers
    restart: unless-stopped
    ports:
      - "8080:8080"
    env_file:
      - .env
    environment:
      - DB_TYPE=POSTGRESQL
      - DB_PG_HOST=postgres
      - DB_PG_PORT=5432
      - DB_PG_NAME=mini_numbers
      - DB_PG_USERNAME=analytics
      - DB_PG_PASSWORD=${DB_PG_PASSWORD}
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8080/"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 10s

  postgres:
    image: postgres:16-alpine
    container_name: mini-numbers-db
    restart: unless-stopped
    volumes:
      - postgres-data:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: mini_numbers
      POSTGRES_USER: analytics
      POSTGRES_PASSWORD: ${DB_PG_PASSWORD}
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U analytics -d mini_numbers"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s

volumes:
  postgres-data:
```

**Running with Docker Compose:**

```bash
# Start all services
docker compose up -d

# View logs
docker compose logs -f mini-numbers

# Stop all services
docker compose down

# Stop and remove volumes (deletes data!)
docker compose down -v
```

#### Docker Tips

- **Data persistence**: Always use named volumes (`analytics-data:`, `postgres-data:`) or bind mounts to persist data across container restarts
- **Environment variables**: Use `--env-file .env` or the `env_file` directive in docker-compose instead of hardcoding secrets
- **GeoIP database**: The GeoIP database is bundled inside the JAR and extracted to a temp file automatically — no volume mount needed
- **Resource limits**: For production, set memory limits: `docker run --memory=512m ...` or use `mem_limit: 512m` in docker-compose
- **Networking**: When using docker-compose with PostgreSQL, the app connects to `postgres` (the service name) instead of `localhost`
- **Updating**: Pull the latest code, rebuild the image, and recreate the container. Database schema migrations are handled automatically

---

## First-Time Setup

When you run Mini Numbers for the first time (no `.env` file present), the setup wizard launches automatically.

### Setup Wizard

1. Open your browser and navigate to `http://your-server:8080`
2. You'll be redirected to the interactive setup wizard

**Step 1 — Security Configuration**
- Set admin username and password
- A cryptographic server salt is auto-generated (128-character hex string)
- This salt is used for privacy-preserving visitor hashing

**Step 2 — Database Selection**
- **SQLite** (recommended for getting started): Simple file-based database, no additional setup
- **PostgreSQL**: Production-ready with connection pooling, recommended for high-traffic sites

**Step 3 — Server Configuration**
- Port number (default: 8080)
- CORS allowed origins (comma-separated domains, e.g., `https://example.com,https://www.example.com`)
- Development mode toggle

**Step 4 — Advanced Settings**
- GeoIP database path (bundled by default, no action needed)
- Rate limiting: requests per minute per IP (default: 1000) and per API key (default: 10000)

**Step 5 — Review & Confirm**
- Review all settings
- Click "Complete Setup" to save configuration and start services

After completing setup, the application initializes instantly (< 1 second, no restart required) and redirects you to the admin dashboard.

### Quick Setup (Testing)

For rapid testing, click the "Quick Setup (Testing)" button in the wizard header. This auto-fills all fields with sensible defaults and completes setup in one click.

---

## Configuration Reference

All configuration is stored in the `.env` file in the application root. Environment variables take precedence over `.env` values.

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `ADMIN_PASSWORD` | Admin panel password | `your-secure-password` |
| `SERVER_SALT` | Cryptographic salt for visitor hashing (min 32 chars) | `openssl rand -hex 64` |

### Optional Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ADMIN_USERNAME` | `admin` | Admin panel username |
| `SERVER_PORT` | `8080` | HTTP port |
| `KTOR_DEVELOPMENT` | `false` | Enable development mode (allows all CORS origins) |
| `ALLOWED_ORIGINS` | _(empty)_ | Comma-separated list of allowed CORS origins |
| `DB_TYPE` | `SQLITE` | Database type: `SQLITE` or `POSTGRESQL` |
| `DB_SQLITE_PATH` | `./stats.db` | SQLite database file path |
| `GEOIP_DATABASE_PATH` | `src/main/resources/geo/geolite2-city.mmdb` | GeoIP database path |
| `RATE_LIMIT_PER_IP` | `1000` | Max requests per minute per IP |
| `RATE_LIMIT_PER_API_KEY` | `10000` | Max requests per minute per API key |

### PostgreSQL Variables (required when `DB_TYPE=POSTGRESQL`)

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_PG_HOST` | `localhost` | PostgreSQL host |
| `DB_PG_PORT` | `5432` | PostgreSQL port |
| `DB_PG_NAME` | `mini_numbers` | Database name |
| `DB_PG_USERNAME` | `postgres` | Database username |
| `DB_PG_PASSWORD` | _(required)_ | Database password |
| `DB_PG_MAX_POOL_SIZE` | `3` | HikariCP connection pool size |

### Example `.env` File

```env
# Security (required)
ADMIN_USERNAME=admin
ADMIN_PASSWORD=your-secure-password-here
SERVER_SALT=your-128-char-hex-salt-here

# Server
SERVER_PORT=8080
KTOR_DEVELOPMENT=false
ALLOWED_ORIGINS=https://example.com,https://www.example.com

# Database (SQLite)
DB_TYPE=SQLITE
DB_SQLITE_PATH=./stats.db

# Rate Limiting
RATE_LIMIT_PER_IP=1000
RATE_LIMIT_PER_API_KEY=10000
```

---

## Tracker Integration

### Basic Installation

Add the tracking script to your website's `<head>` or before `</body>`:

```html
<script
  async
  src="https://your-analytics-domain.com/admin-panel/tracker.js"
  data-project-key="YOUR_PROJECT_API_KEY">
</script>
```

Replace:
- `your-analytics-domain.com` with your Mini Numbers server domain
- `YOUR_PROJECT_API_KEY` with the API key from your project settings

### What It Tracks

- **Pageviews**: Automatically tracked on page load
- **Time on page**: 30-second heartbeat intervals (pauses when tab is hidden)
- **SPA navigation**: Automatically detects route changes via History API
- **Session duration**: Using `sessionStorage` (no cookies)

### Custom Event Tracking

Track user interactions with the JavaScript API:

```javascript
// Track a custom event
MiniNumbers.track('signup');
MiniNumbers.track('download');
MiniNumbers.track('purchase');
MiniNumbers.track('newsletter_subscribe');
```

Custom events appear in a dedicated "Custom Events" section on the dashboard.

### SPA Support

The tracker automatically patches `history.pushState` and `history.replaceState` to detect route changes in single-page applications. No additional configuration is needed for React, Vue, Angular, or other SPA frameworks.

### Custom API Endpoint

If your analytics server is on a different domain:

```html
<script
  async
  src="https://your-analytics-domain.com/admin-panel/tracker.js"
  data-project-key="YOUR_PROJECT_API_KEY"
  data-api-endpoint="https://your-analytics-domain.com/collect">
</script>
```

### Tracker Size

- Source: ~2 KB
- Minified: ~1.5 KB (auto-generated during build)
- No external dependencies

---

## Reverse Proxy

Running Mini Numbers behind a reverse proxy is recommended for production. This provides SSL termination, caching, and additional security.

### Nginx

```nginx
server {
    listen 80;
    server_name analytics.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name analytics.example.com;

    ssl_certificate /etc/letsencrypt/live/analytics.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/analytics.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Cache static assets
    location /admin-panel/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        expires 1h;
        add_header Cache-Control "public, immutable";
    }

    # Cache tracker script
    location = /admin-panel/tracker.min.js {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        expires 1d;
        add_header Cache-Control "public, immutable";
    }
}
```

### Caddy

```
analytics.example.com {
    reverse_proxy 127.0.0.1:8080

    @static path /admin-panel/*
    header @static Cache-Control "public, max-age=3600, immutable"

    @tracker path /admin-panel/tracker.min.js
    header @tracker Cache-Control "public, max-age=86400, immutable"
}
```

Caddy automatically provisions and renews SSL certificates via Let's Encrypt.

---

## SSL / HTTPS

### Let's Encrypt with Certbot (Nginx)

```bash
# Install certbot
sudo apt install certbot python3-certbot-nginx

# Obtain certificate
sudo certbot --nginx -d analytics.example.com

# Auto-renewal is configured automatically
# Test with:
sudo certbot renew --dry-run
```

### Caddy (Automatic)

Caddy handles SSL automatically. Just point your domain to the server and Caddy will obtain and renew certificates.

---

## Running as a System Service

### systemd (Linux)

Create `/etc/systemd/system/mini-numbers.service`:

```ini
[Unit]
Description=Mini Numbers Analytics
After=network.target

[Service]
Type=simple
User=analytics
Group=analytics
WorkingDirectory=/opt/mini-numbers
ExecStart=/usr/bin/java -jar /opt/mini-numbers/mini-numbers-all.jar
Restart=always
RestartSec=5

# Security hardening
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=/opt/mini-numbers

# Environment
EnvironmentFile=/opt/mini-numbers/.env

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
# Create service user
sudo useradd -r -s /bin/false analytics

# Set permissions
sudo chown -R analytics:analytics /opt/mini-numbers

# Enable and start
sudo systemctl daemon-reload
sudo systemctl enable mini-numbers
sudo systemctl start mini-numbers

# Check status
sudo systemctl status mini-numbers

# View logs
sudo journalctl -u mini-numbers -f
```

---

## Backup & Recovery

### SQLite Backup

```bash
# Simple file copy (stop service first for consistency)
sudo systemctl stop mini-numbers
cp /opt/mini-numbers/stats.db /opt/mini-numbers/backups/stats-$(date +%Y%m%d).db
sudo systemctl start mini-numbers

# Or use SQLite's online backup (no downtime)
sqlite3 /opt/mini-numbers/stats.db ".backup /opt/mini-numbers/backups/stats-$(date +%Y%m%d).db"
```

### PostgreSQL Backup

```bash
# Full database dump
pg_dump -U postgres mini_numbers > backup-$(date +%Y%m%d).sql

# Restore
psql -U postgres mini_numbers < backup-20260220.sql
```

### Automated Backup (Cron)

```bash
# Add to crontab: daily backup at 2 AM
0 2 * * * sqlite3 /opt/mini-numbers/stats.db ".backup /opt/mini-numbers/backups/stats-$(date +\%Y\%m\%d).db"

# Keep last 30 days
0 3 * * * find /opt/mini-numbers/backups -name "stats-*.db" -mtime +30 -delete
```

---

## Upgrading

1. **Backup** your database and `.env` file
2. **Download** the new JAR or pull the latest source
3. **Stop** the service: `sudo systemctl stop mini-numbers`
4. **Replace** the JAR file
5. **Start** the service: `sudo systemctl start mini-numbers`

Database schema migrations are handled automatically — Mini Numbers uses `createMissingTablesAndColumns` which adds new columns to existing tables without data loss.

```bash
# Example upgrade workflow
cd /opt/mini-numbers

# Backup
cp stats.db backups/stats-pre-upgrade-$(date +%Y%m%d).db
cp .env backups/env-pre-upgrade-$(date +%Y%m%d)

# Replace JAR
sudo systemctl stop mini-numbers
cp /path/to/new/mini-numbers-all.jar .
sudo systemctl start mini-numbers

# Verify
sudo systemctl status mini-numbers
curl -s http://localhost:8080 | head -5
```

---

## Troubleshooting

### Application won't start

**"Required configuration missing: ADMIN_PASSWORD"**
- The `.env` file is missing or doesn't contain `ADMIN_PASSWORD`
- Solution: Run the setup wizard by visiting `http://your-server:8080` or create a `.env` file manually

**"JAVA_HOME is set to an invalid directory"**
- Java 21 is not installed or `JAVA_HOME` points to the wrong location
- Solution: Install JDK 21 and set `JAVA_HOME` correctly
- Find Java: `/usr/libexec/java_home -V` (macOS) or `update-alternatives --list java` (Linux)

**Port already in use**
- Another process is using port 8080
- Solution: Change `SERVER_PORT` in `.env` or stop the conflicting process
- Find what's using the port: `lsof -i :8080`

### GeoIP not working

**"GeoIP database not found"**
- The MaxMind database file is missing
- When running from JAR: the database is bundled and extracted automatically
- When running from source: ensure `src/main/resources/geo/geolite2-city.mmdb` exists
- Location data will show as "Unknown" until the database is available

### Database issues

**"Database is locked" (SQLite)**
- Multiple processes are trying to write simultaneously
- Solution: Ensure only one instance of Mini Numbers is running
- Check: `ps aux | grep mini-numbers`

**Connection refused (PostgreSQL)**
- PostgreSQL is not running or connection settings are wrong
- Verify: `psql -U postgres -h localhost -p 5432 mini_numbers`
- Check PostgreSQL status: `sudo systemctl status postgresql`

### CORS errors in browser console

**"Origin not allowed"**
- Your website domain is not in the `ALLOWED_ORIGINS` list
- Solution: Add your domain to `ALLOWED_ORIGINS` in `.env` (comma-separated)
- In development mode (`KTOR_DEVELOPMENT=true`), all origins are allowed

### Rate limiting

**429 Too Many Requests**
- A client or API key has exceeded the rate limit
- Defaults: 1000 requests/minute per IP, 10000 per API key
- Adjust in `.env`: `RATE_LIMIT_PER_IP` and `RATE_LIMIT_PER_API_KEY`

### Re-running setup

To reconfigure the application from scratch:

```bash
# Backup current config
cp .env .env.backup

# Remove config to trigger setup wizard
rm .env

# Restart application
sudo systemctl restart mini-numbers

# Visit http://your-server:8080 to run setup wizard
```
