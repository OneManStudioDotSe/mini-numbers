# Corner Shop: the spec the three sample apps follow

The Android, iOS and Flutter samples are the same app. This file is the single description they are built from,
so a change here is a change to all three.

## What the app is

A pretend shop with three tabs. It exists to show how a native app sends screen views and events to Mini Numbers.
Nothing is really sold and nothing is stored except the settings.

## Tracking contract (what every app sends)

`POST {baseUrl}/collect` with headers `Content-Type: application/json`, `X-Project-Key: {projectKey}` and
`User-Agent` as below. Success is `202`. The body is JSON:

| Call | `type` | `path` | Other fields |
|---|---|---|---|
| `screen(path)` | `pageview` | the screen's path | |
| heartbeat (every 30 s in the foreground) | `heartbeat` | the current screen's path | |
| `event(name, properties)` | `custom` | the current screen's path | `eventName`, `properties` |
| `purchase(amount, currency)` | `custom` | current path | `eventName: "purchase"`, `properties: {"revenue": amount, "currency": currency}` |
| `refund(amount, currency)` | `custom` | current path | `eventName: "refund"`, `properties: {"revenue": -amount, "currency": currency}` |
| `outbound(url)` | `outbound` | current path | `eventName`: the URL's host, `targetUrl`: the URL |

Rules the tracker file must follow and explain in comments:

- `sessionId` is on every event: 32 random lowercase hex characters, made when the app starts and again on
  "Start a new session". It is kept in memory only.
- `properties` is a **string holding JSON** (`"{\"plan\":\"free\"}"`), not a nested object. Omit it when empty.
- Never send `referrer`. Never send `eventName` on `pageview` or `heartbeat` (the server rejects it).
- `path` starts with `/` and has no spaces.
- **User-Agent.** Android: `MiniNumbersSample/1.0 (Linux; Android {release}; {model}) Mobile`. iOS:
  `MiniNumbersSample/1.0 (iPhone; CPU iPhone OS {major}_{minor} like Mac OS X) Mobile`. Flutter uses the one for
  the platform it runs on. Without it the device shows as "Unknown"; a name containing "bot", "crawl" or "spider"
  is answered with `202` and silently dropped.
- **Opt-out.** While "Don't track me" is on, nothing is sent at all and the log shows the event as "not sent".
- A failed send (no network, non-202) is shown in the log with its status or error. It is not retried.
- No third-party networking or JSON library.

Tracker API, same names everywhere (adapted to each language's casing):
`configure(baseUrl, projectKey)`, `screen(path)`, `event(name, properties)`, `purchase(amount, currency)`,
`refund(amount, currency)`, `outbound(url)`, `startHeartbeat()`, `stopHeartbeat()`, `optOut`, `newSession()`,
plus an observable `log` (newest first, at most 50 entries) of
`{time, type, label, status}` where status is the HTTP code, `"not sent"` or a short error.

## Screens

Bottom tab bar with three tabs, in this order. The selected tab's icon and label use the primary colour.

### 1. Shop — path `/shop`, icon store-2-line

- Screen title "Corner Shop", subtitle "Three things, none of them real."
- A card per product, each a row: icon badge, then the name (card title style) with the price under it (label
  style, textSecondary), then a chevron (arrow-right-s-line) at the right edge. Tapping opens the detail.

| id | Name | Price | Icon | Badge colour |
|---|---|---|---|---|
| `mug` | Mug | 12.00 EUR | cup-line | badge 1 |
| `notebook` | Notebook | 8.50 EUR | book-open-line | badge 2 |
| `t-shirt` | T-shirt | 24.00 USD | t-shirt-line | badge 3 |

Prices are shown as `€12.00`, `€8.50`, `$24.00`. Two currencies on purpose: the dashboard keeps them apart.

### Product detail — path `/shop/{id}`

- Back button (arrow-left-s-line) with the label "Shop".
- The tab bar stays visible, with Shop selected.
- One card: large icon badge, name (screen title style, 28/500), price (figure), one line of text: "Buying sends a purchase event with
  the price as revenue. Refunding sends the same amount as a negative."
- Under the card, not inside it: primary pill button "Buy" (shopping-bag-3-line) → `purchase(price, currency)`.
- Ghost pill button "Refund" (refund-2-line) → `refund(price, currency)`.
- After either, a line under the buttons: "Sent purchase · €12.00" or "Sent refund · -€12.00" in the muted colour.

### 2. Events — path `/events`, icon pulse-line

- Title "Events", subtitle "Tap a button, watch it go."
- Two stat tiles side by side: "Sent" (count of events answered 202 this run) and "Session" (first 8 characters of
  the session id).
- Card "Try it" with two full-width buttons:
  - Primary "Sign up" (user-add-line) → `event("signup", {"plan": "free", "source": "sample-app"})`.
  - Ghost "Open the docs" (external-link-line) → `outbound("https://github.com/OneManStudioDotSe/mini-numbers")`,
    then opens that address in the system browser.
- Card "Log": the tracker's log, newest first. Each row: type badge (`pageview`, `heartbeat`, `custom`,
  `outbound`), label (path, event name or host), time `HH:mm:ss`, and the status on the right: `202` in the
  success colour, anything else in the danger colour, `not sent` in the muted colour. Empty state: "Nothing sent
  yet."

