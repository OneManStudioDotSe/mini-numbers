# Mini Numbers - Project Evaluation

**Date**: February 20, 2026
**Status**: Production-Ready (Beta)

---

## Executive Summary

Mini Numbers is a privacy-focused web analytics platform that is **~97% complete** with strong fundamentals, comprehensive security, custom event tracking, conversion goals, basic funnels, and a well-structured codebase. All critical security blockers have been resolved. Deployment documentation is complete.

---

## Quick Assessment

| Aspect | Rating | Change |
|--------|--------|--------|
| **Core Functionality** | 9.5/10 | up from 9/10 |
| **Frontend/Dashboard** | 9.5/10 | up from 9/10 — goals cards, funnel visualization |
| **Backend API** | 9.5/10 | up from 9/10 — 9 new endpoints for goals & funnels |
| **Privacy Design** | 9/10 | — |
| **Security Posture** | 8/10 | — |
| **Testing Coverage** | 8/10 | up from 7.5/10 — 166 tests (55 new) |
| **Production Readiness** | 7/10 | up from 5/10 |
| **Documentation** | 9/10 | — |
| **Integration Ease** | 8.5/10 | up from 8/10 |
| **Code Architecture** | 8.5/10 | up from 8/10 — dedicated ConversionAnalysisUtils |

---

## Strengths

- Unique privacy approach with daily-rotating visitor hashes
- Beautiful, full-featured admin dashboard with dark mode
- Custom event tracking with `MiniNumbers.track()` API and dashboard visualization
- Conversion goals (URL-based and event-based) with conversion rate tracking and period comparison
- Basic funnels with multi-step conversion tracking, drop-off analysis, and time between steps
- Lightweight architecture with minimal dependencies
- Comprehensive analytics (heatmaps, contribution calendar, comparisons, custom events, goals, funnels)
- Modern Kotlin/JVM stack
- Session-based authentication with setup wizard
- Environment variable configuration
- Rate limiting, input validation, and configurable CORS
- 166 tests covering security, validation, custom events, analytics, goals, funnels, lifecycle, and integration
- Clean package-per-feature code architecture
- Comprehensive deployment documentation (JAR, Docker, reverse proxy, SSL, backups)
- GeoIP database bundled and works from fat JAR deployments

## Remaining Gaps

- No email reports or webhooks
- No production Dockerfile in repo yet (documented but not created)
- No cloud hosting option

---

## Feature Completion (95% Overall)

### Completed

| Area | Completion | Highlights |
|------|-----------|------------|
| **Data Collection** | 100% | Privacy-first hashing, geolocation, user agent parsing, heartbeat, custom events |
| **Database** | 98% | SQLite + PostgreSQL, proper indexing, connection pooling, schema evolution, goals/funnels tables |
| **API Endpoints** | 98% | CRUD, analytics, live feed, reports, comparisons, calendar, custom events, goals (5), funnels (4) |
| **Analytics Engine** | 98% | Page views, visitors, heatmap, peak times, time series, comparison, custom events, goal conversions, funnel analysis |
| **Dashboard UI** | 97% | Charts, maps, filters, exports, dark mode, responsive, custom events, goal cards, funnel visualization |
| **Tracking Script** | 100% | Auto pageview, heartbeat, SPA support, sendBeacon delivery, custom events API |
| **Security** | 85% | Session auth, API keys, rate limiting, input validation, CORS |
| **Documentation** | 90% | Deployment guide, configuration reference, tracker integration, Docker |

### Not Yet Implemented

- Email reports and webhooks
- API pagination and caching
- Loading states and accessibility improvements
- Production Dockerfile in repo

---

## Competitive Position

Mini Numbers competes with **Umami** (6,400 stars), **Plausible CE** (19,000 stars), **Matomo** (19,000 stars), and **PostHog** (20,000 stars).

| Platform | Overall Score |
|----------|--------------|
| PostHog | 9.0/10 |
| Matomo | 8.4/10 |
| **Mini Numbers** | **8.3/10** |
| Umami | 7.5/10 |
| Plausible CE | 7.1/10 |
| Fathom | 6.4/10 |
| Simple Analytics | 6.2/10 |

### Where Mini Numbers Wins

- **Daily hash rotation** — No competitor has this privacy feature
- **Contribution calendar** — Unique GitHub-style visualization
- **Activity heatmap** — Rare among competitors
- **JVM/Kotlin stack** — Underserved market segment
- **Beautiful UI** — Dark mode, 6 chart types, interactive maps
- **Custom events** — Name-based tracking with dedicated dashboard card
- **Conversion goals** — URL and event-based goals with conversion rate tracking
- **Funnel analysis** — Multi-step drop-off visualization with time between steps

### Where Competitors Win

- Feature breadth (session replay, A/B testing)
- Community size and ecosystem
- Tracker size (<1KB vs ~1.5KB)
- Cloud hosting options

---

## Recommendation

**Ready for public launch.** All critical blockers resolved. Custom events, conversion goals, and funnels implemented. Deployment docs complete. Focus now on:

1. Production Docker image (Dockerfile + docker-compose in repo)
2. Email reports and webhooks
3. Community building and public launch

---

## Sources

- [Best Google Analytics Alternatives](https://www.accuwebhosting.com/blog/best-google-analytics-alternatives/)
- [Plausible alternatives & competitors](https://posthog.com/blog/best-plausible-alternatives)
- [Umami vs Plausible vs Matomo](https://aaronjbecker.com/posts/umami-vs-plausible-vs-matomo-self-hosted-analytics/)
- [Privacy-First Analytics Alternatives 2026](https://www.databuddy.cc/blog/7-privacy-first-google-analytics-alternatives-you-need-to-know-in-2026)
- [GDPR-compliant analytics tools](https://posthog.com/blog/best-gdpr-compliant-analytics-tools)
