# Car 3-state play/pause/off weather button + dead-code cleanup

**Date:** 2026-07-12
**Surface:** Android Auto (car) only — phone is unchanged.
**Method:** TDD (red → change → green) for the behavior change; deletion-after-refs-gone for the cleanup.

## Problem

The car toolbar's weather action uses `vm.toggleWeatherPlayPause()`, whose cycle is
`OFF → PLAYING → ON → PLAYING` — it **never returns to OFF**. After the earlier car-menu
refactor deleted `WeatherScreen` (which held the parked "Turn radar off" action), the car
surface had **no way to turn the radar fully off**. The phone FAB reaches OFF via long-press,
which is fine for the phone but the car has no long-press affordance on toolbar actions.

A single button with three states in a cycle cannot make *both* "resume from pause" and
"turn off" one tap each — in a 3-state cycle one of them costs two taps. The chosen trade-off
(see Decision 1) makes **off** one tap from the paused state, at the cost of resume-from-pause
being two taps (`ON → OFF → PLAYING`). Turning the radar fully off is the action that was
stranded; resuming is the more common action but the phone already covers the common case and
the car's gap is specifically "cannot turn off at all."

## Decisions

1. **Car button is a 3-state cycle `OFF → PLAYING → ON(paused) → OFF`.** Icons show the
   *next* action (media-button convention): `ic_car_play` / `ic_car_pause` / `ic_car_weather_off`.
   Off is a first-class tap state. Resume-from-pause costs two taps (accepted).
2. **Phone is unchanged.** FAB tap stays `toggleWeatherPlayPause` (`OFF→PLAYING→ON→PLAYING`),
   long-press stays `turnOffWeather`, drawer toggle stays `toggleWeatherOnOff`. The phone's
   long-press-for-off is sufficient there.
3. **Approach A — resurrect the existing `cycleWeatherMode()` / `nextWeatherModeOnCycle()`**
   rather than rename or modify the shared play/pause helper. Modifying
   `nextWeatherModeOnPlayPauseToggle` to include OFF would change the phone (rejected by
   Decision 2). Renaming `cycleWeatherMode` would over-specialize a function that is just
   "cycle all three modes." No ViewModel logic changes.
4. **Car button is icon-only, no content description** — matches the other toolbar actions
   (Menu, Recenter), which are also icon-only. No new strings.
5. **Fold in the dead-code cleanup** surfaced by the post-refactor verification (see §4).
   `cycleWeatherMode` is *not* dead after this change — the car button resurrects it.

## Behavior & state machine (car)

`WeatherMode` enum is unchanged: `OFF` / `ON` (paused, radar visible, polling continues) /
`PLAYING` (animating).

```
OFF  --tap-->  PLAYING  --tap-->  ON(paused)  --tap-->  OFF
```

This is exactly `nextWeatherModeOnCycle(current)` (MapViewModelLogic.kt:58), already unit-tested
in `MapViewModelLogicTest`. The car action calls `vm.cycleWeatherMode()` (MapViewModel.kt:588),
which calls `updateWeatherMode(nextWeatherModeOnCycle(weatherMode))`.

## Changes

### 1. Car UI — `app/src/main/java/.../car/screens/CarNavMapScreen.kt`
- Weather action listener: `vm.toggleWeatherPlayPause()` → `vm.cycleWeatherMode()`.
- `radarPlayPauseIcon()` → rename to `radarCycleIcon()`, 3-way:
  - `WeatherMode.OFF` → `R.drawable.ic_car_play`
  - `WeatherMode.PLAYING` → `R.drawable.ic_car_pause`
  - `WeatherMode.ON` → `R.drawable.ic_car_weather_off` (existing asset — cloud with slash)
- Icon-only, no `setContentDescription` (matches Menu/Recenter).
- Update the KDoc/comment: describe the 3-state cycle and the resume-costs-2-taps trade-off;
  stop saying it "mirrors the phone FAB" (the phone FAB is 2-state + long-press).
- No new drawables, no new/changed strings. Toolbar still has exactly 3 actions
  (Menu / Weather / Recenter) — `NavigationTemplate` action-strip limit preserved.

