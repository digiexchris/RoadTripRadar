# Car 3-state play/pause/off weather button + dead-code cleanup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the car toolbar weather button a 3-state cycle (Off → Playing → Paused → Off) so the car can turn the radar fully off again, and remove the dead code the post-refactor verification surfaced.

**Architecture:** Car-only. The car action switches from `vm.toggleWeatherPlayPause()` (2-state, never reaches OFF) to the existing `vm.cycleWeatherMode()` (3-state `OFF→PLAYING→ON→OFF`), and its icon becomes a 3-way next-action map using the already-present `ic_car_weather_off` asset. The phone is untouched. Separately, delete `radarModeLabel`, `MapStyle.nextCycle`, and the `car_radar_*` strings orphaned by the prior car-menu refactor, plus two verifier nits.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Car App Library 1.7.0 (`NavigationTemplate`, `ActionStrip`, `Action`), Robolectric unit tests (SDK 33), Gradle.

## Global Constraints

- **i18n is mandatory:** when touching strings, edit BOTH `app/src/main/res/values/strings.xml` and `app/src/main/res/values-en-rUS/strings.xml`. Other locales are Crowdin-managed (out of scope).
- **No changelog entries** for this work (standing user instruction). Do NOT edit `app/src/main/assets/changelog.json`.
- **Prefer Android SDK / AndroidX / first-party** libraries over third-party.
- **Kotlin code style:** `official` (set in `gradle.properties`).
- **TDD:** red (failing test asserting new behavior) → change → green, with `./gradlew` as the gate.
- **Commit messages** end with `Co-Authored-By: Claude <noreply@anthropic.com>`.

---

## Task 1: Car 3-state weather button (TDD)

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/car/screens/CarNavMapScreen.kt` (lines 43-68: the weather action + `radarPlayPauseIcon` helper + its comment)
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/viewmodel/MapViewModelLogic.kt` (lines 23-27: the stale `cycleWeatherMode` comment)
- Test: `app/src/test/java/ca/voiditswarranty/roadtripradar/car/screens/CarNavMapScreenTest.kt`

**Interfaces:**
- Consumes: `vm.cycleWeatherMode()` (`MapViewModel.kt:588`, public), `vm.weatherMode` (public mutable `WeatherMode`), `WeatherMode` enum (`OFF`/`ON`/`PLAYING`), `R.drawable.ic_car_play` / `ic_car_pause` / `ic_car_weather_off`.
- Produces: a car toolbar weather action that cycles `OFF→PLAYING→ON→OFF` and shows `ic_car_play`/`ic_car_pause`/`ic_car_weather_off` respectively.

- [ ] **Step 1: Write the failing tests (red)**

In `app/src/test/java/ca/voiditswarranty/roadtripradar/car/screens/CarNavMapScreenTest.kt`:

Add the `OnDoneCallback` import as the new first import line (it sorts before `androidx.car.app.model.*`, matching `SettingsScreenTest.kt:3-4`):

```kotlin
import androidx.car.app.OnDoneCallback
import androidx.car.app.model.Action
```

Replace the existing `carNavMapScreen_radarIconIsPlayWhenOnButNotPlaying` test (lines 142-155) with a test asserting the OFF icon for the paused (`ON`) state:

```kotlin
    @Test
    fun carNavMapScreen_radarIconIsOffWhenPaused() {
        // ON (paused, radar visible) — the next tap turns the radar fully off,
        // so the icon shows the off affordance (ic_car_weather_off).
        val v = vm()
        v.updateWeatherMode(WeatherMode.ON)
        val template = buildScreen()
        val secondIcon = actionIconRes(template.actionStrip!!.actions[1])
        assertEquals(
            "second action must use the off icon when weatherMode is ON (paused)",
            R.drawable.ic_car_weather_off,
            secondIcon,
        )
    }
```

Add a new click-cycle test after the `carNavMapScreen_radarIconIsOffWhenPaused` test (before the `// -------- map action strip --------` comment):

