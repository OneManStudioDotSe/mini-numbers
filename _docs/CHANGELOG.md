# Changelog

All notable changes to Mini Numbers will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-03-05

### Added
- **Premium Setup Flow**: Animated "Constellation" background using HTML5 Canvas, glassmorphism UI, and streamlined step-by-step configuration.
- **Privacy Modes**: Introduced STANDARD, STRICT, and PARANOID modes to control data collection granularity.
- **Unified State Management**: Dashboard-wide handling for Loading (skeletons), Empty (helpful hints), and Error (retry mechanisms) states.
- **Enhanced Time Analysis**: Merged Heatmap and 365-day Calendar with a Zoom in/out feature and Peak Activity cards.
- **New Summary Cards**: Busiest Day, Peak Hour, and Top Month metrics added to the dashboard.
- **Dark Mode Support**: Full theme-awareness for the setup flow, login page, and background animations.
- **JWT Authentication**: Programmatic API access now supported alongside traditional session-based auth.

### Changed
- **Modular Routing Architecture**: Massive `Routing.kt` refactored into 7 feature-based modules under `se.onemanstudio.routing`.
- **Package Restructuring**: Unified project structure under the `se.onemanstudio` package for better convention compliance.
- **Dashboard Grid**: Standardized all primary and revenue stat cards into a consistent 4-column layout.
- **Icon Bar Charts**: Switched Referrers, Countries, and Custom Events to a premium proportional bar style.
- **Raw Events Viewer**: Improved data formatting with monospace paths, country flags, and intelligent timestamping.
- **Landing Page Separation**: Moved the landing page outside of the main source code for easier standalone deployment.

### Fixed
- **Tracker Leak**: Fixed a memory/network leak in `tracker.js` related to visibility change heartbeats.
- **401 Fallback**: Corrected authentication logic to properly fall back to `.env` credentials when the database is empty.
- **Config Sync**: Ensured `.env` changes are picked up immediately by the application without a restart.
- **Security**: Masked API keys for `VIEWER` roles to prevent unauthorized data spoofing.
- **CSP Headers**: Updated Content Security Policy to allow Google Fonts and trusted CDNs.
- **Static Analysis**: Resolved all `detekt` warnings, including line lengths, generic exceptions, and unused parameters.

## [1.0.0-beta] - 2026-02-27

### Added

- **Webhooks System**
  - Real-time HTTP POST notifications for goal conversions and traffic spikes
  - HMAC-SHA256 signed deliveries with configurable secret per webhook
  - Admin panel UI with CRUD, delivery log, test delivery, and setup guide
  - Webhook trigger engine with Caffeine-cached active webhooks per project
  - 3-attempt retry with exponential backoff for failed deliveries
  - OpenAPI documentation for all webhook endpoints

- **Email Reports**
  - Scheduled HTML analytics reports via SMTP (Daily / Weekly / Monthly)
  - SMTP service with Jakarta Mail, async delivery, and hourly scheduler
  - Customizable templates: subject line, header/footer text, section selection
  - Admin panel with SMTP status indicator, setup guide, and report management
  - Support for Gmail, SendGrid, Mailgun, Amazon SES, and self-hosted SMTP

- **Revenue Tracking & Attribution**
  - Revenue tracking via existing `MiniNumbers.track()` API with `revenue` property
  - Revenue aggregation engine: total revenue, AOV, revenue per visitor, with period comparison
  - Revenue by event breakdown (purchases, subscriptions, etc.)
  - Revenue attribution by referrer source and UTM campaign
  - Dashboard revenue section with stat cards, bar charts, and attribution table
  - In-dashboard guide with setup examples, framework integrations, and best practices
  - Demo data generator enhanced with realistic revenue events

- **Landing Page**
  - Integrated landing page served at `/landing/` via Ktor static resources
  - Root `/` redirects to landing page when services are ready
  - Particle canvas hero background with mouse-reactive animation
  - Gradient text effects and floating trust badges
  - Revenue Intelligence showcase section with animated mockup
  - Login/Dashboard link in navigation
  - Updated content: 288 tests, v1.0.0-beta version, 2026 copyright

### Changed

- Test suite expanded from 250 to 288 tests (18 new for webhooks, email, revenue)
- Demo data generator now produces revenue-bearing purchase events
- Root URL redirects to landing page instead of login
