# Sample apps

Three small apps that send screen views and events to Mini Numbers from a phone. They are the same app, a
pretend shop called Corner Shop, written three times:

| Folder | Built with | The tracking code to copy |
|---|---|---|
| [`android/`](android/) | Kotlin, Jetpack Compose | `MiniNumbers.kt` |
| [`ios/`](ios/) | Swift, SwiftUI | `MiniNumbers.swift` |
| [`flutter/`](flutter/) | Dart, Flutter | `lib/mini_numbers.dart` |

Mini Numbers has no SDK for apps. These samples show that you don't need one: each app's tracking is one file of
about a hundred lines with no third-party library.

## What they show

- **Screen views**: a path per screen (`/shop`, `/shop/mug`, `/events`, `/settings`).
- **Custom events** with properties (`signup`).
- **Purchases and refunds**: a `purchase` event with `revenue` and `currency`, and a refund as a negative amount.
  They show up in the dashboard's revenue section (a lab: start the server with `LABS=revenue`).
- **Outbound links**.
- **A heartbeat** every 30 seconds while the app is open, which is what makes the visit show as live and gives
  sessions a length.
- **Opting out**: a switch that stops the app sending anything.
- A log on the Events tab that shows each event and what the server answered.

## Try one

1. Start Mini Numbers and create a project. Copy its key from Settings → Project → Tracking ID.
2. Build and start one of the apps (each folder's README has the commands).
3. In the app, open Settings, enter your server's address and the key, and tap Save.
4. Tap around, then look at the dashboard: Real-Time shows the visit, Content & Pages shows the screens.

For a server on your own computer, the Android emulator reaches it at `http://10.0.2.2:8080` and the iOS simulator
at `http://localhost:8080` (use your server's port). The debug builds allow plain HTTP for those addresses only.
Use HTTPS for a real server.

## Three things that differ from a website

1. **You make the session id.** 32 random hex characters when the app starts, sent with every event.
2. **Your User-Agent is how the server knows the device.** The apps send one like
   `MiniNumbersSample/1.0 (Linux; Android 14; Pixel 8) Mobile`. With an HTTP library's default the dashboard
   says "Unknown" device. Don't put "bot", "crawl" or "spider" in your app's name: the server answers such a
   request with `202` and throws it away.
3. **Opting out is your job.** The server reads no privacy header, so a "don't track me" switch means not
   sending.

The exact request, every screen and the design tokens are in [`SPEC.md`](SPEC.md). The three apps are built from
that one file, so they stay the same.

## Licences of what the apps bundle

Plus Jakarta Sans (SIL Open Font License 1.1) and Remix Icon (Apache 2.0); each app carries both licence files
next to the fonts. The apps themselves are MIT, like the rest of this repository.
