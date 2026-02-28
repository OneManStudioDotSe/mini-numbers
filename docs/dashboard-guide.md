---
title: Dashboard Guide
layout: default
nav_order: 5
---

# Dashboard Guide

A walkthrough of the Mini Numbers analytics dashboard — what everything means and how to use it.

---

## Logging in

Navigate to your Mini Numbers server URL and log in with your admin username and password. After logging in, you'll land on the main dashboard.

---

## Dashboard layout

The dashboard is split into three areas:

- **Sidebar (left)** — Project list, navigation, and project management
- **Top bar** — Time filter, live feed toggle, and theme switch
- **Main area** — Charts, metrics, and reports

---

## Selecting a project

Click on a project name in the sidebar to view its analytics. You can manage multiple websites — each one appears as a separate project.

To create a new project, click the **"+"** button in the sidebar and enter your website's name and domain.

---

## Key metrics

At the top of the dashboard, you'll see four key numbers:

| Metric | What it means |
|--------|--------------|
| **Total Views** | The total number of page views in the selected time period |
| **Unique Visitors** | How many different people visited (estimated without cookies) |
| **Bounce Rate** | Percentage of visitors who left after viewing only one page. Lower is better |
| **Top Page** | Your most-visited page in the selected time period |

Each metric includes a **comparison arrow** showing the change compared to the previous period. Green means improvement, red means decline.

---

## Time filters

Use the time filter buttons at the top of the dashboard:

| Filter | Shows data from |
|--------|----------------|
| **24h** | The last 24 hours |
| **3d** | The last 3 days |
| **7d** | The last 7 days (default) |
| **30d** | The last 30 days |
| **365d** | The last year |

The charts and metrics update automatically when you change the filter.

---

## Charts and visualizations

### Time series

The main chart shows page views and visitors over time. The granularity adjusts automatically:
- 24h filter → hourly data points
- 7d/30d filter → daily data points
- 365d filter → weekly data points

### Activity heatmap

A grid showing traffic intensity across **hours of the day** (columns) and **days of the week** (rows). Darker cells = more traffic. Use this to identify:
- Peak traffic hours
- Quiet periods
- Day-of-week patterns

### Top pages

A ranked list of your most-visited pages. Click any page to see its individual traffic trend.

### Referrers

Shows where your visitors came from:
- **Direct** — Typed your URL or used a bookmark
- **Search engines** — Google, Bing, etc.
- **Social media** — Twitter, Facebook, etc.
- **Other sites** — Links from blogs, forums, etc.

### Browser, OS, and device charts

Pie/bar charts showing the breakdown of:
- **Browsers** — Chrome, Firefox, Safari, Edge, etc.
- **Operating systems** — Windows, macOS, Linux, iOS, Android
- **Devices** — Desktop, Mobile, Tablet

### Geographic distribution

A map and table showing where your visitors are located, broken down by country and city. Click a country to drill down into its cities.

### Custom events

If you're tracking custom events, this section shows a bar chart of event frequency. Events are listed by name with their count.

### Contribution calendar

A GitHub-style calendar spanning 365 days. Each day is a colored square — the darker the color, the more traffic that day. Hover over a square to see the exact count.

---

## Live feed

Toggle the **live feed** to see visitors arriving in real time. Each entry shows:
- The visitor's current page
- Their approximate location (country/city)
- Their device type
- How long ago they arrived

The feed refreshes automatically and shows visitors from the last 5 minutes.

---

## Conversion goals

### Viewing goals

The **Goals** tab shows all your conversion goals with their current conversion rates. Each goal displays:
- The goal name
- Current conversion rate (percentage of visitors who completed it)
- Comparison with the previous period

### Creating a goal

1. Go to the **Goals** tab
2. Click **"New Goal"**
3. Choose the type:
   - **URL-based** — Triggers when a visitor reaches a specific page (e.g., `/thank-you`)
   - **Event-based** — Triggers when a custom event fires (e.g., `purchase`)
4. Enter a name and the match pattern
5. Click **Create**

---

## Funnels

### Viewing funnels

The **Funnels** tab shows your multi-step conversion funnels. Each funnel displays:
- The number of visitors at each step
- Drop-off percentages between steps
- Overall completion rate

### Creating a funnel

1. Go to the **Funnels** tab
2. Click **"New Funnel"**
3. Give it a name (e.g., "Checkout Flow")
4. Add steps in order — each step can be a URL or custom event
5. Click **Create**

---

## User segments

### Viewing segments

The **Segments** tab lets you analyze subsets of your visitors. Each segment shows its own set of metrics filtered by your criteria.

### Creating a segment

1. Go to the **Segments** tab
2. Click **"New Segment"**
3. Use the visual filter builder to define conditions:
   - Choose a property (country, browser, device, page, referrer, etc.)
   - Choose an operator (equals, contains, starts with, etc.)
   - Enter the value
4. Add more conditions with **AND** (all must match) or **OR** (any can match)
5. Click **Create**

Example: "Mobile visitors from Germany" → Device equals Mobile **AND** Country equals Germany.

---

## Data export

### CSV export

Every section has an export button. Click it to download the data as a CSV file that you can open in Excel, Google Sheets, or any spreadsheet application.

### Raw events

The **Events** tab shows individual tracking events with:
- Filtering by event type, page, and date range
- Sorting by any column
- Pagination for large datasets
- CSV export

---

## Settings

Click the **gear icon** to access project settings:

- **Time format** — 12-hour or 24-hour clock
- **Date format** — Your preferred date display format
- **Heatmap colors** — Customize the activity heatmap color scheme
- **API key** — View or copy your project's API key
- **Tracker script** — Copy the ready-to-use tracking script
- **Rename project** — Change the project name
- **Delete project** — Remove the project and all its data

---

## Theme

Click the **sun/moon icon** in the top bar to toggle between light and dark mode. Your preference is saved automatically.
