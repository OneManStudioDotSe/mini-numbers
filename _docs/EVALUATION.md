# Mini Numbers - Project evaluation

**Date**: October 7, 2026 (previous evaluation: March 8, 2026)
**Status**: Pre-release. Public repository, no tagged release yet.

---

## Executive summary

Mini Numbers is a feature-rich, single-JAR, privacy-first analytics server with a SQLite or
PostgreSQL backend. Between March and October 2026 the project did not change, but the
market did: Umami shipped session replay, heatmaps, web vitals, annotations and an MCP
server; Plausible shipped funnels, journeys, annotations and an AI-assistant traffic channel;
and a new generation (Rybbit, Databuddy, Swetrix, Seline) now occupies the "privacy plus
product analytics" slot. The March matrix that rated Mini Numbers 9.4/10 and first overall is
no longer accurate and is replaced below.

The October revival work closed the audit from `GOING_LIVE.md` (CORS guard, rate limits, live
role checks, LGPL dependency, docs drift), brought every dependency current, made the test
suite honest (it had been silently skipping every authenticated test), added a PostgreSQL CI
job and a Playwright browser suite, and in doing so found and fixed three defects that had
made the tracker non-functional on real sites. Those facts, not the March self-ratings, are
the baseline for the go/no-go below.

---

## Where Mini Numbers stands (honest, October 2026)

| Aspect | Assessment |
|--------|------------|
| Core analytics | Complete for the "simple privacy" segment: page views, visitors, sessions, bounce, referrers, UTM, geo, devices, entry/exit, scroll depth, outbound/download, custom events with properties, goals, basic funnels, segments, revenue attribution. |
| Privacy design | Still the strongest differentiator: cookieless salted hashing with **configurable** rotation (1 h to 1 year) and three modes (STANDARD / STRICT / PARANOID), now verified by tests for what each mode stores. |
| Deployment | Single fat JAR or Docker image, SQLite by default. Only GoatCounter and Vince offer anything comparable; every 2025-26 newcomer needs ClickHouse. |
| Security posture | Audit closed except GeoLite2 redistribution and the history purge. Rate limits and origin allowlist are real now, not documented aspirations. |
| Testing | 309 JVM tests (unit + integration, SQLite and PostgreSQL in CI) and 12 browser tests. Until October the integration tests never exercised an authenticated request. |
| Tracker | 1.9 KB gzipped (4.9 KB minified); was advertised as 1.3 KB. Now proven end to end in a real browser, including SPA, custom events, offline queue and outbound/download detection. |
| Accessibility | Structural WCAG issues fixed; 16 colour-contrast failures in the light theme remain (tracked). |
| Documentation | Extensive; was partly wrong (PostgreSQL variables, SMTP, test counts, tracker size), corrected in October. |
| Community | None yet. 0 stars, no release, no launch content. |

---

## Feature comparison, October 2026

Y = included, N = absent, P = paid tier or plugin only, ~ = partial or unverified.

| Feature | Mini Numbers | Umami 3.4 | Plausible | Matomo 5.14 | PostHog | Fathom | Rybbit 2.9 | GoatCounter |
|---|---|---|---|---|---|---|---|---|
| Open-source self-host | Y (MIT) | Y (MIT) | Y (AGPL CE) | Y (GPL) | Y (MIT+ee) | N | Y (AGPL) | Y |
| Runs on SQLite / single artifact | **Y** | N (Postgres) | N (ClickHouse+PG) | N (MySQL) | N | – | N (ClickHouse+PG+Redis) | Y |
| Cookieless by default | Y | Y | Y | ~ | ~ | Y | Y | Y |
| Configurable hash rotation + privacy modes | **Y (unique)** | N | N | N | N | N | N | N |
| Goals / custom events / properties | Y | Y | Y / P | Y | Y | Y / ~ | Y | Y / N |
| Funnels | Y (basic) | Y | P | P | Y | N | Y | N |
| User journeys / paths | N | Y | P | P | Y | N | Y | N |
| Retention / cohorts | N | Y | N | P | Y | N | Y | N |
| Saved segments | Y | Y | Y | Y | Y | Y | Y | N |
| Annotations | N | Y | Y | Y | Y | N | Y | N |
| AI-assistant referrer channel | N (planned) | ~ | Y | Y | Y | Y | ~ | N |
| Bot filtering beyond UA | ~ (UA only) | Y | Y | Y | Y | Y | Y | Y |
| Session replay / heatmaps | N | Y | N | P | Y | N | Y | N |
| Web vitals | N | Y | N | ~ | Y | N | Y | N |
| Revenue tracking | Y | Y | P | Y | Y | Y | ~ | N |
| Webhooks / email reports | Y / Y | ~ / cloud | N / Y | ~ / Y | Y / ~ | ~ / Y | ~ / ~ | N / Y |
| Public API + OpenAPI | Y / Y | Y | P | Y | Y | Y | Y | Y / ~ |
| MCP / agent access | N | Y | N | N | Y | ~ | Y | N |
| Teams with roles | Y (2 roles) | Y | Y (5) | Y | Y | Y | Y | Y |
| GA4 import | N | N | Y | Y | ~ | N | N | N |
| Tracker size (gzipped) | ~1.9 KB | ~2.4 KB | ~1.9 KB | ~21 KB | ~44 KB | ~3 KB | ~9 KB | ~3.5 KB |
| Contribution calendar + activity heatmap | **Y (unique)** | N | N | N | N | N | N | N |

