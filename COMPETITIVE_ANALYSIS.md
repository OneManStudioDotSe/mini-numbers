# Mini Numbers - Competitive Analysis

**Version**: 1.0
**Date**: February 9, 2026

---

## Table of Contents

1. [Feature Comparison Matrix](#1-feature-comparison-matrix)
2. [Scoring Summary](#2-scoring-summary)
3. [Competitor Deep-Dive](#3-competitor-deep-dive)
   - [Umami](#31-umami-primary-competitor)
   - [Plausible CE](#32-plausible-ce-community-edition)
   - [Matomo](#33-matomo-enterprise-leader)
   - [PostHog](#34-posthog-all-in-one-platform)
   - [Fathom Analytics](#35-fathom-analytics-privacy-first-saas)
   - [Simple Analytics](#36-simple-analytics-eu-based-saas)

---

## 1. Feature Comparison Matrix

Comprehensive comparison of Mini Numbers against the leading open-source and SaaS analytics platforms.

| Feature Category | Mini Numbers | Umami | Plausible CE | Matomo | PostHog | Fathom | Simple Analytics |
|------------------|--------------|-------|--------------|--------|---------|--------|------------------|
| **Basic Analytics** |
| Page views tracking | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Unique visitors | ✅ (daily hash) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Referrer tracking | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Geographic data | ✅ (country/city) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Device detection | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Browser detection | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| OS detection | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Time series | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Real-time feed | ✅ (5 min) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Bounce rate | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Session duration | ⚠️ (captured, not analyzed) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Advanced Analytics** |
| Custom events | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| Event properties | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| Conversion goals | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Funnels | ❌ | ✅ (v3) | ❌ (paid) | ✅ | ✅ | ❌ | ❌ |
| Cohort analysis | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| Retention analysis | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| User journey | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| Session replay | ❌ | ❌ | ❌ | ✅ (paid) | ✅ | ❌ | ❌ |
| Heatmaps (activity) | ✅ | ❌ | ❌ | ✅ (paid, click) | ❌ | ❌ | ❌ |
| A/B testing | ❌ | ❌ | ❌ | ✅ (paid) | ✅ | ❌ | ❌ |
| Feature flags | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Error tracking | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| **Privacy & Compliance** |
| Cookie-free tracking | ✅ | ✅ | ✅ | ⚠️ (optional) | ⚠️ (config) | ✅ | ✅ |
| No PII storage | ✅ | ✅ | ✅ | ⚠️ (partial) | ⚠️ (config) | ✅ | ✅ |
| IP anonymization | ✅ (never stored) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| GDPR compliant | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Daily hash rotation | ✅ (unique) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| No consent banner needed | ✅ | ✅ | ✅ | ⚠️ | ⚠️ | ✅ | ✅ |
| EU data hosting | ⚠️ (self-hosted) | ✅ (cloud) | ✅ (cloud) | ✅ | ✅ | ✅ | ✅ |
| **Integration & API** |
| REST API | ✅ (basic) | ✅ | ✅ | ✅ (extensive) | ✅ (extensive) | ✅ | ✅ |
| GraphQL API | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Webhooks | ❌ | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ |
| Data export (CSV) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Data export (JSON) | ⚠️ (API only) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Email reports | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Scheduled reports | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Segments/filters | ⚠️ (basic) | ✅ (v3) | ⚠️ (basic) | ✅ (advanced) | ✅ (advanced) | ⚠️ | ⚠️ |
| Custom dashboards | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| **UI & UX** |
| Dark mode | ✅ | ✅ | ❌ | ⚠️ (limited) | ✅ | ❌ | ❌ |
| Contribution calendar | ✅ (unique) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Multiple chart types | ✅ (6 types) | ⚠️ (3 types) | ⚠️ (basic) | ✅ (extensive) | ✅ (extensive) | ⚠️ (basic) | ⚠️ (basic) |
| Interactive maps | ✅ (Leaflet) | ❌ | ❌ | ✅ | ⚠️ | ❌ | ❌ |
| Period comparison | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Data visualization | ✅ (excellent) | ⚠️ (good) | ⚠️ (basic) | ✅ (excellent) | ✅ (excellent) | ⚠️ (basic) | ⚠️ (basic) |
| Mobile responsive | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Single-page dashboard | ⚠️ (multi-section) | ✅ | ✅ | ❌ (tabs) | ❌ (tabs) | ✅ | ✅ |
| **Technical** |
| Multi-project support | ✅ | ✅ | ⚠️ (limited CE) | ✅ | ✅ | ✅ | ✅ |
| Self-hosted option | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| Cloud option | ❌ | ✅ ($20/mo) | ✅ ($9/mo) | ✅ (€19/mo) | ✅ (free tier) | ✅ ($15/mo) | ✅ (€19/mo) |
| Docker support | ✅ | ✅ | ✅ | ✅ | ✅ | N/A | N/A |
| Docker Compose | ⚠️ (manual) | ✅ | ✅ | ✅ | ✅ | N/A | N/A |
| Kubernetes manifests | ❌ | ✅ | ✅ | ❌ | ✅ | N/A | N/A |
| Database options | SQLite, PostgreSQL | MySQL, PostgreSQL | PostgreSQL, ClickHouse | MySQL, MariaDB | PostgreSQL, ClickHouse | N/A | N/A |
| Horizontal scaling | ⚠️ (untested) | ✅ | ✅ | ✅ | ✅ | N/A | N/A |
| **Developer Experience** |
| Tracker size | ~4KB | <1KB ⭐ | <1KB ⭐ | ~21KB | ~44KB | <1KB ⭐ | <1KB ⭐ |
| API documentation | ⚠️ (partial) | ✅ | ✅ | ✅ (extensive) | ✅ (extensive) | ✅ | ⚠️ |
| SDK support | ❌ | ✅ (Node) | ❌ | ✅ (multiple) | ✅ (extensive) | ❌ | ❌ |
| Plugin system | ❌ | ❌ | ❌ | ✅ (extensive) | ✅ | ❌ | ❌ |
| Open source | ⚠️ (TBD) | ✅ (MIT) | ✅ (AGPL) | ✅ (GPL-3.0) | ✅ (MIT) | ❌ | ❌ |
| GitHub stars | N/A (new) | ~6,400 | ~19,000 | ~19,000 | ~20,000 | N/A | N/A |
| Active development | ✅ (new) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Stack** |
| Language | Kotlin | TypeScript | Elixir | PHP | Python/TypeScript | N/A | N/A |
| Database | SQLite/PG | MySQL/PG | PG/ClickHouse | MySQL/MariaDB | PG/ClickHouse | N/A | N/A |
| Frontend | Vanilla JS | React | React | Angular/Vue | React | N/A | N/A |

---

## 2. Scoring Summary

| Platform | Basic Features | Advanced Features | Privacy | Integration | UI/UX | Technical | Overall |
|----------|----------------|-------------------|---------|-------------|-------|-----------|---------|
| **Mini Numbers** | 8.5/10 | 4/10 | 9/10 ⭐ | 6/10 | 8.5/10 | 7/10 | **7.2/10** |
| **Umami** | 9/10 | 6/10 | 8/10 | 7/10 | 7/10 | 8/10 | **7.5/10** |
| **Plausible CE** | 8.5/10 | 5/10 | 9/10 | 6/10 | 6/10 | 8/10 | **7.1/10** |
| **Matomo** | 9.5/10 | 9/10 | 7/10 | 9/10 ⭐ | 8/10 | 8/10 | **8.4/10** ⭐ |
| **PostHog** | 9/10 | 10/10 ⭐ | 7/10 | 10/10 ⭐ | 9/10 | 9/10 | **9.0/10** ⭐ |
| **Fathom** | 8/10 | 3/10 | 10/10 ⭐ | 5/10 | 6/10 | N/A | **6.4/10** |
| **Simple Analytics** | 8/10 | 2/10 | 10/10 ⭐ | 5/10 | 6/10 | N/A | **6.2/10** |

**Key**: ⭐ = Market leader in category

---

## 3. Competitor Deep-Dive

### 3.1 Umami (Primary Competitor for Mini Numbers)

**Positioning**: Simple, fast, privacy-focused Google Analytics alternative

**Key Stats:**
- **GitHub Stars**: ~6,400
- **License**: MIT
- **Stack**: Node.js (Next.js), TypeScript, React
- **Database**: MySQL or PostgreSQL
- **Tracker Size**: <1KB (minified)
- **Cloud Pricing**: Free tier (3 sites, 100k events/month), Paid from $20/month

**Strengths:**
- Proven track record with established community
- v3 features: Links tracking, pixels, segments, data export UI
- Very lightweight tracker (<1KB vs Mini Numbers' 4KB)
- Active development with frequent updates
- Both self-hosted and cloud options
- MIT license (permissive, business-friendly)
- Node.js/React stack (popular, easy to find developers)

**Weaknesses:**
- No activity heatmap visualization
- No contribution calendar
- No daily hash rotation (less privacy protection)
- Smaller feature set compared to Matomo
- Limited advanced analytics (no session replay, no A/B testing)

**Comparison vs Mini Numbers:**
- **Umami wins**: Community size, tracker size (<1KB), cloud option, custom events, MIT license
- **Mini Numbers wins**: Daily hash rotation (stronger privacy), contribution calendar, activity heatmap, JVM stack (enterprise appeal), theme system
- **Tie**: Basic analytics features, GDPR compliance, UI quality

**Market Position**: Strong choice for developers wanting simplicity and proven reliability.

---

### 3.2 Plausible CE (Community Edition)

**Positioning**: Lightweight, GDPR-compliant, EU-focused analytics

**Key Stats:**
- **GitHub Stars**: ~19,000
- **License**: AGPL-3.0
- **Stack**: Elixir (Phoenix), ClickHouse, React
- **Database**: PostgreSQL + ClickHouse
- **Tracker Size**: <1KB (minified)
- **Cloud Pricing**: From $9/month (10k pageviews), $19/month (100k pageviews)
- **CE Update Frequency**: Twice per year (slower than cloud)

**Strengths:**
- Largest GitHub star count in analytics space
- <1KB tracker (extremely lightweight)
- Strong EU presence and GDPR focus
- Elixir + ClickHouse stack (high performance)
- Beautiful, minimalist UI
- Strong brand recognition
- Cloud service is very popular ($9/mo entry)

**Weaknesses:**
- AGPL license (restrictive copyleft, limits commercial use)
- Community Edition updated only twice/year
- Premium features (funnels, ecommerce) not in CE
- Limited CE support (community only)
- Multi-project support limited in CE
- No advanced analytics in CE (cohorts, retention)

**Comparison vs Mini Numbers:**
- **Plausible wins**: Brand recognition (19k stars), tracker size (<1KB), cloud option, performance (ClickHouse), update frequency
- **Mini Numbers wins**: Daily hash rotation, contribution calendar, activity heatmap, license flexibility (TBD), multi-project support
- **Tie**: GDPR compliance, basic analytics, UI quality

**Market Position**: Market leader in privacy-focused analytics with strong cloud business and open-source community.

---

### 3.3 Matomo (Enterprise Leader)

**Positioning**: Enterprise-grade, feature-rich Google Analytics replacement

**Key Stats:**
- **GitHub Stars**: ~19,000
- **License**: GPL-3.0
- **Stack**: PHP (Symfony), MySQL/MariaDB, Angular/Vue
- **Database**: MySQL or MariaDB
- **Tracker Size**: ~21KB
- **Cloud Pricing**: From €19/month (50k pageviews)
- **History**: 15+ years of development (formerly Piwik)

**Strengths:**
- Most comprehensive feature set (100+ features)
- 100% data sampling (unlike Google Analytics)
- Session recording, heatmaps (click-based), A/B testing
- Extensive plugin ecosystem (1,000+ plugins)
- User journey visualization
- Cohort and retention analysis
- eCommerce tracking
- Custom dimensions and metrics
- Multi-user support with roles
- White-labeling options
- 15+ years of maturity
- PHP stack (easy to host on shared hosting)

**Weaknesses:**
- Heavier footprint (~21KB tracker vs <1KB competitors)
- Many premium features require paid plugins
- More complex setup and configuration
- PHP stack (less modern than Node/Elixir/Kotlin)
- UI can be overwhelming (too many features)
- Cookie-based tracking by default (optional cookieless)

**Comparison vs Mini Numbers:**
- **Matomo wins**: Feature breadth (10x more features), enterprise features, plugin ecosystem, maturity, extensive documentation, custom events, funnels, cohorts, session replay, A/B testing
- **Mini Numbers wins**: Lightweight (4KB vs 21KB), modern stack (Kotlin vs PHP), daily hash rotation, contribution calendar, simpler UI, cookie-free by default
- **Tie**: Self-hosted option, multi-project support, GDPR compliance

**Market Position**: The go-to choice for enterprises needing comprehensive analytics with full data ownership.

---

### 3.4 PostHog (All-in-One Platform)

**Positioning**: Complete developer platform (analytics + feature flags + session replay + more)

**Key Stats:**
- **GitHub Stars**: ~20,000
- **License**: MIT (with proprietary Enterprise Edition features)
- **Stack**: Python (Django), TypeScript (React), ClickHouse
- **Database**: PostgreSQL + ClickHouse
- **Tracker Size**: ~44KB (includes session replay)
- **Cloud Pricing**: Free tier (1M events/month), Scale plan usage-based
- **Funding**: Well-funded ($27M Series B)

**Strengths:**
- All-in-one platform (analytics + feature flags + experiments + session replay + error tracking + data warehouse)
- Most advanced product analytics (funnels, cohorts, retention, user paths, SQL insights)
- AI product assistant
- Mobile SDK support (iOS, Android, React Native, Flutter)
- Data warehouse integration (BigQuery, Snowflake, Postgres)
- Event autocapture (no manual instrumentation)
- GraphQL API
- Extensive documentation and learning resources
- Strong developer community
- Generous free tier (1M events)
- MIT license (permissive)

**Weaknesses:**
- Much broader scope than pure analytics (may be overkill)
- Heavier resource requirements
- Larger tracker size (44KB)
- More complex to self-host
- Developer-focused (less suitable for business users)
- Steeper learning curve
- Some features proprietary (Enterprise Edition)

**Comparison vs Mini Numbers:**
- **PostHog wins**: Feature breadth (5x more features), advanced analytics (funnels, cohorts, retention, user paths), session replay, feature flags, A/B testing, mobile SDKs, data warehouse, GraphQL API, cloud free tier, community size
- **Mini Numbers wins**: Lightweight (4KB vs 44KB), simplicity, focus on web analytics only, daily hash rotation, contribution calendar, easier to understand/deploy
- **Tie**: GDPR compliance, self-hosted option, modern stack

**Market Position**: The developer's choice for a complete product analytics platform, but overkill if you only need web analytics.

---

### 3.5 Fathom Analytics (Privacy-First SaaS)

**Positioning**: Simple, privacy-first cloud analytics (no self-hosted option)

**Key Stats:**
- **Type**: Closed-source SaaS only
- **Stack**: Proprietary
- **Tracker Size**: <1KB
- **Pricing**: From $15/month (100k pageviews), $60/month (1M pageviews)
- **Privacy**: GDPR, CCPA, PECR compliant by default

**Strengths:**
- Extremely simple setup and UI
- Pioneer in privacy-friendly analytics
- Beautiful single-page dashboard
- Ad-blocker bypass technology (ethical method)
- Email reports
- GDPR compliance without consent banners
- Excellent customer support
- Fast performance
- Affordable pricing ($15/mo vs Plausible $19/mo)

**Weaknesses:**
- Closed-source (no transparency)
- Cloud-only (no self-hosted option)
- Limited features (no custom events in basic plan)
- No funnels, cohorts, or advanced analytics
- No API access in lower tiers
- Vendor lock-in
- Limited integrations

**Comparison vs Mini Numbers:**
- **Fathom wins**: Cloud simplicity, proven business model, customer support, ad-blocker bypass
- **Mini Numbers wins**: Open-source, self-hosted, daily hash rotation, contribution calendar, activity heatmap, more features, no recurring costs
- **Tie**: Privacy focus, lightweight tracker, GDPR compliance

**Market Position**: Best for non-technical users wanting simple cloud analytics with strong privacy guarantees.

---

### 3.6 Simple Analytics (EU-Based SaaS)

**Positioning**: 100% GDPR compliant, EU-hosted, simple analytics

**Key Stats:**
- **Type**: Closed-source SaaS only
- **Stack**: Proprietary
- **Tracker Size**: <1KB
- **Pricing**: From €19/month (100k pageviews)
- **Privacy**: 100% GDPR, ePrivacy Directive, UK GDPR, PECR compliant

**Strengths:**
- 100% GDPR compliant (no personal data collected)
- All data stored in EU
- No cookies or tracking (fully anonymous)
- Simple, clean interface
- Email reports
- Automatic compliance (no consent needed)
- Referrer spam filtering

**Weaknesses:**
- Closed-source
- Cloud-only (no self-hosted)
- Very limited features (no custom events, goals, or funnels)
- No advanced analytics
- No API access in starter plan
- Higher pricing (€19/mo vs competitors)
- Smaller feature set than competitors

**Comparison vs Mini Numbers:**
- **Simple Analytics wins**: Cloud simplicity, automatic EU hosting
- **Mini Numbers wins**: Open-source, self-hosted, daily hash rotation, more features, contribution calendar, activity heatmap, no recurring costs, better UI
- **Tie**: Privacy focus, lightweight tracker, GDPR compliance

**Market Position**: Best for EU-based businesses prioritizing data sovereignty and automatic compliance.

---

## Summary

### Competitive Landscape Overview

**Market Leaders:**
1. **PostHog** (9.0/10) - Most comprehensive, all-in-one platform
2. **Matomo** (8.4/10) - Enterprise-grade, 15+ years maturity
3. **Umami** (7.5/10) - Best balance of simplicity and features
4. **Mini Numbers** (7.2/10) - Strong privacy, unique visualizations, new entrant
5. **Plausible CE** (7.1/10) - Minimalist, strong brand
6. **Fathom** (6.4/10) - Simple SaaS, no self-hosted
7. **Simple Analytics** (6.2/10) - EU-focused SaaS

### Mini Numbers Competitive Position

**Advantages:**
- ✅ **Daily hash rotation** - Unique, strongest privacy protection
- ✅ **Contribution calendar** - Unique visualization
- ✅ **Activity heatmap** - Rare feature
- ✅ **JVM/Kotlin stack** - Underserved market
- ✅ **Beautiful UI** - Dark mode, theme system, modern design

**Disadvantages:**
- ⚠️ **Feature gaps** - No custom events, goals, funnels
- ⚠️ **No community yet** - Zero stars vs thousands for competitors
- ⚠️ **Tracker size** - 4KB vs <1KB for most competitors
- ⚠️ **No cloud option** - Self-hosted only

### Target Market Fit

**Best For:**
- Privacy-conscious developers
- JVM/Kotlin ecosystem users
- Open-source projects
- EU businesses needing GDPR compliance
- Users wanting self-hosted with no recurring costs

**Not Best For:**
- Users needing comprehensive features (choose Matomo or PostHog)
- Non-technical users wanting cloud simplicity (choose Fathom or Simple Analytics)
- Users needing immediate advanced analytics (choose PostHog)
- Users needing extensive integrations (choose Matomo)

---

**Document Metadata:**
- **Version**: 1.0
- **Date**: February 9, 2026
- **Lines**: ~340
- **Source**: PROJECT_EVALUATION.md (Section 3-4)
