---
title: Embeddable Widgets
layout: default
nav_order: 10
---

# Embeddable Widgets

Mini Numbers provides lightweight widget endpoints that let you embed live analytics numbers on your own public-facing pages — no admin login required.

---

## How widgets work

Widget endpoints accept a public project key as a query parameter. They return JSON data suitable for rendering in a `<script>` tag or via `fetch()` from your frontend. Responses are cached for 60 seconds to keep your server load low.

**Base URL:** `https://your-domain.com/widget/`

---

## Available widgets

### Active visitors right now

```
GET /widget/realtime?key=YOUR_API_KEY
```

Returns the number of unique visitors who have been active in the last 5 minutes.

```json
{ "activeVisitors": 12 }
```

### Total page views

```
GET /widget/pageviews?key=YOUR_API_KEY&period=7d
```

Returns total page views for the given period.

| Parameter | Values | Default |
|-----------|--------|---------|
| `period` | `24h`, `7d`, `30d` | `7d` |

```json
{ "pageviews": 4821, "period": "7d" }
```

### Top pages

```
GET /widget/toppages?key=YOUR_API_KEY&limit=5
```

Returns the most visited pages ordered by views.

| Parameter | Description | Default |
|-----------|-------------|---------|
| `limit` | Number of pages to return (max 20) | `5` |

```json
{
  "pages": [
    { "path": "/", "views": 1240 },
    { "path": "/blog", "views": 830 }
  ]
}
```

### 7-day sparkline

```
GET /widget/sparkline?key=YOUR_API_KEY
```

Returns daily page view counts for the last 7 days, suitable for rendering a trend chart.

```json
{
  "data": [120, 145, 98, 210, 187, 203, 176],
  "labels": ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
}
```

---

## Embedding example

```html
<div id="visitor-count">—</div>

<script>
  fetch('https://analytics.example.com/widget/realtime?key=YOUR_API_KEY')
    .then(r => r.json())
    .then(data => {
      document.getElementById('visitor-count').textContent =
        data.activeVisitors + ' visitors online now';
    });
</script>
```

---

## Notes

- Widget endpoints do **not** require authentication — they are intentionally public
- Use your project's **API key** (tracking ID), not your admin password
- Responses include `Cache-Control: public, max-age=60` — widgets refresh at most once per minute
- Widget endpoints only return aggregated, anonymized data — no visitor-level detail is exposed
- To embed on a page served from a different domain, ensure your Mini Numbers server has the correct `ALLOWED_ORIGINS` configured, or that the widget request is made server-side to avoid CORS issues
