# KissKH TV

A native Android TV app for kisskh.co, built for Chromecast with Google TV. It talks
directly to the site's JSON API instead of wrapping the website, so it browses and plays
smoothly like YouTube — no web popups, banners, or redirect ads.

## Features

- Home screen with section rows: Latest, Popular, Top Rating, Anime, Upcoming
- Browse grid with infinite paging and filters (type, country, status, order)
- Search
- Detail screen with synopsis and full episode list (first episode auto-focused)
- Playback with Media3 ExoPlayer: HLS, hardware video/audio decode
- Subtitles: fetches and decrypts the site's encrypted subtitle tracks
- Resume: remembers your position per episode
- Leanback launcher entry with a TV banner

## How it works

The stream and subtitle endpoints require a `kkey` that the site generates in its
obfuscated `common.js`. The app runs that script in a hidden WebView (the same V8 engine
a browser uses) on a kisskh.co-origin page, and auto-detects the key function by its
output shape. Because it fetches the live `common.js` (with a bundled copy as offline
fallback), the app keeps working if the site rotates its cipher. Encrypted subtitles are
decrypted with AES-CBC using keys recovered from the site's bundle (see `API.md`).

## Build

Requires JDK 17+ and the Android SDK (platform 35, build-tools 35).

```
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Install on a TV

```
adb connect <tv-ip>:5555
adb install app-build/outputs/apk/debug/app-debug.apk
```

## Disclaimer

For personal and educational use. This project is not affiliated with kisskh.co. It only
reads the site's public API; it hosts no content.
