# Mini Numbers - TODO List

**Timeline**: 8-12 weeks to production launch
**Last Updated**: 2026-02-09

## Quick Priority Guide

- 🔴 **CRITICAL** - Must complete before any public release
- 🟡 **HIGH** - Essential for launch quality
- 🟢 **MEDIUM** - Production confidence
- 📚 **IMPORTANT** - User success
- 🎨 **POLISH** - Final touches
- 🚀 **LAUNCH** - Go public!

---

## 🔴 PHASE 1: CRITICAL SECURITY & FOUNDATION (Weeks 1-3)

**Must complete before any public release**

### 1.1 Security Hardening (Week 1)

- [ ] **Remove all hardcoded credentials**
  - Remove hardcoded admin username/password from `Security.kt`
  - Remove hardcoded server salt from `SecurityUtils.kt`
  - Remove hardcoded CORS `anyHost()` from `HTTP.kt`
  - Remove hardcoded tracker endpoint from `tracker.js`
  - Remove hardcoded database paths from `DatabaseFactory.kt`

- [ ] **Implement environment variable system**
  - Create `Config.kt` data class for all configuration
  - Support reading from environment variables
  - Support reading from `.env` file (using library like `dotenv-kotlin`)
  - Add validation for required variables
  - Throw clear errors if critical config missing

- [ ] **Add input validation & sanitization**
  - Validate all `/collect` endpoint inputs (path length, referrer length)
  - Sanitize user inputs to prevent injection
  - Add request size limits
  - Return proper 400 errors with validation messages

- [ ] **Implement rate limiting**
  - Add rate limiting middleware for `/collect` endpoint
  - Configurable: requests per minute per API key
  - Return 429 (Too Many Requests) when exceeded
  - Environment variables: `RATE_LIMIT_REQUESTS`, `RATE_LIMIT_WINDOW`

- [ ] **Fix CORS configuration**
  - Replace `anyHost()` with configurable allowed origins
  - Environment variable: `ALLOWED_ORIGINS` (comma-separated)
  - Default to empty list (reject all) if not set
  - Support wildcard for development: `ALLOWED_ORIGINS=*`

### 1.2 Interactive Setup Wizard (Weeks 2-3) - ⭐ KEY FEATURE

This is the game-changer for easy deployment!

- [ ] **Create setup wizard CLI application**
  - New file: `src/main/kotlin/setup/SetupWizard.kt`
  - Interactive CLI using library like `clikt` or `kotlinx-cli`
  - ASCII art welcome screen
  - Step-by-step questions with defaults
  - Validates inputs as you go

- [ ] **Wizard questions to ask:**
  1. Database type? (SQLite recommended for single-server, PostgreSQL for production)
  2. Database connection details? (skip if SQLite, local file path)
  3. Admin username? (default: admin)
  4. Admin password? (generate strong random password by default)
  5. Server port? (default: 8080)
  6. Allowed origins for CORS? (default: *, can add specific domains)
  7. GeoIP database path? (default: auto-download if not exists)
  8. Server salt? (auto-generate cryptographically secure random string)

- [ ] **Wizard outputs:**
  - Generate `.env` file with all configuration
  - Generate `docker-compose.yml` (if Docker selected)
  - Generate `application.yaml` (if needed)
  - Show "Next Steps" instructions
  - Display admin credentials prominently
  - Save to `config/` directory

- [ ] **Gradle task for wizard**
  ```bash
  ./gradlew setup
  # OR for fat JAR:
  java -jar mini-numbers.jar setup
  ```

- [ ] **Wizard features:**
  - ASCII progress bars
  - Color-coded output (success=green, warning=yellow, error=red)
  - Option to skip and use defaults: `./gradlew setup --quick`
  - Option to reconfigure: `./gradlew setup --reconfigure`
  - Save config to file and optionally load from existing

### 1.3 Environment Configuration System (Week 2)

- [ ] **Create Config data classes**
  - Create `src/main/kotlin/config/AppConfig.kt` with all config data classes
  - `AppConfig` (root), `DatabaseConfig`, `SecurityConfig`, `ServerConfig`, `GeoIPConfig`

- [ ] **Configuration loading priority**
  1. Environment variables (highest priority)
  2. `.env` file in current directory
  3. `config/.env` file
  4. Fail with clear error if required values missing

- [ ] **Add `.env.example` file**
  - Template with all available options
  - Comments explaining each variable
  - Safe defaults where applicable
  - Clear warnings about security-sensitive values

---

## 🟡 PHASE 2: EASY DEPLOYMENT (Weeks 3-5)

**Make deployment as simple as possible**

### 2.1 Docker Optimization (Week 3)

