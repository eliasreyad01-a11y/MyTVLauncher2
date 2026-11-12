# MYTVLAUNCHER_AUDIT.md — Phase 1 (project inspection)

Date: 2026-11-03 · Scope: `MyTVLauncher` only. No source file was modified during this phase; this report is the only file added.

## 0. How to read this report — limits of the evidence

- **Nothing in this project has ever been compiled or run.** The environment used for this audit has no Android SDK and cannot download one (network allowlist), so there was no Gradle build, no emulator and no TV.
- Every status below is therefore **code-level only**: the source was read and cross-checked with `grep` and a resource-reference script (all `R.string`/`R.drawable`/`R.id`/`R.color`/`R.style` references resolve; all XML files parse; `values` and `values-ar` define the same string keys). Under your rule 5 no feature can be reported as *IMPLEMENTED AND WORKING* until it runs on a device, so the label used here is **IMPLEMENTED (code only, not run)**.
- Facts about external services were **not** all verifiable. See section 8.

## 1. Current architecture

Single-module Android app, Kotlin, plain Views (no Compose, no Fragments, no DI, no database). Package `com.mytv.launcher`, 13 Kotlin files, 2 267 lines.

| File | Lines | Role |
|---|---|---|
| `MainActivity.kt` | 595 | HOME activity. Header (greeting, clock, date, Wi-Fi/search/settings pills), prayer card, dock (`rvFav`), grid (`rvApps`), popup menu, app context menu, hide/dock/module dialogs, move-app session, search dialog |
| `SettingsActivity.kt` | 650 | Programmatic settings screen: Adhan, city, method, sound, wallpaper presets/image/video/slideshow, dimming, language, about |
| `WallpaperView.kt` | 289 | Background: gradient preset, image, looping video (`TextureView` + `MediaPlayer`), photo slideshow; also `MediaDirs` |
| `AppRepository.kt` | 48 | Queries launchable apps (`LEANBACK_LAUNCHER` + `LAUNCHER`), loads label, icon, banner; sorts by saved order |
| `AppAdapter.kt` | 76 | RecyclerView adapter for both dock and grid tiles |
| `Prefs.kt` | 127 | `SharedPreferences` wrapper (all settings) |
| `PrayerRepository.kt` | 137 | Aladhan timings + Open-Meteo city search, JSON cache, next-prayer logic (blocking calls) |
| `AdhanScheduler.kt` / `AdhanReceiver.kt` / `BootReceiver.kt` / `AdhanService.kt` | 46 / 16 / 20 / 162 | `setAlarmClock` scheduling, foreground playback service, re-arm after boot/time change |
| `Dialogs.kt` | 71 | Remote-friendly list dialog |
| `RatioFrameLayout.kt` | 30 | 16:9 rounded tile container |

Data flow: `AppRepository.load()` (background `Thread`) → `MainActivity.applyLists()` → two `AppAdapter`s. State lives in `SharedPreferences`; there is no database and no ViewModel. Threading is raw `Thread { }` + `runOnUiThread` (13 uses across 6 files); no cancellation.

Layout: `activity_main.xml` = `WallpaperView` + scrim + fixed header + a `ScrollView` containing the prayer card, focused-app title, dock and a **non-scrolling, `wrap_content` RecyclerView grid**.

## 2. Build configuration

- Gradle Kotlin DSL; AGP 8.5.2, Kotlin 1.9.24, Gradle 8.7 (`gradle-wrapper.properties`), Java 17.
- `compileSdk 34`, `targetSdk 34`, `minSdk 26` (Android 8.0), `versionName 1.1` via README / `1.0.0` in Gradle (**version mismatch**: `app/build.gradle.kts` still says `versionName = "1.0.0"`, strings/README say 1.1).
- Dependencies: `core-ktx 1.13.1`, `appcompat 1.7.0`, `recyclerview 1.3.2` only. No extra libraries (meets rule 7).
- `isMinifyEnabled = false`; no signing config (debug APK only).
- **`gradlew`, `gradlew.bat` and `gradle-wrapper.jar` are absent.** Android Studio can still sync from `gradle-wrapper.properties`, but a command-line build (`./gradlew assembleDebug`) is not possible from this folder.
- **No tests**: `app/src/test` and `app/src/androidTest` do not exist; no test dependencies.
- **Build result: unknown (never built).** See section 7 for the lines most likely to produce compiler errors.

