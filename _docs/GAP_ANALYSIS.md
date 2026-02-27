# Mini Numbers - Gap analysis & recommendations

**Date**: February 27, 2026

---

## 1. Critical priority — COMPLETE

All critical security, foundation, and deployment documentation items have been implemented.

### 1.1 Security hardening — COMPLETE
- Environment variable configuration system
- Configurable CORS (development vs production)
- Rate limiting (per IP and per API key)
- Input validation & sanitization
- Formal penetration testing still recommended

### 1.2 Comprehensive testing — COMPLETE
- 288 tests (all passing, 100% pass rate)
- Unit tests for security, parsing, validation, lifecycle, custom events, and analytics calculations
- Integration tests for data collection, setup wizard, admin endpoints, and health endpoint
- End-to-end tests for full tracking workflow

### 1.3 Production configuration — COMPLETE
- Deployment documentation complete (JAR, Docker, reverse proxy, SSL, systemd, backups)
- GeoIP database bundled (classpath fallback for fat JAR deployments)
- Production Dockerfile with multi-stage build, Alpine runtime, JVM container tuning
- docker-compose.yml variants for SQLite and PostgreSQL
- Health check endpoint (`GET /health`) with uptime, version, and service state
- Metrics endpoint (`GET /metrics`) with event counts, cache stats, and privacy config

### 1.4 Tracker optimization — COMPLETE
- Tracker reduced to 1.9KB source, 1.3KB minified (was ~3.2KB)
- SPA detection switched from MutationObserver to History API patching
- Visibility-aware heartbeat (pauses when tab hidden)
- Bounce rate calculation and dashboard display
- Configurable heartbeat interval via `data-heartbeat-interval` attribute
- SPA tracking can be disabled via `data-disable-spa="true"` attribute
- Server-side tracker config endpoint (`GET /tracker/config`)

---

## 2. High priority (feature parity with competitors)

### 2.1 Custom event tracking — COMPLETE
- Name-based event tracking with `MiniNumbers.track("name")` API
- Event breakdown in dashboard (summary cards, breakdown list with progress bars, bar chart, hidden when no data)
- Raw events filter and CSV export support

### 2.2 Conversion goals — COMPLETE
- URL-based and event-based goals with conversion rate tracking
- Goal management UI (create, toggle active/inactive, delete)
- Conversion rate cards with previous period comparison

### 2.3 Basic funnels — COMPLETE
- Multi-step conversion tracking with sequential step completion
- Drop-off analysis and horizontal funnel visualization
- Average time between steps analysis
- Funnel management modal with dynamic step builder

### 2.4 API enhancements — COMPLETE
- Pagination for all list endpoints (`?page=&limit=` query parameters, backward compatible)
- Query result caching with Caffeine (30-second TTL, 500 max entries, auto-invalidation)
- Standardized error responses (`ApiError` model with error, code, and details fields)
- OpenAPI 3.0.3 specification (`/admin-panel/openapi.yaml`) documenting all endpoints

### 2.5 Email reports — COMPLETE
- Scheduled reports (daily, weekly, monthly) with configurable send hour and day
- SMTP integration via Jakarta Mail (Gmail, SendGrid, Mailgun, Amazon SES, self-hosted)
- Report preview and test send
- Customizable templates (subject, header, footer, section selection)
- Admin panel with SMTP status indicator, setup guide, and report management

### 2.6 Webhooks — COMPLETE
- Real-time HTTP POST notifications for goal conversions and traffic spikes
- HMAC-SHA256 signed deliveries with per-webhook secrets
- Admin panel with CRUD, delivery log, test delivery, and setup guide
- Integration recipes for Slack, Discord, Zapier, and custom servers

### 2.7 Revenue Tracking — COMPLETE
- Revenue tracking via `MiniNumbers.track("purchase", { revenue: 49.99 })` API
- Revenue aggregation: total, AOV, revenue per visitor, with period comparison
- Revenue attribution by referrer source and UTM campaign
- Dashboard section with stat cards, breakdown bars, and attribution table

---

## 3. Medium priority (competitive advantages) — COMPLETE

- **Enhanced Privacy** — COMPLETE: Configurable hash rotation (1-8760 hours), three privacy modes (STANDARD, STRICT, PARANOID), data retention policies with auto-purge
- **Performance Optimization** — COMPLETE: 8 database indexes, Caffeine query cache (500 entries, 30s TTL), GeoIP lookup cache (10K entries, 1h TTL)
- **User Segments** — COMPLETE: Visual filter builder with AND/OR logic, segment analysis, CRUD API

---

## 4. Low priority (future enhancements)

- **Advanced Analytics** — User journey visualization, retention/cohort analysis
- **UI Enhancements** — Customizable dashboards, saved reports, annotations
- **Mobile** — PWA support, optional native apps
- **Enterprise** — Multi-user support with RBAC, SSO integration, white-labeling

---

## Summary

| Priority | Timeline | Status | Impact |
|----------|----------|--------|--------|
| **Critical** | Weeks 1-6 | Complete | Blocks launch |
| **High** | Weeks 7-14 | Complete | Feature parity |
| **Medium** | Weeks 15-22 | Complete | Competitive edge |
| **Low** | Weeks 22+ | Pending | Future growth |
