---
title: Deployment
layout: default
nav_order: 7
---

# Deployment

This guide covers running Mini Numbers in production — from Docker containers to cloud platforms and reverse proxy setups.

---

## Requirements

| Resource | Minimum |
|----------|---------|
| **CPU** | 1 core |
| **RAM** | 512 MB |
| **Disk** | 100 MB (plus database storage) |
| **Runtime** | Java 21+ (included in Docker images) |

---

## Docker deployment

### Using Docker Compose (recommended)

#### With SQLite (simplest)

```bash
curl -O https://raw.githubusercontent.com/OneManStudioDotSe/mini-numbers/main/docker-compose.yml
docker compose up -d
```

#### With PostgreSQL

```bash
curl -O https://raw.githubusercontent.com/OneManStudioDotSe/mini-numbers/main/docker-compose.postgres.yml
docker compose -f docker-compose.postgres.yml up -d
```

### Using Docker directly

```bash
docker run -d \
  -p 8080:8080 \
  -v mini-numbers-data:/app/data \
  --name mini-numbers \
  ghcr.io/onemanstudiodotse/mini-numbers:latest
```

### Building from source

```bash
git clone https://github.com/OneManStudioDotSe/mini-numbers.git
cd mini-numbers
docker build -t mini-numbers .
docker run -d -p 8080:8080 mini-numbers
```

---

## Cloud platforms

### Railway

1. Fork the [Mini Numbers repository](https://github.com/OneManStudioDotSe/mini-numbers)
2. Create a new project on [Railway](https://railway.app)
3. Connect your forked repository
4. Railway detects the Dockerfile automatically
5. Add a persistent volume mounted at `/app/data` (for SQLite)
6. Deploy — the setup wizard will guide you through the rest

### Render

1. Create a new **Web Service** on [Render](https://render.com)
2. Connect your repository
3. Set the build command: `./gradlew buildFatJar`
4. Set the start command: `java -jar build/libs/mini-numbers-all.jar`
5. Add a persistent disk mounted at `/app/data`
6. Set the environment to **Java 21**

### Fly.io

1. Install the [Fly CLI](https://fly.io/docs/getting-started/installing-flyctl/)
2. Run `fly launch` in the project directory
3. Create a volume: `fly volumes create data --size 1`
4. Deploy: `fly deploy`

---

## Reverse proxy setup

In production, you'll typically run Mini Numbers behind a reverse proxy that handles SSL/HTTPS.

### Nginx

```nginx
server {
    listen 80;
    server_name analytics.example.com;
    return 301 https://$server_name$request_uri;
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
}
```

### Caddy

```
analytics.example.com {
    reverse_proxy localhost:8080
}
```

Caddy handles SSL certificates automatically — no extra configuration needed.

---

## SSL/HTTPS

### With Let's Encrypt (free)

```bash
# Install Certbot
sudo apt install certbot python3-certbot-nginx

# Get a certificate
sudo certbot --nginx -d analytics.example.com

# Auto-renewal is set up automatically
```

### With Caddy

Caddy obtains and renews SSL certificates automatically. Just use the Caddy configuration above — no extra steps.

---

## Running as a system service

To run Mini Numbers as a background service on Linux:

### Create the service file

```bash
sudo nano /etc/systemd/system/mini-numbers.service
```

```ini
[Unit]
Description=Mini Numbers Analytics
After=network.target

[Service]
Type=simple
User=mini-numbers
WorkingDirectory=/opt/mini-numbers
ExecStart=/usr/bin/java -jar mini-numbers-all.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### Enable and start

```bash
sudo systemctl enable mini-numbers
sudo systemctl start mini-numbers
```

### Check status

```bash
sudo systemctl status mini-numbers
```

---

## Backups

### SQLite

The SQLite database is a single file. Back it up by copying it:

```bash
# Stop the server first (or use SQLite's backup API)
cp /app/data/stats.db /backups/stats-$(date +%Y%m%d).db
```

### PostgreSQL

```bash
pg_dump -U postgres mini_numbers > /backups/mini-numbers-$(date +%Y%m%d).sql
```

### Automated backups with cron

```bash
# Edit crontab
crontab -e

# Add daily backup at 2 AM
0 2 * * * cp /app/data/stats.db /backups/stats-$(date +\%Y\%m\%d).db
```

---

## Upgrading

### Docker

```bash
# Pull the latest image
docker compose pull

# Restart with the new version
docker compose up -d
```

### JAR deployment

```bash
# Download or build the new JAR
./gradlew buildFatJar

# Stop the current server
sudo systemctl stop mini-numbers

# Replace the JAR file
cp build/libs/mini-numbers-all.jar /opt/mini-numbers/

# Start the server
sudo systemctl start mini-numbers
```

Database migrations run automatically on startup — no manual steps needed.

---

## Health check

Mini Numbers provides a health endpoint at `/health` that returns:

```json
{
  "status": "healthy",
  "uptime": 86400,
  "version": "1.0.0-beta"
}
```

Use this for Docker health checks, load balancer probes, or uptime monitoring.

---

## Troubleshooting

### The server won't start

- Check that port 8080 (or your configured port) is not in use
- Verify Java 21+ is installed: `java -version`
- Check the logs for error messages

### GeoIP is not working

- The GeoIP database is bundled with the application. If you see location data missing, check that the database file exists at the configured path
- In Docker, the database is included in the image

### CORS errors in the browser

- Make sure `ALLOWED_ORIGINS` includes your website's domain (with `https://`)
- For local development, set `KTOR_DEVELOPMENT=true`

### Too many rate limit errors

- Increase `RATE_LIMIT_PER_IP` or `RATE_LIMIT_PER_API_KEY` in your configuration
- Default limits are generous for most sites

### Need to re-run the setup wizard

- Delete the `.env` file and restart the application
- The setup wizard will appear again