## 3. Manifest and permissions

`AndroidManifest.xml`:

- `MainActivity`: `exported=true`, `launchMode=singleTask`, `screenOrientation=sensorLandscape`, filters `MAIN+LAUNCHER+LEANBACK_LAUNCHER` and `MAIN+HOME+DEFAULT`. Application has `android:banner` and `<queries>` for `LAUNCHER` and `LEANBACK_LAUNCHER`.
- `uses-feature` `leanback` and `touchscreen` both `required=false`.
- Permissions: `INTERNET`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `REQUEST_DELETE_PACKAGES`. No Accessibility, no `QUERY_ALL_PACKAGES`, no storage permission.
- Components: `AdhanReceiver` not exported; `BootReceiver` exported (needed for system broadcasts); `AdhanService` not exported, `foregroundServiceType=mediaPlayback`.
- `allowBackup="true"` (backs up `SharedPreferences`, including content URIs that will be invalid after a restore). Low risk; recommend `false`.
- Theme `Theme.Launcher` extends `Theme.AppCompat.NoActionBar` and is opaque by default, but **does not set** `windowIsTranslucent`/`windowIsFloating`/`windowSwipeToDismiss` explicitly (the attached CleanYourTV prompt asks for this because some firmware makes DeviceDefault translucent). `windowIsFloating=true` appears only in the dialog theme (themes.xml:19), which is correct there.

## 4. Existing features and their status

Legend: **IC** = implemented, code read only, not run. Findings that are known defects are listed in section 5.

| Feature | Status | Evidence |
|---|---|---|
| Main launcher (HOME) | IC | HOME/DEFAULT filter, `singleTask`; Back is consumed (`OnBackPressedCallback`) so Home never exits |
| Installed-app detection | **PARTIALLY IMPLEMENTED** | `AppRepository.load` (AppRepository.kt:20-36) queries both categories with `<queries>`. Refreshes only in `onResume` (MainActivity.kt:156-163); **no `PACKAGE_ADDED/REMOVED` receiver** (grep: none) |
| Application grid | IC (perf concern) | `GridLayoutManager`, 3–8 columns computed from screen width (MainActivity.kt:113-115); not user-configurable |
| Favorites dock | IC | `dockAdapter`, "Edit dock" multi-choice dialog, toggle in long-press menu; slot count not configurable |
| App hiding / restore | IC | `Prefs.hidden`; long-press → Hide app; "Hidden apps" dialog |
| App search | IC | `showSearch()` dialog with live filter. Soft-keyboard behaviour on TV not verified |
| Reorder / "Move app" | IC | `MoveSession` + `dispatchKeyEvent`; RTL left/right swapped. Order saved in `Prefs.appOrder` |
| App info / uninstall | IC | `ACTION_APPLICATION_DETAILS_SETTINGS`, `ACTION_DELETE` |
| Tile banners | IC | `ActivityInfo.loadBanner` with icon fallback |
| Wallpapers: 4 built-in gradients, image, video, slideshow, dimming | IC | `WallpaperView.kt`, `SettingsActivity.kt`; slideshow add (copy + SHA-256 duplicate check), delete |
| Clock, date, greeting | IC | `TextClock`, `SimpleDateFormat`, `updateHeader()` |
| Wi-Fi / TV settings shortcuts | IC | `ACTION_WIFI_SETTINGS`, `ACTION_SETTINGS` (may be absent on some firmware; guarded by try/catch) |
| Prayer card + Adhan (alarm, service, boot re-arm, city search) | IC (external assumptions unverified, section 8) | `PrayerRepository`, `AdhanScheduler`, `AdhanService` |
| Settings persistence | IC | `SharedPreferences.apply()` for every setting |
| Arabic / English UI | **PARTIALLY IMPLEMENTED** | `values-ar`, `supportsRtl`, start/end attributes in layouts, RTL key mapping in move mode; one hard left margin (section 5, B8). Not tested in RTL |
| Offline use | **PARTIALLY IMPLEMENTED** | Launcher itself needs no network. Prayer data needs network once per date (cache holds ≤ 6 date entries) |
| Remote-control navigation | **PARTIALLY IMPLEMENTED** | Relies on default focus search; no deterministic Up/Down rules; no remembered focus (B1) |
| Themes (light/dark, colour themes) | **MISSING** | Only 4 wallpaper presets; no theme system |
| Tile size, grid columns, dock slots, animation level | **MISSING** | No such `Prefs` keys; focus scale 1.06 is hard-coded in `AppAdapter` |
| Screensaver / full-screen clock | **MISSING** | grep for `Dream`/`screensaver`: none |
| System-app management / debloat | **MISSING** | No package-state UI; no `pm` usage anywhere (grep: none) |
| ADB integration | **MISSING** | No ADB client or shell execution in the app |
| Backup / restore of package states | **MISSING** | — |
| Automated tests | **MISSING** | No test folders |

