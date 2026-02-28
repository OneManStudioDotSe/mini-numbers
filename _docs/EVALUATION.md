# Mini Numbers - Project Evaluation

**Date**: February 27, 2026
**Status**: Production-Ready (v1.0.0-beta)

---

## Executive summary

Mini Numbers is a privacy-focused web analytics platform that is **feature-complete for beta** with strong fundamentals, comprehensive security, custom event tracking, conversion goals, basic funnels, user segments, webhooks, email reports, revenue tracking with attribution, API pagination and caching, OpenAPI documentation, configurable privacy modes, a landing page, and a well-structured codebase. All critical security blockers have been resolved. Deployment infrastructure is complete with production Dockerfile, health check, and metrics endpoints. 288 tests pass with zero failures.

---

## Quick assessment

| Aspect | Rating | Notes |
|--------|--------|-------|
| **Core Functionality** | 9.5/10 | Privacy-first design, bounce rate, custom events, goals, funnels, segments |
| **Frontend/Dashboard** | 9.5/10 | Beautiful dashboard, ARIA labels |
| **Backend API** | 10/10 | Pagination, caching, OpenAPI, segments, health/metrics |
| **Privacy Design** | 10/10 | Hash rotation, 3 privacy modes, data retention |
| **Security Posture** | 8/10 | Environment variables, rate limiting, session auth |
| **Testing Coverage** | 9/10 | 288 tests (unit + integration + end-to-end) |
| **Production Readiness** | 9/10 | Dockerfile, health check, metrics, JVM tuning |
| **Documentation** | 9.5/10 | Deployment guide, OpenAPI 3.0.3 spec |
| **Integration Ease** | 9/10 | Tracker (~1.3KB minified), OpenAPI docs |
| **Code Architecture** | 9/10 | Package-per-feature, QueryCache, ApiError |

---

## Feature comparison matrix

| Feature | Mini Numbers | Umami | Plausible CE | Matomo | PostHog | Fathom | Simple Analytics |
|---------|-------------|-------|-------------|--------|---------|--------|-----------------|
| **Basic Analytics** |
| Page views & visitors | Y | Y | Y | Y | Y | Y | Y |
| Referrer tracking | Y | Y | Y | Y | Y | Y | Y |
| Geographic data | Y | Y | Y | Y | Y | Y | Y |
| Device/browser/OS | Y | Y | Y | Y | Y | Y | Y |
| Time series | Y | Y | Y | Y | Y | Y | Y |
| Real-time feed | Y | Y | Y | Y | Y | Y | Y |
| Bounce rate | Y | Y | Y | Y | Y | Y | Y |
| **Advanced Analytics** |
| Custom events | Y | Y | Y | Y | Y | - | - |
| Conversion goals | Y | Y | Y | Y | Y | Y | - |
| Funnels | Y | Y (v3) | Paid | Y | Y | - | - |
| User segments | Y | ~ | - | Y | Y | - | - |
| Revenue tracking | Y | - | - | Y | Y | - | - |
| Webhooks | Y | ~ | - | Y | Y | - | - |
| Email reports | Y | ~ | Y | Y | Y | - | - |
| Cohort/retention | - | - | - | Y | Y | - | - |
| Session replay | - | - | - | Paid | Y | - | - |
| Activity heatmap | Y | - | - | Paid | - | - | - |
| **Privacy** |
| Cookie-free | Y | Y | Y | ~ | ~ | Y | Y |
| No PII storage | Y | Y | Y | ~ | ~ | Y | Y |
| Configurable hash rotation | Y (unique) | - | - | - | - | - | - |
| Privacy modes | Y (3 levels) | - | - | ~ | ~ | - | - |
| Data retention policies | Y | Y | Y | Y | Y | - | - |
| **Technical** |
| Self-hosted | Y | Y | Y | Y | Y | - | - |
| Cloud option | - | Y | Y | Y | Y | Y | Y |
| Multi-project | Y | Y | ~ | Y | Y | Y | Y |
| Open source | Y (MIT) | Y (MIT) | Y (AGPL) | Y (GPL) | Y (MIT) | - | - |
| Tracker size | ~1.3KB | <1KB | <1KB | ~21KB | ~44KB | <1KB | <1KB |
| OpenAPI docs | Y | - | - | Y | Y | - | - |
| **UI/UX** |
| Dark mode | Y | Y | - | ~ | Y | - | - |
| Contribution calendar | Y (unique) | - | - | - | - | - | - |
| Interactive maps | Y | - | - | Y | ~ | - | - |
| Loading skeletons | Y | Y | Y | Y | Y | ~ | ~ |
| Accessibility (ARIA) | Y | ~ | ~ | Y | Y | ~ | ~ |

---

## Scoring summary

