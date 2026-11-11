---
title: Privacy
layout: default
nav_order: 6
---

# Privacy

Mini Numbers counts visits without cookies, without storing IP addresses and without sending data anywhere
else. This page says exactly what it keeps.

---

## The short version

- **No cookies.** The tracker keeps only a random tab session ID (gone when the tab closes) and, while the
  visitor is offline, a short queue of unsent events that is cleared once they're delivered.
- **IP addresses are never stored.** They're used in memory for a moment and then dropped.
- **Visitors are anonymous.** A returning visitor is recognised only until the next "forget" moment you choose
  (default: 24 hours), and never across different websites.
- **"Don't track me" is respected.** Browsers that send Global Privacy Control or Do Not Track aren't counted
  at all. A site can turn this off with `data-respect-privacy-signals="false"` on the snippet.
- **Everything stays on your server.** Locations are looked up in a database that ships with Mini Numbers, not
  through an outside service.

---

## What is stored for each visit

| | Standard | Strict | Paranoid |
|---|:---:|:---:|:---:|
| Page path, time, event type and name | ✓ | ✓ | ✓ |
| Anonymous visitor ID (forgotten on schedule) | ✓ | ✓ | ✓ |
| Tab session ID (random, ends when the tab closes) | ✓ | ✓ | ✓ |
| Referrer and UTM campaign tags | ✓ | ✓ | ✓ |
| Scroll depth, clicked outbound link or download | ✓ | ✓ | ✓ |
| Properties you send with custom events | ✓ | ✓ | ✓ |
| Country | ✓ | ✓ | — |
| Region, city and their approximate coordinates | ✓ | — | — |
| Browser, operating system, device type | ✓ | ✓ | — |

**Never stored, in any mode:** IP addresses, the full browser identification string (user agent), cookies,
names, email addresses, form contents or page contents.

{: .warning }
Custom event properties are stored as you send them. Don't put personal data such as email addresses in
`MiniNumbers.track(...)` properties.

The dashboard's privacy chip shows the mode and the forget window your server is actually using.

---

## How visitors stay anonymous

To count unique visitors without cookies, Mini Numbers combines the visitor's IP address, their browser's user
agent, the website, your secret `SERVER_SALT` and the current time window, and turns them into a one-way hash
(SHA-256). Only that hash is stored. It can't be turned back into an IP address.

When the time window ends, the same visitor produces a completely different hash, so they can no longer be
linked to their earlier visits. You choose the window with `HASH_ROTATION_HOURS`:

| Window | Effect |
|--------|--------|
| 1 hour | Most private; the same person counts again every hour |
| 24 hours (default) | Accurate daily visitor counts |
| 1 week (168) | Weekly patterns become visible |
| 1 year (8760) | Most accurate long-term counts, least private |

The tab session ID is a random number kept in the browser's `sessionStorage`. It's sent with each event so
visits within one tab can be grouped, and disappears when the tab is closed.

## Deleting old data

Set `DATA_RETENTION_DAYS` to have events older than that deleted automatically (checked every six hours).

## GDPR and consent banners

Because Mini Numbers uses no cookies, stores no IP addresses and keeps identifiers only for a short, fixed
time, analytics with it generally doesn't need a consent banner, and no data leaves your server. Whether that
holds for your site also depends on everything else your site does — check with someone who knows your
jurisdiction.