Note on system management: an ordinary APK cannot run `pm disable-user` without shell/privileged access. Whether and how the reference does it is a Phase 2 question; the attached CleanYourTV prompt describes PC-side ADB scripts, which is a claim in that prompt, not something verified here.

## 5. Known defects and risks (confirmed from the source)

| # | Severity | Finding | Evidence |
|---|---|---|---|
| B1 | **High** | **Focus is lost every time the user returns Home.** `onResume` → `reload()` → `applyLists()` → `notifyDataSetChanged()` on both adapters; adapters have no stable ids, so the focused tile is rebound. `firstLoad` restores focus only once. Remote presses after returning from an app may land on an arbitrary tile, and "remember last focused tile" does not exist | MainActivity.kt:156-163, 205-221 (212, 215) |
| B2 | **High** | **Never compiled.** Compile or runtime errors are likely somewhere in ~2 300 uncompiled lines | whole project |
| B3 | Medium | No Gradle wrapper scripts/jar; CLI builds impossible | project root |
| B4 | **High (data)** | Default prayer method is `23` "Jordan"; **not verified** (section 8). Wrong id → wrong prayer times and wrong Adhan | `Prefs.method`, PrayerRepository.kt:62-71 |
| B5 | Medium | "Today" is taken from the device time zone, not the city's. Wrong near midnight if the TV zone differs from the chosen city | PrayerRepository.kt:89, MainActivity.kt:538 |
| B6 | Low | Reordering saves only visible apps, so hidden apps lose their saved position after being un-hidden | `finishMove` in MainActivity.kt |
| B7 | Low | `versionName` mismatch (Gradle 1.0.0 vs UI 1.1) | `app/build.gradle.kts` |
| B8 | Low | RTL cosmetic: `addLabel` uses a hard left margin `setMargins(dp(4), …)` | SettingsActivity.kt:200 |
| B9 | Low | Theme not explicitly opaque (firmware-quirk hardening) | themes.xml |
| B10 | Low | `allowBackup="true"` restores stale content URIs (Adhan sound, wallpaper) | manifest |
| B11 | Info | Unused strings `apps_title`, `favorites_title`; empty `res/raw` (intended for optional `adhan.mp3`) | resources |
| B12 | Info | Video wallpaper exists. Your Phase 11 rules say "No video backgrounds" for 1 GB devices: **a decision is needed** (keep as opt-in, or remove) | `WallpaperView.kt` (`VideoBackground`) |
| B13 | Info | No automatic refresh when apps are installed/removed while Home is visible | B-list item for section 4 |

## 6. Android TV compatibility (code-level)