```kotlin
    @Test
    fun carNavMapScreen_weatherAction_clickCyclesOffToPlayingToOnToOff() {
        // The weather action is a 3-state cycle: OFF -> PLAYING -> ON -> OFF.
        // Driving three clicks from OFF must land back on OFF. (Today the
        // listener is toggleWeatherPlayPause, whose cycle is OFF -> PLAYING ->
        // ON -> PLAYING, so the third click yields PLAYING, not OFF — this test
        // fails until the listener switches to cycleWeatherMode.)
        val v = vm()
        v.updateWeatherMode(WeatherMode.OFF)
        val template = buildScreen()
        val weatherAction = template.actionStrip!!.actions[1]
        weatherAction.onClickDelegate!!.sendClick(object : OnDoneCallback {})
        assertEquals("OFF -> PLAYING on first tap", WeatherMode.PLAYING, v.weatherMode)
        weatherAction.onClickDelegate!!.sendClick(object : OnDoneCallback {})
        assertEquals("PLAYING -> ON on second tap", WeatherMode.ON, v.weatherMode)
        weatherAction.onClickDelegate!!.sendClick(object : OnDoneCallback {})
        assertEquals("ON -> OFF on third tap", WeatherMode.OFF, v.weatherMode)
    }
```

Update the class KDoc (lines 23-26) — replace the Play/Pause bullet:

```kotlin
 * - The top action strip has three custom-icon actions: Menu (pushes
 *   [HomeScreen]), Weather (a 3-state cycle OFF → PLAYING → ON(paused) → OFF via
 *   [MapViewModel.cycleWeatherMode]; the icon shows the *next* action —
 *   [R.drawable.ic_car_play] / [R.drawable.ic_car_pause] /
 *   [R.drawable.ic_car_weather_off]), and Recenter.
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests 'ca.voiditswarranty.roadtripradar.car.screens.CarNavMapScreenTest' 2>&1 | tail -25
```
Expected: FAIL. `carNavMapScreen_radarIconIsOffWhenPaused` fails (current shows `ic_car_play`); `carNavMapScreen_weatherAction_clickCyclesOffToPlayingToOnToOff` fails (third click yields `PLAYING`, not `OFF`). The other existing tests still pass.

- [ ] **Step 3: Switch the car action to the 3-state cycle (change)**

In `app/src/main/java/ca/voiditswarranty/roadtripradar/car/screens/CarNavMapScreen.kt`, change the weather action's listener (line 46):

```kotlin
                            .setOnClickListener { vm.cycleWeatherMode() }
```

Replace the `radarPlayPauseIcon` helper and its comment (lines 64-68) with the 3-way `radarCycleIcon`:

```kotlin
    /**
     * Icon for the 3-state weather cycle, showing the *next* action a tap performs:
     * OFF → play (start animating), PLAYING → pause, ON(paused) → off (turn radar off).
     * Resume-from-pause costs two taps (ON → OFF → PLAYING) — accepted so that turning
     * the radar fully off is one tap from the paused state, since the car has no
     * long-press affordance (unlike the phone FAB).
     */
    private fun radarCycleIcon(): Int = when (vm.weatherMode) {
        WeatherMode.OFF -> R.drawable.ic_car_play
        WeatherMode.PLAYING -> R.drawable.ic_car_pause
        WeatherMode.ON -> R.drawable.ic_car_weather_off
    }
```

Update the action builder (line 45) to call the renamed helper:

```kotlin
                            .setIcon(carIcon(radarCycleIcon()))
```

- [ ] **Step 4: Update the stale `cycleWeatherMode` comment in MapViewModelLogic.kt**

In `app/src/main/java/ca/voiditswarranty/roadtripradar/viewmodel/MapViewModelLogic.kt`, replace lines 23-27:

