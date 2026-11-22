# Mini Numbers landing site

Six plain HTML pages. No build step, no server code, nothing loaded from other sites.

## Hosting it

Copy this folder anywhere a web server can reach it: the root of a domain, a sub-folder, GitHub Pages, Netlify,
an S3 bucket. Every link, font, icon and picture uses a path relative to the page, so the site does not care where
it lives. It even works opened straight from disk.

It does not need the Mini Numbers app, and the app does not need it.

## What's here

| | |
|---|---|
| `index.html` | The home page |
| `how-it-works.html`, `setup-guide.html`, `contact.html`, `privacy-policy.html`, `terms.html` | The other pages |
| `variables.css` | Every colour and size, light and dark. Change the look here |
| `landing.css` | The rules. No colours or sizes of its own |
| `landing.js` | Theme switch, tabs, copy buttons, the scroll effects. No dependencies |
| `assets/fonts/` | Plus Jakarta Sans and Inter (SIL Open Font License) |
| `assets/icons/` | The Remix Icon font (Apache 2.0) and a stylesheet listing only the icons in use |
| `assets/screens/` | Screenshots of the dashboard, one per theme |

## Changing things

- **A new icon:** copy its rule from the full `remixicon.css` (version 4.6.0) into `assets/icons/remixicon.css`.
- **Keep links relative.** `contact.html`, not `/contact.html`, or the site stops working in a sub-folder.
- **The version badge** in the home page's menu is typed by hand: update it when you release.