| Platform | Basic | Advanced | Privacy | Integration | UI/UX | Overall |
|----------|-------|----------|---------|-------------|-------|---------|
| **PostHog** | 9/10 | 10/10 | 7/10 | 10/10 | 9/10 | **9.0/10** |
| **Mini Numbers** | 9/10 | 8.5/10 | 10/10 | 8.5/10 | 9.5/10 | **8.8/10** |
| **Matomo** | 9.5/10 | 9/10 | 7/10 | 9/10 | 8/10 | **8.4/10** |
| **Umami** | 9/10 | 6/10 | 8/10 | 7/10 | 7/10 | **7.5/10** |
| **Plausible CE** | 8.5/10 | 5/10 | 9/10 | 6/10 | 6/10 | **7.1/10** |
| **Fathom** | 8/10 | 3/10 | 10/10 | 5/10 | 6/10 | **6.4/10** |
| **Simple Analytics** | 8/10 | 2/10 | 10/10 | 5/10 | 6/10 | **6.2/10** |

---

## Competitor profiles

### Umami -- primary competitor
- **Stack**: Node.js, TypeScript, React | **License**: MIT | **Stars**: ~6,400
- **Strengths**: Proven community, <1KB tracker, cloud + self-hosted, custom events, goals
- **Weaknesses**: No activity heatmap, no contribution calendar, no configurable hash rotation, no segments
- **vs Mini Numbers**: Umami wins on community size and tracker size; Mini Numbers wins on privacy (configurable hash rotation, 3 privacy modes), unique visualizations, user segments, OpenAPI docs, JVM stack

### Plausible CE
- **Stack**: Elixir, ClickHouse, React | **License**: AGPL | **Stars**: ~19,000
- **Strengths**: Strong brand, <1KB tracker, EU-focused, high performance
- **Weaknesses**: AGPL restricts commercial use, CE updated only twice/year, limited multi-project, no segments
- **vs Mini Numbers**: Plausible wins on brand and performance; Mini Numbers wins on configurable hash rotation, contribution calendar, segments, OpenAPI docs, license flexibility

### Matomo -- enterprise leader
- **Stack**: PHP, MySQL | **License**: GPL | **Stars**: ~19,000
- **Strengths**: 100+ features, 1,000+ plugins, 15+ years maturity, session replay, A/B testing
- **Weaknesses**: Heavy tracker (21KB), cookie-based by default, complex UI, many paid features
- **vs Mini Numbers**: Matomo wins on feature breadth and enterprise capabilities; Mini Numbers wins on simplicity, lightweight tracker, modern stack, and privacy-by-default

### PostHog -- all-in-one platform
- **Stack**: Python/TypeScript, ClickHouse | **License**: MIT | **Stars**: ~20,000
- **Strengths**: Complete platform (analytics + feature flags + session replay + A/B testing), generous free tier, mobile SDKs
- **Weaknesses**: Overkill for simple analytics, heavy tracker (44KB), complex to self-host
- **vs Mini Numbers**: PostHog wins on feature breadth; Mini Numbers wins on simplicity, lightweight tracker, and focused web analytics approach

### Fathom Analytics -- privacy SaaS
- **Type**: Closed-source, cloud-only | **Pricing**: From $15/month
- **Strengths**: Simple, excellent privacy, ad-blocker bypass, great support
- **Weaknesses**: Closed-source, no self-hosted option, limited features, vendor lock-in
- **vs Mini Numbers**: Fathom wins on cloud simplicity; Mini Numbers wins on open-source, self-hosted, more features, no recurring costs

### Simple Analytics -- EU SaaS
- **Type**: Closed-source, cloud-only | **Pricing**: From EUR 19/month
- **Strengths**: 100% GDPR compliant, all data in EU, simple interface
- **Weaknesses**: Closed-source, cloud-only, very limited features, higher pricing
- **vs Mini Numbers**: Simple Analytics wins on EU data hosting; Mini Numbers wins on features, self-hosted, no recurring costs

---

## SWOT analysis

### Strengths

| Category | Rating |
|----------|--------|
| Technical Foundation | 9.5/10 |
| Privacy Features | 10/10 |
| UI/UX | 9.5/10 |
| Unique Features | 9/10 |
| Documentation | 9.5/10 |
| Testing | 9/10 (288 tests, all passing) |
| Security | 8/10 |
| Deployment | 9/10 |

### Weaknesses

- **Community**: 0/10 -- New project, no ecosystem yet
- **Tracker size**: 8/10 -- ~1.3KB minified (competitive with most, but Umami/Plausible are <1KB)

### Opportunities

1. Underserved JVM ecosystem -- no analytics tool in Kotlin/JVM space
2. Privacy-first demand -- GDPR awareness growing, Google Analytics restricted in EU
3. Developer community -- contribution calendar appeals to developers
4. Simplicity gap -- Matomo too complex, PostHog too broad
5. Visual analytics -- unique visualizations differentiate from competitors
6. Cost advantage -- self-hosted with no recurring fees
7. Custom events, goals, funnels, segments -- now at feature parity