```kotlin
 * **What's here, what's not:** the helpers that take a [MapViewModel] parameter
 * are gone — every helper is a pure function of its inputs. The VM's
 * `cycleWeatherMode` (the car toolbar's 3-state OFF→PLAYING→ON→OFF button) and
 * `toggleWeatherPlayPause` (the phone FAB's 2-state play/pause + long-press off)
 * both call the pure `nextWeatherModeOnCycle`/`nextWeatherModeOnPlayPauseToggle`
 * helpers here. Same pattern for `addWaypoint` / `tutorialNext` / `tutorialBack` /
 * `maybeAutoAdvance` / `evaluateWhatsNewChangelog` / `deleteCustomTheme`.
```

- [ ] **Step 5: Run the tests to verify they pass (green)**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests 'ca.voiditswarranty.roadtripradar.car.screens.CarNavMapScreenTest' 2>&1 | tail -15
```
Expected: BUILD SUCCESSFUL (all CarNavMapScreenTest tests pass).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ca/voiditswarranty/roadtripradar/car/screens/CarNavMapScreen.kt \
        app/src/main/java/ca/voiditswarranty/roadtripradar/viewmodel/MapViewModelLogic.kt \
        app/src/test/java/ca/voiditswarranty/roadtripradar/car/screens/CarNavMapScreenTest.kt
git commit -m "Car: 3-state play/pause/off weather button

Switch the car toolbar weather action from toggleWeatherPlayPause (2-state,
never OFF) to cycleWeatherMode (OFF→PLAYING→ON→OFF) so the car can turn the
radar fully off again. Icon maps to the next action: play / pause / off.
Phone unchanged.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 2: Remove CarUi dead code + orphaned strings

Delete `radarModeLabel` and `MapStyle.nextCycle` (zero production callers — their callers were deleted in the prior car-menu refactor), their tests, the `car_radar_*` strings (only `radarModeLabel` used them), and convert `CarUiTest` to plain JUnit since the remaining tests are pure Kotlin.

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarUi.kt` (whole file)
- Modify: `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarUiTest.kt` (whole file)
- Modify: `app/src/main/res/values/strings.xml` (delete 3 lines)
- Modify: `app/src/main/res/values-en-rUS/strings.xml` (delete 3 lines)

**Interfaces:**
- Consumes: nothing (pure deletion).
- Produces: nothing new. Ensures `WindSpeedUnit.nextCycle()` / `TemperatureUnit.nextCycle()` remain (still called by `SettingsScreen.kt:61,69`).

- [ ] **Step 1: Rewrite `CarUi.kt` — drop `radarModeLabel` and `MapStyle.nextCycle`**

Replace the entire contents of `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarUi.kt` with:

```kotlin
package ca.voiditswarranty.roadtripradar.car

import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit

/**
 * Small car-surface UI helpers shared across the car screens, so the unit
 * "cycle to next" behaviour isn't re-implemented per screen. Used by the car
 * Settings picker rows (wind speed, temperature).
 */

/** Cycle to the next enum entry (wrapping). Used by the car unit picker rows. */
internal fun WindSpeedUnit.nextCycle(): WindSpeedUnit {
    val all = WindSpeedUnit.entries
    return all[(all.indexOf(this) + 1) % all.size]
}

internal fun TemperatureUnit.nextCycle(): TemperatureUnit {
    val all = TemperatureUnit.entries
    return all[(all.indexOf(this) + 1) % all.size]
}
```

(This drops the `Context`, `MapStyle`, `WeatherMode`, and `R` imports — all were used only by `radarModeLabel` / `MapStyle.nextCycle`.)

- [ ] **Step 2: Rewrite `CarUiTest.kt` — drop the dead tests, convert to plain JUnit**

Replace the entire contents of `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarUiTest.kt` with:

