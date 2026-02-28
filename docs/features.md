---
title: Features
layout: default
nav_order: 4
---

# Features

An overview of everything Mini Numbers can do for you.

---

## Real-time analytics

Your dashboard updates in real time. See who's on your site right now, which pages they're viewing, and where they're visiting from — all without refreshing the page.

The **live feed** shows visitors from the last 5 minutes with their location, current page, and device information.

---

## Page view tracking

Every page visit is automatically tracked when you add the tracking script. You'll see:

- **Total page views** with comparison to the previous period
- **Unique visitors** counted without cookies or personal data
- **Bounce rate** — the percentage of visitors who leave after viewing one page
- **Top pages** — your most popular content ranked by traffic

---

## Time-based insights

### Time series charts

See how your traffic changes over time with flexible time filters:
- Last 24 hours, 3 days, 7 days, 30 days, or 365 days
- Automatic granularity (hourly, daily, or weekly) based on the time range

### Activity heatmap

A 7-day by 24-hour grid showing when your site gets the most traffic. Each cell is color-coded by intensity — great for spotting patterns like lunch-hour traffic spikes or weekend dips.

### Peak time analysis

Quickly identify your busiest hours and days so you know the best times to publish content or run promotions.

### Contribution calendar

A GitHub-style 365-day view of your site's activity. Each day is colored by traffic intensity, giving you a big-picture view of trends over the year.

---

## Visitor breakdowns

Understand your audience with detailed breakdowns:

- **Browsers** — Chrome, Firefox, Safari, Edge, and others
- **Operating systems** — Windows, macOS, Linux, iOS, Android
- **Devices** — Desktop, mobile, tablet
- **Referrers** — Where your visitors came from (search engines, social media, other sites)
- **Countries and cities** — Geographic distribution with interactive maps

---

## Custom event tracking

Track any action on your site by adding a simple JavaScript call:

```javascript
MiniNumbers.track("signup");
MiniNumbers.track("add-to-cart");
MiniNumbers.track("download");
```

Events appear in a dedicated section of your dashboard with a bar chart showing relative frequency.

---

## Conversion goals

Set up goals to measure how well your site converts visitors into actions.

### URL-based goals

Track when visitors reach a specific page:
- Thank-you page after a purchase
- Confirmation page after a signup
- Any URL pattern you define

### Event-based goals

Track when visitors trigger a custom event:
- Click a "Buy" button
- Submit a contact form
- Complete an onboarding flow

Each goal shows its **conversion rate** — the percentage of visitors who completed the action — with comparisons to the previous period.

---

## Funnels

Funnels help you understand multi-step processes on your site. Define a sequence of steps (pages or events), and Mini Numbers shows you:

- How many visitors entered the funnel
- How many completed each step
- Where the biggest drop-offs happen
- The overall completion rate

For example, a checkout funnel might track: Product Page → Add to Cart → Checkout → Payment → Confirmation.

---

## User segments

Create custom audience segments using a visual filter builder. Combine conditions with AND/OR logic to isolate specific groups:

- Visitors from a specific country
- Mobile users who visited a certain page
- Visitors referred from a particular source
- Any combination of the above

Segments help you understand how different groups of visitors behave differently on your site.

---

## Multi-project support

Track multiple websites from a single Mini Numbers instance. Each project has:

- Its own API key
- Separate statistics and dashboards
- Independent goals, funnels, and segments

Switch between projects from the sidebar in the dashboard.

---

## Data export

Export any report as a **CSV file** for further analysis in spreadsheets or other tools. The raw events view lets you browse and export individual tracking events with filtering and sorting.

---

## Embeddable widgets

Share live stats publicly by embedding widgets on any page:

```html
<!-- Real-time visitor count -->
<div data-mn-widget="realtime" data-mn-key="YOUR_API_KEY"></div>

<!-- Page view counter -->
<div data-mn-widget="pageviews" data-mn-key="YOUR_API_KEY"></div>

<!-- Top pages list -->
<div data-mn-widget="toppages" data-mn-key="YOUR_API_KEY"></div>

<!-- Sparkline chart -->
<div data-mn-widget="sparkline" data-mn-key="YOUR_API_KEY"></div>
```

---

## Dark mode

Toggle between light and dark themes from the dashboard. Your preference is saved automatically.

---

## Single-page app (SPA) support

If your website is built with React, Vue, Next.js, or similar frameworks, Mini Numbers automatically tracks page navigation without any extra setup. This can be turned off if needed.

---

## Privacy modes

Choose the level of data collection that matches your privacy requirements:

| Mode | What's collected |
|------|-----------------|
| **Standard** | Page views, country, city, browser, OS, device |
| **Strict** | Page views, country only, browser, OS, device |
| **Paranoid** | Page views only — no location or device data |

Read more in the [Privacy](privacy) guide.