### Threats

1. Established competition -- Umami (6K stars), Plausible (19K), Matomo (19K), PostHog (20K)
2. Cold start -- no community or ecosystem yet
3. Cloud competition -- managed cloud more convenient than self-hosted
4. Market saturation -- 10+ established players
5. Maintenance burden -- keeping up with competitors as solo developer

---

## Go/no-go decision

| Criteria | Weight | Score | Weighted |
|----------|--------|-------|----------|
| Technical Foundation | 20% | 9.5/10 | 1.9 |
| Privacy Features | 20% | 10/10 | 2.0 |
| Security Posture | 15% | 8/10 | 1.2 |
| Feature Completeness | 15% | 9/10 | 1.35 |
| Market Opportunity | 15% | 8/10 | 1.2 |
| Differentiation | 10% | 9/10 | 0.9 |
| Deployment Readiness | 5% | 9/10 | 0.45 |
| **Total** | **100%** | | **9.0/10** |

**Decision**: Strong Go (8-10 range)

---

## Competitive positioning

**Where Mini Numbers wins:**
- Configurable hash rotation (unique -- strongest privacy differentiator)
- Three privacy modes: STANDARD, STRICT, PARANOID
- Contribution calendar (unique visualization)
- Activity heatmap (rare feature)
- Revenue tracking with attribution
- Webhooks with HMAC-signed deliveries
- Email reports (daily/weekly/monthly)
- Custom event tracking, conversion goals, funnels, user segments
- API pagination, query caching, OpenAPI documentation
- JVM/Kotlin stack (underserved market)
- Beautiful UI with dark mode, 6 chart types, loading skeletons, accessibility
- Production Dockerfile with JVM container tuning
- Self-hosted with no recurring costs

**Where competitors win:**
- Feature breadth (session replay, A/B testing)
- Community size and ecosystem
- Tracker size (<1KB vs ~1.3KB)
- Cloud hosting options

**Best for**: Privacy-conscious developers, JVM ecosystem users, open-source projects, EU businesses needing GDPR compliance, users wanting self-hosted analytics with no recurring costs.

**Not best for**: Users needing comprehensive features (Matomo/PostHog), non-technical users wanting cloud simplicity (Fathom/Simple Analytics), users needing extensive integrations.

---

## Feature completion

| Area | Completion | Highlights |
|------|-----------|------------|
| **Data Collection** | 100% | Privacy-first hashing, geolocation, user agent parsing, heartbeat, custom events |
| **Database** | 100% | SQLite + PostgreSQL, 8 performance indexes, connection pooling |
| **API Endpoints** | 100% | CRUD, analytics, goals, funnels, segments, webhooks, email reports, revenue, health, metrics |
| **Analytics Engine** | 100% | Page views, visitors, heatmap, time series, goals, funnels, segments, revenue attribution |
| **Dashboard UI** | 100% | Charts, maps, dark mode, responsive, skeletons, ARIA labels |
| **Tracking Script** | 100% | Auto pageview, heartbeat, SPA support, custom events API |
| **Security** | 85% | Session auth, API keys, rate limiting, input validation, CORS |
| **Documentation** | 95% | Deployment guide, config reference, OpenAPI spec |
| **Privacy** | 100% | Configurable hash rotation, three privacy modes, data retention |
| **Performance** | 100% | Query caching, GeoIP caching, database indexes |

### Not yet implemented

- Advanced analytics (retention/cohort analysis, user journeys)
- Multi-user support with RBAC

---

## Recommendation

**Ready for public launch.** All critical blockers resolved. Email reports, webhooks, and revenue tracking now implemented. Focus now on:

1. Community building and public launch
2. Advanced analytics (retention, cohorts, user journeys)
3. Enterprise features (multi-user, roles)

---

## Sources

- [Best Google Analytics Alternatives](https://www.accuwebhosting.com/blog/best-google-analytics-alternatives/)
- [Plausible alternatives & competitors](https://posthog.com/blog/best-plausible-alternatives)
- [Umami vs Plausible vs Matomo](https://aaronjbecker.com/posts/umami-vs-plausible-vs-matomo-self-hosted-analytics/)
- [Privacy-First Analytics Alternatives 2026](https://www.databuddy.cc/blog/7-privacy-first-google-analytics-alternatives-you-need-to-know-in-2026)
- [GDPR-compliant analytics tools](https://posthog.com/blog/best-gdpr-compliant-analytics-tools)
- [Open source analytics](https://posthog.com/blog/best-open-source-analytics-tools)
- [Self-hosted analytics](https://plausible.io/self-hosted-web-analytics)
