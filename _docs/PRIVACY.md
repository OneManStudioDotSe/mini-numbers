# Mini Numbers - Privacy architecture

## Philosophy

Mini Numbers is designed around a core principle: **collect useful analytics without storing any personally identifiable information (PII)**. Every architectural decision prioritizes visitor privacy while still providing meaningful insights.

- No cookies or persistent identifiers
- No IP addresses stored in the database
- No raw user agent strings persisted
- No cross-day tracking by default
- No consent banners needed

---

## Visitor identification

### How the rotating hash works

Mini Numbers identifies unique visitors using a one-way SHA-256 hash that rotates on a configurable schedule.

**Hash input:**
```
SHA-256(IP + UserAgent + ProjectID + ServerSalt + RotationBucket)
```

**Rotation bucket calculation** (from `core/AnalyticsSecurity.kt`):
```
hoursSinceEpoch = hours since 2024-01-01T00:00:00Z
rotationBucket = hoursSinceEpoch / HASH_ROTATION_HOURS
```

**Key properties:**
- The **same visitor** produces a **different hash** each rotation period
- The hash **cannot be reversed** to recover the original IP or user agent
- The **server salt** (128-char hex string) ensures hashes are unique to your instance
- **Cross-instance tracking is impossible** - different salts produce different hashes

### Configurable rotation

The `HASH_ROTATION_HOURS` environment variable controls how often hashes rotate:

| Value | Behavior | Use Case |
|-------|----------|----------|
| 1 | Hourly rotation | Maximum privacy |
| 24 | Daily rotation (default) | Good balance of privacy and analytics accuracy |
| 168 | Weekly rotation | Better visitor counting accuracy |
| 8760 | Yearly rotation | Maximum analytics accuracy |

Shorter rotation = more privacy but less accurate unique visitor counts (same person counted as new visitor after each rotation).

---

## Privacy modes

Three privacy modes control what data is collected. Set via `PRIVACY_MODE` environment variable.

### STANDARD (default)

Full data collection with privacy-preserving hashing:
- Country and city from GeoIP lookup
- Browser, OS, and device type from user agent parsing
- Page paths and referrers

### STRICT

Country-only geographic data, no user agent parsing:
- Country only (no city)
- No browser, OS, or device data
- Page paths and referrers

### PARANOID

Minimal data collection:
- No geographic data at all
- No browser, OS, or device data
- Page paths and referrers only
- Visitor hash still generated (for unique visitor counting)

---

## Data retention

The `DATA_RETENTION_DAYS` environment variable controls automatic data purging:

| Value | Behavior |
|-------|----------|
| 0 | Disabled (keep data forever) |
| 30 | Delete events older than 30 days |
| 90 | Delete events older than 90 days |
| 365 | Delete events older than 1 year |

A background timer runs every 6 hours to purge expired events.

---

## Geolocation

- Uses the **MaxMind GeoLite2** database for offline IP-to-location lookup
- The GeoIP database is bundled in the application (no external API calls)
- Only **country name** and **city name** are stored - no GPS coordinates
- The IP address is used in-memory during the lookup and immediately discarded
- In STRICT mode, only country is stored; in PARANOID mode, no geo data at all
- GeoIP results are cached (10,000 entries, 1-hour TTL) to avoid repeated lookups

---

## User agent parsing

- Browser, OS, and device type are extracted **server-side** from the User-Agent header
- The **raw User-Agent string is never stored** in the database
- Only the parsed category names are saved (e.g., "Chrome", "Windows", "Desktop")
- In STRICT and PARANOID modes, no user agent data is stored at all

---

## Session management

- Sessions use `sessionStorage` (browser tab-scoped, not cookies)
- Session IDs are generated client-side using `crypto.getRandomValues()`
- Session IDs are random 128-bit values (32 hex characters)
- Sessions automatically expire when the browser tab is closed
- No cookies are set by the tracking script

---

## What IS stored

The `Events` table stores:

| Column | Description |
|--------|-------------|
| `visitorHash` | SHA-256 hash (rotated, not reversible to IP) |
| `sessionId` | Random session identifier (tab-scoped) |
| `eventType` | "pageview", "heartbeat", or custom event name |
| `path` | Page path (e.g., `/blog/post-1`) |
| `referrer` | Traffic source URL (nullable) |
| `country` | Country name from GeoIP (nullable, mode-dependent) |
| `city` | City name from GeoIP (nullable, mode-dependent) |
| `browser` | Browser name (nullable, mode-dependent) |
| `os` | OS name (nullable, mode-dependent) |
| `device` | Device type (nullable, mode-dependent) |
| `duration` | Time on page in seconds |
| `timestamp` | When the event occurred |

---

## What is NOT stored

- IP addresses
- Raw User-Agent strings
- Cookies or persistent identifiers
- Email addresses or usernames
- GPS coordinates or precise location
- Screen resolution or window size
- Fingerprinting data (canvas, WebGL, fonts)
- Cross-site tracking identifiers

---

## GDPR compliance

Mini Numbers is designed to be GDPR-friendly:

1. **No consent banner needed** - No cookies or PII means no consent requirement under most interpretations of GDPR/ePrivacy
2. **No data processor agreements** - Self-hosted, no third-party data sharing
3. **Data minimization** - Only collects what's needed for analytics
4. **Storage limitation** - Configurable data retention with auto-purge
5. **Data subject rights** - No individual can be identified, so individual access/deletion requests don't apply in the traditional sense
6. **Privacy by design** - The architecture makes it impossible to store PII, not just a policy choice

**Note:** While Mini Numbers is designed with GDPR principles in mind, you should consult with a legal professional for your specific compliance requirements.

---

## Comparison with other analytics tools

| Feature | Mini Numbers | Google Analytics | Plausible | Umami |
|---------|-------------|-----------------|-----------|-------|
| Cookies | None | Multiple | None | None |
| IP storage | Never | Yes (processed) | Never | Never |
| Hash rotation | Configurable (1h-1yr) | N/A | Daily | Daily |
| Privacy modes | 3 levels | 1 | 1 | 1 |
| Data retention | Configurable | 14-26 months | Configurable | Configurable |
| Self-hosted | Yes | No | Yes | Yes |
| Consent needed | No | Yes | No | No |
