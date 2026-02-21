# Mini Numbers - Competitive analysis

**Date**: February 21, 2026

---

## Feature comparison matrix

| Feature | Mini Numbers | Umami | Plausible CE | Matomo | PostHog | Fathom | Simple Analytics |
|---------|-------------|-------|-------------|--------|---------|--------|-----------------|
| **Basic Analytics** |
| Page views & visitors | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Referrer tracking | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Geographic data | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Device/browser/OS | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Time series | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Real-time feed | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Bounce rate | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Advanced Analytics** |
| Custom events | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| Conversion goals | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Funnels | ✅ | ✅ (v3) | ❌ (paid) | ✅ | ✅ | ❌ | ❌ |
| User segments | ✅ | ⚠️ | ❌ | ✅ | ✅ | ❌ | ❌ |
| Cohort/retention | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| Session replay | ❌ | ❌ | ❌ | ✅ (paid) | ✅ | ❌ | ❌ |
| Activity heatmap | ✅ | ❌ | ❌ | ✅ (paid) | ❌ | ❌ | ❌ |
| A/B testing | ❌ | ❌ | ❌ | ✅ (paid) | ✅ | ❌ | ❌ |
| **Privacy** |
| Cookie-free | ✅ | ✅ | ✅ | ⚠️ | ⚠️ | ✅ | ✅ |
| No PII storage | ✅ | ✅ | ✅ | ⚠️ | ⚠️ | ✅ | ✅ |
| Configurable hash rotation | ✅ (unique) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Privacy modes | ✅ (3 levels) | ❌ | ❌ | ⚠️ | ⚠️ | ❌ | ❌ |
| Data retention policies | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| No consent needed | ✅ | ✅ | ✅ | ⚠️ | ⚠️ | ✅ | ✅ |
| **API & Integration** |
| API pagination | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ⚠️ |
| OpenAPI documentation | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| Query caching | ✅ | ⚠️ | ✅ | ✅ | ✅ | ⚠️ | ⚠️ |
| Health check endpoint | ✅ | ✅ | ✅ | ✅ | ✅ | N/A | N/A |
| **UI & UX** |
| Dark mode | ✅ | ✅ | ❌ | ⚠️ | ✅ | ❌ | ❌ |
| Contribution calendar | ✅ (unique) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Multiple chart types | ✅ (6) | ⚠️ (3) | ⚠️ | ✅ | ✅ | ⚠️ | ⚠️ |
| Interactive maps | ✅ | ❌ | ❌ | ✅ | ⚠️ | ❌ | ❌ |
| Loading skeletons | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ⚠️ |
| Accessibility (ARIA) | ✅ | ⚠️ | ⚠️ | ✅ | ✅ | ⚠️ | ⚠️ |
| **Technical** |
| Self-hosted | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| Cloud option | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Multi-project | ✅ | ✅ | ⚠️ | ✅ | ✅ | ✅ | ✅ |
| Open source | ⚠️ (TBD) | ✅ (MIT) | ✅ (AGPL) | ✅ (GPL) | ✅ (MIT) | ❌ | ❌ |
| Tracker size | ~1.3KB | <1KB | <1KB | ~21KB | ~44KB | <1KB | <1KB |

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

### Umami — primary competitor
- **Stack**: Node.js, TypeScript, React | **License**: MIT | **Stars**: ~6,400
- **Strengths**: Proven community, <1KB tracker, cloud + self-hosted, custom events, goals
- **Weaknesses**: No activity heatmap, no contribution calendar, no configurable hash rotation, no segments
- **vs Mini Numbers**: Umami wins on community size and tracker size; Mini Numbers wins on privacy (configurable hash rotation, 3 privacy modes), unique visualizations, user segments, OpenAPI docs, JVM stack, and matches on custom events, goals, and funnels

### Plausible CE
- **Stack**: Elixir, ClickHouse, React | **License**: AGPL | **Stars**: ~19,000
- **Strengths**: Strong brand, <1KB tracker, EU-focused, high performance
- **Weaknesses**: AGPL restricts commercial use, CE updated only twice/year, limited multi-project, no segments
- **vs Mini Numbers**: Plausible wins on brand and performance; Mini Numbers wins on configurable hash rotation, contribution calendar, segments, OpenAPI docs, license flexibility

### Matomo — enterprise leader
- **Stack**: PHP, MySQL | **License**: GPL | **Stars**: ~19,000
- **Strengths**: 100+ features, 1,000+ plugins, 15+ years maturity, session replay, A/B testing
- **Weaknesses**: Heavy tracker (21KB), cookie-based by default, complex UI, many paid features
- **vs Mini Numbers**: Matomo wins on feature breadth and enterprise capabilities; Mini Numbers wins on simplicity, lightweight tracker, modern stack, and privacy-by-default

### PostHog — all-in-one platform
- **Stack**: Python/TypeScript, ClickHouse | **License**: MIT | **Stars**: ~20,000
- **Strengths**: Complete platform (analytics + feature flags + session replay + A/B testing), generous free tier, mobile SDKs
- **Weaknesses**: Overkill for simple analytics, heavy tracker (44KB), complex to self-host
- **vs Mini Numbers**: PostHog wins on feature breadth; Mini Numbers wins on simplicity, lightweight tracker, and focused web analytics approach

### Fathom Analytics — privacy SaaS
- **Type**: Closed-source, cloud-only | **Pricing**: From $15/month
- **Strengths**: Simple, excellent privacy, ad-blocker bypass, great support
- **Weaknesses**: Closed-source, no self-hosted option, limited features, vendor lock-in
- **vs Mini Numbers**: Fathom wins on cloud simplicity; Mini Numbers wins on open-source, self-hosted, more features (custom events, heatmap, calendar, segments), no recurring costs

### Simple Analytics — EU SaaS
- **Type**: Closed-source, cloud-only | **Pricing**: From EUR 19/month
- **Strengths**: 100% GDPR compliant, all data in EU, simple interface
- **Weaknesses**: Closed-source, cloud-only, very limited features, higher pricing
- **vs Mini Numbers**: Simple Analytics wins on EU data hosting; Mini Numbers wins on features, self-hosted, no recurring costs

---

## Mini Numbers — competitive position

**Advantages**:
- Configurable hash rotation (unique, strongest privacy)
- Three privacy modes: STANDARD, STRICT, PARANOID
- Data retention policies with auto-purge
- Contribution calendar (unique visualization)
- Activity heatmap (rare feature)
- Custom event tracking with `MiniNumbers.track()` API
- Custom events breakdown with summary cards and progress bar visualization
- Conversion goals (URL and event-based) with conversion rate tracking
- Basic funnels with drop-off analysis and time between steps
- User segments with AND/OR visual filter builder
- API pagination, query caching, OpenAPI documentation
- Health check and metrics endpoints for production monitoring
- JVM/Kotlin stack (underserved market)
- Beautiful UI with dark mode, 6 chart types, loading skeletons, accessibility
- Enhanced demo data generator with auto-seeded goals, funnels, and segments
- Project management with delete confirmation from sidebar
- Comprehensive deployment documentation
- Production Dockerfile with JVM container tuning

**Disadvantages**:
- No community or ecosystem yet
- No cloud option

**Best For**: Privacy-conscious developers, JVM ecosystem users, open-source projects, EU businesses needing GDPR compliance, users wanting self-hosted with no recurring costs.

**Not Best For**: Users needing comprehensive features (Matomo/PostHog), non-technical users wanting cloud simplicity (Fathom/Simple Analytics), users needing extensive integrations.
