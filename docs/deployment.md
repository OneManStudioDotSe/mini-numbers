---
title: Install
layout: default
nav_order: 2
---

# Install Mini Numbers

Mini Numbers is a single program that runs on your own server. Pick the kind of server you have; each
path takes about five minutes. When it starts, open it in your browser and a short setup wizard does the rest.

| You have… | Use |
|-----------|-----|
| A VPS (Hetzner, DigitalOcean, Hostinger VPS, OVHcloud, Linode, Vultr…) | [Option 1: Docker on a VPS](#option-1-docker-on-a-vps-recommended) |
| A VPS with a panel (Coolify, Dokploy, CapRover, Easypanel) | [Option 2: Server panel](#option-2-a-server-panel-on-your-vps) |
| An account on Railway, Render or Fly.io | [Option 3: Hosting platforms](#option-3-hosting-platforms) |
| Shared web hosting with cPanel | [Option 4: cPanel](#option-4-cpanel-hosting) |
| A server where you prefer not to use Docker | [Option 5: Java + systemd](#option-5-without-docker-java--systemd) |
| Just your own computer, to try it | [Try it locally](#try-it-on-your-own-computer) |

**What it needs:** 1 CPU, 1 GB of RAM (512 MB works for small sites), and a (sub)domain such as
`stats.example.com` so it can use HTTPS. Your website itself can stay wherever it is today — only the
analytics server runs here.

---

## Option 1: Docker on a VPS (recommended)

Works on any Ubuntu or Debian server. HTTPS is set up automatically.

1. **Point your domain at the server.** In your DNS settings, add an `A` record for `stats` (or any name)
   with your server's IP address.
2. **Install Docker** (skip if it's already installed):
   ```bash
   curl -fsSL https://get.docker.com | sh
   ```
3. **Download and start Mini Numbers** (replace the domain with yours):
   ```bash
   mkdir mini-numbers && cd mini-numbers
   curl -fsSLO https://raw.githubusercontent.com/OneManStudioDotSe/mini-numbers/main/docker-compose.yml
   echo "DOMAIN=stats.example.com" > .env
   docker compose up -d
   ```
4. **Open `https://stats.example.com`** and complete the setup wizard.

{: .important }
Until the wizard is completed, whoever opens the address first chooses the admin password. Complete it right
after starting, or skip it altogether: add `ADMIN_PASSWORD=<a long password>` and `SERVER_SALT=<output of
openssl rand -hex 64>` to `.env` before `docker compose up -d`, and Mini Numbers starts ready to sign in.

If the page doesn't load, check that ports 80 and 443 are open in your provider's firewall
(Hetzner, DigitalOcean and others have one in their control panel).

{: .tip }
Prefer PostgreSQL over SQLite? Download `docker-compose.postgres.yml` instead, add a line
`DB_PG_PASSWORD=<a long random password>` to `.env`, and start it with
`docker compose -f docker-compose.postgres.yml up -d`. SQLite is fine for most sites.

---

## Option 2: A server panel on your VPS

In Coolify, Dokploy, CapRover, Easypanel or a similar panel, create a new app from a **Docker image**:

| Setting | Value |
|---------|-------|
| Image | `ghcr.io/onemanstudiodotse/mini-numbers:latest` |
| Port | `8080` |
| Persistent storage (volume) | mount at `/app/data` |
| Environment variable | `TRUST_PROXY=true` |

Add your domain in the panel (it takes care of HTTPS), deploy, then open the domain to run the setup wizard.

---

## Option 3: Hosting platforms

All three need a persistent volume at `/app/data` — that's where your settings and data live.

**Railway**
1. New project → **Deploy a Docker image** → `ghcr.io/onemanstudiodotse/mini-numbers:latest`.
2. Add a **Volume** mounted at `/app/data`.
3. Add the variable `TRUST_PROXY=true`, then generate a domain under **Networking**.

Deploying a fork of the repository instead also works: Railway reads `railway.json`, builds the `Dockerfile` and
uses `/health` as its health check. The volume and `TRUST_PROXY=true` still have to be added by hand (steps 2–3),
because a Railway config file can't declare them.

**Render** (needs a paid plan, because free services can't keep files)
1. Fork the repository on GitHub.
2. In Render: **New → Blueprint** → pick your fork. It reads `render.yaml` and creates the service and its disk.

**Fly.io**
```bash
git clone https://github.com/OneManStudioDotSe/mini-numbers.git && cd mini-numbers
fly launch --copy-config --no-deploy        # choose a unique app name when asked
fly volumes create analytics_data --size 1
fly deploy
```
Fly stops the app when nobody visits; scheduled email reports and data clean-up only run while it's awake.
Set `min_machines_running = 1` in `fly.toml` if you use them.

After deploying, open the address the platform gives you and complete the setup wizard.

---

## Option 4: cPanel hosting

**Shared cPanel hosting can't run Mini Numbers.** It needs a program that keeps running all the time
(Java), and shared hosting only runs PHP websites (and sometimes Node.js or Python apps).

What you can do instead:

- **Keep your website on cPanel** and run Mini Numbers on a small VPS (from about €4/month) using
  [Option 1](#option-1-docker-on-a-vps-recommended). Point a subdomain like `stats.yoursite.com` at the VPS
  and add the tracking script to your cPanel website as usual.
- **cPanel on your own VPS or dedicated server (root access):** cPanel's web server already uses ports 80
  and 443, so don't use the compose file. Run the container on a local port and add a reverse proxy for
  your subdomain in WHM — see [Behind your own web server](#behind-your-own-web-server).

---

## Option 5: Without Docker (Java + systemd)

For Ubuntu 22.04 or newer (on Debian 12, install Java 21 from [adoptium.net](https://adoptium.net) first).

1. **Install Java and Caddy** (Caddy handles HTTPS):
   ```bash
   sudo apt update && sudo apt install -y openjdk-21-jre-headless caddy
   ```
2. **Install Mini Numbers as a service:**
   ```bash
   sudo useradd --system --home /opt/mini-numbers --shell /usr/sbin/nologin mini-numbers
   sudo mkdir -p /opt/mini-numbers
   sudo curl -fL -o /opt/mini-numbers/mini-numbers.jar \
     https://github.com/OneManStudioDotSe/mini-numbers/releases/latest/download/mini-numbers.jar
   sudo chown -R mini-numbers:mini-numbers /opt/mini-numbers
   sudo curl -fsSL -o /etc/systemd/system/mini-numbers.service \
     https://raw.githubusercontent.com/OneManStudioDotSe/mini-numbers/main/deploy/mini-numbers.service
   sudo systemctl daemon-reload && sudo systemctl enable --now mini-numbers
   ```
3. **Connect your domain.** Replace the contents of `/etc/caddy/Caddyfile` with:
   ```
   stats.example.com {
       reverse_proxy 127.0.0.1:8080
   }
   ```
   then run `sudo systemctl reload caddy` and open `https://stats.example.com`.

Your settings (`.env`) and database (`stats.db`) are stored in `/opt/mini-numbers`.

---

## Try it on your own computer

With Docker:
```bash
docker run --rm -p 8080:8080 -v mini-numbers-data:/app/data ghcr.io/onemanstudiodotse/mini-numbers:latest
```
Or with Java 21: download `mini-numbers.jar` from the
[latest release](https://github.com/OneManStudioDotSe/mini-numbers/releases/latest) and run
`java -jar mini-numbers.jar`.

Then open [http://localhost:8080](http://localhost:8080).

{: .note }
Signing in only works over HTTPS or on `localhost`, because the login cookie is secure-only. If you open the
server by its IP address over plain HTTP, the login page tells you so — set up a domain with HTTPS
(Options 1–5 all do), or turn on *development mode* in the setup wizard for testing.

---

## Behind your own web server

Already running nginx, Apache, Traefik or a panel's proxy? Run Mini Numbers on a local port only:

```bash
docker run -d --name mini-numbers --restart unless-stopped \
  -p 127.0.0.1:8080:8080 -v mini-numbers-data:/app/data -e TRUST_PROXY=true \
  ghcr.io/onemanstudiodotse/mini-numbers:latest
```

and forward your (sub)domain to `http://127.0.0.1:8080`. For nginx:

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

`TRUST_PROXY=true` makes Mini Numbers read the visitor's real IP address from the `X-Forwarded-For`
header your proxy adds. Without it every visitor looks like your proxy: one "unique visitor", no countries.
Only turn it on when a proxy sits in front — otherwise visitors could fake their address.

---

## After installing

**Upgrading** — make a backup (below), then:

- Docker Compose: `docker compose pull && docker compose up -d`
- Java: download the new `mini-numbers.jar` over the old one, then `sudo systemctl restart mini-numbers`

Database changes are applied automatically when the new version starts, and the tracking script is served by
your server, so websites get the new version without any change. What's new in each version is in the
[changelog](https://github.com/OneManStudioDotSe/mini-numbers/blob/main/_docs/CHANGELOG.md). To go back,
restore your backup and run the previous version (for Docker, pin it, e.g. `mini-numbers:1.3`).

{: .warning }
Upgrading a Docker install made before version 1.4? Those kept the wizard's `.env` outside the data volume, so
the wizard would appear again. Before upgrading, run `docker cp mini-numbers:/app/.env ./old.env`; afterwards,
`docker cp ./old.env mini-numbers:/app/data/.env && docker compose restart mini-numbers`.

**Backups** — your settings and data are two files: `.env` and `stats.db`. SQLite keeps recent writes in
`stats.db-wal` until it checkpoints, so copy the files with the service stopped (as below) or take a consistent copy
while running with `sqlite3 stats.db ".backup mini-numbers-backup.db"`.

- Docker Compose (run inside the `mini-numbers` folder):
  ```bash
  docker compose stop mini-numbers
  docker run --rm -v mini-numbers_analytics-data:/data -v "$PWD":/backup alpine \
    tar czf /backup/mini-numbers-backup.tgz -C /data .
  docker compose start mini-numbers
  ```
- Java: `sudo systemctl stop mini-numbers && sudo tar czf ~/mini-numbers-backup.tgz -C /opt/mini-numbers .env stats.db && sudo systemctl start mini-numbers`
- PostgreSQL: `docker compose -f docker-compose.postgres.yml exec postgres pg_dump -U analytics mini_numbers > backup.sql`

**Verify your install** — `scripts/smoke.sh` checks a running server end to end in about 30 seconds: health,
HTTPS and security headers, the tracker script, that a page view is accepted, stored and readable, that wrong
passwords are refused, and that widgets stay off unless you switched them on. Create a project named "Smoke test"
first (its numbers fill with the test's page views, not your site's), then:

```bash
curl -fsSLO https://raw.githubusercontent.com/OneManStudioDotSe/mini-numbers/main/scripts/smoke.sh && chmod +x smoke.sh
BASE_URL=https://stats.example.com PROJECT_KEY=<smoke project key> ./smoke.sh
```

It asks for the admin password, prints one line per check and exits non-zero if any fails. Run it after every upgrade.

**Health check** — `GET /health` returns `{"status":"healthy","state":"READY",…}` once setup is complete,
for uptime monitors and load balancers.

**Settings** — every option is listed in [Configuration](configuration). Something not working? See
[Troubleshooting](troubleshooting).
