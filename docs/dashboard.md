---
title: Using the dashboard
layout: default
nav_order: 4
---

# Using the dashboard

Pick a project in the sidebar. The bar at the top sets what you're looking at:

- **Time range** — the last 24 hours, 3 days, 7 days, 30 days or year. Most numbers are compared with the
  period before.
- **Filter by** — highlight one browser, operating system, device, country or referrer in its own chart. The
  totals and the other charts don't change. **Clear** removes the filter.
- **Privacy chip** — shows this server's privacy mode and when visitors are forgotten; click it for the
  full list of what is and isn't stored.

The page is split into eight sections. Each can be collapsed.

| Section | What it shows |
|---------|---------------|
| **Overview** | Page views, unique visitors, sessions, bounce rate, average session length, conversion rate and visits from AI assistants, each with the change from the previous period; a chart of views over time |
| **Content & Pages** | Top pages, entry pages, exit pages, how far people scroll (25/50/75/100%), outbound links and file downloads |
| **Traffic Sources** | Referrers and UTM sources, mediums and campaigns |
| **Audience & Technology** | Countries and cities on a map or globe, browsers, operating systems, devices |
| **Time Patterns** | Busiest day, peak hour, top month, an hour-by-day heatmap and a 365-day activity calendar |
| **Events** | Your custom events; with the revenue [lab](configuration#labs) also revenue totals, revenue per event and revenue by source and campaign |
| **Real-Time** | Visitors on the site in the last five minutes, as they arrive |
| **Conversions** | Conversion goals; funnels and visitor segments with their [labs](configuration#labs) on |

## Goals, funnels and segments

In **Conversions**, each card has a **Manage** button:

- **Goal** — a page someone reaches (`/thank-you`) or an event they trigger (`signup`). Shows how many
  visitors convert.
- **Funnel** *(lab)* — a list of steps (pages or events). Shows how many visitors make it through each step and where
  they drop off.
- **Segment** *(lab)* — a saved filter, for example "mobile visitors from Germany", combining conditions with AND/OR.

## Raw events

**Raw events** in the header lists every recorded event with its details, and can export them as CSV.

## Settings

**Settings** in the header holds:

- **Project information** — name, domain, the tracking ID (the project key in your snippet) and **Rotate key**
  to issue a new one; the old key stops working.
- **Dashboard preferences** — 12/24-hour time, date format and heatmap colours.
- **Export report** — tick what to include (views, pages, referrers, campaigns, technology, locations, events,
  revenue, raw events) and download one CSV file.
- **Automations** *(labs)* — **Webhooks** (Mini Numbers calls your URL when a goal converts or traffic spikes; each
  call is signed so you can verify it) and **Email reports** (daily, weekly or monthly summaries; needs the
  [SMTP settings](configuration#email-reports)).

## Demo data

**Generate demo data** fills the demo project with up to 3,000 sample visits over a period you choose, so you can
explore every chart before real traffic arrives. It is only offered on the demo project, so sample visits never
mix with a real site's numbers. **Demo project** in the sidebar creates a separate project
with demo data, goals, a funnel and segments ready-made.

## Light and dark

The sun/moon button switches themes; by default the dashboard follows your system setting.
