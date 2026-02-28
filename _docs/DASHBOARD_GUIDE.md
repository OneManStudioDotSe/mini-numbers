# Mini Numbers - Dashboard user guide

## Getting started

### First login

1. Visit your Mini Numbers instance (e.g., `https://analytics.example.com`)
2. Log in with your admin credentials (configured during setup)
3. You'll see an empty project list with a "Create Project" button

### Creating your first project

1. Click **Create Project** in the sidebar
2. Enter a **Project Name** (e.g., "My Website")
3. Enter the **Domain** (e.g., `example.com`)
4. Click **Create** - an API key is generated automatically
5. Copy the API key from the confirmation dialog

### Installing the tracker

Add the tracking script to your website's `<head>` or before `</body>`:

```html
<script
  async
  src="https://your-analytics-domain.com/tracker/tracker.js"
  data-project-key="YOUR_API_KEY">
</script>
```

Optional attributes:
- `data-heartbeat-interval="30000"` - Heartbeat interval in milliseconds (default: 30s)
- `data-disable-spa="false"` - Disable SPA route detection (default: enabled)

---

## Dashboard overview

### Layout

- **Sidebar** (left) - Project list with delete buttons, create project button, demo project, sign out
- **Header** (top) - Project title, time filter, theme toggle, settings, demo data
- **Main content** - Analytics cards, charts, tables, and visualizations

### Selecting a project

Click any project in the sidebar to load its analytics. The selected project is highlighted with a checkmark.

To delete a project, hover over it in the sidebar and click the trash icon. A confirmation dialog will appear warning that all analytics data, goals, funnels, and segments will be permanently removed.

---

## Key metrics

The top row shows four stat cards:

| Metric | Description |
|--------|-------------|
| **Total Views** | Number of page views in the selected period |
| **Unique Visitors** | Distinct visitors (based on privacy-preserving hashes) |
| **Bounce Rate** | Percentage of single-page sessions with no heartbeat |
| **Top Page** | Most visited page path |

Each card includes:
- **Sparkline** - Mini trend chart for the metric
- **Comparison** - Percentage change vs. the previous period (e.g., "7d" compares current 7 days to prior 7 days)

---

## Time filtering

The filter dropdown and date range are displayed together in a unified filter bar at the top of the dashboard:

| Filter | Description |
|--------|-------------|
| Last 24 hours | Hourly granularity |
| Last 3 days | Daily granularity |
| Last 7 days | Daily granularity (default) |
| Last 30 days | Daily granularity |
| Last 365 days | Weekly granularity |

The date range is displayed next to the filter dropdown.

---

## Charts & visualizations

### Time series

Line chart showing views over time, limited to a 3-day window for readability. Granularity adjusts automatically based on the selected time period.

### Activity heatmap

A 7-day x 24-hour grid showing traffic intensity with actual dates displayed next to day names (e.g., "Mon Feb 17"). Darker cells = more traffic. Peak hour and peak day are highlighted. The heatmap color scheme can be changed in Settings.

### Peak times

Two cards showing:
- **Peak Hours** - Top 5 busiest hours of the day
- **Peak Days** - Top 3 busiest days of the week

The top entry in each list is marked with a "Peak" badge.

### Top pages

Bar chart showing the top visited pages with consistent spacing between bars. If more than 5 entries exist, a "Show more" button appears to expand the full list. Click the export button to download as CSV.

### Referrers

Bar chart showing the top traffic sources with consistent spacing between bars. If more than 5 entries exist, a "Show more" button expands the full list. "Direct / None" represents visitors who typed the URL directly.

### Browsers, OS, devices

Each has a toggle between **doughnut** and **bar** chart views. Shows the top 8 entries with the rest grouped as "Other". Your chart type preference is saved in localStorage.

### Geographic distribution

Two view modes (toggle in the header):
- **Bar chart** - Top countries by visits. Click a country to drill down into cities.
- **Map view** - Interactive Leaflet map with markers sized by visit count.

### Custom events

Appears only when custom events exist. The section includes:
- **Summary cards** — Total custom events count and top event with occurrence count
- **Breakdown list** — All custom events with proportional progress bars showing relative volume and percentages
- **Bar chart** — Visual representation of event names and their counts

