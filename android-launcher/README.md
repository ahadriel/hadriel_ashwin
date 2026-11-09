# macOS-inspired Android Launcher

A native Android HOME app built with Kotlin and Jetpack Compose. The project lives in this folder so it can coexist with the website at the repository root.

## Features

- Android HOME / default-launcher intent filters
- Installed-app discovery through PackageManager
- Four-column swipeable home pages
- Animated page indicators
- Frosted dock with touch-drag magnification and Launchpad shortcut
- Five-column searchable Launchpad overlay
- Long-press app preview panel with macOS-style traffic-light controls
- Multi-stop gradient wallpaper and radial highlight
- Dock favorites persisted with Preferences DataStore
- System light/dark Material theme
- GitHub Actions debug APK build

## Build

Requirements: JDK 17, Android SDK Platform 35, and Gradle 8.9.

From this directory:

```bash
gradle assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

The GitHub Actions workflow builds this APK on pushes, pull requests, or manual workflow dispatch and uploads it as an artifact.

## Install and set as Home app

1. Install the debug APK on an Android 10+ device.
2. Press Home and choose **macOS Launcher**.
3. Select **Always** if you want it as the default launcher.
4. To switch back, open Android Settings → Apps → Default apps → Home app.

## Notes and platform limits

- The launcher uses Android's public app-launching APIs; it does not manage or resize other apps' windows.
- App preview is a launcher-side preview panel, not a live screenshot or floating app window.
- Android package visibility is declared for app discovery. Review Google Play's package-visibility policy before publishing.
- Blur and animation quality depend on the device GPU and Android version. A translucent surface is used where full system-level macOS blur is unavailable.
- This is a first implementation. Device-specific testing is still needed for OEM gesture navigation, cutouts, tablets, and 90/120 Hz displays.
