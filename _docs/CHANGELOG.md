# Changelog

All notable changes to Mini Numbers will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- **A tidier top bar.** Raw events and the time range are pills, the theme switch and Settings are round icon
  buttons of the same height (the theme switch shows the current theme's icon instead of a sliding knob), and
  the date range and filter sit under it as a plain row instead of a boxed bar. On a phone the controls wrap
  under the project name, which no longer sits under the menu button.
- **A new typeface, Plus Jakarta Sans**, across the dashboard, login page, setup wizard and landing site, bundled
  with the app (SIL Open Font License) so no page calls a font service. Chart labels and tooltips use it too.
- **"Views over time" is a dot-matrix chart**: one column of dots per day or hour, the tallest in the accent
  with a dashed guide to the axis; hovering a column moves the accent and shows its value.
- **The landing page shows real screenshots of the dashboard** in its hero and in each of the seven feature
  tabs, in the light or dark version to match the page's theme. They replace hand-drawn mock-ups and seven
  illustrations that were missing from the site.
- **The landing pages share the dashboard's new look.** Same graphite surfaces and coral accent in both themes,
  a dark hero in place of the amber band, pill buttons, and the bundled Inter typeface. Their colours and sizes
  now come from one file (`landing/variables.css`) and the pages carry no inline styles. The text is unchanged.
- **A new look for the dashboard, login page and setup wizard.** Neutral graphite surfaces in place of the
  brown-tinted ones, one coral accent in place of amber mixed with indigo, flat cards with hairline borders and
  rounder corners, pill-shaped buttons, sentence-case labels, and numbers in the text colour with a small tinted
  icon badge per card. Sections sit directly on the page instead of being a card around cards. Bars are grey
  with only the leading one in the accent, badges are tinted pills, and checkboxes take the accent. The
  dashboard now loads the bundled Inter typeface, as the login page and wizard already did. Both themes are
  redone and pass the WCAG AA contrast checks. The default heatmap colour is now the accent ("Ember"); green,
  purple and orange are unchanged.
- **The dashboard, login page and setup wizard take every colour, size and shadow from one place**
  (`css/variables.css`, with `css/themes.css` for the dark theme), instead of values scattered over the
  stylesheets, the pages and the scripts. Nothing is meant to look different. A few sizes moved by less than a
  pixel onto the common scale (13.6px and 14.4px text is now 14px, 12.8px is 13px, 17.6px is 18px), and all code
  and keys use the same monospace font.

## [1.0.0] - 2026-11-22

The first public release of Mini Numbers. It includes the security fixes from the audit of 2026-11-14.

**Upgrading an install made before this release**

- The database is upgraded by itself at start. Use the image tag `1.0.0` or `latest`.
- **The setup wizard now asks for a setup token** on a fresh install. It is printed in the server log at start
  (`docker compose logs mini-numbers | grep "Setup token"`), or set your own with `SETUP_TOKEN`. Installs that are
  already configured are not affected.
- **Signing out now also ends that user's API tokens.** A script that uses `/api/token` with the same account
  as someone who signs out of the dashboard has to request a new token.
- **Counts can drop a little:** crawlers that announce themselves are no longer stored.
- Sites pick up the new tracker by themselves. Referrers and outbound/download addresses are stored without their
  query string from then on.
- **Widgets (lab) are now off for every project until you switch them on**, in Settings → Project information →
  **Embeddable widgets**. If you have `LABS=widgets` and widgets on your pages, they show nothing after the
  upgrade until you do.
- **Funnels and segments are on for everyone.** Remove `funnels` and `segments` from `LABS` if you had them there;
  left in, they are ignored with a warning in the log.
- One database change, applied by itself at start: a `widgets_enabled` column on `projects`.

### Added

- **Funnels and visitor segments are standard features**, no longer labs: they show in **Conversions** on every
  server without a `LABS` setting.
- **Segments can be edited.** The pencil in **Manage segments** loads a segment into the form; saving
  replaces its name, description and filters (`PUT /admin/projects/{id}/segments/{segmentId}`).
- **Funnels can be edited.** The pencil in **Manage funnels** loads a funnel into the form; saving replaces
  its name and steps (`PUT /admin/projects/{id}/funnels/{funnelId}`).

### Changed

- **Funnel bars now show each step as a share of the visits that entered the funnel**, so the first step
  is always 100%. Before, they were a share of every visit to the site, which made a funnel starting on a quiet
  page look empty. `conversionRate` in the funnel analysis response changes the same way; `totalSessions` still
  counts every session of the period.

### Security

- **Widgets (lab) no longer answer to the tracking ID alone.** The ID is in every tracked page's HTML, so with the
  lab on, anyone could read any project's visitor and page-view numbers. Each project now has its own switch, off
  by default (`GET|PUT /admin/projects/{id}/widgets`); only an admin can change it.

- **Large request bodies could crash the server.** A handful of 60 MB posts to `/collect` or `/api/login` (no login
  needed) exhausted the memory and left the server running but unresponsive. Bodies over 64 KB are now refused
  with 413 before they are read.
- **A failed start reopened the setup wizard's save.** When the server could not start with its configuration
  (database unreachable, for example), anyone could post a new configuration and replace `SERVER_SALT`, which
  allowed forging an admin sign-in after the next restart. Saving is now refused as soon as a configuration
  exists. To change one, edit `.env` or the environment and restart.
- **The first visitor to a fresh install could claim it.** The wizard's save now needs the setup token.
- **Anyone could lock the admin out** with five wrong passwords, again and again. The 15-minute lock now applies to
  the address the wrong passwords came from, not to the account for everyone.
- **API tokens outlived a password change and a sign-out** by up to 15 minutes (refresh tokens outlived a sign-out
  until they expired). Both now end every token of that user.
- `/health` and the start page no longer show why a start failed; the reason is in the server log.

### Fixed

- **Visitor addresses appeared in the server log** when a rate limit was hit, against the promise that IP addresses
  are never written anywhere. The warning now names the limit only.
- The tracker sent the full address of clicked outbound links and downloads, and the full referrer, query strings
  included (they can carry tokens or search terms). Only the address up to the path is sent now. A referrer that is
  not a web address (an Android app, for example) no longer makes the server reject the page view.
- `/collect` accepted any text as a link target or as custom-event properties. The target must be a web address and
  properties a JSON object.
- Crawlers that announce themselves in their User-Agent were counted as visitors.
- Segments: **OR between two filters was ignored** (the dialog stored it on the wrong filter, so every
  segment behaved as AND); a project without segments had no way to create the first one, because the card with
  the **Manage segments** button was hidden; after switching projects one click could create a segment several
  times; a refused save closed the dialog; a failed delete was reported as done. Names over 100 characters now
  get a clear message instead of a database error.
- Funnels: **the drop-off and average time between two steps were shown one step too early**, so the first
  gap always read "0% drop-off" and the last gap's figures never appeared; removing a step left a hole in the
  numbering ("Step 1, Step 2, Step 4"); a failed delete went unreported; a step with a blank name or match value
  could be saved through the API, and over-long names or match values gave a database error instead of a message.
  One session is now "1 session".
- Revenue (lab): **totals stopped at 21,474,836.47**, whatever was sold beyond that; an amount written as `1e3`
  counted as 1, `"12abc"` as 12, and a `revenue` nested inside another property was counted too (only a top-level
  number or numeric string counts now; negative amounts are still left out); in **revenue by source** two
  purchases in one visit counted as one transaction, so the column did not add up to the total.
- **After renaming a project the sidebar kept the old name, and after rotating its key Settings could show the
  old key again**, until the page was reloaded. Both now show at once.
- Widgets (lab): **the embed from the docs left its placeholder empty.** `data-target` was read as a CSS selector,
  so the documented `data-target="visitors-now"` matched nothing and the widget was added after the script
  instead. It now takes an element id or a selector.
- Webhooks (lab): a webhook could be created through the API with no events or unknown ones (it then never
  fired), and an address that was malformed or longer than 1,024 characters was stored or gave a database error.
  These are now refused with a message.
- Email reports (lab): **a mail server that stopped answering held the sender for ever**, so no later report went
  out until a restart; sending now gives up after 15 seconds. A recipient such as `@`, a list of addresses or
  `Name <address>` was accepted and then failed (or went to several people) at send time, and an update could set
  a schedule that never fires; both are now refused.
- CSV exports: a cell that starts with `=`, `+`, `-` or `@` is now prefixed with an apostrophe, so a spreadsheet
  does not run it as a formula.
- When the wizard's configuration does not start (wrong database password, for example) nothing is saved and
  the wizard can be submitted again; before, the half-working configuration stayed.
- `src/main/.claude/settings.local.json` (local tool settings) is no longer tracked.
