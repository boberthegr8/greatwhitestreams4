# Building Great White Streams on GitHub

Builds two APKs in the cloud — one for phones, one for Android TV / Fire TV.

## Upload
1. Sign in to GitHub.
2. Create a new repository (e.g. `greatwhitestreams`).
3. Upload the **contents** of this folder so `app/`, `tv/`, `gradlew`,
   `build.gradle.kts`, and the hidden `.github/` folder all sit at the repo root.
   (Pushing with git from the command line is the most reliable way to include
   the hidden `.github` folder.)
4. Commit.

## Get the APKs
1. Go to the **Actions** tab — the build starts automatically (~6-8 min for both).
2. When it goes green, open the run and scroll to **Artifacts**:
   - **GreatWhiteStreams-phone-apk** → `app-debug.apk` for phones
   - **GreatWhiteStreams-tv-apk** → `tv-debug.apk` for Android TV / Fire TV
3. Download whichever you need.

## Installing the TV APK on Fire TV / Android TV
The TV APK won't sideload by tapping like on a phone. Easiest routes:
- **Downloader app** (Fire TV / Android TV): install "Downloader" from the store,
  then point it at a direct link to the APK (e.g. upload `tv-debug.apk` to your
  own cloud and use the share link), or
- **adb**: `adb connect <tv-ip>` then `adb install tv-debug.apk` from a computer
  on the same network (enable Developer Options + ADB debugging on the TV first).

The TV app appears in the Android TV / Fire TV launcher row once installed.

## Notes
- Both apps share the same login and stream code, so what works on the phone
  works on the TV.
- TV navigation is D-pad / remote based: Up/Down/Left/Right to move focus,
  center to select, Back to go back. Live uses a TiviMate-style layout
  (sections + search + categories on the left, scrolling guide on the right).
- TV Settings: toggle EPG auto-fetch, EPG refresh interval, buffer size, and
  hide/show live categories.
