<p>
  <img src="https://img.shields.io/github/v/release/onemanstudiodotse/mini-numbers?label=version&color=blue" alt="Version">
  <img src="https://img.shields.io/badge/license-MIT-green" alt="License">
  <img src="https://img.shields.io/badge/tests-296%20passing-brightgreen" alt="Tests">
  <img src="https://img.shields.io/badge/tracker-1.9KB%20gzipped-orange" alt="Tracker Size">
  <img src="https://img.shields.io/badge/cookies-zero-purple" alt="No Cookies">
</p>

# Mini Numbers

**Privacy-first, self-hosted web analytics.** Track your website traffic without compromising visitor privacy — no cookies, no personal data stored, no consent banners needed.

A lightweight open-source alternative to Google Analytics, Plausible, and Umami.

## Why Mini Numbers?

Honest comparison as of October 2026 (full matrix and sources in `_docs/EVALUATION.md`):

|                              | Google Analytics | Plausible CE | Umami | Rybbit | **Mini Numbers** |
|------------------------------|------------------|--------------|-------|--------|------------------|
| No cookies                   |                  | Yes          | Yes   | Yes    | **Yes**          |
| Self-hosted                  |                  | Yes          | Yes   | Yes    | **Yes**          |
| Runs on SQLite, single JAR   |                  |              |       |        | **Yes**          |
| Privacy modes + hash rotation|                  |              |       |        | **3 levels, 1h–1y** |
| Tracker size (gzipped)       | 45KB+            | ~1.9KB       | ~2.4KB| ~9KB   | **1.9KB**        |
| Goals, funnels, segments     | Yes              | Paid         | Yes   | Yes    | **Yes**          |
| Revenue tracking             | Yes              | Paid         | Yes   | ~      | **Yes**          |
| Webhooks + email reports     |                  | Email        | Cloud | ~      | **Both**         |
| Session replay / heatmaps    |                  |              | Yes   | Yes    | No (by design)   |
| Licence                      |                  | AGPL         | MIT   | AGPL   | **MIT**          |

## Key Features

- **Real-time dashboard** — Live visitor feed, time series charts, activity heatmaps
- **Privacy by design** — Three modes (Standard, Strict, Paranoid) with configurable hash rotation
- **Conversion tracking** — Goals, multi-step funnels, and user segments with visual filter builder
- **Revenue analytics** — Track purchases and AOV with `MiniNumbers.track("purchase", { revenue: 29.99 })`
- **Webhooks** — HMAC-signed delivery on goal conversions and traffic spikes
- **Email reports** — Scheduled daily/weekly/monthly analytics emails via SMTP
- **Tiny footprint** — 1.9KB (gzipped) tracking script, SQLite or PostgreSQL, runs anywhere
- **Multi-project** — Manage multiple websites from a single instance
- **No cookies** — GDPR-friendly by default, no consent banners required
- **Dark mode** — Full light/dark theme support with accessible UI

## Quick Start

**Up and running in under a minute:**

### 1. Run the server

```bash
# With Docker (recommended)
docker compose up -d

# Or run directly
./gradlew run
```

### 2. Complete the setup wizard

Open your browser and visit your server URL. The setup wizard walks you through security, database, and server configuration — no config files to edit manually.

### 3. Add the tracker to your website

```html
<script async src="https://your-domain.com/tracker/tracker.js" data-project-key="YOUR_API_KEY"></script>
```

### 4. Track custom events (optional)

```javascript
MiniNumbers.track("signup");
MiniNumbers.track("purchase", { revenue: 29.99, currency: "USD" });
```

That's it. Visit your dashboard to see analytics flowing in.

## Documentation

Full documentation is available at **[onemanstudiodotse.github.io/mini-numbers](https://onemanstudiodotse.github.io/mini-numbers/)**.

| Guide | Description |
|-------|-------------|
| [Getting Started](https://onemanstudiodotse.github.io/mini-numbers/getting-started) | Installation, setup wizard, first project |
| [Configuration](https://onemanstudiodotse.github.io/mini-numbers/configuration) | All settings and environment variables |
| [Features](https://onemanstudiodotse.github.io/mini-numbers/features) | Full feature overview with examples |
| [Dashboard Guide](https://onemanstudiodotse.github.io/mini-numbers/dashboard-guide) | How to use the analytics dashboard |
| [Privacy](https://onemanstudiodotse.github.io/mini-numbers/privacy) | How your visitors' privacy is protected |
| [Deployment](https://onemanstudiodotse.github.io/mini-numbers/deployment) | Docker, cloud platforms, reverse proxies |
| [Architecture](https://onemanstudiodotse.github.io/mini-numbers/architecture) | Technical details for developers |

## Tech Stack

**Backend:** Kotlin 2.4.20 + Ktor 3.6.0 on JDK 21 &bull; **Database:** SQLite or PostgreSQL &bull; **Caching:** Caffeine &bull; **GeoIP:** MaxMind GeoLite2 &bull; **Build:** Gradle

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

[MIT](LICENSE) — use it however you like.

### Third-party notices

- This product includes GeoLite2 data created by MaxMind, available from [https://www.maxmind.com](https://www.maxmind.com).
- User-agent detection uses the [uap-core](https://github.com/ua-parser/uap-core) regexes via `uap-java` (Apache-2.0).
