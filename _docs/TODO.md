# Mini Numbers - TODO List

**Last Updated**: February 20, 2026

---

## Priority Guide

- **CRITICAL** — Must complete before any public release
- **HIGH** — Essential for launch quality
- **MEDIUM** — Production confidence
- **POLISH** — Final touches

---

## Phase 1: Security & Foundation — COMPLETED

- [x] Remove all hardcoded credentials
- [x] Environment variable system with `.env` support
- [x] Input validation and sanitization
- [x] Rate limiting (per IP and per API key)
- [x] Configurable CORS
- [x] Interactive setup wizard (WordPress-style, zero-restart)
- [x] Demo data generator
- [x] Session-based authentication with login page
- [x] Comprehensive test suite (111 tests)
- [x] Code architecture restructuring (package-per-feature)
- [x] Configurable tracker endpoint
- [x] Bounce rate calculation and dashboard display
- [x] Tracker optimization (1.9KB source, 1.3KB minified, History API SPA detection, visibility-aware heartbeat)
- [x] Gradle minification task (`minifyTracker`)
- [x] Realistic demo data generator (multi-event sessions, bounce/engaged mix)
- [x] Custom event tracking with `MiniNumbers.track()` API and dashboard visualization
- [x] GeoIP database bundling (classpath fallback for fat JAR deployments)
- [x] Comprehensive deployment documentation (JAR, Docker, reverse proxy, SSL, backups)

---

## Phase 2: Easy Deployment (HIGH)

### Docker Optimization
- [ ] Production Dockerfile in repo (multi-stage build, Alpine-based, non-root user)
- [ ] docker-compose.yml in repo (single-command deployment, volume mounts)
- [ ] Docker Hub automated builds with version tagging
- [ ] Multi-platform builds (amd64, arm64)

### One-Click Platform Deployments
- [ ] Railway deployment (button + guide)
- [ ] Render deployment (blueprint + guide)
- [ ] Fly.io deployment (config + guide)

### Completed
- [x] GeoIP database bundled — works from filesystem and classpath (fat JAR)
- [x] Tracker size optimization (<2KB minified) — 1.9KB source, 1.3KB minified
- [x] Deployment documentation (JAR, Docker, reverse proxy, SSL, systemd, backups)

---

## Phase 3: Testing & Quality (MEDIUM)

### Additional Tests
- [ ] Analytics calculation tests
- [ ] Admin endpoint integration tests
- [ ] End-to-end tests (full tracking workflow)
- [ ] Docker build and deployment tests

### CI/CD Pipeline
- [ ] GitHub Actions: test, Docker build, release workflows
- [ ] Code quality checks (linting, static analysis, vulnerability scanning)

### Monitoring
- [ ] Health check endpoint
- [ ] Metrics endpoint (Prometheus format)
- [ ] Structured JSON logging

---

## Phase 4: Documentation (HIGH) — Partially Complete

- [x] Installation guide (Docker, JAR, source) — in DEPLOYMENT.md
- [x] Configuration reference (all environment variables) — in DEPLOYMENT.md
- [x] Deployment guide (reverse proxy, SSL, backups, upgrading) — in DEPLOYMENT.md
- [x] Tracking integration guide (setup, SPA, custom events) — in DEPLOYMENT.md
- [ ] Dashboard user guide
- [ ] API documentation
- [ ] Privacy architecture explanation
- [ ] Contributing guidelines and code of conduct
- [ ] License selection (MIT recommended)

---

## Phase 5: Polish & Launch Prep (POLISH)

### UI/UX
- [ ] Loading states and skeleton screens
- [ ] Error handling improvements
- [ ] Accessibility (ARIA labels, keyboard navigation, contrast)
- [ ] Empty state designs

### First-Time Experience
- [ ] Onboarding flow after first login
- [ ] In-app getting started checklist

### Performance
- [ ] Database query optimization (additional indexes, caching)
- [ ] Frontend optimization (lazy loading, debouncing)
- [ ] GeoIP lookup caching

### Final Review
- [ ] Manual testing across browsers and devices
- [ ] Security audit
- [ ] Performance benchmarking

---

## Phase 6: Launch

### Pre-Launch
- [ ] Screenshots and demo GIFs
- [ ] GitHub repository setup (public, issue templates, discussions)
- [ ] Launch content (blog post, Show HN, Reddit, Product Hunt)

### Launch Day
- [ ] GitHub release v1.0.0
- [ ] Announce on Hacker News, Reddit, Product Hunt, Dev.to
- [ ] Monitor and respond to feedback

### Post-Launch
- [ ] Address initial feedback and bugs
- [ ] Comparison and tutorial content
- [ ] Community building (Discord or GitHub Discussions)

---

## Phase 7: Post-Launch Features

### High Priority (Next 3 months)
- [x] Custom event tracking — name-based tracking with `MiniNumbers.track()` API and dashboard card
- [ ] Conversion goals
- [ ] Basic funnels
- [ ] Email reports
- [ ] Webhooks

### Future (6+ months)
- [ ] Retention and cohort analysis
- [ ] User journey visualization
- [ ] Multi-user support with roles
- [ ] Mobile PWA
- [ ] Plugin system
- [ ] Integrations (Slack, Discord, Zapier)

---

## Progress

| Phase | Status |
|-------|--------|
| Phase 1 (Security & Foundation) | COMPLETED |
| Phase 2 (Easy Deployment) | In Progress (docs done, Docker pending) |
| Phase 3 (Testing & Quality) | Partially done (111 tests) |
| Phase 4 (Documentation) | Partially done (deployment docs complete) |
| Phase 5-7 | Pending |