- [ ] **Create production Dockerfile**
  - Multi-stage build (build stage + runtime stage)
  - Based on `eclipse-temurin:21-jre-alpine` (small image)
  - Include GeoIP database in image OR download on startup
  - Non-root user for security
  - Health check support
  - Minimal layers for size optimization

- [ ] **Create docker-compose.yml**
  - Single-command deployment: `docker-compose up -d`
  - PostgreSQL service (optional, commented out by default)
  - SQLite by default (simpler for most users)
  - Volume mounts for data persistence
  - Volume mount for config
  - Environment variable support
  - Restart policies
  - Example with reverse proxy (Caddy) for HTTPS

- [ ] **Docker best practices**
  - `.dockerignore` file
  - Layer caching optimization
  - Build argument for version
  - Labels with metadata (version, commit, build date)
  - Multi-platform builds (amd64, arm64)

- [ ] **Docker Hub setup**
  - Create Docker Hub account/repository
  - Automated builds via GitHub Actions
  - Version tagging: `latest`, `v1.0.0`, `v1.0`, `v1`
  - README on Docker Hub with quick start

### 2.2 One-Click Platform Deployments (Week 4)

Target: Railway, Render, Fly.io - platforms where users just click a button!

- [ ] **Railway deployment**
  - Create `railway.toml` configuration
  - Add "Deploy on Railway" button to README
  - Pre-configure environment variables template
  - Automatically provision PostgreSQL
  - Setup wizard runs on first deployment
  - Documentation: `docs/deploy-railway.md`

- [ ] **Render deployment**
  - Create `render.yaml` blueprint
  - Add "Deploy to Render" button to README
  - Web service + PostgreSQL database
  - Environment variables pre-configured
  - Free tier compatible
  - Documentation: `docs/deploy-render.md`

- [ ] **Fly.io deployment**
  - Create `fly.toml` configuration
  - Add deployment instructions to README
  - Volume for SQLite persistence
  - Multi-region support (optional)
  - Documentation: `docs/deploy-flyio.md`

- [ ] **One-click deployment features**
  - Each platform gets dedicated guide
  - Screenshots of deployment process
  - Expected costs (Railway free tier, Render $7/mo, etc.)
  - Troubleshooting section
  - Migration guides between platforms

### 2.3 GeoIP Database Auto-Download (Week 4)

Remove manual download requirement!

- [ ] **Implement auto-download on startup**
  - Check if GeoIP database exists at configured path
  - If missing, show warning and offer to download
  - Download from MaxMind (requires free license key) OR use alternative free source
  - Extract and place in correct location
  - Verify integrity (checksum)
  - Log success/failure clearly

- [ ] **Environment variables**
  - `GEOIP_AUTO_DOWNLOAD=true` (default)
  - `GEOIP_LICENSE_KEY=<maxmind_key>` (optional, for MaxMind)
  - `GEOIP_DATABASE_PATH=./data/geolite2-city.mmdb`

- [ ] **Fallback strategy**
  - If download fails, continue without geolocation
  - Log warning but don't crash
  - Analytics work without geo data (just no country/city)

### 2.4 Tracker Configuration (Week 4)

Make tracker endpoint configurable at build time or runtime!

- [ ] **Option 1: Build-time configuration**
  - Gradle task: `./gradlew buildTracker -Pendpoint=https://analytics.example.com`
  - Replaces placeholder in tracker.js
  - Generates `tracker-configured.js`
  - Include instructions in docs

- [ ] **Option 2: Runtime configuration (better!)**
  - Tracker reads endpoint from `data-api-endpoint` attribute
  - Defaults to same origin if not specified
  - No build step needed!

- [ ] **Optimize tracker size (<2KB)**
  - Remove comments and whitespace
  - Minify variable names
  - Use terser or similar minifier
  - Automated in build process
  - Source maps for debugging
  - Measure final size, aim for <2KB

---

## 🟢 PHASE 3: TESTING & QUALITY (Weeks 5-7)

**Ensure production reliability**

### 3.1 Comprehensive Test Suite (Weeks 5-6)

- [ ] **Unit tests (Target: 80% coverage)**
  - `SecurityUtils.generateVisitorHash()` - various inputs, daily rotation
  - `UserAgentParser.parse*()` - known user agents, edge cases
  - `DataAnalysisUtils` time period functions - edge cases
  - Configuration loading and validation
  - Setup wizard logic

- [ ] **Integration tests**
  - `POST /collect` - valid/invalid payloads, missing API keys
  - Authentication - valid/invalid credentials
  - Rate limiting - exceed limits, verify 429 responses
  - CORS - allowed/disallowed origins
  - All admin endpoints - CRUD operations
  - Database transactions and rollbacks

