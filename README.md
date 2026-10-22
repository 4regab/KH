# KH

A native Android app for phones and Android TV (Chromecast with Google TV). It browses and plays
from several sources through one design, and talks to each site's own data instead of wrapping
the website.

Version 1.3.

## Sources

Pick one with the source button at the top of the phone screen, or from the TV rail.

| Source | Catalog | Playback |
| --- | --- | --- |
| KissKH | Asian dramas, movies, anime; country, status and sort filters | Native player (HLS and MP4), decrypted subtitles |
| CineWave | Movies, TV and anime (TMDB); country filter | Embed servers. A server whose stream can be found plays in the native player; the rest play in their own web page |
| CineJoy | Movies and TV (TMDB); country filter | Febbox (with an optional token) in the native player, or CineJoy's own page |
| Reelix | Movies, TV and anime | VidCore and the site's embed servers |
| Anisuge | Anime, sub and dub | MegaPlay streams in the native player, other servers in their page |
| Anime Nexus | Anime | The site's own watch page |

## Features

- Home with a large featured title, Continue Watching (with progress) and My List rows, then the
  source's own rows. Every row uses the same card style.
- Movies, TV Shows and Anime grids with paging. Filter by country (South Korea, China, Japan,
  Thailand, the Philippines, Hong Kong, Taiwan, the United States and more) like KissKH.
- Search one source or every source at once.
- Title page with Play or Resume, seasons and episodes, **My List**, **Watched** (for the title and
  for each episode) and **Download**.
- **Downloads**: save a movie or episode to the phone at the best quality the source offers,
  with its subtitles, and watch it later with no connection. Start one from the Download button
  beside My List (movies) or beside an episode. Progress shows in a notification.
- **My KH** tab: Downloads, My List and Watched across all sources.
- Player: play and pause, 10 s skip (double tap on a phone), seek bar, subtitles on or off and by
  language, audio and quality choice, previous and next episode, server list with failed servers
  marked, resume where you stopped. The best quality is always chosen. Movies and TV start with
  English audio and subtitles when the stream has them; anime keeps its original audio.
- Leaving the app pauses the video. The screen stays on and the system bars hide while watching.

## Limits

- Servers that only play inside their own web page can't be downloaded, and their controls are
  the site's own.
- Sources are third-party sites. When one is down or changes its pages, its rows or servers fail
  with a message and a Retry button.

## How KissKH works

The stream and subtitle endpoints need a `kkey` that the site makes in its obfuscated
`common.js`. The app runs that script in a hidden WebView on a kisskh.co page and finds the key
function by its output shape. It fetches the live `common.js` (with a bundled copy as fallback),
so it keeps working if the site changes its cipher. Encrypted subtitles are decrypted with
AES-CBC using keys from the site's bundle (see `API.md`).

## Build

Requires JDK 21 and the Android SDK (platform 35, build-tools 35). `local.properties` must point
`sdk.dir` at the SDK.

```
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Install

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` upgrades in place and keeps saved positions, lists and downloads. The APK must be signed
with the same key as the installed one; releases use the same debug key as earlier versions.

## Disclaimer

For personal and educational use. This project is not affiliated with any of the sites above. It
reads their public pages and APIs and hosts no content.
