---
title: Features
layout: default
nav_order: 2
---

# Features

Mini Numbers provides a robust set of web analytics features focused on performance, privacy, and actionable insights.

---

## 🛡️ Privacy by Design

Privacy isn't a checkbox; it's our architecture. Mini Numbers offers three distinct privacy modes to suit your needs:

- **Standard Mode**: Detailed Geolocation (City/Country) and Device detection. 100% cookie-free and GDPR-compliant.
- **Strict Mode**: Country-level location only. No city or region data is ever processed or stored.
- **Paranoid Mode**: Zero-PII. No location, device, or browser data. Only the page path and a rotating timestamp are recorded.

**Key Privacy Attributes:**
- **No Cookies**: We never store or read cookies from the visitor's browser.
- **Rotating Hashes**: Visitor IDs are hashed with a server salt and rotated every 24 hours.
- **No PII**: No IP addresses or personal data ever touch the database.

---

## 📊 Analytics Dashboard

A premium, unified dashboard that gives you a complete view of your site's health.

- **Real-time Feed**: See visitors as they arrive with a simulated "Live Feed" and active visitor count.
- **Unified Time Analysis**:
    - **Hourly Heatmap**: Visualize peak activity times throughout the week.
    - **Activity Calendar**: Track long-term trends across 365 days.
    - **Zoom Logic**: Effortlessly switch between period-specific and yearly views.
- **Peak Metrics**: Instant identification of your Busiest Day, Peak Hour, and Top Month.
- **Interactive Globe**: A 3D-style visualization of where your traffic is coming from.

---

## 🎯 Conversion & Growth

Go beyond page views to understand how your site performs as a business tool.

- **Conversion Goals**: Track key actions like signups, downloads, or specific URL arrivals.
- **Multi-step Funnels**: Visualize where users drop off in your onboarding or checkout flows.
- **User Segments**: Create meaningful groups (e.g., "Mobile Users", "US Traffic") to analyze behavior in isolation.
- **Revenue Tracking**: Attribute monetary value to events to calculate AOV (Average Order Value) and RPV (Revenue per Visitor).

---

## 🛠️ Developer & Admin Tools

Built for those who want control and flexibility.

- **Premium Setup Wizard**: A streamlined, animated onboarding flow that configures your server in minutes.
- **Modular API**: Full REST API support with both Session and JWT authentication.
- **Webhooks**: Send real-time event data to Slack, Discord, or your own custom backend.
- **Automated Email Reports**: Schedule daily, weekly, or monthly analytics summaries delivered to your inbox.
- **Raw Events Viewer**: A high-performance, paginated browser for inspecting every anonymized event in detail. The Path and Location columns are wider than usual to reduce truncation; filter dropdowns sit side-by-side for convenience.
- **One-Click Demo Data**: Instantly populate your dashboard with realistic sample data to test your setup. The button is automatically hidden once a demo project already exists.

---

## ⚡ Technical Excellence

- **Ultra-lightweight Tracker**: The `tracker.js` script is under 2KB, ensuring zero impact on your site's performance.
- **Single-Binary Deployment**: Self-host anywhere with a single Fat JAR or a minimal Docker image.
- **Multi-Database Support**: Use SQLite for simplicity or PostgreSQL for scale.
- **Zero-Restart Config**: Update your server settings via the UI without dropping a single visitor event.

## 🔔 Webhooks

Send real-time event data to any HTTPS endpoint the moment a goal is converted or a traffic spike is detected.

- Each project can have multiple webhooks
- Webhook secrets use HMAC-SHA256 signatures — verify payloads in your receiver
- 3-attempt retry with exponential backoff on delivery failure
- Inspect delivery history (last 50 attempts) and send test events from the dashboard
- See the [Dashboard Guide](dashboard-guide#%EF%B8%8F-project-settings) for setup instructions

---

## 📧 Email Reports

Schedule automated analytics summaries delivered directly to your inbox.

- Schedules: **Daily**, **Weekly**, or **Monthly**
- Configurable send hour and day
- Customizable subject line, header, and footer
- Choose which sections to include (page views, referrers, top pages, custom events, revenue, etc.)
- Requires SMTP configuration (see [Configuration](configuration))

---

## 💰 Revenue Tracking

Attribute monetary value to custom events to measure e-commerce and subscription performance.

- Attach `revenue` and `currency` to any `MiniNumbers.track()` call
- Dashboard shows **Total Revenue**, **Transactions**, **Average Order Value (AOV)**, and **Revenue per Visitor (RPV)**
- Revenue breakdown by event name
- Revenue attribution by referrer source and UTM campaign

```javascript
MiniNumbers.track("purchase", { revenue: 49.99, currency: "USD", product: "Pro Plan" });
```

---

## 👤 User Management / RBAC

Control who can access your analytics instance with role-based access control.

- **Admin** role: full access — create/delete projects, manage goals, rotate API keys
- **Viewer** role: read-only access to analytics data
- JWT authentication available as an alternative to session-based login (`POST /api/token`)
- Manage users via the admin panel or the `/admin/users` API

---

## 🧩 Embeddable Widgets

Embed live analytics counters on your own pages without exposing admin credentials.

- `GET /widget/realtime` — active visitors right now
- `GET /widget/pageviews` — total page views for a period
- `GET /widget/toppages` — top 5 pages by traffic
- `GET /widget/sparkline` — last-7-days trend data

Each widget endpoint accepts a public `key` parameter — no login required. See [Widgets](widgets) for full embed instructions.

---

## 🎨 Dashboard polish

- **Prominent date range**: The filter bar leads with the selected period in large, bold text — you always know exactly what you're looking at.
- **Contextual empty states**: Every chart, table, and list renders a purpose-matched illustration when there is no data to display, so the UI is always informative rather than blank.
- **Consistent hover feedback**: Table row hover uses a color visually distinct from both the default and zebra-stripe row colors, in both light and dark themes.
- **Light/dark theme toggle**: A pill-shaped toggle in the dashboard header switches themes instantly. Your preference is saved and respects your system's `prefers-color-scheme` on first visit.
