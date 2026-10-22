# KH

KH is an Android app for phones and Android TV. It browses and plays titles from several streaming
sources in one design. It reads each site's own data and does not wrap the website.

Version 3.1. Package `com.kisskh.tv`. Android 6.0 (API 23) or newer.

## Sources

Pick a source with the source pill at the top of the phone screen, or at the top of the TV rail.
The app opens on CineWave.

| Source | Catalog | Playback |
| --- | --- | --- |
| CineWave | Movies, TV and anime (TMDB) | Embed servers. A server whose stream can be found plays in the native player; the rest play in their own web page |
| CineJoy | Movies and TV (TMDB) | Febbox (with an optional token) in the native player, or CineJoy's own page |
| Reelix | Movies, TV and anime | VidCore and the site's embed servers |
| Anisuge | Anime, sub and dub | MegaPlay streams in the native player, other servers in their page |
| Anime Nexus | Anime | The site's own watch page |
| KissKH | Asian dramas, movies, anime | Native player (HLS and MP4), decrypted subtitles |

## Install

1. Download `app-release.apk` from the latest release on the GitHub Releases page of this
   repository.
2. Phone: open the file and allow installs from your browser or file manager when asked.
3. Android TV: sideload the APK, for example with `adb connect <tv-ip>` then
   `adb install -r app-release.apk`, or with a file manager app such as Send Files to TV.

`-r` upgrades in place and keeps saved positions, lists and downloads. An update must be signed with
the same key as the installed app.

## Features

### Home

- TV: a Netflix-style billboard at the top that follows the focused card. After focus rests for a
  moment it loads the new picture and shows that title's name, facts and overview. The rows sit
  under it, with the focused row pinned to a fixed spot.
- Phone: a featured title, a row of category chips (Movies, TV, Anime, Countries) under the top
  bar, then the rows. The top bar turns solid as the page scrolls.
- Rows: Continue Watching (with progress), My List, then the source's own rows. All rows use the same
  cards.

### Browse and filters

Movies, TV Shows and Anime grids with paging. Each grid shows only the filters its source really
supports, so no button on screen does nothing:

| Source | Country | Genre | Year | Status | Orders |
| --- | --- | --- | --- | --- | --- |
| KissKH | yes | no | no | TV series only | Popular, Recently updated, Newest |
| CineWave, CineJoy, Reelix (movies and TV) | yes | yes | yes | no | Popular, Top rated, Newest |
| CineWave, CineJoy, Reelix (anime) | no | yes | yes | no | Popular, Top rated, Newest |
| Anisuge | no | yes | yes | yes | Popular, Top rated, Newest, Recently updated |
| Anime Nexus | no | yes | no | yes | Newest, Recently updated |

The reason for each gap is in `api/source/BrowseFilters.kt`.

### Search

Search one source or all sources at once. Before you type, the page shows the source's trending
titles. Results carry the source name when you search all sources.

### Title page

A Disney+-style page:

- Key art, the title logo (text until the logo loads), year, rating badge, length, seasons or
  episodes, score, genres and synopsis. Missing facts are left out.
- Play or Resume, **My List**, **Watched** and **Download**.
- Episodes with a still, name, length, description and a Watched check. Episodes that have not aired
  show "Coming" with the date and cannot be played or downloaded. Seasons are chips, or a dropdown
  above six.
- Cast (TMDB sources) and More like this.
- Titles that can't be played yet are marked "Coming soon" or "Not available".

### My KH, My List, Watched

The My KH tab holds Downloads, My List and Watched across all sources.

### Downloads

Save a movie or an episode to the device at the best quality the source offers, with subtitles, and
watch it later offline. Progress shows in a notification. Downloads run one at a time. Servers that
play only inside their own web page can't be downloaded.

### Player

- Native player (Media3 ExoPlayer): play and pause, 10 s skip (double tap on a phone), seek bar,
  subtitles, audio and quality choice, previous and next episode, server list with failed servers
  marked, resume where you stopped, pinch or Fit/Fill zoom.
- English by default: movies and TV start with English audio and subtitles when the stream has
  them, and servers whose audio has no English are skipped. Anime keeps its original audio or the
  source's own sub or dub setting.
