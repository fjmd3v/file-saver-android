# 6.0 migration summary

This document records what changed when Phone Saver was modernised with
[Miuix XML](https://github.com/fjmd3v/miuix-xml), what was deliberately kept, how it was
verified, and the known limitations.

## Non-negotiables (kept)

| Requirement | Status |
| --- | --- |
| `targetSdkVersion 28` | Kept. Verified in the built APK with `aapt2 dump badging` (`targetSdkVersion:'28'`). |
| `applicationId link.standen.michael.phonesaver` | Kept (verified in APK). |
| No Jetpack Compose | Views/XML only. |
| Storage architecture | Unchanged `java.io` + `DownloadManager` + SAF "location select". No `MANAGE_EXTERNAL_STORAGE`. |
| Destination data | Same `FOLDER_STORE` JSON file, same format, same sorting on add. |
| Preferences | Same keys, value formats and defaults. Nothing is written just by opening Settings. |
| Share intents | `ACTION_SEND` / `ACTION_SEND_MULTIPLE` with `*/*`, unchanged handling, automatic save with one destination. |

## Build system

| | Before | After |
| --- | --- | --- |
| Android Gradle Plugin | 3.4.2 | 8.13.2 |
| Gradle wrapper | 5.1.1 | 8.14.3 |
| Kotlin | 1.3.30 | 2.2.21 |
| Support library 28.0.0 | `com.android.support:*` | AndroidX: appcompat 1.7.1, preference 1.2.1, recyclerview 1.4.0, cardview 1.0.0 |
| Repositories | JCenter | `google()`, Maven Central |
| compileSdk / minSdk / targetSdk | 28 / 15 / 28 | 36 / **21** / **28** |
| versionCode / versionName | 36 / 5.2.0 | 37 / 6.0.0 |

Dependency changes:

* **Removed** `com.amulyakhare:com.amulyakhare.textdrawable` — only published on JCenter,
  which no longer works. Replaced by `util/DirectShareIcon.kt` (same round letter icon).
* **Removed** `constraint-layout`, `design`, `support-vector-drawable`, `support-v4` — no
  longer used by the new layouts / covered by AppCompat.
* **Added** `com.android.tools:desugar_jdk_libs` (core library desugaring) — required by
  Miuix XML (`java.util.stream`) below API 24.
* **Kept** Gson 2.8.2 and ckChangeLog 1.2.2 (both on Maven Central, no reason to change).
* **Added (tests only)** JUnit 4, Robolectric 4.16.1, AndroidX Test.

## Why minSdk is 21 (not 15, not 29)

* 15 → 21: current AndroidX (appcompat 1.7.x, recyclerview 1.4) requires API 21.
* Miuix XML declared `minSdk 29`. Its sources were audited by running Android lint with
  `NewApi`/`InlinedApi` at `minSdk 21`. Only these APIs above 21 were used, and they were
  fixed in the library (branch `ccr-4958b7dd-tn8be5` of fjmd3v/miuix-xml):

  | API | Level | Fix |
  | --- | --- | --- |
  | `Context#getColor` | 23 | `ContextCompat.getColor` |
  | `Parcel#readBoolean/writeBoolean` | 29 | int encoding |
  | `Activity#registerActivityLifecycleCallbacks` | 29 | `Application` callbacks filtered to the activity |
  | `LayerDrawable#setDrawable` | 23 | `setDrawableByLayerId` fallback |
  | `GradientDrawable#getColor` | 24 | version check, default color |
  | `SeekBar#setMin/getMin` | 26 | `setMinCompat` (minimum fixed at 0 below 26) |
  | `HapticFeedbackConstants.CONTEXT_CLICK` | 23 | `VIRTUAL_KEY` below 23 |
  | `ImageView#setImageIcon`, `autofill()` | 23 / 26 | `@RequiresApi` (framework only calls autofill on 26+) |
  | `java.util.stream`, `Optional`, `Map#getOrDefault` … | 24 | core library desugaring |
  | Vectors with `fillType` | 24 | `vectorDrawables.useSupportLibrary` |

  After the changes lint reports no `NewApi` issues at minSdk 21.

## Miuix XML integration

Added as a git submodule at `miuix-xml/` (Gradle project `:HiMiuix`). Other library fixes
made while integrating (all on the same library branch):

* `MiuixAppBar`: no `NullPointerException` when a nested scroll happens before the
  scroll target is registered.
* `PropUtils`: no crash in the static initialiser when `android.os.SystemProperties` cannot
  be reflected (this crashed every `MiuixAlertDialog`).
* `MiuixStateView` (switch / checkbox / radio rows): reports checkable state and widget
  class to accessibility services; the inner indicator is hidden from TalkBack.
* `MiuiSuperBlur.setBlurAllowed()` / `isBlurAvailable()`: lets the app disable blur.
* `WindowInsetsHelper`: still runs the DecorView default inset handling.
* Dialog buttons are no longer all-caps.
* Drop-down and list dialog options respond to real taps (rows forwarded touch events to a
  small check box with the wrong coordinates, so any finger movement cancelled the tap).

App-side handling of library behaviour:

* **Preference value format.** `MiuixDropDownPreference` stores the entry *index*; the old
  `ListPreference` stored the entry *value* ("Overwrite", "Debug", …) and the saving code
  reads that. `util/SettingsDataStore.kt` converts in both directions so existing settings
  are read and written in the original format.
* **Chinese-only library strings** (dialog buttons, color picker) are overridden in the
  app, with translations for the app's locales.
* **Accent colors** of library widgets are redirected (API 31+) to `?attr/fsColorAccent`,
  so dynamic colors also apply to switches, dialogs and lists.

## UI changes

* Main screen: large collapsing Miuix title, grouped rounded destination cards with folder
  icons, folder name + full path, delete button with confirmation dialog, extended
  "Add destination" button, empty-state card, rounded overflow menu.
* Settings: grouped Miuix preference cards (Saving, Links, Appearance, Advanced, About),
  new **Theme** (follow system / light / dark) and **Dynamic colors** (Android 12+ only;
  disabled with an explanation elsewhere).
* Share target: a bottom sheet over the sharing app instead of a full-screen list; shows
  what is being shared; tap outside or Cancel to close. One destination still saves
  immediately without showing a choice.
* Folder picker: Miuix list with current folder card, parent folder row, sorted folders,
  "Save here" / read-only button.
* About and Change log: Miuix screens/dialogs. ckChangeLog still tracks the last seen
  version, so "What's New" still appears once after upgrading.
* The "support the developer" pop up and its "Hide support pop up" setting were removed.
  A stored `hide_support` value from older versions is simply ignored. The overflow menu
  item is replaced by a GitHub item ("fjmd3v") that opens https://github.com/fjmd3v.
* Dark theme backgrounds use #131313 instead of pure black.
* Save result notifications: after a share is saved (or fails) a notification shows the
  file name and folder, or the reason it failed (channel "Save results"). URL downloads
  report when DownloadManager finishes. Because the app targets API 28, Android 13+ asks
  for the notification permission automatically the first time the app is opened; if it is
  denied only the toasts are shown.

## Manifest

* `package` attribute moved to `namespace` (required by AGP 8).
* `android:exported` declared everywhere:
  * exported: launcher activity, `SaverActivity` (share target), `DownloadBroadcastReceiver`
    (DOWNLOAD_COMPLETE comes from the DownloadManager provider, another package),
    `LocationChooserTargetService` (protected by `BIND_CHOOSER_TARGET_SERVICE`).
  * not exported: `FolderSelectActivity`, `SettingsActivity`, `CreditsActivity`.
* Added sharing shortcuts (`res/xml/shortcuts.xml`) for Direct Share on Android 10+.

## Verification performed

Environment: Linux container, JDK 21, Android SDK platform 36 / build-tools 36.0.0. No
emulator could be run (no KVM), so **no test was run on a physical or emulated device**.

| Check | Result |
| --- | --- |
| Gradle dependency resolution | OK |
| `assembleDebug`, `assembleRelease` | OK |
| `lintDebug` | 0 errors, 9 warnings (pre-existing: deprecated ChooserTargetService, `ObsoleteSdkInt` checks in unchanged saving code, `scheduleAtFixedRate` in DebugLogger) |
| Miuix XML lint `NewApi`/`InlinedApi` at minSdk 21 | No issues |
| APK `aapt2 dump badging` | applicationId unchanged, versionCode 37, versionName 6.0.0, minSdk 21, **targetSdk 28** |
| Robolectric unit tests | 316 run, 0 failures (2 opt-in screenshot tests skipped) on API 23, 26, 28, 29, 30, 31, 33, 34, 35, 36 |
| Visual review | Screens rendered with Robolectric native graphics (light + dark) and reviewed; found and fixed a sheet layout bug |

Robolectric coverage (see `app/src/test`):

| Scenario from the test plan | Covered |
| --- | --- |
| Fresh installation | Defaults, empty state, nothing written on first Settings open |
| Upgrade installation / existing settings | 5.2.0-style SharedPreferences read unchanged, not rewritten |
| Existing destinations | Existing `FOLDER_STORE` shown in stored order |
| Adding / removing destinations | Folder picker result (sorted, no duplicates), remove with confirmation |
| Sharing images / videos / documents / text / multiple files | Files written with correct names and content |
| Duplicate filename / overwrite | Overwrite, do not save, append number |
| Automatic saving | Single destination saves without the selector |
| Direct Share | `DIRECT_SHARE_FOLDER` extra and sharing shortcut id |
| Dark / light mode, dynamic colors | Night qualifier inflation, theme mapping, Monet accent on 31+, ignored on 28 |
| Accessibility | Miuix switch row reports checkable/checked/Switch class |

Not covered by automated tests (requires devices):

* Runtime storage permission prompts and their enforcement on Android 10 – 16.
* Real `DownloadManager` downloads, MediaScanner registration, SAF picker UI
  ("location select").
* System share sheet presentation of Direct Share targets (ChooserTargetService on 6 – 9,
  sharing shortcuts on 10+).
* Xiaomi/HyperOS blur on real hardware; OEM specific storage restrictions.
* API 21 – 22: Robolectric 4.16 no longer supports these levels; they were only checked by
  lint (`NewApi`).
* TalkBack, switch access and font scaling on real devices (layouts use `sp`, 48dp touch
  targets and focus indicators, but this was not manually tested).

## Known limitations

* targetSdk 28 means the app cannot be published as a *new update* on Google Play under
  current target API policy; this is an intentional trade-off to keep legacy storage.
* On Android 11+, `Android/data`, `Android/obb` and some removable storage roots are not
  writable regardless of target SDK; use "location select" for SD cards.
* "Follow system" theme has no effect before Android 10 (no system dark theme); the app
  uses the light theme there and says so in Settings.
* Dynamic colors require Android 12.
* Xiaomi blur is only enabled when the platform reports blur as enabled (Android 12+);
  on older HyperOS/MIUI builds the app bar uses a solid surface color.
* Sharing shortcuts may also be shown by some launchers when long-pressing the app icon;
  launching one opens the app.
* Miuix XML seek bars cannot have a minimum other than 0 below API 26 (not used here).
