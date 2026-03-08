---
title: Troubleshooting
layout: default
nav_order: 11
---

# Troubleshooting

Common issues and how to resolve them.

---

## GeoIP data not showing

**Symptom:** Country and city columns are empty, or the dashboard shows no geographic data.

**Causes and fixes:**

1. **GeoIP database not found.** The MaxMind GeoLite2 database must be present at the path configured by `GEOIP_DATABASE_PATH` (default: `src/main/resources/geo/geolite2-city.mmdb`). The file ships with the repository; if you built a custom Docker image, verify it was included.

2. **Privacy mode is STRICT or PARANOID.** `STRICT` mode stores country only (no city). `PARANOID` mode stores no location data at all. Check your `PRIVACY_MODE` setting.

3. **GeoIP for local/private IPs.** Requests from `127.0.0.1`, `::1`, or RFC 1918 addresses (`10.x`, `192.168.x`, `172.16-31.x`) cannot be geolocated. This is expected during local development.

---

## CORS errors in the browser console

**Symptom:** The tracker fails to send events and the browser console shows a CORS error like `Access-Control-Allow-Origin`.

**Fix:** Add your website's origin to the `ALLOWED_ORIGINS` environment variable:

```bash
ALLOWED_ORIGINS=https://example.com,https://www.example.com
```

Restart the server after changing this value. In development, set `KTOR_DEVELOPMENT=true` to relax CORS restrictions entirely.

---

## Tracker script not firing

**Symptom:** No events appear in the dashboard after adding the tracker script to your site.

**Checklist:**

1. **Verify the `data-project-key` attribute** matches the API key shown in your project settings (Settings modal → Tracking ID).
2. **Check the browser network tab** for requests to `/collect`. A successful call returns HTTP 202.
3. **Check for JavaScript errors** in the browser console that may be preventing the script from running.
4. **Ad blockers.** Many ad blockers block analytics scripts by hostname or URL pattern. Test in a browser with extensions disabled.
5. **Script tag placement.** The script must be on a page that is actually loaded. It will not fire if added only to a template that is never rendered.
6. **`async` attribute.** Make sure the `async` attribute is present — the script must finish loading after the DOM is ready to read `data-*` attributes correctly.

---

## 401 login loop

**Symptom:** The login page keeps reloading after entering correct credentials.

**Causes and fixes:**

1. **Wrong password.** Double-check `ADMIN_PASSWORD` in your `.env` file. There are no default passwords — you set this during the setup wizard.
2. **Session cookie blocked.** Mini Numbers uses a session cookie for authentication. If your browser or reverse proxy blocks cookies, the login will fail silently. Ensure cookies are enabled and that your reverse proxy passes the `Set-Cookie` header through.
3. **Application not initialized.** If the setup wizard was not completed, the application will redirect all requests back to `/setup`. Check the server logs for `ServiceManager` state errors.

---

## SQLite database locked

**Symptom:** Server logs show `SQLITE_BUSY` or `database is locked` errors under load.

**Causes and fixes:**

1. **Multiple processes.** Only one process should connect to the SQLite file at a time. If you are running multiple server instances, use PostgreSQL instead.
2. **WAL mode.** Mini Numbers enables SQLite WAL (Write-Ahead Logging) automatically. Verify the `.db-wal` and `.db-shm` companion files exist alongside your database file — if they are missing, WAL may not be active.
3. **Volume permissions (Docker).** If the database file is on a mounted Docker volume, ensure the container user has write permission on the directory. Restart the container after fixing permissions.

---

## Docker volume permissions

**Symptom:** Server starts but fails to create or write the SQLite database file inside a Docker container.

**Fix:**

```bash
# On the host, ensure the volume directory is writable
chown -R 1000:1000 /path/to/data-volume
```

Or in `docker-compose.yml`, add `user: "1000:1000"` to the service definition to match the container's UID.

---

## No data after generating demo events

**Symptom:** Clicked "Generate demo data" but the dashboard still shows empty state.

**Fix:** Demo data is generated for the **currently selected project**. Make sure a project is selected in the sidebar before opening the demo data modal. After generation, the dashboard reloads automatically. If the loading state gets stuck, refresh the page manually.

---

## Rate limit errors (HTTP 429)

**Symptom:** Some events are not being recorded and the server logs show rate limit rejections.

**Fix:** Increase the rate limit settings in your `.env`:

```bash
RATE_LIMIT_PER_IP=5000
RATE_LIMIT_PER_API_KEY=50000
```

For high-traffic sites, also consider placing a caching reverse proxy (nginx, Caddy) in front of Mini Numbers.
