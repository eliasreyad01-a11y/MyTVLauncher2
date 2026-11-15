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

## Phase 2 – Bug fixing: version 3.7.1
Fixed: 1, 3, 4 (relabelled honestly), 5, 6, 7, 8, 9, 10. Deferred: 2 (Phase 5).
Files: AppRepository, PhotoBrowser, AdhanService, MainActivity, AppAdapter, SettingsActivity, activity_main.xml, both strings.xml.
Build / device result: see the final report.
