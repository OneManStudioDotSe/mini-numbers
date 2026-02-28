---
title: Privacy
layout: default
nav_order: 6
---

# Privacy

Mini Numbers is built from the ground up to respect visitor privacy. This page explains exactly how it works and what data is (and isn't) collected.

---

## The basics

- **No cookies** — Mini Numbers doesn't set any cookies, ever. No consent banners needed
- **No IP addresses stored** — IP addresses are processed in memory to generate anonymous hashes, then immediately discarded
- **No personal data** — No names, emails, or any personally identifiable information is collected
- **No cross-site tracking** — Each website you track is completely independent
- **No third-party sharing** — All data stays on your server

---

## How visitors are identified

To count unique visitors without cookies or personal data, Mini Numbers creates a temporary anonymous identifier for each visitor. Here's how:

1. When a visitor arrives, the server takes four pieces of information:
   - The visitor's IP address
   - Their browser's user agent string
   - The project ID
   - A secret server salt (that only you know)
   - The current time bucket

2. These are combined and run through a one-way hash function (SHA-256), producing a random-looking string like `a3f8b2c1...`

3. **The IP address and user agent are immediately discarded** — only the hash is kept

4. The hash **cannot be reversed** — there is no way to get the original IP address or any personal information from it

5. The hash **automatically rotates** on a schedule you control (default: every 24 hours). After rotation, the same visitor generates a completely different hash — effectively becoming a "new" visitor from a privacy perspective

---

## Hash rotation

Hash rotation is a key privacy feature. It controls how long a visitor's anonymous identifier stays the same.

| Rotation period | Effect |
|----------------|--------|
| **1 hour** | Maximum privacy — visitors are "new" every hour |
| **24 hours** (default) | Good balance — accurate daily visitor counts |
| **168 hours** (1 week) | Better for tracking weekly patterns |
| **8760 hours** (1 year) | Most accurate long-term counts, least privacy |

You can set this to any value between 1 and 8760 hours using the `HASH_ROTATION_HOURS` setting.

---

## Privacy modes

Mini Numbers offers three privacy modes that control what data is collected:

### Standard mode

The default mode. Provides full analytics while respecting privacy.

**What's collected:** Page URL, referrer, country, city, browser name, operating system, device type, anonymous visitor hash

**What's NOT collected:** IP address, full user agent string, cookies, personal information

### Strict mode

For websites in privacy-sensitive regions or with stricter requirements.

**What's collected:** Page URL, referrer, country only (no city), browser name, operating system, device type, anonymous visitor hash

**What's NOT collected:** City-level location, IP address, full user agent string, cookies, personal information

### Paranoid mode

Maximum privacy — only the bare minimum for page view counting.

**What's collected:** Page URL, anonymous visitor hash

**What's NOT collected:** Location data, browser/OS/device information, referrer, IP address, user agent, cookies, personal information

---

## What is stored vs. what is NOT stored

### Stored (in your database)

- Page URLs visited
- Referrer URLs (Standard and Strict modes)
- Country and/or city (depending on privacy mode)
- Browser name (e.g., "Chrome" — not the full user agent string)
- Operating system name (e.g., "Windows")
- Device type (e.g., "Desktop")
- Anonymous visitor hash (rotates on schedule)
- Session identifier (random, stored only in browser session storage)
- Timestamps
- Custom event names
- Time spent on page (from heartbeat signals)

### Never stored

- IP addresses
- Full user agent strings
- Cookies
- Names, emails, or any personal information
- Login credentials of your site's visitors
- Form data or page content
- Browser fingerprints

---

## Session tracking

Instead of cookies, Mini Numbers uses the browser's `sessionStorage` to maintain a session identifier. This has important privacy benefits:

- The session ID is a random string — not derived from any personal data
- It exists only in the current browser tab
- It is automatically deleted when the tab is closed
- It cannot be accessed by other websites
- It does not persist across browsing sessions

---

## Geolocation

Mini Numbers determines visitor location using a **bundled offline database** (MaxMind GeoLite2). This means:

- No external API calls are made for geolocation
- No visitor data leaves your server
- The lookup happens in memory — the IP address is never written to disk
- Results are cached temporarily for performance (1 hour, then discarded)

---

## GDPR compliance

Mini Numbers is designed to be GDPR-friendly:

- **No consent required** — Since no cookies or personal data are used, consent banners are generally not needed for analytics purposes
- **No data processor agreements needed** — All data stays on your own server
- **Data minimization** — Only essential data is collected
- **Purpose limitation** — Data is used only for aggregate analytics
- **Storage limitation** — Configurable data retention with automatic deletion
- **Right to erasure** — No personal data is stored, so there's nothing to erase

> **Note:** While Mini Numbers is designed with GDPR compliance in mind, consult with a legal professional for your specific situation, as compliance depends on your overall website setup and jurisdiction.

---

## Comparison with other tools

| Feature | Mini Numbers | Google Analytics | Plausible | Umami |
|---------|-------------|-----------------|-----------|-------|
| Cookies | None | Multiple | None | None |
| IP storage | Never | Yes | Never | Never |
| Privacy modes | 3 levels | None | 1 level | 1 level |
| Hash rotation | Configurable | N/A | Daily | Daily |
| Self-hosted | Yes | No | Optional | Yes |
| Consent needed | No | Yes | No | No |
| Data stays on your server | Yes | No | Optional | Yes |
