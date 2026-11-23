---
title: For developers
layout: default
nav_order: 7
---

# For developers

The tracking snippet, custom events, embeddable widgets, the REST API and webhooks.

---

## Tracking snippet

```html
<script async src="https://stats.example.com/tracker/tracker.min.js"
  data-project-key="YOUR_PROJECT_KEY"></script>
```

It sends events to the server it was loaded from. Optional attributes:

| Attribute | Default | What it does |
|-----------|---------|--------------|
| `data-project-key` | *(required)* | The project's key (Settings → tracking ID) |
| `data-api-endpoint` | `<script origin>/collect` | Send events somewhere else, e.g. through your own proxy |
| `data-heartbeat-interval` | `30000` | Milliseconds between "still on the page" signals, used for time on page |
| `data-disable-spa` | `false` | `true` stops counting page changes made with the History API (React, Vue…) |
| `data-respect-privacy-signals` | `true` | `false` also counts visitors whose browser sends Global Privacy Control or Do Not Track |

Recorded automatically: page views (including single-page-app navigation), heartbeats while the tab is visible,
scroll depth at 25/50/75/100%, clicks on links to other sites, and file downloads. Events sent while the visitor
is offline are queued and delivered when they're back online. Visitors whose browser sends Global Privacy
Control or Do Not Track are not counted at all; `MiniNumbers.track()` still exists for them but sends nothing.

## Custom events

```javascript
MiniNumbers.track("signup");
MiniNumbers.track("plan_selected", { plan: "pro" });
MiniNumbers.track("purchase", { revenue: 29.99, currency: "EUR" });
```

An event with a numeric `revenue` property (at the top level of the properties) counts towards the revenue cards and attribution. Each `currency` (an ISO code such as `USD` or `EUR`; `USD` when missing) is shown on its own and never added to another. A negative `revenue` is a refund: it is taken off the net revenue and shown as its own figure. Amounts beyond 1,000,000,000 are ignored. Event names and
properties are stored as sent — keep personal data out of them.

---

## Widgets

{: .warning }
Widgets are a [lab](configuration#labs), off unless `LABS` includes `widgets`, and then off for each project
until an admin turns on **Settings → Project information → Embeddable widgets**. While a project has them on,
anyone who copies its project key from your site's HTML can read the numbers below for that site.

Show live numbers on your own pages. Add a placeholder and the widget script:

```html
<div id="visitors-now"></div>
<script src="https://stats.example.com/widget/widget.js"
  data-project-key="YOUR_PROJECT_KEY" data-widget="realtime" data-target="visitors-now"></script>
```

| `data-widget` | Shows | Extra attributes |
|---------------|-------|------------------|
| `realtime` | Visitors in the last 5 minutes, refreshed every 12 s | `data-poll` (ms) |
| `pageviews` | Page views for a period | `data-filter`, `data-scope` (`site` or `page`), `data-path` |
| `toppages` | The most-viewed pages | `data-filter`, `data-limit` (1–10, default 5), `data-title` |
| `sparkline` | A small chart of daily views over 7 days | `data-width`, `data-height` |

`data-target` is the placeholder's id (a CSS selector also works); without it the widget appears right after the
script. All widgets accept `data-theme` (`light` or `dark`). `data-filter` is one of `24h`, `3d`, `7d` (default),
`30d`, `365d`.

The same data is available as JSON, with the key as `?key=` or an `X-Widget-Key` header. Results are cached
on the server for 60 seconds.

| Endpoint | Response |
|----------|----------|
| `GET /widget/realtime` | `{"activeVisitors": 3, "timestamp": "…"}` |
| `GET /widget/pageviews?filter=7d&scope=site` | `{"views": 1200, "scope": "site", "filter": "7d"}` (plus `"path"` when `scope=page`) |
| `GET /widget/toppages?filter=7d&limit=5` | `{"pages": [{"path": "/", "views": 340}], "filter": "7d"}` |
| `GET /widget/sparkline` | `{"points": [{"date": "2026-11-01", "views": 120}], "maxValue": 180}` |


---

## REST API

Everything the dashboard shows is available over HTTP. The full reference is the OpenAPI file at
`/admin-panel/openapi.yaml` on your server (sign in first), also in the repository at
`src/main/resources/static/openapi.yaml`.

For scripts, get a token and send it as a Bearer header:

```bash
curl -s -X POST https://stats.example.com/api/token \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"…"}'
# → {"accessToken":"…","refreshToken":"…","tokenType":"Bearer","expiresIn":900}

curl -s https://stats.example.com/api/projects -H "Authorization: Bearer $ACCESS_TOKEN"
```

Access tokens last 15 minutes; `POST /api/token/refresh` with `{"refreshToken":"…"}` returns a new pair.
Every `/admin/...` route is also available as `/api/...`.

Users with the **viewer** role can read everything but change nothing; the dashboard hides the controls they
can't use. Admins manage users in **Settings → Account → Manage users**, or through the API (`/api/users`). Any user
changes their own password with `POST /api/me/password` (`{"currentPassword":"…","newPassword":"…"}`), which signs
that account out everywhere.

Mini Numbers tracks websites. There is no SDK for Android or iOS apps yet (it's on the
[roadmap](https://github.com/OneManStudioDotSe/mini-numbers/blob/main/_docs/ROADMAP.md)).

---

## Webhooks

A [lab](configuration#labs): on when `LABS` includes `webhooks`. Set up in **Settings → Automations → Webhooks**. Mini Numbers sends a `POST` with a JSON body when a
conversion goal is reached or traffic spikes. Each request carries:

- `X-MiniNumbers-Event` — `goal_conversion` or `traffic_spike`
- `X-MiniNumbers-Signature` — `sha256=` + the HMAC-SHA256 of the raw body, keyed with the webhook's secret

Check the signature before trusting a request (Python):

```python
import hmac, hashlib
expected = "sha256=" + hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()
valid = hmac.compare_digest(signature_header, expected)
```

A traffic spike is 20 or more page views in the last 10 minutes and more than twice the average of the six
10-minute windows before; it fires at most once per 10 minutes, and not in the first 70 minutes after the server
starts. A failed delivery is tried up to three times (5 s, then 10 s apart); a retry does not go out if the
webhook was paused or deleted meanwhile. Addresses in the link-local range (169.254.0.0/16, fe80::/10) are
refused; localhost and your own network are allowed. Delivery history older than 30 days is deleted.

The body is plain JSON, not a Slack or Discord message format; connect those through a tool such as Zapier,
n8n or a small script.
