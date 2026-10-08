# Mini Numbers - TODO

Open items only, in the order they should be done. History lives in `CHANGELOG.md`, the
phase view in `ROADMAP.md`, and the original pre-release audit with per-item status in
`../GOING_LIVE.md`. Last updated 2026-10-08 (v1.3.0, 15 local commits ahead of origin, nothing pushed).

## Blocked on the owner (nothing below can start before these)

- [ ] **Rotate credentials** that were committed in `stats.db`: admin password, `SERVER_SALT`, and the API keys of the three projects (one is the live www.poofly.se site). The repository is public, so treat them as burned now.
- [ ] **Purge history**: `brew install git-filter-repo`, strip `stats.db` and `src/main/resources/geo/geolite2-city.mmdb` from every commit, force-push `main`. Anyone who cloned must re-clone. Needs an explicit yes for the install and a separate yes for the force-push.
- [ ] **Push** the local commits; watch the first CI run with the PostgreSQL and browser jobs and fix what it surfaces. No live PostgreSQL has run against this code yet.
- [ ] **GeoLite2 distribution**: download at start with a MaxMind licence key (recommended; geolocation becomes opt-in) or keep committing the 61 MB file and accept the redistribution question. Attribution is already in the README; the Docker image needs it too once decided.
- [ ] **Light-theme contrast**: 16 dashboard nodes fail WCAG AA (comparison deltas `#10b981` 2.53:1 and `#ef4444` 3.76:1 on white, muted `#a8a29e` 2.52:1, `.sidebar-subtitle` 3.72:1). Smallest fix is darkening those three tokens one step; then re-enable `color-contrast` in `e2e/tests/a11y.spec.js`.
- [ ] **Enable GitHub Discussions** (the issue-template contact link points there), then **tag `v1.3.0`** so Docker Publish builds the release image.
- [ ] Launch content built on the niche (single JAR, SQLite, configurable privacy, honest AI-traffic view): Show HN, r/selfhosted, Dev.to.

## Tracked gaps, not blocking

- [ ] Hash-rotation boundary and the retention purge are untested because both read the wall clock; an injectable clock would make them testable (small production change).
- [ ] `js/*.js` duplicates table and bar-chart rendering across files, which is how the escaping gap happened; a shared `renderTable` / `renderIconBarChart` would centralise escaping (GOING_LIVE #19).
- [ ] CSP still allows `'unsafe-inline'` for scripts and styles in the admin panel.
- [ ] OWASP / Trivy dependency scanning in CI (Dependabot only, today).
- [ ] CI action major bumps (checkout v7, setup-java v6, setup-gradle v6, upload-artifact v7) left to Dependabot.
- [ ] Session lifetime is a flat 4 hours from login; a sliding window may be the intended behaviour.

## Post-launch features (in order; see ROADMAP Phase 8)

- [ ] Annotations with auto-detected change points (needs one small table)
- [ ] User journeys as a privacy-aware river
- [ ] Bot filter v2 (ASN exclusion, flood detection, "filtered" count)
- [ ] Read-only MCP server over the existing API
- [ ] Web vitals as a tracker opt-in (three nullable columns)
- [ ] Retention and cohorts
