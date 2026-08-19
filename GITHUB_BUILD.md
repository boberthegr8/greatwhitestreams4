# Building Great White Streams on GitHub

Great White Streams TV now uses a permanent release-signing key and an automatic in-app update pipeline.

## Permanent first install

Do not distribute or install a debug APK as the long-term baseline. The first customer/test installation should be the signed TV release produced by the `Build & Publish Great White TV` GitHub Action.

The repository must contain the Actions secret `GWS_SIGNING_BUNDLE`. The workflow restores the permanent keystore from that secret, builds `:tv:assembleRelease`, verifies the APK signature, uploads the signed APK as a GitHub Actions artifact, and publishes the same APK to `auto/greatwhite-latest.apk`.

## Automatic updates

Every qualifying push to `main` creates a new release version with a higher `versionCode`. The workflow computes the APK SHA-256 and rewrites `auto/update.json`. Installed TV copies check that feed and can download and install the newer APK over the existing installation.

For an Android in-place update to work, the package/application ID and signing key must remain unchanged. Never replace or regenerate the permanent Great White Streams release keystore after devices have been deployed.

## Installing the TV APK

Download the signed `GreatWhiteStreams-TV-*` artifact from the successful GitHub Action and sideload that APK onto Android TV / Google TV / Fire TV. This signed release is the permanent update baseline.

After installation, allow Great White Streams to install unknown apps when Android requests that permission. Future signed releases should then install over the existing app while preserving its app data.
