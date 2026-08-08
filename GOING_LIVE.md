# Mini Numbers — Going Live Audit

**Purpose**: Independent pre-public-release audit. The project's own `_docs/` (SECURITY.md, ROADMAP.md, EVALUATION.md) self-rate almost everything 9-10/10 and "Production-Ready." This audit does not take those ratings at face value — every finding below was verified against actual source, running tests, or git history. Several places where the docs turned out to be wrong are called out explicitly.

Findings are grouped by severity, not by audit area. Each item lists what was verified, where, and a recommendation. Nothing here has been fixed yet — this is a punch list to work through.

---

## Critical — fix before anything goes public

### 1. A live database with real credentials and API keys is committed to git history
`stats.db` (5.7MB) is tracked in git across 5 commits (`git log -- stats.db`). It is **not** an empty schema/fixture — it contains:
- 1 real user (`admin`) with a bcrypt password hash
- 3 real projects with live 32-character API keys, including one that is clearly a real production site ("Poofly — Where did the money go?" @ www.poofly.se), not just the demo project
- 7,004 real events, plus goals/segments/funnels tied to that data

`*.db` is in `.gitignore` now, but the file was already committed before that rule existed and was never `git rm --cached`. Anyone who clones this repo once it's public gets the admin password hash and all API keys, permanently, in history.

**Fix**:
- `git rm --cached stats.db`, commit.
- Purge it from history entirely (`git filter-repo` or BFG) before making the repo public — a plain new commit is not enough, the blob stays reachable in old commits/forks.
- Rotate the admin password and regenerate API keys for any real project that was in this file (treat the www.poofly.se key as burned).
- Rotate `SERVER_SALT` too, since it's what the visitor-hash and JWT signing derive from.

