# Corner Shop: the Android sample

A pretend shop that shows how a native Android app sends screen views and events to Mini Numbers. Kotlin, Jetpack
Compose, Material 3, Navigation Compose. The tracker is one file with no third-party library:
[`MiniNumbers.kt`](app/src/main/java/se/onemanstudio/mininumbers/sample/MiniNumbers.kt). Copy it into your app.
The behaviour of all three samples is described in [`../SPEC.md`](../SPEC.md).

## Build and test

You need a JDK 17 or 21 and the Android SDK (put its path in `local.properties` as `sdk.dir=...`, Android Studio
does this for you).

```
./gradlew assembleDebug testDebugUnitTest
```

## Run

Start an emulator (or plug in a phone), then:

```
./gradlew installDebug
adb shell am start -n se.onemanstudio.mininumbers.sample.android/se.onemanstudio.mininumbers.sample.MainActivity \
  --es serverUrl http://10.0.2.2:8092 --es projectKey YOUR_PROJECT_KEY
```

`10.0.2.2` is the emulator's name for your computer. The two extras are optional and are saved as the settings;
without them, open the Settings tab, paste the key (Dashboard, Settings, Project, Tracking ID) and tap Save.
Debug builds allow plain HTTP to `localhost`, `127.0.0.1` and `10.0.2.2` only; use HTTPS for a real server.

## Licences

The code is MIT like the rest of the repository. Plus Jakarta Sans is under the SIL Open Font Licence
([`OFL-PlusJakartaSans.txt`](OFL-PlusJakartaSans.txt)); Remix Icon under its own licence
([`LICENSE-RemixIcon.txt`](LICENSE-RemixIcon.txt)).
