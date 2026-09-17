# Oh Shoot Cleaner

A one-button "refresh everything" app for Android that does **only what a
non-rooted, non-Shizuku app is actually allowed to do** — no fake progress
bars, no pretending to kill other apps' services.

## Presets (Power Saving / Balanced / Performance)
Two Android permission categories matter here:
- **Special access** (`WRITE_SETTINGS`, notification policy access): the
  user grants these once via a normal Settings toggle — no ADB, no root.
  Once granted, this app genuinely and instantly sets brightness,
  brightness mode, screen timeout, auto-rotate, and Do Not Disturb.
- **Signature-level** (`WRITE_SECURE_SETTINGS`): needed for global refresh
  rate, animator/window/transition animation scale, and Battery Saver.
  Android only grants this via `adb shell pm grant` or root — there is no
  user-facing toggle for it, so no honest app can flip these directly.
  The presets clearly list these as "needs one manual tap" and the
  Developer-options / Battery-usage deep links get you there fast.

## What it really does when you tap "Restart Background Apps"
- Deletes everything in this app's own cache/code-cache directories
- Clears the shared system WebView cache + storage
- Forces a GC pass and reports real before/after free-RAM numbers
- Surfaces one-tap deep links into the Settings screens that **do** have
  OS-level permission to clear every other app's cache (Storage settings),
  show real drain sources (Battery usage), and limit background process
  count (Developer options)

## What it deliberately does NOT try to do
Android's sandboxing (not a limitation of this app) means a normal app cannot:
- Delete another app's cache directory
- Force-stop / "restart" another app's services
- List what's really running system-wide

If you truly need that level of control without full root, the only two
legitimate paths are:
1. **Shizuku** (which you said you want to avoid), or
2. A companion **ADB over Wi-Fi** command run from a desktop/laptop once —
   e.g. `adb shell pm trim-caches 999999999999` or scripted
   `adb shell am force-stop <package>` calls. This still isn't root, but it
   does require a one-time wireless-debugging pairing per device, so it's a
   deliberate "Pro power-user" add-on rather than baked into the main button.
   Happy to help you build that as an optional companion script if you want it.

## Setup (Android Studio)
1. File → New → New Project → **Empty Activity** (Compose), Kotlin,
   min SDK 26 — name it `OhShootCleaner`, package `com.example.oshootcleaner`.
2. Replace the generated `app/build.gradle.kts`, `AndroidManifest.xml`, and
   add `MainActivity.kt` / `CleanupManager.kt` with the files in this folder.
3. Sync Gradle, run on device/emulator (API 26+).
4. No permissions dialog will appear — the app doesn't request any.

## Theming
Four built-in flavors — Latte 🌻, Frappé 🪴, Macchiato 🌺, Mocha 🌿 — using the
official Catppuccin palette (MIT-licensed, catppuccin.com). Tap the "Theme"
pill in the top-right, tap a row to apply instantly; the mini swatch on each
row previews background/accent/success colors before you commit. Selection
persists via SharedPreferences (`Theme.kt` / `ThemeStore`). Mocha is the
default per the dark-mode-first request.

## Theming (v2)
Every color role from the palette spec is wired to its named purpose —
fonts (primary/secondary/headings/links/code), backgrounds (base/mantle/
crust/surface/overlay), buttons (bg/text/primary/hover/active/disabled),
bevel (highlight/shadow/border), console (bg/text/prompt/error/warning/
success/info), and the 8 accent colors — see `Theme.kt`. No emojis
anywhere in the UI; theme rows use a 4-color swatch strip instead so the
flavors read as visibly distinct.

**Day/Dark mode** is now separate from flavor choice: the picker opens on
a Day/Dark segmented toggle. Day always applies Latte (the only light
flavor in the spec). Dark keeps your last-picked flavor (Frappé,
Macchiato, or Mocha) and switching flavors while in Dark mode applies
instantly. Both choices persist independently (`ThemeStore`).

**Bevel**: `Bevel.kt` approximates "light top-left, dark bottom-right"
with a diagonal-gradient border + soft shadow — cheaper and more
consistent across devices than hand-drawn highlight/shadow arcs.

**Clean button**: circular, no emoji — just "CLEAN" in bold caps. While
running, it swaps to a spinning progress ring instead of static text.

**Console color-coding**: lines get colored by role (`consoleError` for
denied/failed, `consoleWarning` for skipped, `consoleSuccess` for
completed actions, `consoleInfo` for everything else) via a keyword match
in `MainActivity.kt` — `CleanupManager`/`PresetManager` text wasn't
touched, so this is presentation-only.

## Home-screen widget (v2)
Redesigned per feedback: the button is now a real circle
(`GlanceModifier.cornerRadius(200.dp)`, which clamps to fully round), sitting
inside a wider outer ring (16dp padding, up from 6dp) so "tap outside to
open the app" is actually easy to hit instead of a hair's-width margin.
Text is plain ("CLEAN" / "..." / "DONE"), no emoji. Still fixed to the
Mocha palette regardless of in-app theme — flagged as a follow-up if you
want the widget to sync with your in-app choice.

## Home-screen widget (Glance)

## Suggested next steps
- Add a home-screen widget / quick-settings tile wrapping `runQuickClean()`
  so it's truly a single tap from anywhere (no app-open needed).
- Add a "Restart this app" convenience button using
  `packageManager.getLaunchIntentForPackage()` + `Process.killProcess()` —
  works for your OWN app only, useful if the cleaner app itself gets sluggish.
