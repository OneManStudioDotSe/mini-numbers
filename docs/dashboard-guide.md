---
title: Dashboard Guide
layout: default
nav_order: 4
---

# Dashboard Guide

A walkthrough of the Mini Numbers dashboard and how to interpret your analytics data.

---

## 🚀 Getting Started

When you first log in, you'll see your **Project List** in the sidebar. Select a project to load its "Live" dashboard.

**Pro Tip:** Use the **"Generate Demo Data"** button in the page header (visible when a project is selected) to populate your dashboard with realistic sample traffic instantly. The button is hidden automatically once a Demo project already exists in your list.

---

## 🗓️ Filter bar

Just below the page header sits the **unified filter bar**. Its two-part layout makes the current context immediately clear:

- **Left — selected period**: Displayed in large, bold text with a primary-coloured calendar icon (e.g., "Feb 8 – Mar 8, 2026"). You can't miss it.
- **Right — dimension filter**: The "Filter by" dropdown (Browser, OS, Device, Country, Referrer) is anchored to the far right of the bar. When a dimension is selected, a value picker and a "Clear" button appear next to it.

The time period itself is selected from the **time filter** dropdown in the main page header (24h / 3d / 7d / 30d / 365d).

---

## ⏲️ Real-time Insights

The top of your dashboard focuses on what's happening **right now**.

- **Active Visitors**: The number of unique people who have interacted with your site in the last 5 minutes.
- **Live Feed**: A streaming list of recent events.
    - **Path**: The exact page they are viewing (e.g., `/blog/hello-world`).
    - **Location**: Country flag and city/region of the visitor.
    - **Time**: How long ago the event occurred (e.g., "just now", "2m ago").

---

## 📈 Overview & Trends

The primary stat cards give you a high-level health check:

| Metric | Description |
| :--- | :--- |
| **Page Views** | Every time a page is loaded or changed (SPA). |
| **Unique Visitors** | Distinct people (anonymized) visiting your site. |
| **Sessions** | Total number of distinct visits (sessions end after 30m of inactivity). |
| **Bounce Rate** | % of visitors who left after viewing only one page. |
| **Avg. Duration** | How much time people spend on your site per session. |
| **Conversion Rate** | % of sessions that completed at least one **Conversion Goal**. |

---

## 🕒 Time Patterns

Understanding *when* your audience is active is crucial for timing content or maintenance.

- **Peak Performance Cards**:
    - **Busiest Day**: The day of the week with the most overall traffic.
    - **Peak Hour**: The specific hour of the day when you see the most activity.
    - **Top Month**: Your most active month in the current yearly cycle.
- **Time Analysis (Zoomable)**:
    - **Hourly Heatmap**: A detailed view of activity by day/hour for your selected filter period.
    - **Activity Calendar**: A broad "GitHub-style" heatmap showing the last 365 days.
    - **Switching**: Use the 🔍 **Zoom in/out** toggle to jump between detailed and yearly views.

---

## 🗺️ Geographic Distribution

Mini Numbers identifies where your traffic comes from without storing personal data.

- **Interactive Globe**: See a 3D-style visualization of visitor density across the world.
- **Drill-down Table**:
    - Click any **Country** in the list to see a detailed breakdown of **Regions and Cities** for that specific country.
    - Click the **Back Arrow** in the card header to return to the global view.
    - All bars are **proportional** to total traffic, making relative impact easy to see.

---

## 🎯 Features & Advanced Tracking

- **Content & Pages**: See which specific URLs are your most popular.
- **Referrers**: Identify which external sites (Google, Twitter, GitHub) are sending you traffic.
- **UTM Campaigns**: Separate cards for **Sources**, **Mediums**, and **Campaigns** let you track the ROI of your marketing efforts.
- **Events & Revenue**:
    - Track custom actions like `signup` or `download`.
    - **Revenue Tracking**: View your Total Revenue, Average Order Value (AOV), and Revenue per Visitor (RPV) if you've implemented monetary tracking.

---

## 🔍 Raw Events Viewer

Click **Raw Events** in the header (visible when a project is selected) to open a full-screen paginated view of every recorded event.

- Two filter dropdowns (event type and sort order) sit side-by-side at the top
- The **Path** column is intentionally wide to accommodate long URLs; the **Location** column is wider than typical to show country/city without truncation
- Use the download icon to export everything as CSV

---

## ⚙️ Project Settings

Click the **Gear Icon** in the page header to:
- Rename your project or view your Tracking ID.
- **Rotate API Key**: Click the **Rotate key** button next to your Tracking ID to generate a new API key immediately. The old key stops working right away — update your tracker `<script>` tag with the new key to resume tracking.
- **2-Column Layout**: Configure dashboard preferences (Date/Time formats) and Export settings side-by-side.
- **Automations**: Access Webhook management and Scheduled Email Reports.
- **CSV Export**: Select exactly which dimensions you want to include in your data export.