- Leanback launcher entry, HOME category, banner, `required=false` features: present.
- Package visibility (`<queries>`): present, so Android 11+ should not return an empty grid.
- `minSdk 26`, `targetSdk 34`: OK for recent Google TV; Android 7 and older TVs would answer `INSTALL_FAILED_OLDER_SDK`.
- Foreground service type declared and permission added (required on targetSdk 34).
- `sensorLandscape` fixed orientation; fine for TV, forced on phones.
- Not verifiable here: whether a given TV lets a third-party launcher become Home (vendor locks, Google TV setup wizard taking Home), whether `Settings.ACTION_WIFI_SETTINGS` / `ACTION_SETTINGS` exist on the set, soft-keyboard behaviour in the search dialog, `setAlarmClock` behaviour on firmware that kills background apps.

## 7. Performance concerns (reasoned from the code; **not measured**)

1. **All tiles are inflated at once.** The grid is a `wrap_content` RecyclerView inside a `ScrollView`, so RecyclerView cannot recycle; ~100 apps ≈ 100 tile hierarchies in memory. Bad for 1 GB RAM.
2. **All icons and banners are decoded eagerly** in `AppRepository.load` (`loadIcon`/`loadBanner` for every app, MainActivity.kt:195, AppRepository.kt:31-35), and this **repeats on every `onResume`**, on a raw thread, with no cache.
3. `notifyDataSetChanged()` on every resume rebinds every tile (also the cause of B1).
4. `VideoBackground` keeps a `MediaPlayer` + `TextureView` alive (paused in `onPause`); heavy on low-end SoCs.
5. Slideshow decodes a screen-sized bitmap every 30 s and replaces it; no recycling/cache control.
6. Raw `Thread` use without cancellation; short-lived but unbounded if the user opens and closes screens quickly.
7. Positive: no database, no polling services (a 20 s UI ticker only while Home is resumed), no blur, no third-party image loader.

Highest-risk places to check first when Gradle reports errors (uncompiled): `MainActivity.showMainMenu` (typed `Pair<Int, () -> Unit>` list, local `lateinit var popup`), the SAM/trailing-lambda calls to `Dialogs.showChoice`, `AppAdapter` named-argument constructors, `SettingsActivity.addSlidePhoto` (`return@Thread`), `activity_main.xml` (`android:tint` with a colour-state-list), `Theme.AppCompat.Dialog` used with a platform `Dialog`.

## 8. Assumptions that could **not** be verified (must be checked before trusting the app)

1. **Aladhan method ids.** The Aladhan "calculation methods" page that could be read lists 22 methods by name, gives an explicit id only for *Gulf Region = 8*, and **does not list Jordan at all**; it points to `https://api.aladhan.com/v1/methods` for ids, which this environment could not fetch. The ids used in `SettingsActivity.methods` (23 Jordan, 5, 4, 3, 2, 1, 8, 9, 10, 16, 13, 21, 19, 18) come from memory. **Action:** open `https://api.aladhan.com/v1/methods` in a browser and compare.
2. **Aladhan endpoint form.** Code uses `https://api.aladhan.com/v1/timings/{DD-MM-YYYY}?latitude=…&longitude=…&method=…`. The docs page example shows `timingsByAddress/{DD-MM-YYYY}?address=…&method=…`. The `latitude/longitude` form is from memory.
3. **Open-Meteo geocoding parameters** (`name`, `count`, `language`, `format`) and response fields (`results[].name/admin1/country/latitude/longitude`) are from memory.
4. **Time-zone field** `data.meta.timezone` in the Aladhan response is assumed.
5. The reference project (`Gathin23/cleanyourtv`) has **not** been inspected yet (Phase 2). Anything said about it comes from the attached prompt text only.

## 9. Recommendations for the next phases (not started)

Priority candidates, in order, subject to your approval:

1. Resolve B1 (stable focus + remember last focused tile) and B13 (package-change receiver), because they affect every use.
2. Replace the ScrollView + wrap grid with a virtualized layout and lazy icon loading (perf).
3. Verify item 8.1/8.2 with a real API call from your PC and fix `method` ids/endpoint if needed.
4. Add the Gradle wrapper and a first real build on Android Studio; fix compiler errors before anything else (rule 11).
5. Decide the video-wallpaper question (B12) and the system-management scope after Phase 2 shows what is technically possible without special privileges.

**Stopping here.** Phase 2 (reference inspection) will not start until you approve.