### 2. ViewModel — `app/src/main/java/.../viewmodel/MapViewModelLogic.kt`
- No logic changes. Update the stale comment at MapViewModelLogic.kt:24-25 (written when
  `cycleWeatherMode` was dead: "The VM's `cycleWeatherMode` still exists, but it now calls the
  pure helpers…") to state `cycleWeatherMode` is the car toolbar's 3-state cycle.
- `cycleWeatherMode()` (MapViewModel.kt:588), `nextWeatherModeOnCycle` (MapViewModelLogic.kt:58)
  move from dead-code to live (called by the car).
- `toggleWeatherPlayPause()`, `turnOffWeather()`, `toggleWeatherOnOff()`,
  `nextWeatherModeOnPlayPauseToggle`, `nextWeatherModeOnOnOffToggle` — all unchanged (phone).

### 3. Tests (TDD) — `app/src/test/java/.../car/screens/CarNavMapScreenTest.kt`
- **Red:**
  - Change `carNavMapScreen_radarIconIsPlayWhenOnButNotPlaying` → assert `ON` uses
    `R.drawable.ic_car_weather_off` (fails today: shows `ic_car_play`). Rename the test to
    reflect the new semantics (e.g. `…_radarIconIsOffWhenPaused`).
  - Add `carNavMapScreen_weatherAction_clickCyclesOffToPlayingToOnToOff`: set `OFF`, click
    `actionStrip.actions[1]`, assert `vm.weatherMode == PLAYING`; click → `ON`; click → `OFF`.
    Fails today: the listener is `toggleWeatherPlayPause` (`OFF→PLAYING→ON→PLAYING`), so the
    third click yields `PLAYING`, not `OFF`.
- **Change:** the §1 + §2 edits.
- **Green:** updated tests pass; `./gradlew clean test` green; `compileDebugKotlin` warning-free.
- `nextWeatherModeOnCycle` is already covered by `MapViewModelLogicTest` — no new VM-logic tests.

### 4. Dead-code cleanup (deletion — no red needed; refs already gone)

Verified zero production callers (excluding definitions + tests); git history confirms the
callers were deleted in this session's earlier car-menu refactor:

| Symbol | Former caller (deleted) | Phase deleted |
|---|---|---|
| `radarModeLabel()` | `HomeScreen` radar-status row text; `WeatherScreen` radar-mode row label | Phase 1 / Phase 4 |
| `MapStyle.nextCycle()` | `SettingsScreen` map-style row `vm.updateMapStyle(vm.mapStyle.nextCycle())` | Phase 2 |
| `car_radar_off/on/playing` | only `radarModeLabel` | (transitive) |
| `cycleWeatherMode()` | `WeatherScreen` radar-mode row listener | Phase 4 — **resurrected by §1** |

- **`app/src/main/java/.../car/CarUi.kt`**: delete `radarModeLabel()` and `MapStyle.nextCycle()`.
  Keep `WindSpeedUnit.nextCycle()` / `TemperatureUnit.nextCycle()` (still called by
  `SettingsScreen.kt:61,69`). Drop now-unused imports (`Context`, `MapStyle`, `WeatherMode`,
  `R`). Rewrite the file KDoc — drop "radar-mode label" and the "Home / Weather / Settings"
  phrasing; describe it as the unit cycle helpers used by car Settings.
- **`app/src/test/java/.../car/CarUiTest.kt`**: delete the 3 `radarModeLabel_*` tests, the 6
  `MapStyle.nextCycle` tests, and `nextCycle_mapStyle_coversAllEntriesExactlyOnce`. Keep the
  wind/temp `nextCycle` tests and their two `coversAllEntriesExactlyOnce` tests. Drop unused
  imports (`MapStyle`, `WeatherMode`, `R`, `ApplicationProvider`, `assertTrue`) and the
  `context` getter. **Convert from Robolectric to plain JUnit** — the remaining tests are pure
  Kotlin (no `Context`/resource access), so drop `@RunWith(RobolectricTestRunner::class)` and
  `@Config(sdk = [33])`. Rewrite the class KDoc.
- **`app/src/main/res/values/strings.xml`** and **`app/src/main/res/values-en-rUS/strings.xml`**:
  delete `car_radar_off`, `car_radar_on`, `car_radar_playing` (only `radarModeLabel` used them;
  other locales are Crowdin-managed and out of scope).
- **`app/src/main/java/.../car/screens/HomeScreen.kt`**: drop the unused `vm: MapViewModel`
  parameter from `carMenuRows()` and `menuListTemplate()` and their call sites — the body
  doesn't read `vm` (the menu is two static navigation rows). Update KDoc if it mentions the
  parameter.
- **`app/src/test/java/.../car/screens/PoiUtilitiesScreenTest.kt`**: remove the unused
  `org.junit.Assert.assertEquals` import.

### 5. Out of scope
- Phone play/pause/off (unchanged — Decision 2).
- Changelog (per standing instruction: no changelog entries for this work).
- `car_action_play` / `car_action_pause` strings — pre-existing dead (not a verifier finding
  for this change; the car toolbar uses icons, not these strings). Left for a separate pass.
- Other Crowdin-managed locale `strings.xml` files.

## Verification
1. `./gradlew clean test` — all unit tests green; no dangling references; no new warnings.
2. `./gradlew :app:compileDebugKotlin` — warning-free.
3. `./gradlew :app:assembleDebug` — APK builds.
4. Device/head-unit smoke (manual, requires attached device): car toolbar weather button
   cycles Off → Playing → Paused → Off with the correct icon each state; radar raster + polling
   follow the state; phone FAB unchanged (tap play/pause, long-press off).

## Files touched
- `app/src/main/java/.../car/screens/CarNavMapScreen.kt` (edit)
- `app/src/main/java/.../viewmodel/MapViewModelLogic.kt` (comment only)
- `app/src/main/java/.../car/CarUi.kt` (delete 2 fns + imports + KDoc)
- `app/src/test/java/.../car/CarUiTest.kt` (delete tests + imports; convert to plain JUnit; KDoc)
- `app/src/main/java/.../car/screens/HomeScreen.kt` (drop `vm` param + call sites)
- `app/src/test/java/.../car/screens/PoiUtilitiesScreenTest.kt` (drop unused import)
- `app/src/test/java/.../car/screens/CarNavMapScreenTest.kt` (TDD: edit + add test)
- `app/src/main/res/values/strings.xml` (delete 3 strings)
- `app/src/main/res/values-en-rUS/strings.xml` (delete 3 strings)