- [ ] **Security tests**
  - SQL injection attempts
  - XSS payload attempts
  - CSRF protection
  - Authentication bypass attempts
  - Rate limiting enforcement
  - Input validation edge cases
  - Large payload handling

- [ ] **End-to-end tests**
  - Full tracking workflow: tracker.js → /collect → database → dashboard
  - Project CRUD operations
  - Dashboard data visualization
  - CSV export functionality
  - Filter and search operations
  - Theme switching
  - Live feed updates

- [ ] **Setup wizard tests**
  - Test all input combinations
  - Test generated configs are valid
  - Test validation logic
  - Test default values

- [ ] **Docker tests**
  - Build Docker image successfully
  - Container starts and responds to health checks
  - Environment variables passed correctly
  - Volume mounts work for data persistence
  - docker-compose.yml works end-to-end

### 3.2 CI/CD Pipeline (Week 6)

- [ ] **GitHub Actions workflows**
  - **Test workflow** (`.github/workflows/test.yml`)
    - Run on every push and PR
    - Build project
    - Run all tests
    - Generate coverage report
    - Upload coverage to Codecov
  - **Docker build workflow** (`.github/workflows/docker.yml`)
    - Build Docker image on main branch
    - Push to Docker Hub with version tags
    - Multi-platform builds (amd64, arm64)
    - Cache layers for speed
  - **Release workflow** (`.github/workflows/release.yml`)
    - Trigger on version tag (v1.0.0)
    - Build fat JAR
    - Create GitHub release
    - Attach JAR as asset
    - Generate release notes automatically

- [ ] **Code quality checks**
  - ktlint for code formatting
  - detekt for static analysis
  - Dependency vulnerability scanning
  - License compliance check

### 3.3 Monitoring & Observability (Week 7)

- [ ] **Health check endpoint**
  - `GET /health` - returns 200 if healthy
  - Checks database connectivity
  - Checks GeoIP database loaded
  - Returns JSON with status details

- [ ] **Metrics endpoint** (optional)
  - `GET /metrics` - Prometheus format
  - Request counts by endpoint
  - Response times
  - Error rates
  - Database connection pool stats

- [ ] **Structured logging**
  - JSON logging format
  - Correlation IDs for request tracing
  - Log levels configurable via environment
  - Sensitive data redaction (passwords, API keys)

---

## 📚 PHASE 4: DOCUMENTATION (Weeks 7-8)

**Excellent documentation is critical for adoption**

### 4.1 Core Documentation (Week 7)

- [ ] **README.md** (update existing)
  - Eye-catching hero section with screenshots
  - Key features highlighted
  - "Quick Start" section (3 steps max)
  - Deploy buttons for Railway, Render, Fly.io
  - Link to full documentation
  - Badges: build status, test coverage, Docker pulls, license

- [ ] **INSTALLATION.md**
  - Method 1: Interactive Setup Wizard (recommended)
  - Method 2: Docker Compose
  - Method 3: Binary (JAR)
  - Method 4: Build from source

- [ ] **CONFIGURATION.md**
  - Complete environment variable reference
  - Configuration file format (`.env`)
  - All available options with descriptions
  - Security considerations
  - Examples for common scenarios

- [ ] **DEPLOYMENT.md**
  - Production checklist
  - Reverse proxy setup (Nginx, Caddy, Traefik)
  - HTTPS/SSL setup with Let's Encrypt
  - Database backup strategies
  - Monitoring setup
  - Upgrading between versions
  - Troubleshooting common issues

### 4.2 User Guides (Week 8)

- [ ] **TRACKING.md**
  - How to add tracker to website
  - Configuration options
  - Testing tracking is working
  - Debugging common issues
  - SPA integration examples

- [ ] **DASHBOARD.md**
  - Dashboard overview tour
  - Understanding metrics
  - Using filters and segments
  - Exporting data
  - Interpreting heatmaps and calendars

- [ ] **API.md**
  - API endpoint documentation
  - Authentication
  - Rate limiting
  - Request/response examples
  - Error codes
  - Code examples (curl, JavaScript, Python)

- [ ] **PRIVACY.md**
  - Privacy architecture explanation
  - Daily hash rotation explained
  - GDPR compliance
  - What data is collected
  - What data is NOT collected
  - Data retention policies
  - User rights (data deletion, etc.)

### 4.3 Community & Contribution (Week 8)

- [ ] **CONTRIBUTING.md**
  - How to contribute (code, docs, issues)
  - Development setup
  - Running tests
  - Code style guidelines
  - PR process
  - Code of conduct

