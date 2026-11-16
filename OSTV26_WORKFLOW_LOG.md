# OSTV26 – phase log

Project: this launcher (`com.mytv.glass`, branch `main`). Maintenance only: no rebuild, no architecture change.
Evidence level: **code review + CI compile only**. Nothing here has been run on a TV, with a remote, or on Android 14 hardware.
(`MYTVLAUNCHER_AUDIT.md` in the repo root is an older audit of v1.1 and is out of date.)

## Phase 1 – Full inspection: DONE (read-only)
Static checks that passed: string keys identical in `values` / `values-ar`; no missing `R.id` / `R.drawable` / `R.string`; manifest and permissions fit targetSdk 34; every setting traced UI -> Prefs -> effect.

Findings
| # | Sev | Finding |
|---|-----|---------|
| 1 | Med | `AppRepository.load`: one failing app aborts the whole load |
| 2 | Med | Frost glass always samples the built-in wallpaper, whatever wallpaper is active (Phase 5) |
| 3 | Med | `PhotoBrowser` thumbnail cache keyed by id only: photo/video ids collide |
| 4 | Med | Control-center "Sleep" / "Sleep timer" only start the screensaver (misleading label) |
| 5 | Low | Settings language rule differs between `tr()` and resources; hard-coded English toast |
| 6 | Low | "Test Adhan" row keeps "Stop" after the Adhan ends by itself |
| 7 | Low | Focus jumps after the Adhan ends / Stop is pressed |
| 8 | Low | Updated apps keep stale icon/label/intent until the list order changes |
| 9 | Low | Screensaver ignores the Gregorian/Hijri date setting |
| 10 | Low | Wrong accessibility labels on Wi-Fi and Home buttons |
Dead code (not reachable by the user): `buildApple()`, `showStats()`, `wallFg`, `headerTime`, 12 strings, 6 drawables.
Performance notes (Phase 7): `GlassTheme.key/opacity` allocate a `Prefs` on every frame; `wallFg` decodes a hidden 9.4 MB image; `DockBand` decodes the wallpaper a second time.

## Phase 2 – Bug fixing: version 3.7.1 — PASSED (CI compile + JVM tests)
Fixed: 1, 3, 4 (relabelled honestly), 5, 6, 7, 8, 9, 10. Deferred: 2 (done in Phase 5).
Files: AppRepository, PhotoBrowser, AdhanService, MainActivity, AppAdapter, SettingsActivity, activity_main.xml, both strings.xml.

## Phase 3 – Settings: PASSED (code trace + Robolectric simulation)
Every row on every page opens without crashing; every choice made through the real dialogs is saved (theme, transparency, date,
home toggles, apps per row, screensaver, time format, Adhan, volume, calculation method, prayer offsets, wallpaper interval/reset,
Hijri adjust, time zone, language) and survives a new `Prefs` instance (XML on disk checked). No dead / fake setting found.
Not verified: rows that open Android system screens (Video, Audio, Remotes ... — firmware dependent); the main-list "Screen Saver"
row opens the system Daydream settings while the launcher's own clock screensaver timer is under Home Screen (left as is).

## Phase 4 – UI: version 3.7.2 / 3.7.3 — PASSED (simulated renderer)
Screens rendered at 1920x1080 TV density and looked at: home, focus on dock and grid, control center, screensaver, folder, every
settings page, theme dialog. The simulator draws no live blur / elevation shadows, clips no rounded outlines and sizes dialogs
differently, so those differences were NOT treated as defects.
Found and fixed: the focused settings / choice row was cut flat on both ends (it grows 3 %, but a LinearLayout clips its children to
its padding by default). Fix: side padding + `clipToPadding=false` (Settings page and `Dialogs.showChoice`). Pixel probe before/after:
the white fill now extends past the old edge. Guard: `FocusRoomTest`.
Left as is: the "Screen saver" tile in the control center keeps the power icon.

## Phase 5 – Liquid Glass identity: PASSED (simulated)
Frost glass, dock band, settings pages and choice pages now use the picture that is really on the home screen (user photo or
slideshow frame) instead of always the built-in Kirkjufell photo; video wallpaper / built-in photo use the built-in photo.
Luminance adaptation follows the same picture. The palette system (8 themes incl. Crystal White, Pearl/Platinum Silver, Arctic Ice
Blue, Icy Blue ...) and the exact Liquid Glass material constants were already in place from 3.7.0 and were not changed.
Not verified: how it looks with a real custom wallpaper on the TV.

## Phase 6 – Remote control: PASSED (simulated, `RemoteControlTest`)
Outside touch mode, 1920x1080: dock walkable with Right/Left; Down/Up between dock and grid; Up from the dock reaches a header button
and Down returns to a tile; every focusable header button can go down; every app tile reachable from the first one; every settings
page and a choice page walkable with Up/Down; Left/Right wrap at row ends; Back never closes the launcher, closes a folder, closes
a choice page without closing Settings, and closes Settings; any key ends the screensaver; move mode (Right moves, OK saves, Back cancels).
Not verified: a real remote (key repeat, Back from system screens, control-center Back through PopupWindow).

## Phase 7 – Performance: version 3.7.3 — compiled, not measured
Done: no decode of the always-hidden `wallFg` picture on every start; `DockBand` shares Frost's bitmap (one wallpaper decode less);
no second full-screen blur behind the folder where the system already blurs (TVs without cross-window blur keep the fallback);
`VolumePill` no longer allocates a Path per draw.
Reviewed and left: `GlassTheme.key/opacity` allocate a small `Prefs` per draw (microseconds, not worth the risk); GlassClock draws
the text five times but only when the minute changes; crash-handler chain grows per `onCreate` (harmless).
Nothing was profiled on a TV: no CPU / RAM / GPU numbers exist.

## Phase 8 – QA: 52 JVM tests green, CI build green
`FrostTest`, `FocusRoomTest`, `RemoteControlTest`, `MainScreenTest`, `SettingsScreenTest`, `PrefsPersistenceTest`, `LogicTest`,
`ScreenshotTest` (renders, no assertions). No compiler warnings in app code.

