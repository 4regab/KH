# KH (kisskh-tv) handoff

## What this is
Native Android app for kisskh.co, built for Chromecast with Google TV and also usable on phones.
Kotlin, Jetpack Compose, Compose for TV Material, Media3 ExoPlayer, Coil, Retrofit/Moshi/OkHttp.
Package and applicationId stay `com.kisskh.tv` (keeps installs and saved data compatible).
The visible brand is being changed to "KH".

## State right now
- Branch `kh-netflix` implements the Netflix-style TV and phone UI described in the spec below.
  Version is `versionName` 1.1, `versionCode` 2.
- The last version confirmed working on a device before this redesign was the original "KissKH TV"
  UI (commit `cc99857`): playback, subtitles, resume, touch taps and phone layout all worked then.
- The first KH redesign pass was rejected as generic "Disney+-like" (titles under TV cards, episode
  chip grids, a rail that always shows labels). Do not ship that look.

## Working parts you should keep
- `api/KeyService.kt`: makes the `kkey` that the stream and subtitle endpoints need. It runs the site's live
  `common.js` in a hidden WebView and picks the key function by its output shape. Do not swap in Rhino:
  it failed (parse error, then an endless self-defense loop).
- `api/SubtitleCrypto.kt`: AES-CBC keys for `.txt`, `.txt1` and other encrypted subtitle files.
- `api/Repository.kt`: `streamUrl()` returns null for type 2 (countdown, not released yet).
  Type 0 is a direct mp4, type 1 is HLS.
- `player/VideoPlayer.kt`: sends Referer/Origin headers and loads subtitles as local SRT files.
- `api/Catalog.kt`, `api/CatalogPager.kt`, `ui/SearchController.kt`, `api/WatchHistory.kt`, `ui/Navigation.kt`:
  these hold the category mapping, paging (with stale-response rejection), debounced search, watch history
  and the back stack. JVM unit tests for them are in `app/src/test`.
- `ui/Touch.kt`: tv-material3 Card and Button only react to the remote's OK key, never to touch.
  `Modifier.onTap` adds touch taps for phones.
- `ui/theme/*`: KH design tokens (colors, type, spacing, focus, motion, form factor).

## Verified catalog facts
Full details are in `API.md` ("Verified catalog mapping") and `docs/categories*.txt`.
- type: 1 TV Series, 2 Movie, 3 Anime, 4 Hollywood (US only, mixes series and movies)
- country, in the site's order: 2 South Korea, 1 China, 6 United States, 5 Thailand, 8 Philippines,
  3 Japan, 4 Hong Kong, 7 Taiwan. Do not offer 9.
- status: 0 All, 1 Ongoing, 2 Completed, 3 Upcoming
- order: 1 Popular, 2 Last Update, 3 Release Date. There is no rating sort.
- These endpoints are global and accept no filters: LastUpdate, MostView, TopRating, Upcoming, Animate, Show.
  Never label them as series-only, movie-only or country-specific.
- Some movies have several parts. Never guess type from the episode count.
  Episode lists arrive newest first and can have fractional numbers (29.6).

## Build
- JDK 21: `C:\Users\james\chrome-tv\toolchain\jdk-21.0.12.1+1`
- Android SDK: `C:\Users\james\chrome-tv\toolchain\sdk`
- Gradle wrapper (8.13) is in the repo:
  `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
- Background runner, because builds take 1-3 min:
  `C:\Users\james\chrome-tv\toolchain\run-gradle.ps1 -Tasks "..." -Log <file>`
- The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
- `local.properties` (sdk.dir) is gitignored. Recreate it on a new machine.

## Devices
- Phone: vivo V2205, Android 14, USB serial `10ACCQ2YXM002B6`.
  `svc power stayon usb` was turned on for testing. Undo it with `adb shell svc power stayon false`.
  Auto-rotate may have been changed. Restore it with `adb shell settings put system accelerometer_rotation 1`.
- Chromecast with Google TV: wireless ADB at `192.168.16.105:5555`. It was refusing connections at the end.
  Turn wireless debugging back on in its Developer options.

## Releases
GitHub repo `4regab/kisskh-tv` is PRIVATE. Release `v1.0` holds the last working APK
(original UI with phone fixes).

## Spec for the next agent
The binding spec is `C:\Users\james\chrome-tv\toolchain\KH_BRIEF.md`. It is outside the repo, so its key points are summarized here:
- TV: a collapsed left icon rail that expands as an overlay. Home is a Netflix-style billboard whose art
  and info follow the focused card. Rows keep the focused card fixed at the left edge. Cards show only
  16:9 artwork, focus at 1.08 scale with a white border. Netflix-style title page with an episode panel
  (episode ranges plus a row list). Netflix-like player controls.
- Phone: top app bar, category pills, bottom navigation, rounded hero card, cards with titles underneath,
  full-screen landscape player.
- Tokens: background #09090B, surface #17171B, elevated surface #232329, red #F52236, text #F5F5F7 / #B5B5BF.
  Animations use a strong ease-out curve (0.23, 1, 0.32, 1), 150-220 ms, never starting from scale 0.
- No fake data, no buttons that do nothing, no Netflix logo or typeface.