```kotlin
package ca.voiditswarranty.roadtripradar.car

import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the car-surface unit "cycle to next" helpers in [CarUi.kt] —
 * [WindSpeedUnit.nextCycle] and [TemperatureUnit.nextCycle], the building blocks
 * the car Settings picker rows reuse. Pure Kotlin (no Context / resource access),
 * so plain JUnit — no Robolectric needed.
 */
class CarUiTest {

    // -------- WindSpeedUnit.nextCycle --------

    @Test
    fun nextCycle_windSpeedUnit_kmh_advancesToMph() {
        assertEquals(WindSpeedUnit.MPH, WindSpeedUnit.KMH.nextCycle())
    }

    @Test
    fun nextCycle_windSpeedUnit_mph_advancesToKnots() {
        assertEquals(WindSpeedUnit.KNOTS, WindSpeedUnit.MPH.nextCycle())
    }

    @Test
    fun nextCycle_windSpeedUnit_knots_wrapsToKmh() {
        // The last entry wraps to the first — the cycle is for a "tap to change"
        // picker row in the car Settings screen.
        assertEquals(WindSpeedUnit.KMH, WindSpeedUnit.KNOTS.nextCycle())
    }

    // -------- TemperatureUnit.nextCycle --------

    @Test
    fun nextCycle_temperatureUnit_celsius_advancesToFahrenheit() {
        assertEquals(TemperatureUnit.FAHRENHEIT, TemperatureUnit.CELSIUS.nextCycle())
    }

    @Test
    fun nextCycle_temperatureUnit_fahrenheit_advancesToKelvin() {
        assertEquals(TemperatureUnit.KELVIN, TemperatureUnit.FAHRENHEIT.nextCycle())
    }

    @Test
    fun nextCycle_temperatureUnit_kelvin_wrapsToCelsius() {
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.KELVIN.nextCycle())
    }

    // -------- contract pin: all enum values are covered --------

    @Test
    fun nextCycle_windSpeedUnit_coversAllEntriesExactlyOnce() {
        // Belt-and-suspenders: a future addition to the enum shouldn't silently
        // add a value that the cycle skips. Apply nextCycle to every entry and
        // assert the result set is the same size as the input set.
        val cycled = WindSpeedUnit.entries.map { it.nextCycle() }.toSet()
        assertEquals(
            "nextCycle mapped every entry to a distinct target",
            WindSpeedUnit.entries.toSet(),
            cycled,
        )
    }

    @Test
    fun nextCycle_temperatureUnit_coversAllEntriesExactlyOnce() {
        val cycled = TemperatureUnit.entries.map { it.nextCycle() }.toSet()
        assertEquals(TemperatureUnit.entries.toSet(), cycled)
    }
}
```

(This drops the `radarModeLabel_*` tests, the six `nextCycle_mapStyle_*` tests, `nextCycle_mapStyle_coversAllEntriesExactlyOnce`, the `MapStyle`/`WeatherMode`/`R`/`ApplicationProvider`/`assertTrue` imports, the `context` getter, and the `@RunWith(RobolectricTestRunner)` / `@Config(sdk = [33])` annotations — the remaining tests are pure Kotlin.)

- [ ] **Step 3: Delete the `car_radar_*` strings from `values/strings.xml`**

In `app/src/main/res/values/strings.xml`, delete these three lines (currently lines 460-462, immediately after `car_action_recenter`):

```xml
    <string name="car_radar_off">Off</string>
    <string name="car_radar_on">On</string>
    <string name="car_radar_playing">Playing</string>
```

- [ ] **Step 4: Delete the `car_radar_*` strings from `values-en-rUS/strings.xml`**

In `app/src/main/res/values-en-rUS/strings.xml`, delete these three lines (currently lines 422-424, immediately after `car_action_recenter`):

```xml
    <string name="car_radar_off">Off</string>
    <string name="car_radar_on">On</string>
    <string name="car_radar_playing">Playing</string>
```

- [ ] **Step 5: Run the full test suite + check for warnings**

Run:
```bash
./gradlew clean test 2>&1 | tail -15
```
Expected: BUILD SUCCESSFUL (all tests green; `CarUiTest` still passes as plain JUnit; no dangling references to `radarModeLabel` / `MapStyle.nextCycle` / `car_radar_*`).

Then check for compile warnings:
```bash
./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --rerun-tasks 2>&1 | grep -iE "warning:|w: " | head
```
Expected: no output (zero warnings — confirms the dropped imports were truly unused).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarUi.kt \
        app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarUiTest.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en-rUS/strings.xml