- [ ] **LICENSE**
  - Choose license: **MIT recommended** (permissive, business-friendly)
  - Add LICENSE file to repository

- [ ] **CHANGELOG.md**
  - Version history
  - Format: Keep a Changelog standard
  - Sections: Added, Changed, Deprecated, Removed, Fixed, Security

- [ ] **CODE_OF_CONDUCT.md**
  - Contributor Covenant or similar
  - Sets expectations for community behavior

---

## 🎨 PHASE 5: POLISH & LAUNCH PREP (Weeks 8-10)

**Final touches before public release**

### 5.1 UI/UX Improvements (Week 8)

- [ ] **Fix loading states**
  - Implement skeleton screens
  - Loading spinners during data fetch
  - Disable buttons during operations
  - Show progress for long operations

- [ ] **Error handling improvements**
  - User-friendly error messages
  - Retry buttons for failed operations
  - Offline detection and messaging
  - Network error recovery

- [ ] **Accessibility improvements**
  - Add missing ARIA labels
  - Keyboard navigation
  - Focus management
  - Color contrast compliance (WCAG AA)
  - Screen reader testing

- [ ] **Empty states**
  - Nice designs for "no data yet"
  - Clear next steps / call to action
  - Helpful illustrations or icons

### 5.2 First-Time User Experience (Week 9)

- [ ] **Onboarding flow**
  - Welcome screen after first login
  - Quick tour of dashboard features
  - "Add your first project" wizard
  - Copy-paste tracking script
  - Verify tracking is working
  - Success confirmation

- [ ] **Sample data option**
  - "Load sample data" button
  - Generates realistic demo events
  - Helps users understand analytics
  - Can be cleared easily

- [ ] **Getting started guide in-app**
  - Checklist widget in dashboard
  - Step 1: Create project ✓
  - Step 2: Add tracking script
  - Step 3: Verify first pageview
  - Step 4: Explore features
  - Dismissable when done

### 5.3 Performance Optimization (Week 9)

- [ ] **Database query optimization**
  - Add missing indexes (path, browser, os, device, country)
  - Optimize N+1 queries
  - Use database-level aggregations
  - Query result caching (Redis optional)

- [ ] **Frontend optimization**
  - Lazy load charts (only visible sections)
  - Debounce filter operations
  - Virtualize long tables
  - Optimize bundle size
  - Add service worker for offline capability

- [ ] **GeoIP caching**
  - In-memory LRU cache for lookups
  - Cache IP range results (CIDR blocks)
  - Configurable cache size

### 5.4 Final Testing & Security Review (Week 10)

- [ ] **Manual testing checklist**
  - Test setup wizard on fresh system
  - Test each deployment method (Docker, Railway, Render, Fly.io)
  - Test all dashboard features
  - Test on multiple browsers (Chrome, Firefox, Safari)
  - Test mobile responsive design
  - Test with large dataset (10k+ events)
  - Test error scenarios (database down, network issues)

- [ ] **Security audit**
  - Run OWASP ZAP or similar security scanner
  - Dependency vulnerability scan
  - Review all environment variable usage
  - Verify no secrets in code or logs
  - Test rate limiting effectiveness
  - Test CORS restrictions
  - Penetration testing (if resources available)

- [ ] **Performance benchmarking**
  - Load testing with Apache Bench or k6
  - Measure response times under load
  - Measure resource usage (CPU, memory)
  - Identify bottlenecks
  - Document performance characteristics

---

## 🚀 PHASE 6: LAUNCH (Weeks 10-12)

**Go public!**

### 6.1 Pre-Launch (Week 10)

- [ ] **Create marketing materials**
  - Screenshots of dashboard (light + dark mode)
  - GIF demos of key features
  - Comparison table vs competitors (from evaluation doc)
  - Feature highlight graphics
  - Logo and branding

- [ ] **Set up project infrastructure**
  - GitHub repository (public)
  - GitHub Issues templates (bug, feature request)
  - GitHub Discussions enabled
  - GitHub Sponsors / Open Collective (optional)
  - Social media accounts (optional: Twitter, Mastodon)
  - Website / landing page (optional but helpful)

- [ ] **Write launch content**
  - Blog post: "Introducing Mini Numbers"
  - Hacker News "Show HN" post draft
  - Reddit posts for r/selfhosted, r/privacy, r/kotlin
  - Dev.to article
  - Product Hunt submission

### 6.2 Launch Day (Week 11)

- [ ] **GitHub release**
  - Tag version v1.0.0
  - Create GitHub release
  - Attach fat JAR
  - Write comprehensive release notes
  - Include quick start guide in release

