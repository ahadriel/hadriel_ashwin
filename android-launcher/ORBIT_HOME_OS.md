# ORBIT Home OS

ORBIT is evolving from an Android HOME launcher into a dedicated home operating environment for the Lenovo Tab M8 FHD (TB-8705F, Android 10, 4 GB RAM).

## Locked architecture

- Smart home: direct, independently replaceable integrations. WLED is the first live adapter.
- Music: Spotify + Plex. Build 001 opens their installed Android apps; authenticated playback and remote-control APIs are later milestones.
- Productivity: EONIS is a first-class ORBIT destination. Build 001 discovers and opens the installed EONIS app; a secure data bridge follows once its app contract is confirmed.
- Operating mode: dedicated home mode using ORBIT as Android's default HOME application. Android remains the hardware, driver, security and recovery foundation.
- Device target: Android 10 / API 29 minimum, 4 GB RAM, tablet-first responsive layout.

## Build 001

- Adds Home, Sound, Control and EONIS destinations.
- Adds a WLED direct adapter using the local WLED JSON API at /json/state.
- Saves the WLED host locally on the tablet.
- Adds app hand-offs for Spotify, Plex and EONIS.
- Preserves the existing launcher package identity and GitHub Actions APK workflow.

## WLED setup

1. Put the tablet and WLED controller on the same trusted home Wi-Fi network.
2. Find the WLED device's local IP address.
3. Open Control in ORBIT and enter the IP address, for example 192.168.1.50.
4. Save the address, test the connection, then use Lights On / Lights Off.

The app permits cleartext HTTP for the local WLED endpoint because standard WLED local JSON control commonly uses HTTP. Only enter a trusted device on your home network. Do not expose the WLED controller directly to the public internet.

## Build

Requirements: JDK 17, Android SDK Platform 35, Build Tools 35.0.0, Gradle 8.9.

From this directory run gradle --no-daemon assembleDebug.

APK output: app/build/outputs/apk/debug/app-debug.apk

## Roadmap

1. Home OS foundation and tablet dashboard.
2. WLED scenes, brightness and device discovery.
3. Spotify and Plex account-aware playback integrations.
4. EONIS task/calendar integration.
5. Raspberry Pi service bridge and JARVIS.
6. Dedicated-device polish: boot experience, screen policy, ambient display and kiosk configuration.

ORBIT does not replace Android's kernel, firmware, lock screen or recovery environment. It is a native Android HOME shell designed to feel like a purpose-built home console while retaining a safe route back to Android Settings.