- Ad and popup blocking in web players (`player/AdShield.kt`): `window.open` is blocked, clicks that
  open a new tab are cancelled, and layers drawn over the video are removed. It needs WebView 87 or
  newer. On older WebViews only the navigation rules apply.
- Leaving the app pauses the video. The screen stays on while watching.

### Look

One design for phone and TV. Phone: top bar, bottom navigation, cards with titles under them. TV: a
collapsed icon rail and focus that follows the remote. Design tokens are in `ui/theme`. The typeface
is Inter.

## Limits

- Sources are third-party sites. When one is down or changes its pages, its rows or servers fail
  with a message and a Retry button.
- In a web player the controls are the site's own.

## Build

Requires JDK 17 or newer (21 works) and the Android SDK (platform 35, build-tools 35).
`local.properties` must set `sdk.dir` (it is not in git).

```
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest :app:lintDebug
```

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`. It is shrunk with R8
  (`app/proguard-rules.pro`) and carries a baseline profile for faster starts. It is signed with the
  debug key.

Unit tests are plain JVM tests in `app/src/test`. They cover filters, paging, search, watch history,
saved titles, stream sniffing, the ad shield rules, the title page text rules, home layout and
playback formatting.

## Code map

All paths are under `app/src/main/java/com/kisskh/tv/`.

| Path | What is there |
| --- | --- |
| `api/` | KissKH client, key service, subtitle decryption, watch history, saved titles |
| `api/source/` | One file per source, title models, `BrowseFilters.kt` |
| `api/media/` | TMDB, Febbox, embed servers, stream sniffing, library and progress |
| `ui/shelf/` | Screens every source shares: home, browse, search, title page, top bar, navigation |
| `ui/shelf/detail/` | Title page parts: header, facts, episodes, cast and related, skeleton |
| `ui/shell/` | App frame, phone chrome, TV rail, source picker |
| `ui/theme/` | Colors, type, spacing, motion, focus |
| `ui/components/`, `ui/mine/`, `ui/media/` | Shared widgets, My KH, the CineJoy token screen |
| `player/` | Native player, embed web player, sniffing WebView, `AdShield.kt` |
| `download/` | Media3 downloads, the download service, offline player |

Stack: Kotlin, Jetpack Compose, Compose for TV Material, Media3, Coil, Retrofit, Moshi, OkHttp.

## Developer notes

- KissKH stream and subtitle calls need a `kkey` made by the site's obfuscated `common.js`.
  `api/KeyService.kt` runs that script in a hidden WebView and finds the key function by its output
  shape. It fetches the live script and falls back to the copy in `assets/`. Do not replace the
  WebView with Rhino; that was tried and failed. Subtitles are decrypted in `api/SubtitleCrypto.kt`.
  Endpoint details are in [API.md](API.md).
- KissKH stream type 0 is a direct MP4, type 1 is HLS, type 2 is a countdown (no stream yet).
  Episode lists arrive newest first with fractional numbers (29.6). Never guess a title's type from
  its episode count.
- KissKH LastUpdate, MostView, TopRating, Upcoming, Animate and Show are global and take no filters.
- tv-material3 cards and buttons react only to the remote's OK key. `Modifier.onTap` adds touch.
- Anime Nexus images are signed `.avif` paths; only the `.avif` URL loads.
- Release builds use R8. `proguard-rules.pro` keeps all `com.kisskh.tv` classes whole because Moshi
  and Retrofit read them by reflection and the WebView calls `@JavascriptInterface` methods by name.
  Test a release build on a device after you change models or API interfaces.
- Logcat tags: `KhPlayback` (one line per playback event) and `KhAdShield`.
- Coil has its own OkHttp client (`KhApplication.kt`) so a burst of posters can't hold up API calls.

## Third-party licenses

The Inter typeface is used under the SIL Open Font License 1.1. The license text is in
`app/src/main/assets/licenses/Inter-OFL.txt`.

## Disclaimer

For personal and educational use. This project is not affiliated with any of the sites above. It
reads their public pages and APIs and hosts no content.