Track custom events with:

```javascript
MiniNumbers.track("signup");
MiniNumbers.track("purchase");
```

### Contribution calendar

A GitHub-style 365-day activity grid at the bottom of the dashboard. Intensity levels (0-4) show daily activity. Hover over a cell to see the exact date and visit count.

---

## Conversion goals

### Viewing goals

The Goals section shows cards for each configured goal:
- **Conversion rate** - Large percentage display
- **Completions** - Number of goal completions in the period
- **Comparison** - Change vs. previous period

### Managing goals

Click **Manage Goals** to open the goals modal:
1. Enter a **Goal Name** (e.g., "Sign Up")
2. Select **Type**: URL-based or Event-based
3. Enter the **Match Value**:
   - URL: a path pattern (e.g., `/thank-you`)
   - Event: an event name (e.g., `signup`)
4. Click **Create Goal**

Goals can be toggled active/inactive or deleted from the management table.

---

## Funnels

### Viewing funnels

Each funnel shows:
- **Step-by-step progression** with colored bars
- **Drop-off percentage** between steps
- **Average time** between steps
- **Overall conversion rate**

### Creating funnels

Click **Manage Funnels** to open the funnels modal:
1. Enter a **Funnel Name** (e.g., "Checkout Flow")
2. Add steps (minimum 2):
   - Each step has a **Name**, **Type** (pageview/event), and **Match Value**
3. Click **Create Funnel**

---

## User segments

### Viewing segments

Segment cards show filter criteria and visitor counts. Each segment applies its filters to the analytics data.

### Creating segments

Click **Manage Segments** to open the segments modal:
1. Enter **Segment Name** and optional **Description**
2. Build filters using the visual filter builder:
   - **Field**: country, city, browser, os, device, path, referrer
   - **Operator**: equals, not_equals, contains, starts_with
   - **Value**: the filter value
3. Choose **AND/OR** logic between multiple filters
4. Click **Create Segment**

---

## Data export

### CSV export

Each chart card has an export button (download icon) that exports the visible data as CSV. Available for:
- Top pages, referrers, browsers, OS, devices, countries
- Recent activity table
- Contribution calendar

### Raw events

Click the **Raw Events** button to open a paginated viewer of all collected events. Features:
- Search/filter by any field
- Pagination (50 events per page)
- Export all raw events as CSV

---

## Settings

Click the gear icon in the header to open settings:

| Setting | Description |
|---------|-------------|
| **Time Format** | 12-hour or 24-hour clock |
| **Date Format** | Short (Jan 1) or long (January 1, 2026) |
| **Heatmap Colors** | Blue (default), green, purple, or orange — applies to both the activity heatmap and contribution calendar |
| **API Key** | View the project's API key with copy button |
| **Tracking Script** | Copy-paste snippet for your website |
| **Rename Project** | Change the project name |
| **Delete Project** | Permanently delete the project and all its data |

---

## Demo data

Click **Generate Demo Data** in the header to create realistic test data:
1. Set the **number of events** (0-3000)
2. Choose a **time scope** (7 days to 1 year)
3. Click **Generate**

Generated data includes realistic paths, referrers, browsers, locations, and session patterns with ~40% bounce rate and ~60% engaged sessions. The generator also creates 5 conversion goals, 2 funnels with steps, and 3 user segments. Custom events are generated with ~25% probability per engaged session event across 10 event types.

---

## Live feed

The bottom-right section shows real-time visitor activity:
- Updates every 5 seconds
- Shows the last 5 minutes of activity
- Each entry displays the page path, location, and relative time

---

## Embeddable widgets

Mini Numbers provides 4 embeddable widgets that you can place on your tracked website to display live analytics information to your visitors. Each widget is loaded via a single `<script>` tag — no iframes or external dependencies required.

### Available widgets

| Widget | Description |
|--------|-------------|
| **realtime** | Live visitor counter with pulsing green dot (e.g., "42 visitors online") |
| **pageviews** | Page view count badge, filterable by scope and time range |
| **toppages** | Ordered list of most popular pages with mini progress bars |
| **sparkline** | Tiny inline SVG trend chart showing the last 7 days of traffic |

