# Go-live checklist

The steps for putting a release on a production server and proving it works: Docker Compose with Caddy on a VPS,
as in `docs/deployment.md` Option 1. Work top to bottom and tick as you go; each section says how to tell it worked.
What is in scope and verified is in [LAUNCH-AUDIT.md](LAUNCH-AUDIT.md).

Placeholders: `stats.example.com` is your analytics domain, `$KEY` the key of the "Smoke test" project.

---

## 1. Before tagging (on your machine)

- [ ] **Leaked secrets rotated** on any instance that still uses them (the admin password, the `SERVER_SALT` and the
      three project keys that were in the old `stats.db`; update the snippet on the sites that use them). A fresh
      server with a new `.env` is not affected. The admin password: Settings → Account → Change password, or on the
      server `docker compose exec mini-numbers java -jar /app/app.jar reset-password admin` (editing `ADMIN_PASSWORD`
      in `.env` does nothing once the admin exists). Rotate a project key with Settings → Rotate key.
- [ ] **GeoLite2 refreshed** if `src/main/resources/geo/geolite2-city.mmdb` is more than a few months old (it is from
      2026-01-30): download GeoLite2-City from your MaxMind account, replace the file, and update the date in
      `docs/configuration.md` and `_docs/ARCHITECTURE.md`.
- [ ] **Tests green:** `./gradlew test`, `./gradlew detekt` (JDK 21), and
      `./gradlew buildFatJar && cd e2e && npx playwright test` (then `git checkout -- docs/screenshots`).
- [ ] **CI green on `main`:** `gh run list --branch main --limit 3` shows Build & Test (SQLite + PostgreSQL, browser
      suite, Docker persistence) and Docker Publish as `success`.
- [ ] **Version bumped** in `build.gradle.kts` (`version = "…"`) and `core/AppInfo.kt` (`VERSION`), the CHANGELOG
      `[Unreleased]` heading renamed to `[x.y.z] - date`, and the landing page's version badge updated.

## 2. Release

- [ ] `git tag vX.Y.Z && git push origin vX.Y.Z`
- [ ] The tag's workflows succeed: **Release** attaches `mini-numbers.jar`, **Docker Publish** pushes `X.Y.Z`, `X.Y` and
      `latest` to `ghcr.io/onemanstudiodotse/mini-numbers` (`latest` only moves on a tag).

## 3. Server

- [ ] **DNS:** an `A` record for `stats.example.com` points at the server (`dig +short stats.example.com`).
- [ ] **Firewall:** ports 80 and 443 (TCP, and UDP 443 for HTTP/3) are open, in the provider's panel too.
- [ ] **Files**, in a folder of their own:
  ```bash
  mkdir -p ~/mini-numbers && cd ~/mini-numbers
  curl -fsSLO https://raw.githubusercontent.com/OneManStudioDotSe/mini-numbers/main/docker-compose.yml
  curl -fsSLO https://raw.githubusercontent.com/OneManStudioDotSe/mini-numbers/main/scripts/smoke.sh && chmod +x smoke.sh
  ```
- [ ] **`.env`**, pre-configured so the setup wizard is never open on the internet (whoever reaches `/setup` first on
      an unconfigured server chooses the admin password):
  ```bash
  cat > .env <<EOF
  DOMAIN=stats.example.com
  ADMIN_USERNAME=admin
  ADMIN_PASSWORD=$(openssl rand -base64 24)
  SERVER_SALT=$(openssl rand -hex 64)
  TZ=Europe/Stockholm
  PRIVACY_MODE=STANDARD
  EOF
  chmod 600 .env && grep ADMIN_PASSWORD .env   # store this password in your password manager
  ```
  Leave `LABS` out: every lab stays off. Leave `KTOR_DEVELOPMENT` out (it must be `false` in production).
  To use the wizard instead, keep only `DOMAIN` and `TZ` and complete step 4 the moment the server is up.
- [ ] **Start:** `docker compose pull && docker compose up -d`
- [ ] **Running:** `docker compose ps` shows both containers, `mini-numbers` as `healthy` after about 30 s;
      `docker compose logs caddy | grep -i certificate` shows the certificate was obtained.

## 4. First sign-in

- [ ] `https://stats.example.com` opens the login page with a valid certificate (with the wizard route: the wizard,
      then the login page). Sign in.
- [ ] Create a project **Smoke test** (domain `smoke.invalid`), copy its key from Settings → tracking ID. It only
      ever receives the smoke test's page views, so your real numbers stay clean.
- [ ] Create the project for your real site.

## 5. Automated check (about 30 seconds)

From the server or your own machine:

