# KH

A native Android app for kisskh.co. It is built for Chromecast with Google TV and also
runs on phones. It talks directly to the site's JSON API instead of wrapping the website.

Version 1.1.

## Features

- Home with a billboard that follows the focused title, and rows for Continue Watching,
  Latest, Popular, Top Rating, Anime, Upcoming, Korean Series, Chinese Series and Movies
- TV Series, Movies and Countries, with filters and paging
- Search as you type
- Title page with synopsis and an episode list (ranges on long shows)
- Playback with Media3: HLS and direct video, subtitles, resume
- Phone layout with a bottom bar, and landscape playback

## How it works

The stream and subtitle endpoints require a `kkey` that the site generates in its
obfuscated `common.js`. The app runs that script in a hidden WebView (the same V8 engine
a browser uses) on a kisskh.co-origin page, and auto-detects the key function by its
output shape. Because it fetches the live `common.js` (with a bundled copy as offline
fallback), the app keeps working if the site rotates its cipher. Encrypted subtitles are
decrypted with AES-CBC using keys recovered from the site's bundle (see `API.md`).

## Build

Requires JDK 21 and the Android SDK (platform 35, build-tools 35). `local.properties`
must point `sdk.dir` at the SDK.

```
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Install

```
adb install app/build/outputs/apk/debug/app-debug.apk
```

A debug APK is signed with the debug keystore on the machine that built it. To upgrade
the v1.0 install and keep saved playback positions, sign with the same debug key used
for that build. A different key will not install over the existing app.

## Disclaimer

For personal and educational use. This project is not affiliated with kisskh.co. It only
reads the site's public API; it hosts no content.