### Installation

Add a `<script>` tag for each widget you want to display. The widget renders itself immediately after the script tag (or into a target container you specify).

**Real-time visitor counter:**

```html
<script async src="https://your-analytics-domain.com/widget/widget.js"
  data-project-key="YOUR_API_KEY"
  data-widget="realtime"></script>
```

**Page view counter:**

```html
<script async src="https://your-analytics-domain.com/widget/widget.js"
  data-project-key="YOUR_API_KEY"
  data-widget="pageviews"
  data-scope="site"
  data-filter="30d"></script>
```

**Top pages list:**

```html
<script async src="https://your-analytics-domain.com/widget/widget.js"
  data-project-key="YOUR_API_KEY"
  data-widget="toppages"
  data-limit="5"
  data-title="Popular articles"></script>
```

**Visitor sparkline:**

```html
<script async src="https://your-analytics-domain.com/widget/widget.js"
  data-project-key="YOUR_API_KEY"
  data-widget="sparkline"
  data-color="#6366f1"
  data-width="120"
  data-height="32"></script>
```

### Configuration attributes

All widgets require `data-project-key` and `data-widget`. Additional attributes vary by widget type:

| Attribute | Applies to | Default | Description |
|-----------|------------|---------|-------------|
| `data-project-key` | All | Required | Your project's API key |
| `data-widget` | All | Required | Widget type: `realtime`, `pageviews`, `toppages`, or `sparkline` |
| `data-theme` | All | `light` | Color theme: `light` or `dark` |
| `data-target` | All | — | CSS selector for a container element (e.g., `#my-widget`). If omitted, the widget renders after the script tag. |
| `data-api-endpoint` | All | Script origin | Custom API base URL (useful if your analytics server is on a different domain) |
| `data-scope` | pageviews | `site` | Count scope: `site` (all pages) or `page` (current page only) |
| `data-filter` | pageviews, toppages | `7d` | Time range: `24h`, `3d`, `7d`, `30d`, or `365d` |
| `data-path` | pageviews | — | Specific page path to count (only used when `data-scope="page"`) |
| `data-limit` | toppages | `5` | Number of pages to show (1–10) |
| `data-title` | toppages | `Popular pages` | Heading text above the list |
| `data-poll` | realtime | `12000` | Polling interval in milliseconds |
| `data-color` | sparkline | `#6366f1` | Line and fill color |
| `data-width` | sparkline | `120` | SVG width in pixels |
| `data-height` | sparkline | `32` | SVG height in pixels |

### Rendering into a specific container

By default, each widget renders a `<div>` immediately after its `<script>` tag. To place the widget inside an existing element, use `data-target`:

```html
<div id="visitor-count"></div>

<script async src="https://your-analytics-domain.com/widget/widget.js"
  data-project-key="YOUR_API_KEY"
  data-widget="realtime"
  data-target="#visitor-count"></script>
```

### Styling

All widget CSS uses the `mn-` prefix to avoid conflicts with your site's styles. The widgets support `light` and `dark` themes via the `data-theme` attribute. You can further customize the appearance by overriding the `mn-` prefixed CSS classes in your own stylesheet.

### API endpoints

The widgets fetch data from public API endpoints that use your project's API key for authentication (same key as the tracker):

| Endpoint | Description |
|----------|-------------|
| `GET /widget/realtime?key=<KEY>` | Active visitors in the last 5 minutes |
| `GET /widget/pageviews?key=<KEY>&scope=site&filter=7d` | Page view count |
| `GET /widget/toppages?key=<KEY>&filter=7d&limit=5` | Top pages by views |
| `GET /widget/sparkline?key=<KEY>` | Daily views for the last 7 days |

Responses are cached for 60 seconds to minimize database load. The realtime widget polls automatically (default every 12 seconds) and pauses when the browser tab is hidden.

---

## Theme

Toggle between light and dark mode using the switch in the header. Your preference is saved in localStorage and respects your system's `prefers-color-scheme` setting on first visit.