```bash
BASE_URL=https://stats.example.com PROJECT_KEY=$KEY ./smoke.sh
```

- [ ] It ends with `All checks passed.` and exit code 0. It checks: health and version, HTTP→HTTPS redirect, HSTS and
      CSP headers, the tracker script, a page view accepted by `/collect`, wrong credentials refused, API sign-in, the
      page view stored and readable, the dashboard report, and widgets off (404) while their lab is off.
- [ ] The version it prints is the one you tagged.

## 6. By hand (about 10 minutes)

- [ ] **Snippet:** paste the snippet from the real project's creation dialog (or Settings) into your site and deploy it.
- [ ] **First visit:** open your site in a private window; within a few seconds the visitor shows under Real-Time and
      the page under Content & Pages.
- [ ] **Privacy signal:** with Global Privacy Control on (Brave, Firefox with the setting, or a GPC extension), a visit
      is *not* counted.
- [ ] **Every section**, light and dark theme: Overview, Content & Pages, Traffic Sources, Audience & Technology (chart
      and globe), Time Patterns, Events, Real-Time, Conversions. No section shows funnels, segments, revenue, webhooks
      or email reports.
- [ ] **A goal:** Conversions → Manage goals → a page goal for a path you visit; it counts after the next visit.
- [ ] **Raw events and CSV:** Raw events lists the visits; the export downloads and opens.
- [ ] **Phone width:** the dashboard on a phone, sidebar behind the menu button.
- [ ] **Users:** Settings → Account → Manage users → add a viewer; sign in as them in a private window with the
      password shown once; they see the numbers and no buttons that change things. Then remove them.
- [ ] **Change password:** Settings → Account → Change password signs you out; the new password works.
- [ ] **Sign-out everywhere:** signed in on two browsers, sign out in one; the other is signed out on its next click.
- [ ] **Lockout:** five wrong passwords lock the account for 15 minutes (do it with a second, throwaway user if you
      don't want to wait).

## 7. Restart and persistence

- [ ] `docker compose restart` — the login still works, the numbers are still there, and `./smoke.sh` passes again.
- [ ] `docker compose down && docker compose up -d` (containers recreated, volumes kept) — same result.

- [ ] **Forgotten-password drill:** `docker compose exec mini-numbers java -jar /app/app.jar reset-password admin`,
      type a new password twice; it prints `Password changed for admin`, and the new password signs in.

## 8. Backup and restore drill

- [ ] Back up (from the `~/mini-numbers` folder):
  ```bash
  cp .env env.backup
  docker compose stop mini-numbers
  docker run --rm -v mini-numbers_analytics-data:/data -v "$PWD":/backup alpine tar czf /backup/mini-numbers-backup.tgz -C /data .
  docker compose start mini-numbers
  ```
- [ ] Prove the backup is usable:
  ```bash
  docker run --rm -v "$PWD":/backup alpine sh -c 'apk add -q sqlite && mkdir /r && tar xzf /backup/mini-numbers-backup.tgz -C /r \
    && sqlite3 /r/stats.db "PRAGMA integrity_check; SELECT count(*) FROM events;"'
  ```
  prints `ok` and a count matching what the dashboard shows.
- [ ] Copy `mini-numbers-backup.tgz` and `env.backup` off the server, and schedule the backup (cron, nightly).

## 9. Rollback (know it before you need it)

1. Pin the previous image in `docker-compose.yml`: `image: ghcr.io/onemanstudiodotse/mini-numbers:<previous version>`.
2. If the new version changed the database (its CHANGELOG says so under "Upgrading"), restore the backup into the
   volume: `docker compose stop mini-numbers`, then
   `docker run --rm -v mini-numbers_analytics-data:/data -v "$PWD":/backup alpine sh -c 'rm -rf /data/* && tar xzf /backup/mini-numbers-backup.tgz -C /data'`.
3. `docker compose up -d` and run `./smoke.sh`.

## 10. Trying a lab in production

Labs are features that work but aren't verified for everyone (see `docs/configuration.md#labs`). To try one on your
own server only:

- [ ] Add `LABS=webhooks` (comma-separate several) to `.env`, then `docker compose up -d` to recreate the container.
- [ ] `./smoke.sh` prints it under "labs on", and the feature appears in the dashboard.
- [ ] Remove the line and recreate to switch it off again; stored data is kept either way.
- [ ] Never switch on `widgets` for a site whose numbers should stay private: anyone with the key from the site's
      HTML can read them.

## 11. Afterwards

- [ ] An uptime monitor on `https://stats.example.com/health` (expects 200 and `"status":"healthy"`).
- [ ] Run `./smoke.sh` after every upgrade (`docker compose pull && docker compose up -d`).