### 3. Settings — path `/settings`, icon settings-3-line

- Title "Settings", subtitle "Where the events go."
- Card "Server": field "Server address" and field "Project key" (monospace), then a primary button "Save". Saving
  stores both on the device and calls `configure`. Under the button, muted text: "Find the key in the dashboard
  under Settings → Project → Tracking ID."
- Card "Privacy": toggle row "Don't track me" with the text "While this is on, the app sends nothing." The choice
  is stored on the device, like the two settings, so it survives a restart.
- Card "Session": the full session id (monospace, muted) and a ghost button "Start a new session"
  (refresh-line).

Defaults before anything is saved: server address `http://10.0.2.2:8092` on Android (the emulator's name for the
host machine) and `http://localhost:8092` on iOS; project key empty. With an empty key nothing is sent and the log
row says `no key`.

### Lifecycle

- Entering a tab or the product detail calls `screen(path)` once. Going back from the detail calls
  `screen("/shop")`.
- The heartbeat starts when the app comes to the foreground and stops when it leaves.

## Design system (mirrors `src/main/resources/static/css/variables.css` and `themes.css`)

Light or dark follows the system setting. One theme file per app holds all of it; screens never write a colour or
a size directly.

| Token | Light | Dark |
|---|---|---|
| primary | `#c9340f` | `#ff6e52` |
| onPrimary | `#ffffff` | `#190a06` |
| bgPrimary (screen) | `#f4f4f5` | `#0e0e0f` |
| bgSecondary (cards, tab bar) | `#ffffff` | `#19191b` |
| bgTertiary (fields, ghost button, stat tile) | `#eeeef0` | `#232326` |
| textPrimary | `#151517` | `#f5f5f6` |
| textSecondary | `#4a4a52` | `#b6b6bd` |
| textMuted | `#6a6a73` | `#9797a0` |
| border | `#e3e3e7` | `#29292d` |
| success | `#0b7a53` | `#3ecf8e` |
| danger | `#c81e4a` | `#ff6b8a` |
| badge1 (ember) | `#c9340f` | `#ff6e52` |
| badge2 (blue) | `#2f7df6` | `#5aa7ff` |
| badge3 (green) | `#12a36d` | `#3ecf8e` |
| badge4 (amber) | `#c87a00` | `#f5a524` |
| badgeTint (badge background opacity) | 0.12 | 0.16 |

- **Spacing**: xs 4, sm 8, smPlus 12, md 16, lg 24, xl 32.
- **Radii**: sm 8, md 12 (fields), lg 16 (cards, stat tiles), full (buttons, badges on log rows).
- **Type**: Plus Jakarta Sans (`PlusJakartaSans-Regular/Medium/SemiBold/Bold.ttf`). Screen title 28/500, card
  title 17/500, figure 28/500, body 15/400, label 13/500, small 12/400. Letter spacing is 0 everywhere. Monospace: the system monospace font.
- **Icons**: `remixicon.ttf`, drawn as text. Codepoints: store-2-line `F1A5`, pulse-line `F035`, settings-3-line
  `F0E6`, cup-line `EC06`, book-open-line `EADB`, t-shirt-line `F1D9`, arrow-left-s-line `EA64`,
  arrow-right-s-line `EA6E`, external-link-line `ECAF`, user-add-line `F25E`, shopping-bag-3-line `F116`,
  refund-2-line `F066`, refresh-line `F064`.

Components, same names in each app:

- `Card`: bgSecondary, 1 px border, radius lg, padding md, no shadow. Optional title (card title style) above the
  content with sm spacing.
- `PillButton`: height 44, radius full, label 15/600, optional leading icon 18. `primary`: primary background,
  onPrimary text. `ghost`: bgTertiary background, textPrimary text. Full width inside cards.
- `IconBadge`: 40 × 40 (56 × 56 "large"), radius md, background = badge colour at badgeTint, icon in the badge
  colour, 20 (28 large).
- `StatTile`: bgSecondary, 1 px border, radius lg, padding md; label (label style, textSecondary) above the figure.
- `LogRow`: type pill (bgTertiary, radius full, small 12/500), label (body, one line, truncated), time (small,
  muted), status right-aligned (label style).
- `Field`: label (label style, textSecondary) above an input: bgTertiary, radius md, height 44, padding md, 1 px
  border; the border is primary while focused.
- `ToggleRow`: title and text on the left, the platform's switch on the right, tinted primary.

Screen layout: bgPrimary behind everything; content padded lg horizontally and md between cards; the screen title
block has lg above and md below; the tab bar is bgSecondary with a 1 px top border.

## Local network in debug builds

Plain HTTP is allowed only for `localhost`, `127.0.0.1` and `10.0.2.2`, so the samples can talk to a server on
your own machine. A real server should be reached over HTTPS.
