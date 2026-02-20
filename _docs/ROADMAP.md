# Mini Numbers - Roadmap

**Date**: February 20, 2026

---

## Phase 1: Production Ready (Weeks 1-6) — COMPLETE

**Goal**: Security hardened, tested, and ready for beta testers

| Area | Status |
|------|--------|
| Security hardening (env vars, CORS, rate limiting, validation) | COMPLETED |
| Setup wizard (zero-restart, interactive) | COMPLETED |
| Test suite (166 tests) | COMPLETED |
| Code architecture (package-per-feature) | COMPLETED |
| Session-based authentication | COMPLETED |
| Bounce rate calculation and dashboard | COMPLETED |
| Tracker optimization (1.9KB source, 1.3KB minified) | COMPLETED |
| Custom event tracking (name-only, full stack) | COMPLETED |
| GeoIP database bundling (classpath fallback for JAR) | COMPLETED |
| Deployment documentation (JAR, Docker, reverse proxy, SSL) | COMPLETED |
| Production Docker configuration (multi-stage build, health check, JVM tuning) | COMPLETED |

**Milestone**: v1.0.0-beta

---

## Phase 2: Feature Parity (Weeks 7-16) — COMPLETE

**Goal**: Match Umami and basic Plausible features

| Week | Focus | Status |
|------|-------|--------|
| 7-9 | Custom event tracking | COMPLETED |
| 10-11 | Conversion goals | COMPLETED |
| 12-13 | Basic funnels | COMPLETED |
| 14-15 | API enhancements (pagination, caching, standardized errors, OpenAPI docs) | COMPLETED |
| 16 | Email reports | Not Started |

**Milestone**: v1.0.0

---

## Phase 3: Competitive Edge (Weeks 17-32) — Mostly Complete

**Goal**: Differentiate with unique features and enterprise capabilities

| Focus | Status |
|-------|--------|
| Webhooks and integrations (Slack, Discord, Zapier) | Not Started |
| Enhanced privacy (configurable hash rotation, 3 privacy modes, data retention) | COMPLETED |
| Performance optimization (8 database indexes, query cache, GeoIP cache) | COMPLETED |
| User segments (visual filter builder with AND/OR logic) | COMPLETED |
| Loading states and accessibility (skeletons, ARIA, keyboard nav) | COMPLETED |
| Advanced analytics (retention, cohorts, user journeys) | Not Started |
| Enterprise features (multi-user, roles) | Not Started |

**Milestone**: v2.0.0

---

## Phase 4: Ecosystem Growth (Weeks 32+)

**Goal**: Build community, plugins, and ecosystem

| Focus | Deliverables |
|-------|-------------|
| Mobile | PWA support, optional native apps |
| Customization | Plugin system, custom widgets, white-labeling |
| Integrations | CMS plugins (WordPress, Ghost), framework support |
| Community | Documentation site, forum, contribution guidelines |
| Cloud Service | Managed hosting option (optional revenue stream) |

**Milestone**: v3.0.0

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Security vulnerability | Security audit before launch, bug bounty, responsible disclosure |
| Slow adoption | Strong launch (HN, Reddit, Product Hunt), excellent docs |
| Maintenance burden | Build community early, automate testing/deployment |
| Feature gaps | Prioritize most-requested features, deliver incrementally |
| Competitors innovate faster | Focus on unique strengths (JVM, privacy, visuals) |

---

## Investment

- **Solo developer**: 6-8 months to v1.0.0
- **Small team (2-3)**: 3-4 months to v1.0.0
- **Infrastructure costs**: <$100/year (self-hosted)
