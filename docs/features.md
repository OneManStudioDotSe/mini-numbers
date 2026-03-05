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
- **Raw Events Viewer**: A high-performance, paginated browser for inspecting every anonymized event in detail.
- **One-Click Demo Data**: Instantly populate your dashboard with realistic sample data to test your setup.

---

## ⚡ Technical Excellence

- **Ultra-lightweight Tracker**: The `tracker.js` script is under 2KB, ensuring zero impact on your site's performance.
- **Single-Binary Deployment**: Self-host anywhere with a single Fat JAR or a minimal Docker image.
- **Multi-Database Support**: Use SQLite for simplicity or PostgreSQL for scale.
- **Zero-Restart Config**: Update your server settings via the UI without dropping a single visitor event.
