# Mini Numbers - Roadmap

**Date**: February 20, 2026

---

## Phase 1: Production Ready (Weeks 1-6) — COMPLETE

**Goal**: Security hardened, tested, and ready for beta testers

| Area | Status |
|------|--------|
| Security hardening (env vars, CORS, rate limiting, validation) | COMPLETED |
| Setup wizard (zero-restart, interactive) | COMPLETED |
| Test suite (111 tests) | COMPLETED |
| Code architecture (package-per-feature) | COMPLETED |
| Session-based authentication | COMPLETED |
| Bounce rate calculation and dashboard | COMPLETED |
| Tracker optimization (1.9KB source, 1.3KB minified) | COMPLETED |
| Custom event tracking (name-only, full stack) | COMPLETED |
| GeoIP database bundling (classpath fallback for JAR) | COMPLETED |
| Deployment documentation (JAR, Docker, reverse proxy, SSL) | COMPLETED |
| Production Docker configuration | Pending |

**Milestone**: v1.0.0-beta

---

## Phase 2: Feature Parity (Weeks 7-16)

**Goal**: Match Umami and basic Plausible features

| Week | Focus | Status |
|------|-------|--------|
| 7-9 | Custom event tracking | COMPLETED |
| 10-11 | Conversion goals | Not Started |
| 12-13 | Basic funnels | Not Started |
| 14-15 | API enhancements (pagination, caching, docs) | Not Started |
| 16 | Email reports | Not Started |

**Milestone**: v1.0.0

---

## Phase 3: Competitive Edge (Weeks 17-32)

**Goal**: Differentiate with unique features and enterprise capabilities

| Focus | Status |
|-------|--------|
| Webhooks and integrations (Slack, Discord, Zapier) | Not Started |
| Enhanced privacy (configurable hash rotation, privacy modes) | Not Started |
| Performance optimization (database, caching) | Not Started |
| User segments (visual filter builder) | Not Started |
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

## Success Metrics

### Year 1 — Launch & Growth
- 1,000 GitHub stars
- 100 production deployments
- 10 contributors
- Feature parity with Umami

### Year 2 — Maturity & Ecosystem
- 5,000 GitHub stars
- 1,000 production deployments
- Cloud offering launch
- Plugin ecosystem started

### Year 3 — Leadership
- 10,000+ GitHub stars
- 5,000+ production deployments
- Enterprise customers
- Recognized privacy leader

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
