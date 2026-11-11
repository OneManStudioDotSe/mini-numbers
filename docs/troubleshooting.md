---
title: Troubleshooting
layout: default
nav_order: 8
---

# Troubleshooting

---

## No visits show up

1. Open your website, then the browser's developer tools → **Network**, and look for a request to
   `…/collect`. It should return **202**.
2. No request at all? Check that the snippet is on the page you opened and that `data-project-key` matches the
   tracking ID in **Settings**. Ad blockers can block analytics scripts — try with extensions turned off.
3. Request fails with **404 "Invalid API key"**: the key is wrong or was rotated. Copy it again from **Settings**.
4. Request fails with **429**: you hit a rate limit, see below.

## Every visitor counts as one, or countries are missing

Mini Numbers is behind a proxy (Caddy, nginx, a hosting platform) and sees the proxy's address instead of the
visitor's. Set `TRUST_PROXY=true` and restart. The Docker Compose files, `fly.toml` and `render.yaml` already do.

Countries can also be missing because of the privacy mode (Paranoid stores none, Strict stores country only) or
because you're testing from your own network: local addresses such as `192.168.x.x` have no location.

## I can't stay signed in

The login page says browsers only keep the sign-in on HTTPS sites, or keeps returning to the login page.
The login cookie only works over HTTPS (or on `localhost`). Open your server through its HTTPS address — every
path in the [Install guide](deployment) sets one up. For testing on your own network only, set
`KTOR_DEVELOPMENT=true`.

Wrong password? There's no default: it's the one you chose in the setup wizard, the one **Quick Setup** generated
and showed on its review step, `ADMIN_PASSWORD` if you set it in `.env` before the first start, or the one an admin
gave you when they added you.

Forgot it? Set a new one on the server, for any user:

```bash
docker compose exec mini-numbers java -jar /app/app.jar reset-password admin   # Docker
java -jar mini-numbers.jar reset-password admin                                 # JAR, from the folder with .env
```

It asks for the new password twice and signs that user out everywhere. Changing `ADMIN_PASSWORD` in `.env` doesn't
work for this: it is only read once, to create the first admin. After five wrong passwords the account is locked
for 15 minutes; a new password doesn't lift the lock, waiting (or restarting the server) does. Signed in and just
want a new password? Use **Settings → Account → Change password**.

## The setup wizard appears again

Mini Numbers can't find its `.env` file. In Docker it lives in the `/app/data` volume; make sure that volume is
kept between upgrades. Installs made with Docker files from before version 1.4 kept it elsewhere — see
[Install → Upgrading](deployment#after-installing).

## "Database is locked" (SQLite)

Mini Numbers opens SQLite in WAL mode and waits up to five seconds for a lock, so this only appears when another
process holds the file. Only one copy of Mini Numbers may use a SQLite file at a time. Running several instances? Use PostgreSQL.

## Permission errors in Docker

Named volumes (the default in the compose files) work automatically. If you mounted a folder from the host,
give it to the container's user:

```bash
uid=$(docker run --rm --entrypoint id ghcr.io/onemanstudiodotse/mini-numbers:latest -u)
sudo chown -R "$uid" /path/to/data-folder
```

## Some events are dropped (HTTP 429)

A visitor or website went over its request limit. Raise `RATE_LIMIT_PER_IP` and/or `RATE_LIMIT_PER_API_KEY`
in `.env` and restart. Behind a proxy without `TRUST_PROXY=true`, all visitors share one IP and hit the
per-IP limit together — set it first.

## Demo data doesn't appear

**Generate demo data** is only offered on the project named "Demo project", so sample visits never mix with a
real site's numbers. Click **Demo project** in the sidebar to create it (or open it, if it exists); refresh the page
if the dashboard doesn't update.

## A feature from the docs is missing

Funnels, segments, revenue, webhooks, email reports and widgets are [labs](configuration#labs), off by default:
their parts of the dashboard are hidden and their API answers 404. Add them to `LABS` and restart.

Signed in as a **viewer**? Viewers see every number but none of the buttons that change something (new project,
delete, manage goals, rotate key).

## Visits from my own browser don't count

If your browser sends Global Privacy Control or Do Not Track (Brave and some privacy extensions do by default),
the tracker doesn't count you. That's on purpose; see [Privacy](privacy).
