# GWStreams

An Xtream Codes IPTV player for Android — live TV, movies, series, EPG, and
thumbnails. Built with Kotlin, Jetpack Compose, ExoPlayer (Media3), and Coil.

## Getting your APK (two ways)

You need **one** of these. Option A is fastest.

### Option A — Android Studio (recommended)
1. Install [Android Studio](https://developer.android.com/studio) (free).
2. **File → Open** and select this `gwstreams` folder.
3. Wait for Gradle to sync (first sync downloads the SDK + dependencies — a few minutes).
4. Plug in your phone with USB debugging on, **or** create an emulator.
5. Press the green **Run** button. The app installs and launches.

To get a shareable APK file instead:
**Build → Build Bundle(s) / APK(s) → Build APK(s)**.
The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.
Copy that to any phone and install it (allow "install from unknown sources").

### Option B — command line
From this folder, with the Android SDK installed and `ANDROID_HOME` set:
```
./gradlew assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Using the app
1. Open GWStreams.
2. Enter your **server host** (e.g. `http://yourpanel.com:8080`), **username**, **password**.
   These are your normal Xtream Codes / panel login details.
3. Sign in. Your details are saved for next time.
4. Browse **Live TV / Movies / Series**, tap a category, tap any item.
   - Live channels play instantly with the programme guide (EPG) below.
   - Movies/series open a detail sheet (backdrop, plot, cast) with a Play button.
   - Series let you pick a season and episode.
5. Your recent items appear in a **Continue watching** row at the top.

## What's included
- Xtream login with saved credentials
- Live TV, Movies, Series with categories + search
- **Live TV is EPG-first:** every channel shows Now/Next with a live progress bar,
  auto-fetched whenever the tab opens (cached 10 min)
- **Guide grid view** — toggle Live into a scrolling timeline guide
- **Favorites** — star channels; filter to favorites only
- **Last channel** — one tap to flip back to the previous channel
- **Channel numbers** — shown on each row; type a number in search to jump
- **What's-on-now genre filter** — quick chips (Sports, News, Movies, Kids)
- **Programme reminders** — tap the bell on an upcoming show for a local alert
- **Catch-up / archive** — on channels that support it, replay past programmes
- **Offline downloads** — download icon on movies and episodes saves to the
  phone's public Movies/GWStreams folder (visible in Files/gallery); a Downloads
  screen shows progress and plays offline. Playback uses the local file
  automatically when present.
- Thumbnails with disk caching, skeleton shimmer, initials fallbacks
- Detail sheet with backdrop, plot, cast; season/episode browser for series
- Continue-watching row, press animations, lazy pagination

## Notes
- `usesCleartextTraffic` is enabled so plain `http://` panels work (most do).
- Stream URLs follow standard Xtream patterns
  (`/live/.../ID.m3u8`, `/movie/...`, `/series/...`).
- To rename the app or change the accent colour:
  - Name: `app/src/main/res/values/strings.xml`
  - Colours: `app/src/main/java/com/gwstreams/app/ui/theme/Theme.kt`
- Package id is `com.gwstreams.app`.

## To publish on the Play Store later
You'll need a signed **release** build (`./gradlew assembleRelease` with a keystore)
and a Play Console account. Ask and I can walk you through signing.
