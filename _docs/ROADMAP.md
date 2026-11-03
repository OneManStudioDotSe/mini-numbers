# Mini Numbers — Roadmap

The one place for what's next: owner actions, milestones, known gaps and feature ideas. What already shipped
is in [CHANGELOG.md](CHANGELOG.md); earlier audits and plans are in git history. Last updated 2026-11-03.

**Positioning:** the easiest and most private analytics you can self-host — one program, SQLite built in, every
feature included. Not a race for feature parity with Plausible, Umami or PostHog.

---

## Owner actions (blocking a public launch)

- [ ] **Rotate the credentials** that were committed in `stats.db` (admin password, `SERVER_SALT`, three project
      keys, one of them live on www.poofly.se) on the live instance, then update the Poofly snippet. The repository
      is public, so they are burned; the history purge below does not make them invalid.
- [x] **Purge history** (2026-11-03): `stats.db`, `.env`, `server.log` and `test-*.db` stripped from every commit
      with `git filter-repo`, `main` force-pushed, `v1.5.0` tagged on the clean history. The GeoLite2 file was kept
      on purpose (it is bundled into the JAR; see the next item). The Dependabot branches built on the old history are
      deleted too; the only remaining copies are GitHub's cached pull-request refs (ask support to clear them) and
      clones made before the purge.
- [ ] **GeoLite2**: download at start with a MaxMind licence key (recommended) or keep shipping the 63 MB file and
      accept the redistribution question. Add the attribution to the Docker image either way.
- [x] **GitHub Pages** deploys from the `docs` workflow (also by hand from the Actions tab) since 2026-11-03.
- [ ] **Enable GitHub Discussions** (the issue templates link to it).
- [x] **Tag `v1.5.0`** (2026-11-03): the release JAR and the `1.5.0`, `1.5` and `latest` images are published.

## Milestones

| | Milestone | Status |
|---|---|---|
| M1 | Installs that work: persistent Docker setup, `PORT`/`TRUST_PROXY`, Caddy HTTPS, release JAR, install guide | Released in 1.5.0 |
| M2 | Release hardening: the November audit (escaping, delete cascade, sessions, reports, webhooks, time zones) | Released in 1.5.0 |
| M3 | Landing page: ~400 words, plain language, "Try the demo" + "Install" (needs design input first) | Planned |
| M4 | Public demo: nightly reset at 23:59 UTC; M4a viewer account, M4b sandbox role (goals, funnels, segments only) | Planned |
| M5 | Feature ideas below, one per minor release | Planned |

## Known gaps

The full audit of 2026-11-02, with reproductions, is in [AUDIT-2026-11.md](AUDIT-2026-11.md). Every critical, high
and medium item in it is fixed in 1.5.0. Still open from it:

- Webhook retries are in memory: a restart drops deliveries still waiting for their retry. Durable retries would
  re-read pending rows from `webhook_deliveries` on start (M4 leftover).
- The server computes a **regions** breakdown that the dashboard never shows (the dead chart code was removed);
  add a card next to Countries when the geography section is next designed.
- **`TRACKER_HEARTBEAT_INTERVAL` / `TRACKER_SPA_ENABLED` do nothing**: they're served at `/tracker/config` but the
  tracker never reads it. Either wire them up or remove them (the docs no longer mention them).
- Table and bar-chart rendering is duplicated across `static/js/*.js` (how the escaping gap happened).
- CSP still allows `'unsafe-inline'`; the remaining inline handlers (`admin.html` dialogs, UUID-only row buttons in
  `webhooks.js`/`email-reports.js`, `login.html`) must go before it can be dropped. No OWASP/Trivy scan in CI (Dependabot only).
- Sessions last a flat 4 hours from login; no sliding window.
- Hash rotation and the retention purge read the wall clock directly, so they have no tests.
- No user-management screen; users are managed through `/api/users`.
- The landing page (`landing/`) is out of date and loads Remixicon from a CDN — replaced in M3.

---

## Feature ideas

Ranked by value for the effort, based on the [competitor research](../docs/comparison.html) of 2026-11-02.
S/M/L is a rough size. "Who has it" shows it's expected, not that we should copy it.

| # | Idea | Size | Who has it | Why it fits |
|---|------|------|------------|-------------|
| 1 | **Annotations** — mark releases or campaigns on the views chart | S | Umami, Plausible, Rybbit, Matomo | Cheapest visible gap; one small table |
| 2 | **Read-only MCP server** — ask Claude/ChatGPT about your stats, using the existing API | S–M | Umami, Rybbit, OpenPanel, PostHog | Now expected; our REST API and OpenAPI file do most of the work |
| 3 | **Bot and AI-crawler filter v2** — data-centre IP ranges, flood detection, AI crawlers counted apart from people, a visible "filtered" count | M | Rybbit, Matomo, Fathom, Plausible | Honest numbers are the product; we only check the user agent today |
| 4 | **Share links** — a read-only dashboard link with an expiry date, for clients or the public | S | Fathom, Plausible | Builds on the widget/key infrastructure |
| 5 | **Slack/Discord-ready webhooks** — send messages in their format | S | — | Users expect it; the old docs claimed it |
| 6 | **Privacy receipt** — a downloadable plain-language statement of what this server stores, for a site's privacy policy or DPIA | S | Nobody | Unique; extends the privacy chip and our core promise |
| 7 | **Importers** — Plausible CSV, Umami, GoatCounter, GA4 | M | Plausible, Matomo, Pirsch, Rybbit | Catches people leaving dormant tools (Vince, Shynet, Ackee) |
| 8 | **Footprint proof** — measured idle memory, a low-memory setting, a Raspberry Pi guide | S | Medama (256 MB), GoatCounter | Answers "the JVM is heavy" before it's asked |
| 9 | **Journeys** — common paths through the site, grouped to sections in Paranoid mode | M | Umami, Rybbit, OpenPanel, Plausible cloud | Standard in 2026, and computable from existing events |
| 10 | **2FA and scoped API keys** | M | Umami, Swetrix, Liwan, Rybbit | Expected for anything exposed to the internet |
| 11 | **Free single sign-on (OIDC)** | M | Liwan (free); others paid | Matches the only lightweight rival that has it |
| 12 | **Web vitals, opt-in** — page speed (LCP, INP, CLS), off in Paranoid mode | M | Umami, PostHog, Swetrix, Cloudflare | Useful, but grows the tracker; keep it opt-in |
| 13 | **User-management screen** | S | Most | API exists; only the UI is missing |
| 14 | **Retention / cohorts** | L | Umami, Rybbit, OpenPanel, PostHog | Valuable but large, and hard to do well with rotating hashes |

**Out of scope by design:** session replay, click heatmaps and error tracking. They need much more data about
visitors than our privacy promise allows, and the market is splitting between simple privacy tools (us) and
product-analytics suites.
