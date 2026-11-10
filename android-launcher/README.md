# ORBIT Launcher

ORBIT is a native Android HOME launcher built with Kotlin and Jetpack Compose. It has its own midnight-cosmic visual identity: quiet blue/teal accents, layered atmosphere, glass-like surfaces, and restrained motion. It is inspired by premium desktop launchers without trying to copy macOS.

## Current features

- Registers as an Android HOME app and can be selected as the device's default launcher
- Discovers installed launchable apps through Android's PackageManager
- Swipeable home pages with a responsive four- or five-column grid
- Animated page indicators and a persistent dock
- Searchable app library with All apps, Dock, and Hidden views
- Quick app search from the top bar
- Long-press app actions: open, pin/unpin from dock, hide/show on home, open Android app info, and request uninstall
- Up to five persistent dock favourites, hidden-app list, home page, grid size, accent, wallpaper, label visibility, and clock preference through Preferences DataStore
- Automatic app-library refresh when apps are installed, removed, or updated
- Five accent colours and five atmospheric gradient wallpapers
- Live clock, date, and battery percentage
- Adaptive ORBIT launcher icon
- Edge-to-edge layout with Android system bars respected
- GitHub Actions build that publishes the successful debug APK to `appbuild/launcher.apk`

## Build

Requirements: JDK 17, Android SDK Platform 35, Build Tools 35.0.0, and Gradle 8.9.

From this directory:

```bash
gradle --no-daemon assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

The repository workflow also copies the APK to the repository's `appbuild/launcher.apk` after a successful push build and uploads a downloadable `orbit-launcher-debug` Actions artifact.

## Install and set as default

1. Install `launcher.apk` on an Android 10+ device.
2. Open ORBIT. If it is not already the default, tap **SET UP** on the home-screen prompt, or open Settings → Home App → **SET ORBIT AS DEFAULT**.
3. Confirm Android's system Home-app role prompt.
4. Press the device's Home button; ORBIT should now open.
5. To switch back, open Android Settings → Apps → Default apps → Home app.

## Controls

- Tap an app icon to launch it.
- Swipe horizontally to move between home pages.
- Tap **Explore** or the dock's ORBIT button to open the app library.
- Tap the search icon for quick app search.
- Long-press an app for its app actions.
- Tap the settings icon to customise the launcher.
- Use Android's Home-app setting to leave ORBIT or choose another launcher.

## Platform notes

- ORBIT is a real Android HOME activity, but it does not replace Android's system navigation, notification shade, recents screen, or lock screen.
- App actions use public Android intents. Android confirms uninstall requests through its own system UI.
- The app library requires broad package visibility to enumerate installed launchable apps. Google Play distribution would require a valid policy justification for `QUERY_ALL_PACKAGES`.
- This version does not yet host Android home-screen widgets or support icon packs, drag-to-reorder placement, folders, or per-app notification badges.
- Device testing is still required across OEM launchers, gesture navigation, cutouts, and high-refresh displays. A successful CI build confirms compilation, not every device-specific interaction.
