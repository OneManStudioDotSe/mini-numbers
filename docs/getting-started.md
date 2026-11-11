---
title: Getting started
layout: default
nav_order: 3
---

# Getting started

From a running server to your first visitor in four steps.

---

## 1. Install

Follow the [Install guide](deployment) for your kind of server. To look around on your own computer first:

```bash
docker run --rm -p 8080:8080 -v mini-numbers-data:/app/data ghcr.io/onemanstudiodotse/mini-numbers:latest
```

and open `http://localhost:8080`.

---

## 2. Run the setup wizard

The first time you open Mini Numbers, a six-step wizard appears:

1. **Secure your dashboard** — admin username, password, and a *server salt* (a secret used to make visitor
   identifiers anonymous; it's generated for you).
2. **Choose storage** — SQLite (one file, no setup, right for most sites) or PostgreSQL.
3. **Privacy approach** — Standard, Strict or Paranoid. See [Privacy](privacy) for exactly what each stores.
4. **Server settings** — port, allowed origins for the admin API (leave empty unless other sites call your
   API), and development mode (only for testing without HTTPS).
5. **Fine-tuning** — request limits per visitor and per website.
6. **Review** — check everything and launch. Mini Numbers starts straight away and shows the login page.

The wizard saves your choices in a `.env` file. Hash rotation, data retention and email settings aren't in the
wizard; add them to `.env` later if you need them (see [Configuration](configuration)).

{: .tip }
**Quick Setup** on the first step fills everything in with the recommended defaults and generates a strong admin
password (user name `admin`). It shows the password once, on the review step: copy it before you press launch.

---

## 3. Create a project

Sign in, click **New project**, and enter your website's name and domain. Mini Numbers shows the tracking
snippet for that project, for example:

```html
<script async src="https://stats.example.com/tracker/tracker.min.js"
  data-project-key="YOUR_PROJECT_KEY"></script>
```

Paste it into every page of your website, just before `</head>`. Open your site, then the dashboard: your
visit shows up within seconds under **Real-Time**.

No real site yet? **Demo project** in the sidebar creates a project filled with realistic sample data.

---

## 4. Track what matters (optional)

Count actions such as sign-ups or purchases from your site's JavaScript:

```javascript
MiniNumbers.track("signup");
MiniNumbers.track("purchase", { revenue: 29.99, currency: "EUR" });
```

They appear under **Events**. Turn them into **conversion goals** from the **Conversions** section. All tracker options are in [For developers](developers).
