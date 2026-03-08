---
title: Upgrading
layout: default
nav_order: 12
---

# Upgrading

How to upgrade Mini Numbers to a newer version.

---

## Before you upgrade

1. **Back up your database.** For SQLite, copy the `.db` file to a safe location. For PostgreSQL, run `pg_dump`.
2. **Note your current version** (visible in the `/health` endpoint response).
3. **Read the [CHANGELOG](https://github.com/OneManStudioDotSe/mini-numbers/blob/main/CHANGELOG.md)** for the version you are upgrading to. Look for any breaking changes or new required environment variables.

---

## Schema migrations

Mini Numbers uses **automatic schema migration**. On every startup, it calls `createMissingTablesAndColumns()`, which adds any new database tables and columns introduced in the new version without touching existing data.

You do not need to run migration scripts manually. Simply replace the binary (or Docker image) and restart.

---

## Upgrading with Docker

```bash
# Pull the new image
docker pull ghcr.io/onemanstudiodotse/mini-numbers:latest

# Stop the running container
docker compose down

# Start with the new image
docker compose up -d
```

Watch the logs during startup to confirm the service reaches the `READY` state:

```bash
docker compose logs -f
```

---

## Upgrading a JAR deployment

```bash
# Stop the running process
pkill -f mini-numbers.jar  # or use your process manager

# Replace the JAR
cp mini-numbers-new.jar /opt/mini-numbers/mini-numbers.jar

# Restart
java -jar /opt/mini-numbers/mini-numbers.jar
```

---

## Upgrading the tracker script

The tracker script (`tracker.js`) is served directly from your Mini Numbers server at `/tracker/tracker.js`. When you upgrade the server, visitors automatically receive the new script on their next page load — no CDN purge or manual deployment needed.

If you are self-hosting the tracker script on a separate CDN or serving it as a static file, replace the file manually after upgrading the server.

---

## Configuration changes

New versions may add optional environment variables. These always have sensible defaults, so your existing `.env` file will continue to work. Add new variables to `.env` only if you want to change the default behavior.

Check the [Configuration](configuration) page for the complete list of current settings.

---

## Rollback

If you need to roll back to a previous version:

1. Stop the server
2. Restore the database backup from before the upgrade
3. Deploy the previous binary or Docker image
4. Restart

**Important:** Rolling back the schema is not automatic. If the new version added database columns, the old version will log warnings about unknown columns but will continue to work in most cases. If the rollback causes issues, restore from your pre-upgrade database backup.
