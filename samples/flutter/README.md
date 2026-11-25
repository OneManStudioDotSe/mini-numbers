# Corner Shop (Flutter)

A pretend shop that sends screen views and events to Mini Numbers. It follows `../SPEC.md`; the tracker is the
single file `lib/mini_numbers.dart` (`dart:io` + `dart:convert`, no `http` package). Copy it into your own app.

## Run

```
flutter pub get
flutter run -d <device> \
  --dart-define=SERVER_URL=http://localhost:8092 \
  --dart-define=PROJECT_KEY=<your project key>
```

The two `--dart-define`s are optional developer overrides. Without them the app uses the Settings tab (stored on
the device; defaults `http://localhost:8092` on iOS and `http://10.0.2.2:8092` on Android, empty key). With no
key nothing is sent and the log shows `no key`. Plain HTTP is allowed only for localhost, 127.0.0.1 and 10.0.2.2
(Android debug network security config, iOS `NSAllowsLocalNetworking`); use HTTPS for a real server.

Needs Flutter 3.50 / Dart 3.14. Android minSdk 26, iOS 17.

## Test

```
flutter analyze
flutter test                          # payload builder
flutter test integration_test -d <device> \
  --dart-define=SERVER_URL=... --dart-define=PROJECT_KEY=...   # walks the UI against a real server
```

## Layout

`lib/mini_numbers.dart` tracker, `lib/theme.dart` all tokens, `lib/components.dart` Card, PillButton, IconBadge,
StatTile, LogRow, Field, ToggleRow, one file per screen, `lib/main.dart` the tab shell.
Fonts in `assets/fonts` (Plus Jakarta Sans, OFL; Remix Icon, Apache-2.0; licence files alongside).
