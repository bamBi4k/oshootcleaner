<div align="center">

<img src="fastlane/metadata/android/en-US/images/icon.png" width="120" alt="Oh Shoot icon" />

# OhShootCleaner

**One-tap cleanup and system diagnostics for Android.**
No root. No Shizuku. No fake progress bars.

[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.09-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

</div>

---

## Screenshots

<div align="center">

| Dashboard | Performance | Storage |
|:---:|:---:|:---:|
| <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="220" /> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="220" /> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="220" /> |

| Analysis | Battery | Settings |
|:---:|:---:|:---:|
| <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="220" /> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="220" /> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="220" /> |

</div>

---

## What it does

**Oh Shoot** is a single button that clears what an ordinary Android app is actually allowed to clear — and hands you straight to the one screen that can clear the rest.

- **Frees RAM** by asking Android to evict cached background processes
- **Clears its own cache** plus the shared WebView cache
- **Reports real numbers** — before/after free RAM, bytes freed, time taken
- **Deep-links you** to the Settings screens that hold the OS-level permission to clear every app's cache, show real battery drain, and cap background processes

## What it doesn't do

Android's sandboxing means a regular app cannot:

- Delete another app's cache directory
- Force-stop another app's services
- List what's genuinely running system-wide

Oh Shoot doesn't pretend otherwise. It does what it can, and points you to the right place for the rest.

## Features

| | |
|---|---|
| **Dashboard** | Live memory, storage, battery, thermal, network, CPU stats |
| **One-Tap Boost** | Frees this app's cache and evicts background processes |
| **Storage** | Per-app sizes, cache breakdown, duplicate finder, big-file scanner |
| **Analysis** | Duplicates, large files, unused apps, media scope picker |
| **Performance** | Power Saver / Balanced / Performance presets |
| **Battery** | Health, temperature, charge estimation, quick settings |
| **Themes** | Six flavors — Latte, Matcha, Sakura, Lavender, Mono, Alpha Founder |
| **Languages** | English, German, Spanish, French, Italian, Portuguese, Russian |
| **Widgets** | Home-screen cleaner, dashboard, data usage |
| **Shortcuts** | Quick-settings tile and long-press launcher shortcuts |

## Install

| Source | Status |
|---|---|
| **GitHub Releases** | [Download the latest APK](https://github.com/bamBi4k/oshootcleaner/releases) |
| **F-Droid** | Coming soon |
| **Google Play** | Coming soon |

Requires **Android 8.0 (API 26)** or newer.

## Build from source

```bash
git clone https://github.com/bamBi4k/oshootcleaner.git
cd oshootcleaner
./gradlew assembleDebug