git commit -m "Remove dead car UI code: radarModeLabel, MapStyle.nextCycle, car_radar_* strings

These lost their production callers in the prior car-menu refactor (the
HomeScreen radar-status row, the WeatherScreen radar-mode row, and the
SettingsScreen map-style row). Delete the orphaned helpers + tests, the
car_radar_off/on/playing strings they were the sole users of, and convert
CarUiTest to plain JUnit (the remaining nextCycle tests are pure Kotlin).

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 3: Drop the unused `vm` param + a stray test import (verifier nits)

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/car/screens/HomeScreen.kt` (drop `vm` param from `carMenuRows` / `menuListTemplate` + the call site + the now-unused `MapViewModel` import)
- Modify: `app/src/test/java/ca/voiditswarranty/roadtripradar/car/screens/PoiUtilitiesScreenTest.kt` (drop the unused `assertEquals` import)

**Interfaces:**
- Consumes: nothing.
- Produces: `carMenuRows(carContext: CarContext): ItemList` and `menuListTemplate(carContext: CarContext): ListTemplate` (signatures lose the `vm` parameter).

- [ ] **Step 1: Drop the `vm` param from `HomeScreen.kt`**

In `app/src/main/java/ca/voiditswarranty/roadtripradar/car/screens/HomeScreen.kt`:

Change `buildTemplate` (the class body) to call `menuListTemplate(carContext)`:

```kotlin
    override fun buildTemplate(): ListTemplate =
        menuListTemplate(carContext)
```

Change `carMenuRows` signature and the `menuListTemplate` body's call to it:

```kotlin
fun carMenuRows(carContext: CarContext): ItemList =
    ItemList.Builder().apply {
```

```kotlin
        .setSingleList(carMenuRows(carContext))
```

Change `menuListTemplate` signature:

```kotlin
fun menuListTemplate(carContext: CarContext): ListTemplate {
```

Remove the now-unused import line:

```kotlin
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
```

- [ ] **Step 2: Drop the unused `assertEquals` import from `PoiUtilitiesScreenTest.kt`**

In `app/src/test/java/ca/voiditswarranty/roadtripradar/car/screens/PoiUtilitiesScreenTest.kt`, remove this line:

```kotlin
import org.junit.Assert.assertEquals
```

- [ ] **Step 3: Run the full suite + check for warnings**

Run:
```bash
./gradlew clean test 2>&1 | tail -12
```
Expected: BUILD SUCCESSFUL.

Then:
```bash
./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --rerun-tasks 2>&1 | grep -iE "warning:|w: " | head
```
Expected: no output (confirms the dropped `MapViewModel` import was truly unused, and no unused-import warnings anywhere).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ca/voiditswarranty/roadtripradar/car/screens/HomeScreen.kt \
        app/src/test/java/ca/voiditswarranty/roadtripradar/car/screens/PoiUtilitiesScreenTest.kt
git commit -m "Car: drop HomeScreen's unused vm param + stray test import

carMenuRows/menuListTemplate no longer read the MapViewModel (the menu is two
static navigation rows), so drop the parameter and its call sites, plus the
now-unused MapViewModel import. Remove an unused assertEquals import from
PoiUtilitiesScreenTest.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Final Verification

- [ ] **Full clean test:** `./gradlew clean test` — all green.
- [ ] **Warning-free compile:** `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --rerun-tasks 2>&1 | grep -iE "warning:|w: "` — no output.
- [ ] **APK builds:** `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
- [ ] **No dangling references:** `grep -rn "radarModeLabel\|MapStyle.nextCycle\|car_radar_off\|car_radar_on\|car_radar_playing" app/src` — only matches (if any) inside this plan/spec docs, never in code or `strings.xml`.
- [ ] **Manual (device required):** car toolbar weather button cycles Off → Playing → Paused → Off with the matching icon each state; radar raster + polling follow the state; phone FAB unchanged (tap play/pause, long-press off).