- [ ] **Announce on platforms**
  - Hacker News: "Show HN: Mini Numbers - Privacy-first web analytics"
  - Reddit: r/selfhosted, r/privacy, r/kotlin, r/webdev
  - Product Hunt: Launch product
  - Dev.to: Publish article
  - Lobste.rs (if invited)
  - Kotlin Weekly newsletter submission

- [ ] **Monitor feedback**
  - Watch GitHub issues
  - Respond to HN/Reddit comments
  - Track Product Hunt votes
  - Note feature requests
  - Fix critical bugs immediately

### 6.3 Post-Launch (Week 12)

- [ ] **Address initial feedback**
  - Fix reported bugs
  - Improve unclear documentation
  - Add requested features to roadmap
  - Thank contributors and supporters

- [ ] **Content marketing**
  - Comparison posts: "Mini Numbers vs Umami"
  - Tutorial: "How to self-host your analytics"
  - Privacy post: "Why daily hash rotation matters"
  - Technical deep-dive: "Architecture of Mini Numbers"

- [ ] **Community building**
  - Set up Discord or GitHub Discussions
  - Welcome first contributors
  - Feature user deployments (with permission)
  - Collect testimonials

---

## 🎯 PHASE 7: POST-LAUNCH FEATURES (Weeks 12+)

**Features for v1.1, v1.2, etc. - based on user feedback**

### 7.1 High-Priority Features (Next 3 months)

Based on [PROJECT_STATUS.md](PROJECT_STATUS.md) and [COMPETITIVE_ANALYSIS.md](COMPETITIVE_ANALYSIS.md) recommendations:

- [ ] **Custom event tracking** (3 weeks)
  - Event name field in database
  - Event properties (JSON)
  - Tracker API: `window.miniNumbers.track('button_click', {button: 'signup'})`
  - Event breakdown in dashboard
  - Documentation and examples

- [ ] **Conversion goals** (2 weeks)
  - Goals management UI
  - URL-based goals
  - Event-based goals
  - Conversion rate tracking
  - Goal visualization in dashboard

- [ ] **Basic funnels** (3 weeks)
  - Funnel definition UI
  - Multi-step conversion tracking
  - Drop-off analysis
  - Visualization with percentages

- [ ] **Email reports** (2 weeks)
  - SMTP configuration
  - Report templates
  - Scheduling (daily, weekly, monthly)
  - Configuration UI

- [ ] **Webhooks** (2 weeks)
  - Webhook management UI
  - Delivery system with retries
  - Event filtering
  - HMAC signatures

### 7.2 Nice-to-Have Features (6+ months)

- [ ] Retention analysis
- [ ] Cohort analysis
- [ ] User journey visualization
- [ ] A/B test result tracking
- [ ] Multi-user support with RBAC
- [ ] Mobile PWA
- [ ] Plugin system
- [ ] Integrations (Slack, Discord, Zapier)

---

## Progress Tracking

**Current Phase**: Phase 1 - Critical Security & Foundation
**Week**: 1 of 12
**Overall Progress**: 0% (just starting!)

### Quick Stats
- Total Tasks: 150+
- Phase 1 (Critical): 9 major tasks
- Completed: 0
- In Progress: 0
- Blocked: 0

---

## Key Files to Create/Modify

### New Files Needed
- `src/main/kotlin/setup/SetupWizard.kt`
- `src/main/kotlin/config/AppConfig.kt`
- `src/main/kotlin/config/ConfigLoader.kt`
- `.env.example`
- `Dockerfile`
- `docker-compose.yml`
- `.dockerignore`
- Platform configs: `railway.toml`, `render.yaml`, `fly.toml`
- GitHub Actions workflows
- Documentation files (see Phase 4)

### Files to Modify
- `src/main/kotlin/core/Security.kt`
- `src/main/kotlin/core/HTTP.kt`
- `src/main/kotlin/utils/SecurityUtils.kt`
- `src/main/kotlin/db/DatabaseFactory.kt`
- `src/main/kotlin/services/GeoLocationService.kt`
- `src/main/kotlin/Application.kt`
- `src/main/kotlin/Routing.kt`
- `src/main/resources/static/tracker.js`
- `build.gradle.kts`
- `README.md`

---

## Notes

- Refer to [PROJECT_STATUS.md](PROJECT_STATUS.md) for current state and completion details
- See [COMPETITIVE_ANALYSIS.md](COMPETITIVE_ANALYSIS.md) for how we compare to competitors
- Check [ROADMAP.md](ROADMAP.md) for long-term vision and milestones
- Review [GAP_ANALYSIS.md](GAP_ANALYSIS.md) for detailed missing features and recommendations
