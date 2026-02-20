# Mini Numbers - Gap Analysis & Recommendations

**Date**: February 20, 2026

---

## 1. Critical Priority (Before Any Launch) — RESOLVED

All critical security, foundation, and deployment documentation items have been implemented.

### 1.1 Security Hardening — COMPLETE
- Environment variable configuration system
- Configurable CORS (development vs production)
- Rate limiting (per IP and per API key)
- Input validation & sanitization
- Formal penetration testing still recommended

### 1.2 Comprehensive Testing — COMPLETE
- 166 tests (154 passing, 93% pass rate)
- Unit tests for security, parsing, validation, lifecycle, custom events, and analytics calculations
- Integration tests for data collection, setup wizard, admin endpoints, and health endpoint
- End-to-end tests for full tracking workflow

### 1.3 Production Configuration — Partially Complete
- Deployment documentation complete (JAR, Docker, reverse proxy, SSL, systemd, backups)
- GeoIP database bundled (classpath fallback for fat JAR deployments)
- Remaining: Dockerfile and docker-compose.yml in repository, health check and monitoring endpoints

### 1.4 Tracker Optimization — COMPLETE
- Tracker reduced to 1.9KB source, 1.3KB minified (was ~3.2KB)
- SPA detection switched from MutationObserver to History API patching
- Visibility-aware heartbeat (pauses when tab hidden)
- Gradle `minifyTracker` task for automated minification
- Bounce rate calculation and dashboard display
- Remaining: additional configuration options (heartbeat interval, disable SPA)

---

## 2. High Priority (Feature Parity with Competitors)

**Timeline**: 6-8 weeks

### 2.1 Custom Event Tracking — COMPLETE
- Name-based event tracking with `MiniNumbers.track("name")` API
- Event breakdown in dashboard (bar chart, hidden when no data)
- Validation: regex pattern, max 100 chars, required for custom type only
- Raw events filter and CSV export support
- 8 dedicated tests for custom event validation

### 2.2 Conversion Goals — COMPLETE
- URL-based and event-based goals with conversion rate tracking
- Goal management UI (create, toggle active/inactive, delete)
- Conversion rate cards with previous period comparison
- 5 API endpoints (CRUD + stats)

### 2.3 Basic Funnels — COMPLETE
- Multi-step conversion tracking with sequential step completion
- Drop-off analysis and horizontal funnel visualization
- Average time between steps analysis
- Funnel management modal with dynamic step builder
- 4 API endpoints (CRUD + analysis)

### 2.4 API Enhancements
- Pagination for all list endpoints
- Query result caching
- Standardized error responses
- API documentation (OpenAPI spec)

### 2.5 Email Reports
- Scheduled reports (daily, weekly, monthly)
- SMTP integration
- Report preview and test send

---

## 3. Medium Priority (Competitive Advantages)

**Timeline**: 12-16 weeks

- **Enhanced Privacy** — Configurable hash rotation, privacy mode levels, data retention policies
- **Performance Optimization** — Database indexing, query caching, GeoIP caching
- **User Segments** — Visual filter builder with AND/OR logic

---

## 4. Low Priority (Future Enhancements)

**Timeline**: 16+ weeks

- **Advanced Analytics** — User journey visualization, retention/cohort analysis
- **UI Enhancements** — Customizable dashboards, saved reports, annotations
- **Mobile** — PWA support, optional native apps
- **Enterprise** — Multi-user support with RBAC, SSO integration, white-labeling

---

## Summary

| Priority | Timeline | Status | Impact |
|----------|----------|--------|--------|
| **Critical** | Weeks 1-6 | Complete | Blocks launch |
| **High** | Weeks 7-14 | Custom events, goals, funnels done; API enhancements & email pending | Feature parity |
| **Medium** | Weeks 15-22 | Pending | Competitive edge |
| **Low** | Weeks 22+ | Pending | Future growth |
