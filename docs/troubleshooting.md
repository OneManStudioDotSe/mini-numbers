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

Wrong password? There's no default: it's the one you chose in the setup wizard (`ADMIN_PASSWORD` in `.env`).

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

Demo data goes into the project selected in the sidebar. Select one first; refresh the page if the dashboard
doesn't update.