Sources and dates are in the research notes behind this table (Umami releases, Plausible and
Fathom changelogs, Matomo and PostHog changelogs, Rybbit releases; all read on 2026-10-06).
No acquisitions or licence changes were found for any of these projects in 2025-26.

---

## Market trends that matter for this project

1. **The AI-assistant referrer channel became universal in five months** (Matomo March, GA4 May,
   Plausible June, PostHog July, Fathom October 2026). Plausible reports 2,200% year-on-year
   growth in AI referrals across its network. This is now the first thing a 2026 reviewer
   looks for and Mini Numbers does not have it.
2. **Read-only agent access (MCP) is the new API surface**: Umami, Rybbit, PostHog, Vercel,
   Fathom, Databuddy all shipped it.
3. **Bot and AI-crawler filtering got serious**: ASN lists, flood detection, firewall rules,
   separate bot tables.
4. **Funnels, journeys and retention are no longer "product analytics only"**.
5. **Annotations on the time series are standard**.
6. **Self-host stacks got heavier**: every newcomer requires ClickHouse. The single-JAR,
   SQLite-capable niche is real, small and almost empty.
7. **Script weight is a marketed number**, and claims get checked.

---

## Positioning

**Where Mini Numbers wins**

- The only MIT-licensed, single-artifact, SQLite-capable analytics server with funnels,
  segments, revenue attribution, webhooks and email reports.
- Configurable hash rotation and three privacy modes, now test-verified.
- Visualisation taste: contribution calendar, activity heatmap, globe, a coherent design
  system in both themes.
- A JVM stack, which no other analytics tool offers to teams that already run Kotlin/Java.

**Where competitors win**

- Breadth (Umami, Rybbit, PostHog): replay, heatmaps, vitals, journeys, retention, MCP.
- Community and ecosystem (everyone).
- AI-assistant channel, annotations, bot filtering (table stakes we lack).
- Cloud hosting option (we have none, and that is fine for the niche).

**Not trying to win**: session replay, heatmaps and feature flags. The "simple privacy"
segment (Plausible, Fathom, Simple, Pirsch, Seline) deliberately skips them too.

---

## Go/no-go

| Criteria | Weight | Score | Notes |
|----------|--------|-------|-------|
| Technical foundation | 20% | 8 | Current stack, green gates, honest tests; GeoLite2 redistribution and contrast still open |
| Privacy features | 20% | 10 | Unchanged lead |
| Security posture | 15% | 8 | Audit closed; history purge pending |
| Feature completeness vs 2026 table stakes | 15% | 6 | Missing AI channel, annotations, journeys, bot filtering v2 |
| Market opportunity | 15% | 7 | Niche is real but small; AI-referral curiosity is a launch hook |
| Differentiation | 10% | 8 | Privacy modes, single JAR, visual design |
| Deployment readiness | 5% | 8 | Docker/JAR proven; Postgres covered in CI only |
| **Total** | | **7.9** | |

**Decision**: Go, after the two pre-launch features (AI-assistant channel with the
crawler/referral split, privacy budget card) and the history purge. Launch angle: "the
single JAR, SQLite-ready, privacy-configurable analytics you can run on a Raspberry Pi,
with the honest AI-traffic view everyone is asking for."

---

## Recommendation

1. Ship v1.3.0 with the fixes and the two features above; purge the leaked history first.
2. Launch content built on the niche, not on breadth claims; keep the comparison table honest.
3. Post-launch roadmap in order: annotations (with auto change-points), journeys with
   privacy-aware aggregation, bot filter v2, read-only MCP, web vitals as an opt-in.
