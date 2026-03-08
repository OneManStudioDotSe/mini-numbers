---
title: Tracker Reference
layout: default
nav_order: 9
---

# Tracker Reference

Complete reference for the `tracker.js` client-side script.

---

## Installation

Add the script tag to every page you want to track, ideally just before `</body>`:

```html
<script
  async
  src="https://your-domain.com/tracker/tracker.js"
  data-project-key="YOUR_API_KEY">
</script>
```

Replace `YOUR_API_KEY` with the tracking ID shown in your project settings.

---

## Script attributes

All attributes are set on the `<script>` tag.

| Attribute | Required | Default | Description |
|-----------|----------|---------|-------------|
| `data-project-key` | Yes | — | Your project's API key (tracking ID) |
| `data-api-endpoint` | No | Script origin + `/collect` | Override the collection endpoint (useful if your analytics server is on a different domain) |
| `data-heartbeat-interval` | No | `30000` | How often (in ms) to send a heartbeat signal while the page is visible. Used to measure time-on-page |
| `data-disable-spa` | No | `false` | Set to `"true"` to disable automatic single-page app navigation tracking |

---

## Automatic tracking

The following events are tracked automatically — no extra code needed:

| Event | Description |
|-------|-------------|
| `pageview` | Fired on initial page load |
| `heartbeat` | Fired every `data-heartbeat-interval` ms while the tab is visible. Pauses when the tab is hidden |
| `scroll` | Fired at 25%, 50%, 75%, and 100% scroll depth on each page |
| `outbound` | Fired when a visitor clicks a link to an external domain |
| `download` | Fired when a visitor clicks a link to a file (`.pdf`, `.zip`, `.xlsx`, `.docx`, `.mp4`, and more) |

### SPA navigation

When `data-disable-spa` is not set, the tracker patches `history.pushState`, `history.replaceState`, and the `popstate` event to detect navigation in React, Vue, Angular, and similar frameworks. A new `pageview` is sent each time the path changes. Scroll depth tracking resets on each navigation.

---

## JavaScript API

The tracker exposes a global `MiniNumbers` object for custom event tracking.

### `MiniNumbers.track(name, properties?)`

Send a custom event.

```javascript
// Simple event
MiniNumbers.track("signup");

// Event with properties
MiniNumbers.track("purchase", {
  revenue: 49.99,
  currency: "USD",
  product: "Pro Plan"
});

// Any key-value pairs
MiniNumbers.track("video_play", { video_id: "intro-tour", duration: 120 });
```

**Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `name` | `string` | Event name. Max 100 characters |
| `properties` | `object` (optional) | Arbitrary key-value pairs. Must be JSON-serializable. Revenue tracking requires a `revenue` (number) key |

**Revenue tracking:** If `properties.revenue` is a number, it is stored and included in the Revenue dashboard. Add `currency` (string) for currency attribution.

---

## Session management

The tracker uses `sessionStorage` (not cookies) to maintain a session ID per browser tab. Sessions are isolated across tabs and expire when the tab is closed. The session ID is never sent to your server in plain form — it is used only to group events from the same browsing session.

If `sessionStorage` is unavailable (e.g., private browsing mode with strict settings), a random fallback ID is generated in memory.

---

## UTM parameter tracking

UTM parameters present in the page URL are automatically captured and persisted in `sessionStorage` for the duration of the session:

| Parameter | Stored as |
|-----------|-----------|
| `utm_source` | `utmSource` |
| `utm_medium` | `utmMedium` |
| `utm_campaign` | `utmCampaign` |
| `utm_term` | `utmTerm` |
| `utm_content` | `utmContent` |

UTM data is attached to `pageview` events only. If a visitor lands with UTM parameters, those parameters are reused for subsequent pageviews in the same session.

---

## Offline queue

If `sendBeacon()` fails (e.g., the server is temporarily unreachable), the event payload is saved to `localStorage` under the key `mn_queue` (max 20 items). The queue is replayed automatically on the next page load or when the browser comes back online (`online` event). Successfully delivered entries are removed from the queue.

---

## Privacy

- No cookies are set by the tracker
- The session ID is stored in `sessionStorage` only — not sent to or stored by the server
- UTM parameters are stored in `sessionStorage` only
- The offline queue uses `localStorage` temporarily — entries are deleted after successful delivery
- IP addresses are processed in-memory on the server and never written to the database