### 2. Test suite fails on a clean checkout (41/296 failing) — the "296 tests, zero failures" claim is false
Ran `./gradlew test` from a fresh state: **41 tests failed**. Root cause: `test-dbs/` is gitignored with no `.gitkeep`, and nothing creates the directory before tests write SQLite files into it. `ServiceManagerTest` fails to initialize (directory doesn't exist → `SQLException`), which cascades into `AdminEndpointTest`/`CollectEndpointTest`/`SetupWizardTest` getting wrong HTTP codes because the app never reaches "ready" state.

This is a single root cause with a trivial fix, but as shipped, anyone who clones the repo and runs `./gradlew test` — including CI on a fresh runner — hits 41 failures immediately. This should be checked against recent GitHub Actions run history to confirm whether CI is actually green or has been silently failing/misreported.

**Fix**: create `test-dbs/` before tests run (Gradle `doFirst` block, JUnit `@BeforeAll`, or commit a `.gitkeep`). Also clean up ~80 stray `test-*.db` files sitting in the repo root from local runs (gitignored, not tracked, but repo hygiene).

### 3. Stored XSS via public, unauthenticated input — reaches the admin's browser
Two places interpolate visitor-controlled data into `innerHTML` without escaping, unlike every other field in the same rendering path:
- `js/admin.js` (~line 2391, Raw Events modal): `` `custom: ${e.eventName}` `` — `eventName` comes from `MiniNumbers.track()`, callable by any anonymous visitor via the public `/collect` endpoint.
- `js/revenue.js:117` (`renderBreakdown`, `b.eventName`) and `js/revenue.js:142` (`renderAttribution`, `a.source` — UTM source, also visitor-controlled).

An attacker can call `MiniNumbers.track("<img src=x onerror=...>")` from any page running the tracker, or set `?utm_source=...`, and the payload executes in the **admin's session** the next time they open Raw Events or Revenue. This directly contradicts `_docs/SECURITY.md`'s claim that this class of bug was "FIXED." The equivalent custom-events chart (`charts.js:582,725`, `admin.js:2854`) correctly escapes the same data via `Utils.escapeHtml()` — the fix was applied inconsistently across duplicated rendering code paths.

**Fix**: wrap `e.eventName`, `b.eventName`, and `a.source` in `Utils.escapeHtml()`. Recommend a manual PoC to confirm before/after, since this was found by static review, not reproduced live in a browser.

---

## High

### 4. `ALLOWED_ORIGINS` / admin CORS restriction is dead code
`middleware/AdminCorsGuard.kt` implements per-origin allowlist checking for `/admin/*`, but it is **never called anywhere** (verified via full-repo grep — zero call sites). The actual CORS policy (`core/HTTP.kt:69-82`) uses `anyHost()` for every route, including admin. `_docs/SECURITY.md:127-142` explicitly claims unknown origins get rejected with 403 for admin endpoints — this is false. Partially mitigated by `allowCredentials=false` (browsers won't attach the session cookie cross-origin), but it's a real gap for JWT bearer-token requests, and a flatly incorrect documented security control.

**Fix**: wire `AdminCorsGuard.check()` into the `/admin` and `/api` route blocks in `Routing.kt`, or remove the guard and correct the docs to describe what actually happens.

### 5. No rate limiting on any auth or admin endpoint
`RateLimiter` is only invoked from `CollectionRouting.kt` (the `/collect` endpoint) — confirmed via grep, zero usage elsewhere. `_docs/SECURITY.md` claims "200 req/min per IP" on `/admin/*`; that's not implemented. The only brute-force defense is a per-*username* lockout (5 fails → 15min), which doesn't throttle volume/IP. Since `verifyCredentials` runs a BCrypt cost-12 comparison on every login attempt for the known default username `admin` (`Security.kt:79/96`), an unauthenticated attacker can flood `/api/login` or `/api/token` and force expensive BCrypt hashing per request with no throttle — a credible CPU-exhaustion DoS vector, separate from brute-forcing.

**Fix**: apply the existing `RateLimiter` (or a stricter dedicated bucket) to all `/api/*` and `/admin/*` endpoints, especially login/token/password-reset.

### 6. SMTP config for Email Reports is fully undocumented
Email Reports is a marketed, fully-implemented feature (`EmailService.kt`, `AdminFeatureRouting.kt`, OpenAPI spec), but there is no SMTP configuration section anywhere a self-hoster would look: absent from `.env.example`, absent from `docs/configuration.md`, absent from `CLAUDE.md`'s config table. A new user has no way to discover how to turn this feature on.

**Fix**: add an SMTP section (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_STARTTLS` or whatever the actual variable names are) to `.env.example` and `docs/configuration.md`.

### 7. Documented PostgreSQL env vars don't match the actual code — will silently misconfigure or fail to start
`docs/configuration.md` and `CLAUDE.md` document `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`. The actual loader (`config/ConfigLoader.kt`, `loadDatabaseConfig()`) reads `DB_PG_HOST`, `DB_PG_PORT`, `DB_PG_NAME`, `DB_PG_USERNAME`, `DB_PG_PASSWORD`. Following the public docs means every one of these is silently ignored: `DB_PG_PASSWORD` is required, so the app fails to start with a confusing error — but `DB_PG_HOST`/`NAME`/`USERNAME` just silently fall back to defaults (`localhost`/`mini_numbers`/`postgres`), which could mean **silently connecting to the wrong database**. `docker-compose.postgres.yml` and `_docs/DEPLOYMENT.md` already use the correct `DB_PG_*` names — the repo is internally inconsistent. Related: `CLAUDE.md` also states the Postgres default `DB_NAME` is `mininumbers` (no underscore); the code default is `mini_numbers`.

**Fix**: correct `docs/configuration.md` and `CLAUDE.md` to the real `DB_PG_*` variable names and the real default.

### 8. LGPL-licensed dependency shaded into an MIT-licensed fat JAR
`eu.bitwalker:UserAgentUtils:1.21` is LGPL-3.0. The project builds a single-artifact fat/shaded JAR (`buildFatJar`) and distributes the whole thing as MIT ("use it however you like"). Static-linking an LGPL dependency into one artifact without the relinking mechanism LGPL requires is a real compliance gap, not just theoretical.

**Fix**: either get explicit sign-off that the fat-jar model is compliant with proper LGPL notice/relinking provisions, or swap to a permissively-licensed UA-parsing library. Also worth noting: this library is unmaintained since ~2019 — a maintenance-risk flag independent of licensing.

---

## Medium

### 9. Missing MaxMind GeoLite2 attribution
No MaxMind attribution/NOTICE exists anywhere (LICENSE, README, `_docs/DEPLOYMENT.md`) for the bundled `geolite2-city.mmdb`. MaxMind's GeoLite2 EULA requires attribution text ("This product includes GeoLite2 data created by MaxMind, available from https://www.maxmind.com") for redistribution. Currently absent regardless of how the DB is distributed.

**Fix**: add the required attribution notice. Separately (see item below), reconsider whether the raw `.mmdb` should be redistributed via git/Docker image at all versus downloaded at build/first-run time — MaxMind's terms restrict raw redistribution more broadly than just "add a notice."

### 10. 61MB GeoLite2 database committed to the repo and baked into every artifact
`geolite2-city.mmdb` (61MB) is committed directly to `src/main/resources/geo/`, which means: every `git clone` is 61MB heavier than it needs to be, it's baked into every Docker image layer and every fat JAR, and (per item 9) its redistribution may not be MaxMind-EULA-compliant as a blanket "ship the raw file" approach.

**Fix**: consider downloading the mmdb at build/container-start time (with a documented `GEOIP_LICENSE_KEY`), or via Git LFS, or documenting it as an optional post-install step — rather than committing the raw binary.

### 11. Role/account changes don't revoke already-issued sessions or tokens
Session auth reads the role baked into the cookie at login time (`middleware/RoleGuard.kt`), not from the DB per-request. Demoting (`PUT /admin/users/{id}/role`) or deleting (`DELETE /admin/users/{id}`) a user doesn't invalidate their existing session or in-flight JWT/refresh token — a just-demoted or just-deleted user keeps their old access until natural expiry (up to 4h for sessions, 15min for JWT access tokens). `users.isActive` is only checked at login, never mid-session.

**Fix**: either check role/active-status per-request (accepting the DB-lookup cost), or invalidate sessions/tokens on role change and user deletion.

### 12. Accessibility claims overstated — 4 of 11 modals lack ARIA dialog attributes
`_docs/SECURITY.md`/`CLAUDE.md` claim "Full ARIA dialog attributes... on all primary modals." Verified: `create-project-modal`, `onboarding-modal` (shown to every new user), `logout-modal`, and `delete-project-modal` in `admin.html` lack `role="dialog"`/`aria-modal`. Only 8 of ~70 `.modal` elements have them set.

**Fix**: add `role="dialog"`, `aria-modal="true"`, `aria-labelledby` to the four missing modals, then correct the doc claim (or keep it honest going forward).

### 13. Docker build uses a different Gradle version than CI/local dev
`Dockerfile` builds via `FROM gradle:8.14.4-jdk21`, while `gradle/wrapper/gradle-wrapper.properties` pins `9.3.0` (what CI and local `./gradlew` actually use). Works today, but it's a reproducibility/drift risk — a build that passes locally/in CI isn't guaranteed to behave identically in the Docker build stage.

**Fix**: have the Docker build stage `COPY gradlew` and use the wrapper (`./gradlew buildFatJar`) so exactly one Gradle version is used everywhere.

### 14. No dependency vulnerability scanning in CI
Confirmed: no Dependabot config, no OWASP Dependency-Check, no Snyk/Trivy step anywhere in `.github/workflows/`. `_docs/SECURITY.md` itself lists this as an open recommendation — still true.

**Fix**: add Dependabot (trivial, GitHub-native) at minimum; consider `./gradlew dependencyCheckAnalyze` in CI as the doc suggests.

### 15. No functional contact channel for Code of Conduct enforcement
`CODE_OF_CONDUCT.md`'s "Enforcement" section says reports "may be reported to the project maintainers" with no email, link, or Discussions reference. `CONTRIBUTING.md` has no contact info either. For a public repo this makes the Contributor Covenant clause non-functional.

**Fix**: add a real contact path (maintainer email or a GitHub Discussions/Issues pointer).

### 16. Non-constant-time secret comparison in password reset
`AuthRouting.kt:212`: `if (body.serverSalt != resetConfig.security.serverSalt)` is a plain Kotlin equality check on a high-entropy secret — a byte-by-byte timing side-channel. Low practical exploitability over a network, but a one-line fix.

**Fix**: use `MessageDigest.isEqual()` or another constant-time comparison.

### 17. Inconsistent HTML-escaping on admin-authored fields (self-XSS, lower severity than #3)
`webhooks.js:179` (`wh.url` raw in both a `title` attribute and cell text), `email-reports.js:185` (`r.recipientEmail` raw), `segments.js:124,222` (`f.field`, `f.operator`, `f.value` raw in filter badges). These are admin-entered, not public input, so it's self-XSS today — but inconsistent with escaping used everywhere else, and a latent risk if multi-user/role scope ever expands beyond a single trusted admin.

**Fix**: apply `Utils.escapeHtml()` consistently across all `innerHTML` interpolation sites; consider centralizing table/list rendering (see item 19) so this class of bug can't recur.

---

## Low

### 18. `.env.example` doesn't warn that `ADMIN_PASSWORD` must be pre-hashed if set manually
If `.env`'s `ADMIN_PASSWORD` isn't already a `$2a$`/`$2b$` bcrypt hash, `Security.kt` silently and permanently fails auth (logged server-side only, not surfaced to the user). `.env.example` just says "Set a strong password" with no mention of the hash requirement — anyone bootstrapping via `.env` directly (skipping the setup wizard) gets locked out with a confusing failure.

**Fix**: document the hash requirement in `.env.example`, or accept plaintext and hash it on first load if not already bcrypt-formatted.

### 19. Heavy duplication across dashboard JS files
`admin.js`, `charts.js`, `revenue.js`, `webhooks.js`, `email-reports.js`, `goals.js`, `segments.js` each hand-roll near-identical `<tr>`/empty-state/icon-bar-chart rendering (~13,000 lines total across `js/*.js`). This isn't just a style nit — it's the direct cause of finding #3 and #17 (escaping applied in some copies of the logic, forgotten in others).

**Fix**: not urgent, but a shared `renderTable(rows, columns)` / `renderIconBarChart(items)` helper would remove most duplication and centralize escaping so it can't be missed again.

### 20. `CLAUDE.md` project-structure tree is out of date
It lists a single flat `Routing.kt`; the real routing code is split across `Routing.kt`, `WidgetRouting.kt`, and a `routing/` subpackage (`CollectionRouting.kt`, `AdminUserRouting.kt`, `AdminProjectRouting.kt`, `AuthRouting.kt`, `AdminFeatureRouting.kt`, `AdminAnalyticsRouting.kt`, `PublicRouting.kt`, `RoutingUtils.kt`) — none mentioned. Not user-facing, but misleads anyone (human or AI assistant) using `CLAUDE.md` as the map of the codebase.

**Fix**: regenerate/update the structure tree.

### 21. "Inactivity timeout" is mislabeled in docs
`_docs/SECURITY.md` describes the 4-hour session cap as an inactivity timeout. In code (`Security.kt`), it's checked against `session.createdAt`, which is set once at login and never refreshed — it's actually a flat 4-hour absolute session lifetime, not a sliding window. Not a vulnerability (it's stricter than documented), just a docs/code mismatch.

**Fix**: correct the doc wording, or implement a true sliding window if that was the intent.

### 22. jBCrypt is an unmaintained fork
`org.mindrot:jbcrypt:0.4` hasn't had a meaningful release since ~2013. Works fine, no known CVE, but worth flagging for a project pitching itself as production-ready and depending on it for password hashing specifically.

**Fix**: no urgency; consider migrating to an actively maintained BCrypt implementation (e.g. `com.password4j`) opportunistically.

### 23. README badges will be broken/misleading until the repo actually goes public
Test-count and version badges reference a GitHub release/CI history that doesn't exist yet on a private/pre-launch repo. Self-resolving once there's a real release, but worth a final check right before announcing.

### 24. Marketing/landing page loads a third-party CDN script
`landing/index.html` loads Remixicon from `cdn.jsdelivr.net` — a third-party network request on the marketing page of a project whose entire pitch is privacy-first. Not a security bug (the admin panel itself is CSP-restricted to `'self'`), just an optics inconsistency worth a self-hosted font/icon swap.

---

## Not bugs — verified as accurate/solid (no action needed)

- **SQL injection**: no raw string SQL concatenation anywhere; all queries go through Exposed's typed DSL.
- **Admin endpoint auth coverage**: all four admin route groups are correctly nested under `authenticate("admin-session", "api-jwt")`, and `requireRole(ADMIN)` guards are present on all mutation endpoints checked.
- **Dockerfile**: non-root user, minimal Alpine JRE base, working `HEALTHCHECK`, no secrets baked into image layers.
- **CI workflow hygiene**: least-privilege `permissions:` blocks, no `pull_request_target` misuse, no hardcoded secrets, Docker publish gated to `push`/`tag` on main (no fork PR secret-leak risk).
- **docker-compose files**: no hardcoded secrets; `DB_PG_PASSWORD` is required via `${DB_PG_PASSWORD:?...}`.
- **Setup wizard**: correctly refuses to re-run and reset admin credentials once the app is live (`ServiceManager.isReady()` guard).
- **CSRF**: acceptable given `SameSite=Strict` + JSON-only mutation endpoints (cross-site forms can't trigger without a CORS preflight that would be blocked once item #4 is fixed).
- **CSP `unsafe-inline`**: real, but already honestly disclosed as a known gap in `_docs/SECURITY.md`'s "Future considerations" — not new.
- **Detekt baseline**: small (36 issues) and reasonable, not inflated to hide real problems.
- **Tracker docs**: `tracker-reference.md` accurately matches actual `tracker.js` attribute names/defaults.
- **LICENSE**: complete, valid, correctly dated MIT text.
- **Landing page content**: no lorem ipsum, no placeholder emails, no broken internal links.

---

## Launch-readiness gaps (not bugs, just incomplete per the project's own ROADMAP Phase 7)

These are already tracked as not-done in `_docs/ROADMAP.md` — listed here for completeness since they block "public release" specifically:

- No screenshots or demo GIFs in the README.
- No GitHub issue templates (`.github/` only has `workflows/`, no `ISSUE_TEMPLATE/`).
- No GitHub Discussions/community setup.
- No public GitHub release yet.

---

## Suggested triage order

1. **Critical #1** (secrets in git history) — rotate credentials immediately regardless of when the repo goes public; purge history before it does.
2. **Critical #3** (stored XSS) — quick, contained fix.
3. **Critical #2** (test suite) — trivial fix, but blocks trusting CI/any other verification going forward, do this early.
4. **High #4, #5** (dead CORS guard, missing rate limiting) — both are "wire up code that already exists," not new development.
5. **High #6, #7** (doc/config drift) — pure documentation fixes, low effort, high impact for new self-hosters.
6. **High #8 / Medium #9-10** (licensing + GeoIP bundling) — needs a decision on distribution model, do before the repo is public.
7. Everything else in Medium/Low as capacity allows.
