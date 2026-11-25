# Corner Shop (iOS sample)

A pretend shop that shows how a native iOS app sends screen views and events to Mini Numbers. Swift, SwiftUI,
iOS 17+, no third-party dependency. The tracker is one file you can copy: `CornerShop/MiniNumbers.swift`.

The screens, texts and tracking contract are described in [`../SPEC.md`](../SPEC.md).

## Run it

1. Open `CornerShop.xcodeproj` in Xcode and run the `CornerShop` scheme on a simulator.
2. Open the Settings tab, paste your project key (dashboard: Settings, Project, Tracking ID), and press Save.
   The server address defaults to `http://localhost:8092`.

From the command line:

```sh
xcodebuild -project CornerShop.xcodeproj -scheme CornerShop \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
xcrun simctl install booted <path to CornerShop.app>
xcrun simctl launch booted se.onemanstudio.mininumbers.sample.ios -projectKey YOUR_KEY -serverUrl http://localhost:8092
```

Settings are stored in `UserDefaults` under `serverUrl` and `projectKey`, so launch arguments override them.

## Tests

- `CornerShopTests`: unit tests for the payload builder.
- `CornerShopUITests`: walks Shop, product, Buy, Refund, Events, Sign up, Open the docs, Settings, "Don't track me".
  It sends real events, so pass a key: `TEST_RUNNER_PROJECT_KEY=YOUR_KEY xcodebuild ... test`.

## Notes

- Plain HTTP is allowed for local networking only (`NSAllowsLocalNetworking`). Use HTTPS for a real server.
- Debug builds accept `-startScreen shop|detail|events|settings`, handy for screenshots.
- Fonts: Plus Jakarta Sans (SIL OFL) and Remix Icon (Apache 2.0); licences are next to them in `CornerShop/Fonts`.
