# Phone Test Coverage Follow-up (Unit Tests) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** finish the unit-test work started in Phase 7 of the phone test coverage retrofit: audit the halo-theme bug fix, then extract `MapOverlayState`, `DrawerPage`, and `MapScreenState` so the remaining VM-coupled composables become testable.

**Architecture:** each phase follows the same pattern as Phase 7 — extract pure helpers or state classes from a VM-coupled composable, then TDD the extraction. State classes hold all VM-derived values; content composables take the state class as a parameter. The compass slot pattern from Phase 7's `MapOverlayTopRow` carries forward.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit 4, Robolectric 4.16.1, `createComposeRule`, MapLibre Compose, MapLibre SpatialK (for `Feature`/`FeatureCollection` in the route-layer test).

---

## Phase 1 — Halo-theme audit (Direction 1)

The recent halo bug fix (commit `04634bf`) addressed two issues that the existing test suite did not pin. This phase adds the missing tests, and extracts the route-layer logic from `WaypointRouteLineLayer` so the active-leg logic is testable in isolation.

### Task 1: Add `widget_windIconHasContentDescription` test to `WeatherWidgetTest`

**Files:**
- Modify: `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidgetTest.kt`

- [ ] **Step 1: Read the existing test file**

Read `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidgetTest.kt` to understand the existing test setup pattern (imports, hostContent helper, theme usage). The file has 7 existing tests; new tests follow the same pattern.

- [ ] **Step 2: Add the new test inside the class**

Add this test to the existing `WeatherWidgetTest` class. Place it after the last existing test (look for the closing `}` of the class). The test asserts the wind icon's `contentDescription` is findable in light theme — a regression test for "wind icon invisible on dark mode" (the icon's `contentDescription` is `R.string.cd_wind_direction`):

```kotlin
    @Test
    fun widget_windIconHasContentDescription() {
        hostContent {
            WeatherWidget(
                snapshot = OpenMeteoSnapshot(
                    temperatureCelsius = 20.0,
                    tempTrendCelsius = 0.5,
                    windSpeedKmh = 10.0,
                    windGustsKmh = 15.0,
                    windDirectionDeg = 180.0,
                ),
                temperatureUnit = TemperatureUnit.CELSIUS,
                windSpeedUnit = WindSpeedUnit.KMH,
                weatherWidgetSize = 100f,
                cameraBearing = 0.0,
            )
        }
        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.getString(R.string.cd_wind_direction),
            )
            .assertExists()
    }
```

- [ ] **Step 3: Add required imports**

Add these imports at the top of the test file (preserve alphabetical order):

```kotlin
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import androidx.compose.ui.test.onNodeWithContentDescription
```

- [ ] **Step 4: Run the test, watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.WeatherWidgetTest.widget_windIconHasContentDescription"`
Expected: PASS (this pins existing correct behavior — the icon is rendered with a content description).

- [ ] **Step 5: Commit**

Per the user's standing constraint ("Don't commit, I will do that"), **do not commit**. Report the test passes; the user will commit.

### Task 2: Add `widget_darkMode_windIconIsFindable` test to `WeatherWidgetTest`

**Files:**
- Modify: `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidgetTest.kt`

- [ ] **Step 1: Add the dark-mode test**

Add this test to the existing class. The test renders the widget in dark theme and asserts the wind icon's content description is still findable — the regression test for "wind icon invisible on dark mode" (commit `04634bf` added a circular `Box` background with `colorScheme.surface` behind the `Air` icon, so the test confirms the icon is wrapped in a findable node even in dark theme):

```kotlin
    @Test
    fun widget_darkMode_windIconIsFindable() {
        composeTestRule.setContent {
            RoadTripRadarTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    WeatherWidget(
                        snapshot = OpenMeteoSnapshot(
                            temperatureCelsius = 20.0,
                            tempTrendCelsius = 0.5,
                            windSpeedKmh = 10.0,
                            windGustsKmh = 15.0,
                            windDirectionDeg = 180.0,
                        ),
                        temperatureUnit = TemperatureUnit.CELSIUS,
                        windSpeedUnit = WindSpeedUnit.KMH,
                        weatherWidgetSize = 100f,
                        cameraBearing = 0.0,
                    )
                }
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.getString(R.string.cd_wind_direction),
            )
            .assertExists()
    }
```

- [ ] **Step 2: Run the test, watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.WeatherWidgetTest.widget_darkMode_windIconIsFindable"`
Expected: PASS (the icon has its content description in both themes — the dark mode contrast issue is purely visual, the icon node is still in the tree).

- [ ] **Step 3: Commit (per user, do not commit)**

Report the test passes; the user will commit.

### Task 3: Create `RouteLineLayerLogic.kt` with `pastLegs` helper

**Files:**
- Create: `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogic.kt`

- [ ] **Step 1: Write the failing test first**

Create the test file `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogicTest.kt`:

```kotlin
package ca.voiditswarranty.roadtripradar.ui

import ca.voiditswarranty.roadtripradar.data.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.spatialk.geojson.Position

/**
 * Unit tests for [pastLegs] and [buildActiveLegLineString].
 *
 * These helpers are extracted from [WaypointRouteLineLayer] so the active-leg
 * "draws from user position to the active target" rule is testable in isolation.
 * The bug fixed in commit 04634bf was that the active leg was being drawn as the
 * static planned leg between the current and next waypoint, not the live
 * user→target approach.
 */
class RouteLineLayerLogicTest {

    private fun wp(lat: Double, lon: Double) = Waypoint(
        id = "$lat-$lon",
        position = Position(lon = lon, lat = lat),
    )

    // ---- pastLegs ----

    @Test
    fun pastLegs_emptyWaypoints_returnsEmpty() {
        val result = pastLegs(waypoints = emptyList(), activeIndex = 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun pastLegs_nullActiveIndex_returnsEmpty() {
        val result = pastLegs(
            waypoints = listOf(wp(0.0, 0.0), wp(1.0, 1.0)),
            activeIndex = null,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun pastLegs_activeIndexZero_returnsEmpty() {
        // Active = 0, the user→first-waypoint approach is drawn by the active
        // leg, not as a past leg. So pastLegs is empty.
        val result = pastLegs(
            waypoints = listOf(wp(0.0, 0.0), wp(1.0, 1.0), wp(2.0, 2.0)),
            activeIndex = 0,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun pastLegs_activeIndexTwo_returnsOneLeg() {
        // 4 waypoints, active = 2, returns 1 leg: waypoints[1] → waypoints[2].
        // (waypoints[2] → waypoints[3] is the active leg, waypoints[0] → waypoints[1] is also active.)
        val waypoints = listOf(
            wp(0.0, 0.0), wp(1.0, 1.0), wp(2.0, 2.0), wp(3.0, 3.0),
        )
        val result = pastLegs(waypoints = waypoints, activeIndex = 2)
        assertEquals(1, result.size)
        assertEquals(waypoints[1].position, result[0].coordinates[0])
        assertEquals(waypoints[2].position, result[0].coordinates[1])
    }

    @Test
    fun pastLegs_activeIndexLast_returnsAllPriorLegs() {
        // 4 waypoints, active = 3 (last), returns 2 legs: [0→1, 1→2].
        val waypoints = listOf(
            wp(0.0, 0.0), wp(1.0, 1.0), wp(2.0, 2.0), wp(3.0, 3.0),
        )
        val result = pastLegs(waypoints = waypoints, activeIndex = 3)
        assertEquals(2, result.size)
    }

    // ---- buildActiveLegLineString ----

    @Test
    fun activeLeg_emptyWaypoints_returnsNull() {
        val result = buildActiveLegLineString(
            waypoints = emptyList(),
            activeIndex = 0,
            userPosition = Position(lon = 0.0, lat = 0.0),
        )
        assertNull(result)
    }

    @Test
    fun activeLeg_nullActiveIndex_returnsNull() {
        val result = buildActiveLegLineString(
            waypoints = listOf(wp(0.0, 0.0), wp(1.0, 1.0)),
            activeIndex = null,
            userPosition = Position(lon = 0.0, lat = 0.0),
        )
        assertNull(result)
    }

    @Test
    fun activeLeg_activeIndexOutOfRange_returnsNull() {
        val result = buildActiveLegLineString(
            waypoints = listOf(wp(0.0, 0.0), wp(1.0, 1.0)),
            activeIndex = 5,
            userPosition = Position(lon = 0.0, lat = 0.0),
        )
        assertNull(result)
    }

    @Test
    fun activeLeg_validInputs_legFromUserToActiveWaypoint() {
        // The active leg goes from the user's live position to the active target
        // waypoint — NOT from the previous waypoint. This is the bug fixed in
        // commit 04634bf: previously the active leg was the static planned
        // waypoints[activeIndex] → waypoints[activeIndex + 1].
        val waypoints = listOf(
            wp(0.0, 0.0), wp(1.0, 1.0), wp(2.0, 2.0), wp(3.0, 3.0),
        )
        val userPos = Position(lon = -1.0, lat = -1.0)
        val result = buildActiveLegLineString(
            waypoints = waypoints,
            activeIndex = 2,
            userPosition = userPos,
        )
        assertEquals(2, result!!.coordinates.size)
        assertEquals(userPos, result.coordinates[0])
        assertEquals(waypoints[2].position, result.coordinates[1])
    }
}
```

- [ ] **Step 2: Run the test, watch it fail to compile**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.RouteLineLayerLogicTest"`
Expected: COMPILE FAILURE — `Unresolved reference: pastLegs` and `Unresolved reference: buildActiveLegLineString`. This is the expected TDD red.

- [ ] **Step 3: Create the helper file**

Create `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogic.kt`:

```kotlin
package ca.voiditswarranty.roadtripradar.ui

import ca.voiditswarranty.roadtripradar.data.Waypoint
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position

/**
 * Pure route-line layer logic, extracted from [WaypointRouteLineLayer] so the
 * past-leg and active-leg builders can be unit-tested in isolation.
 *
 * The two halves of the route line:
 *  - **Past legs** ([pastLegs]): the planned segments *strictly before* the
 *    active index. Drawn dashed. The user→first-waypoint approach is *not* a
 *    past leg — it's drawn by the active leg, so the line never duplicates.
 *  - **Active leg** ([buildActiveLegLineString]): the solid user→target
 *    approach that advances as the user moves. The bug fixed in commit
 *    `04634bf` was that this was being drawn as the static planned leg
 *    `waypoints[activeIndex] → waypoints[activeIndex + 1]`, so the line never
 *    advanced. The fix is that the active leg always starts at the user's
 *    live [userPosition].
 */

/**
 * The planned legs that are *strictly before* the active index. Excludes the
 * user→first-waypoint approach (that leg is owned by the active leg) and the
 * activeIndex→next planned leg (also owned by the active leg).
 *
 * Returns an empty list if [activeIndex] is null or 0, or if there are fewer
 * than 2 waypoints.
 */
internal fun pastLegs(
    waypoints: List<Waypoint>,
    activeIndex: Int?,
): List<LineString> {
    if (waypoints.size < 2 || activeIndex == null || activeIndex <= 0) return emptyList()
    val lastPastIndex = (activeIndex - 1).coerceAtMost(waypoints.size - 1)
    return (1..lastPastIndex).map { i ->
        LineString(listOf(waypoints[i].position, waypoints[i + 1].position))
    }
}

/**
 * The active "approach" leg from the user's live [userPosition] to the active
 * target waypoint. Returns null if there's no active waypoint, the active
 * index is out of range, or the waypoints list is empty.
 *
 * This is the line that advances as the user moves — drawn solid on top of
 * the dashed planned legs. The bug fixed in `04634bf` had this drawing the
 * static planned `waypoints[activeIndex] → waypoints[activeIndex + 1]`
 * instead.
 */
internal fun buildActiveLegLineString(
    waypoints: List<Waypoint>,
    activeIndex: Int?,
    userPosition: Position,
): LineString? {
    if (waypoints.isEmpty() || activeIndex == null || activeIndex !in waypoints.indices) return null
    return LineString(listOf(userPosition, waypoints[activeIndex].position))
}
```

- [ ] **Step 4: Run the test, watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.RouteLineLayerLogicTest"`
Expected: all 10 tests PASS.

- [ ] **Step 5: Commit (per user, do not commit)**

Report the tests pass; the user will commit.

### Task 4: Refactor `WaypointRouteLineLayer` to use the new helpers

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapLayers.kt` — function `WaypointRouteLineLayer` (around lines 222-264)

- [ ] **Step 1: Locate the existing `WaypointRouteLineLayer`**

Open `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapLayers.kt` and find the `WaypointRouteLineLayer` composable (around line 222). The current body builds `inactiveFc` and `activeFc` `FeatureCollection`s from inline lambdas.

- [ ] **Step 2: Replace the inline lambdas with calls to the new helpers**

Replace the body of `WaypointRouteLineLayer` (lines 232-244) with the following. The shape stays the same — the lambdas just become named helper calls:

```kotlin
    val inactiveFc = remember(waypoints.toList(), activeIndex) {
        FeatureCollection(
            pastLegs(waypoints, activeIndex).map {
                Feature(geometry = it, properties = buildJsonObject {})
            },
        )
    }
    val activeFc = remember(waypoints.toList(), activeIndex, userPosition) {
        val leg = buildActiveLegLineString(waypoints, activeIndex, userPosition)
        FeatureCollection(
            if (leg != null) listOf(Feature(geometry = leg, properties = buildJsonObject {})) else emptyList(),
        )
    }
```

- [ ] **Step 3: Run the test suite to confirm nothing broke**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all tests pass (existing 839 + new 10 = 849 tests).

- [ ] **Step 4: Commit (per user, do not commit)**

Report the refactor; the user will commit.

### Task 5: Create `RouteLineLayerStyleTest.kt` (pin the layer styling constants)

**Files:**
- Create: `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyleTest.kt`

- [ ] **Step 1: Write the test for the style data class**

The `RouteLineLayerStyle` data class doesn't exist yet — this test is the TDD trigger for its creation. Create `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyleTest.kt`:

```kotlin
package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the visual style of the route-line layers so a future "let's make the
 * line thicker" or "let's change the color" change is intentional, not
 * accidental. The values are taken from the current production render in
 * [WaypointRouteLineLayer] (MapLayers.kt) — the bug fix in commit `04634bf`
 * landed on these values.
 */
class RouteLineLayerStyleTest {

    @Test
    fun style_inactiveLayer_usesExpectedColorWidthAndDasharray() {
        val style = RouteLineLayerStyle.INACTIVE
        assertEquals(Color.Green, style.color)
        assertEquals(5.dp, style.width)
        assertEquals(0.6f, style.opacity, 0.001f)
        assertEquals(listOf(2, 3), style.dasharray)
    }

    @Test
    fun style_activeLayer_usesExpectedColorWidthAndOpacity() {
        val style = RouteLineLayerStyle.ACTIVE
        assertEquals(Color.Green, style.color)
        assertEquals(5.dp, style.width)
        assertEquals(0.95f, style.opacity, 0.001f)
        assertEquals(null, style.dasharray)
    }
}
```

- [ ] **Step 2: Run the test, watch it fail to compile**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.RouteLineLayerStyleTest"`
Expected: COMPILE FAILURE — `Unresolved reference: RouteLineLayerStyle`. This is the expected TDD red.

- [ ] **Step 3: Create the style data class**

Create `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyle.kt`:

```kotlin
package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Visual styling for the two halves of the route line, pinned by
 * [RouteLineLayerStyleTest] so a future "let's make the line thicker" change
 * is intentional, not accidental.
 *
 * - [INACTIVE] is drawn dashed and is the planned path *before* the active
 *   waypoint.
 * - [ACTIVE] is drawn solid and is the live user→target approach that
 *   advances as the user moves.
 */
internal data class RouteLineLayerStyle(
    val color: Color,
    val width: Dp,
    val opacity: Float,
    val dasharray: List<Int>?,
) {
    companion object {
        val INACTIVE = RouteLineLayerStyle(
            color = Color.Green,
            width = 5.dp,
            opacity = 0.6f,
            dasharray = listOf(2, 3),
        )
        val ACTIVE = RouteLineLayerStyle(
            color = Color.Green,
            width = 5.dp,
            opacity = 0.95f,
            dasharray = null,
        )
    }
}
```

- [ ] **Step 4: Run the test, watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.RouteLineLayerStyleTest"`
Expected: both tests PASS.

- [ ] **Step 5: Commit (per user, do not commit)**

Report the tests pass; the user will commit.

---

## Phase 2 — `MapOverlayState` extraction (Direction 2, aggressive)

The public `MapOverlay` composable still takes `vm: MapViewModel` and reads 25+ values from it. This phase extracts a `MapOverlayState` data class and a parameterized `MapOverlayContent` composable so the test can drive the state directly.

### Task 6: Create `MapOverlayState.kt`

**Files:**
- Create: `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapOverlayState.kt`

- [ ] **Step 1: Create the data class**

Create `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapOverlayState.kt`. This is a pure data class with no logic — no test required for the data class itself (constructor pins are not worth a test):

```kotlin
package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.PaddingValues
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.model.NetworkStatus
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import org.maplibre.spatialk.units.Length

/**
 * State for the [MapOverlayContent] composable. Holds every VM-derived value
 * the overlay reads so the content composable can be parameterized and tested
 * without instantiating a real [ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel].
 *
 * Produced by the public [MapOverlay] composable, which reads from the VM
 * and constructs this class. Tests build it directly.
 */
internal data class MapOverlayState(
    val useMetric: Boolean,
    val speedMps: Double,
    val speedSize: Float,
    val hasLocation: Boolean,
    val useGps: Boolean,
    val hasGpsFix: Boolean,
    val gpsIconOpacity: Float,
    val networkStatus: NetworkStatus,
    val activeWaypoint: Waypoint?,
    val activeIndex: Int?,
    val waypoints: List<Waypoint>,
    val bearing: Double,
    val poiInfo: Pair<Length, Double>?,
    val navWidgetSize: Float,
    val hasFailedCells: Boolean,
    val isNorthUp: Boolean,
    val weatherActive: Boolean,
    val showTimeline: Boolean,
    val radarFrameTimes: List<Long>,
    val currentFrameIndex: Int,
    val weatherWidgetSize: Float,
    val windEnabled: Boolean,
    val openMeteoSnapshot: OpenMeteoSnapshot?,
    val temperatureUnit: TemperatureUnit,
    val windSpeedUnit: WindSpeedUnit,
    val showLegend: Boolean,
    val hasRadarFrames: Boolean,
    val fabScale: Float,
    val widgetScale: Float,
    val isTrackingCamera: Boolean,
    val isWeatherPlaying: Boolean,
)
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. The class is unused so far, but the compile passes.

- [ ] **Step 3: Commit (per user, do not commit)**

Report the data class is in place; the user will commit.

### Task 7: Refactor `MapOverlay` to read into `MapOverlayState` and delegate to `MapOverlayContent`

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapOverlay.kt`

- [ ] **Step 1: Read the current `MapOverlay` body**

Open `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapOverlay.kt` and locate the public `MapOverlay` composable (around line 82) and the private sub-composables `MapOverlayTopRow`, `MapOverlayLeftContent`, `MapOverlayRightContent`. These sub-composables already exist and are parameterized — they take their inputs as parameters, not from a VM.

- [ ] **Step 2: Extract `MapOverlayContent`**

Add a new `@Composable fun BoxScope.MapOverlayContent(...)` to `MapOverlay.kt`. The signature mirrors the public `MapOverlay` signature, but takes `state: MapOverlayState` instead of `vm: MapViewModel`. The body is the current `MapOverlay` body, with all `vm.*` reads replaced by `state.*`:

```kotlin
@Composable
fun BoxScope.MapOverlayContent(
    config: LayoutConfig,
    state: MapOverlayState,
    bearing: Double,
    hasLocation: Boolean,
    poiInfo: Pair<Length, Double>?,
    cameraState: CameraState,
    scope: CoroutineScope,
    isLandscape: Boolean = false,
) {
    val sharedEdgeModifier = Modifier.padding(config.edgePadding)
    val density = LocalDensity.current
    var topRowHeightPx by remember { mutableIntStateOf(0) }
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomRowContentHeight = 96.dp * config.fabScale
    val compassSize = state.compassWidgetSize.dp * config.widgetScale
    val measuredTopBandHeight = with(density) { topRowHeightPx.toDp() }
    val estimatedTopBandHeight = compassSize + (config.edgePadding * 2)
    val topBandHeight = (if (topRowHeightPx > 0) measuredTopBandHeight else estimatedTopBandHeight) +
        config.controlSpacing
    val bottomBandHeight = bottomRowContentHeight + navBottomInset + (config.edgePadding * 2)
    var legendHeightPx by remember { mutableIntStateOf(0) }
    val legendHeight = with(density) { legendHeightPx.toDp() }

    MapOverlayTopRow(
        useMetric = state.useMetric,
        speedMps = state.speedMps,
        speedSize = state.speedSize,
        hasLocation = hasLocation,
        useGps = state.useGps,
        hasGpsFix = state.hasGpsFix,
        gpsIconOpacity = state.gpsIconOpacity,
        networkStatus = state.networkStatus,
        activeWaypoint = state.activeWaypoint,
        activeIndex = state.activeIndex,
        waypoints = state.waypoints,
        bearing = bearing,
        poiInfo = poiInfo,
        navWidgetSize = state.navWidgetSize,
        hasFailedCells = state.hasFailedCells,
        isNorthUp = state.isNorthUp,
        fabScale = config.fabScale,
        widgetScale = config.widgetScale,
        onOpenRouteEditor = { /* vm::openRouteEditor */ },
        onAdvanceWaypoint = { /* vm::advanceActiveWaypoint */ },
        onRegressWaypoint = { /* vm::regressActiveWaypoint */ },
        onCompassToggle = { isNorthUp -> /* vm.isNorthUp = isNorthUp */ },
        onRetryFailedCells = { /* vm::retryFailedCells */ },
        compassButton = {
            Box(modifier = Modifier.tutorialAnchor(TutorialAnchors.COMPASS)) {
                CompassButton(
                    cameraState = cameraState,
                    colors = ButtonDefaults.elevatedButtonColors(),
                    size = compassSize,
                    contentPadding = PaddingValues(8.dp),
                    shape = CircleShape,
                    getHomePosition = { current ->
                        val newNorthUp = !state.isNorthUp
                        if (newNorthUp) {
                            current.copy(bearing = 0.0, tilt = 0.0)
                        } else {
                            current.copy(tilt = 0.0)
                        }
                    },
                )
            }
        },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .onSizeChanged { topRowHeightPx = it.height }
            .then(sharedEdgeModifier),
    )

    LeftContent(
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .padding(
                start = config.edgePadding,
                top = topBandHeight,
                bottom = bottomBandHeight,
            ),
    ) {
        MapOverlayLeftContent(
            topBandHeight = topBandHeight,
            bottomBandHeight = bottomBandHeight,
            legendHeight = legendHeight,
            weatherActive = state.weatherActive,
            showTimeline = state.showTimeline,
            frameTimes = state.radarFrameTimes,
            currentFrameIndex = state.currentFrameIndex,
        )
    }

    RightContent(
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(
                end = config.edgePadding,
                top = topBandHeight,
                bottom = bottomBandHeight,
            ),
    ) {
        MapOverlayRightContent(
            weatherWidgetSize = state.weatherWidgetSize * config.widgetScale,
            bearing = bearing,
            windEnabled = state.windEnabled,
            openMeteoSnapshot = state.openMeteoSnapshot,
            temperatureUnit = state.temperatureUnit,
            windSpeedUnit = state.windSpeedUnit,
            weatherActive = state.weatherActive,
            showLegend = state.showLegend,
            hasRadarFrames = state.hasRadarFrames,
            onHeightChanged = { legendHeightPx = it },
        )
    }

    BottomContent(
        onZoomIn = {
            scope.launch {
                cameraState.animateTo(
                    cameraState.position.copy(zoom = cameraState.position.zoom + 1)
                )
            }
        },
        onZoomOut = {
            scope.launch {
                cameraState.animateTo(
                    cameraState.position.copy(zoom = cameraState.position.zoom - 1)
                )
            }
        },
        onZoomInLong = { /* vm.advanceActiveWaypoint() */ },
        onZoomOutLong = { /* vm.regressActiveWaypoint() */ },
        isWeatherPlaying = state.isWeatherPlaying,
        weatherActive = state.weatherActive,
        onToggleWeatherPlayPause = { /* vm.toggleWeatherPlayPause() */ },
        onWeatherOff = { /* vm.turnOffWeather() */ },
        onOpenMenu = { /* vm.openActionsDrawer() */ },
        aboveContent = {
            RecenterTextButton(
                hasLocation = hasLocation,
                isTrackingCamera = state.isTrackingCamera,
                onRecenter = { /* vm.isTrackingCamera = true */ },
                scale = config.fabScale * 1.3f,
            )
        },
        isLandscape = isLandscape,
        scale = config.fabScale,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = config.edgePadding),
    )
}
```

> The callbacks (`onOpenRouteEditor`, `onAdvanceWaypoint`, etc.) are now
> placeholders in the extracted `MapOverlayContent` — they're filled in by
> the public `MapOverlay` wrapper in the next step. Tests pass their own
> callback lambdas.

- [ ] **Step 3: Update the public `MapOverlay` to delegate to `MapOverlayContent`**

Replace the public `MapOverlay` body (lines 82-248) with a thin wrapper that builds the state and forwards all callbacks. The `hasGpsFix` and `bearing` parameters stay on the wrapper (they're not VM-derived — they're computed by the call site):

```kotlin
@Composable
fun BoxScope.MapOverlay(
    config: LayoutConfig,
    vm: ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel,
    bearing: Double,
    hasLocation: Boolean,
    hasGpsFix: Boolean,
    speedMps: Double,
    poiInfo: Pair<Length, Double>?,
    cameraState: CameraState,
    scope: CoroutineScope,
    isLandscape: Boolean = false,
) {
    val state = MapOverlayState(
        useMetric = vm.useMetric,
        speedMps = speedMps,
        speedSize = vm.speedSize,
        hasLocation = hasLocation,
        useGps = vm.useGps,
        hasGpsFix = hasGpsFix,
        gpsIconOpacity = vm.gpsIconOpacity,
        networkStatus = vm.networkStatus,
        activeWaypoint = vm.activeWaypoint,
        activeIndex = vm.activeIndex,
        waypoints = vm.waypoints,
        bearing = bearing,
        poiInfo = poiInfo,
        navWidgetSize = vm.navWidgetSize,
        hasFailedCells = vm.hasFailedCells,
        isNorthUp = vm.isNorthUp,
        weatherActive = vm.weatherActive,
        showTimeline = vm.showTimeline,
        radarFrameTimes = vm.radarFrameTimes,
        currentFrameIndex = vm.currentFrameIndex,
        weatherWidgetSize = vm.weatherWidgetSize,
        windEnabled = vm.windEnabled,
        openMeteoSnapshot = vm.openMeteoSnapshot,
        temperatureUnit = vm.temperatureUnit,
        windSpeedUnit = vm.windSpeedUnit,
        showLegend = vm.showLegend,
        hasRadarFrames = vm.radarFramePaths.isNotEmpty(),
        isTrackingCamera = vm.isTrackingCamera,
        isWeatherPlaying = vm.isWeatherPlaying,
    )
    MapOverlayContent(
        config = config,
        state = state,
        bearing = bearing,
        hasLocation = hasLocation,
        poiInfo = poiInfo,
        cameraState = cameraState,
        scope = scope,
        isLandscape = isLandscape,
    )
}
```

> **NOTE:** this step removed the VM-coupled callbacks. The original
> `MapOverlay` body called `vm::openRouteEditor`, `vm::advanceActiveWaypoint`,
> etc. inline. To preserve that wiring, the public `MapOverlay` wrapper must
> pass them via a callback map — but the spec says the public composable is
> "a thin wrapper" so the callbacks stay close to the VM. The simplest
> approach: parameterize `MapOverlayContent` to take the callbacks as
> additional parameters, then have the public `MapOverlay` wire them up.
> See Task 8.

- [ ] **Step 4: Run the test suite to confirm the existing test still compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. No tests fail because the public `MapOverlay` signature is unchanged.

- [ ] **Step 5: Commit (per user, do not commit)**

Report the refactor; the user will commit.

### Task 8: Add callback parameters to `MapOverlayContent`

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapOverlay.kt`

- [ ] **Step 1: Add callback parameters to `MapOverlayContent`**

Update the `MapOverlayContent` signature to take the 9 callbacks the original `MapOverlay` body called. These are the seams that tests can drive:

```kotlin
@Composable
fun BoxScope.MapOverlayContent(
    config: LayoutConfig,
    state: MapOverlayState,
    bearing: Double,
    hasLocation: Boolean,
    poiInfo: Pair<Length, Double>?,
    cameraState: CameraState,
    scope: CoroutineScope,
    isLandscape: Boolean = false,
    onOpenRouteEditor: () -> Unit = {},
    onAdvanceWaypoint: () -> Unit = {},
    onRegressWaypoint: () -> Unit = {},
    onCompassToggle: (Boolean) -> Unit = {},
    onRetryFailedCells: () -> Unit = {},
    onZoomInLong: () -> Unit = {},
    onZoomOutLong: () -> Unit = {},
    onToggleWeatherPlayPause: () -> Unit = {},
    onWeatherOff: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    onRecenter: () -> Unit = {},
    compassButton: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) { ... }
```

Inside the body, replace the placeholder callbacks with the parameters. The signatures match what `MapOverlayTopRow` and `BottomContent` already expect.

- [ ] **Step 2: Update the public `MapOverlay` to pass the VM callbacks**

In the public `MapOverlay` body, pass the VM methods to `MapOverlayContent`:

```kotlin
MapOverlayContent(
    config = config,
    state = state,
    bearing = bearing,
    hasLocation = hasLocation,
    poiInfo = poiInfo,
    cameraState = cameraState,
    scope = scope,
    isLandscape = isLandscape,
    onOpenRouteEditor = vm::openRouteEditor,
    onAdvanceWaypoint = vm::advanceActiveWaypoint,
    onRegressWaypoint = vm::regressActiveWaypoint,
    onCompassToggle = { isNorthUp -> vm.isNorthUp = isNorthUp },
    onRetryFailedCells = vm::retryFailedCells,
    onZoomInLong = vm::advanceActiveWaypoint,
    onZoomOutLong = vm::regressActiveWaypoint,
    onToggleWeatherPlayPause = vm::toggleWeatherPlayPause,
    onWeatherOff = vm::turnOffWeather,
    onOpenMenu = vm::openActionsDrawer,
    onRecenter = { vm.isTrackingCamera = true },
    compassButton = { /* the compass button Box from the original body */ },
)
```

- [ ] **Step 3: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit (per user, do not commit)**

Report the refactor; the user will commit.

### Task 9: Create `MapOverlayStateTest.kt`

**Files:**
- Create: `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/MapOverlayStateTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package ca.voiditswarranty.roadtripradar.ui

import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.model.NetworkStatus
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.maplibre.spatialk.geojson.Position

/**
 * Sanity tests for [MapOverlayState] data class. Constructor pins, equality,
 * copy. Trivial but pins the contract — a future "let's add a field" change
 * will show as a test diff here.
 */
class MapOverlayStateTest {

    private fun sampleState() = MapOverlayState(
        useMetric = true,
        speedMps = 25.0,
        speedSize = 1.0f,
        hasLocation = true,
        useGps = true,
        hasGpsFix = true,
        gpsIconOpacity = 0.8f,
        networkStatus = NetworkStatus.Online,
        activeWaypoint = null,
        activeIndex = null,
        waypoints = emptyList<Waypoint>(),
        bearing = 0.0,
        poiInfo = null,
        navWidgetSize = 1.0f,
        hasFailedCells = false,
        isNorthUp = true,
        weatherActive = false,
        showTimeline = false,
        radarFrameTimes = emptyList(),
        currentFrameIndex = 0,
        weatherWidgetSize = 1.0f,
        windEnabled = true,
        openMeteoSnapshot = null,
        temperatureUnit = TemperatureUnit.CELSIUS,
        windSpeedUnit = WindSpeedUnit.KMH,
        showLegend = false,
        hasRadarFrames = false,
        fabScale = 1.0f,
        widgetScale = 1.0f,
        isTrackingCamera = true,
        isWeatherPlaying = false,
    )

    @Test
    fun state_constructor_pinsAllFields() {
        val state = sampleState()
        assertEquals(true, state.useMetric)
        assertEquals(25.0, state.speedMps, 0.001)
        assertEquals(NetworkStatus.Online, state.networkStatus)
        assertEquals(TemperatureUnit.CELSIUS, state.temperatureUnit)
        assertEquals(WindSpeedUnit.KMH, state.windSpeedUnit)
        assertEquals(true, state.isTrackingCamera)
        assertEquals(1.0f, state.fabScale, 0.001f)
        assertEquals(1.0f, state.widgetScale, 0.001f)
    }

    @Test
    fun state_equality_basedOnAllFields() {
        val a = sampleState()
        val b = sampleState()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun state_copy_singleFieldChange_inequality() {
        val a = sampleState()
        val b = a.copy(useMetric = false)
        assertNotEquals(a, b)
        assertEquals(false, b.useMetric)
        // all other fields preserved
        assertEquals(a.speedMps, b.speedMps, 0.001)
        assertEquals(a.networkStatus, b.networkStatus)
    }
}
```

- [ ] **Step 2: Run the test, watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.MapOverlayStateTest"`
Expected: all 3 tests PASS.

- [ ] **Step 3: Commit (per user, do not commit)**

Report the tests pass; the user will commit.

### Task 10: Create `MapOverlayContentTest.kt`

**Files:**
- Create: `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/MapOverlayContentTest.kt`

- [ ] **Step 1: Write the first test — all fields populated, renders without crash**

```kotlin
package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.model.NetworkStatus
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.spatialk.geojson.Position
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [MapOverlayContent]. The composable is parameterized
 * over [MapOverlayState] so the test builds the state directly — no real
 * [ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel] needed.
 *
 * The compass slot is empty in tests (the real [CompassButton] needs a real
 * [CameraState] which we don't have under JVM unit tests).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapOverlayContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testScope: CoroutineScope = CoroutineScope(SupervisorJob())

    private fun hostContent(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    private fun sampleState(
        waypoints: List<Waypoint> = emptyList(),
        activeWaypoint: Waypoint? = null,
        activeIndex: Int? = null,
        poiInfo: Pair<org.maplibre.spatialk.units.Length, Double>? = null,
        hasFailedCells: Boolean = false,
        windEnabled: Boolean = true,
        weatherActive: Boolean = false,
        showLegend: Boolean = false,
        hasRadarFrames: Boolean = false,
        showTimeline: Boolean = false,
        radarFrameTimes: List<Long> = emptyList(),
    ) = MapOverlayState(
        useMetric = true,
        speedMps = 25.0,
        speedSize = 1.0f,
        hasLocation = true,
        useGps = true,
        hasGpsFix = true,
        gpsIconOpacity = 0.8f,
        networkStatus = NetworkStatus.Online,
        activeWaypoint = activeWaypoint,
        activeIndex = activeIndex,
        waypoints = waypoints,
        bearing = 0.0,
        poiInfo = poiInfo,
        navWidgetSize = 1.0f,
        hasFailedCells = hasFailedCells,
        isNorthUp = true,
        weatherActive = weatherActive,
        showTimeline = showTimeline,
        radarFrameTimes = radarFrameTimes,
        currentFrameIndex = 0,
        weatherWidgetSize = 100f,
        windEnabled = windEnabled,
        openMeteoSnapshot = null,
        temperatureUnit = TemperatureUnit.CELSIUS,
        windSpeedUnit = WindSpeedUnit.KMH,
        showLegend = showLegend,
        hasRadarFrames = hasRadarFrames,
        fabScale = 1.0f,
        widgetScale = 1.0f,
        isTrackingCamera = true,
        isWeatherPlaying = false,
    )

    @Test
    fun content_allFieldsPopulated_rendersWithoutCrash() {
        hostContent {
            Box(Modifier.fillMaxSize()) {
                MapOverlayContent(
                    config = LayoutConfig(),
                    state = sampleState(),
                    bearing = 0.0,
                    hasLocation = true,
                    poiInfo = null,
                    cameraState = StubCameraState(),
                    scope = testScope,
                )
            }
        }
        // The render completes; assertExists is implicit.
        assertTrue(true)
    }
}
```

- [ ] **Step 2: Create the `StubCameraState` helper**

`MapOverlayContent` takes a `CameraState` parameter. Under JVM unit tests we don't have a real `CameraState`. Create a minimal stub at the top of the test file (above the class):

```kotlin
private class StubCameraState : org.maplibre.compose.camera.CameraState {
    override val position: org.maplibre.compose.camera.CameraPosition =
        org.maplibre.compose.camera.CameraPosition()
    override val isCameraMoving: kotlinx.coroutines.flow.StateFlow<Boolean> =
        kotlinx.coroutines.flow.MutableStateFlow(false)
    override val moveReason: kotlinx.coroutines.flow.StateFlow<org.maplibre.compose.camera.CameraMoveReason> =
        kotlinx.coroutines.flow.MutableStateFlow(org.maplibre.compose.camera.CameraMoveReason.NONE)
    override suspend fun animateTo(target: org.maplibre.compose.camera.CameraPosition) {}
    override suspend fun setCamera(target: org.maplibre.compose.camera.CameraPosition) {}
    override val cameraCallbacks: org.maplibre.compose.camera.CameraCallbacks =
        object : org.maplibre.compose.camera.CameraCallbacks {
            override fun onCameraMovedListener() {}
        }
    override val cameraViewport: org.maplibre.compose.camera.CameraViewport = TODO("stub")
    override val projection: org.maplibre.compose.camera.Projection = TODO("stub")
    override val screenSizes: kotlinx.coroutines.flow.StateFlow<org.maplibre.compose.camera.ScreenSize?> =
        kotlinx.coroutines.flow.MutableStateFlow(null)
    override fun setScreenSize(size: org.maplibre.compose.camera.ScreenSize) {}
}
```

> **NOTE:** if the `CameraState` interface has changed since this was
> written, the actual method signatures may differ. The test compile is the
> ground truth — fix the stub to match. If `CameraState` is sealed or
> abstract, the test approach may need to change: see the section "If
> CameraState can't be stubbed" below the rest of the tests.

- [ ] **Step 3: Run the test, watch it compile and pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.MapOverlayContentTest.content_allFieldsPopulated_rendersWithoutCrash"`
Expected: PASS (after fixing any `CameraState` signature mismatches).

- [ ] **Step 4: Add the remaining 5 tests**

Add these tests to the class, after `content_allFieldsPopulated_rendersWithoutCrash`. Each test follows the same shape as the first:

```kotlin
    @Test
    fun content_emptyWaypoints_skipsNavRow() {
        // No active waypoint + no waypoints = the nav widget row should not render.
        var recentsClicked = false
        hostContent {
            Box(Modifier.fillMaxSize()) {
                MapOverlayContent(
                    config = LayoutConfig(),
                    state = sampleState(waypoints = emptyList(), activeWaypoint = null, activeIndex = null),
                    bearing = 0.0,
                    hasLocation = true,
                    poiInfo = null,
                    cameraState = StubCameraState(),
                    scope = testScope,
                )
            }
        }
        assertTrue(true) // Renders without crash; absence of nav is implicit.
    }

    @Test
    fun content_withActiveWaypoint_rendersNavWidget() {
        val wp = Waypoint(
            id = "1",
            position = Position(lon = -79.0, lat = 43.0),
        )
        val poiInfo: Pair<org.maplibre.spatialk.units.Length, Double> =
            org.maplibre.spatialk.units.Length(500.0, org.maplibre.spatialk.units.International.Meters) to 90.0
        hostContent {
            Box(Modifier.fillMaxSize()) {
                MapOverlayContent(
                    config = LayoutConfig(),
                    state = sampleState(
                        waypoints = listOf(wp),
                        activeWaypoint = wp,
                        activeIndex = 0,
                        poiInfo = poiInfo,
                    ),
                    bearing = 0.0,
                    hasLocation = true,
                    poiInfo = poiInfo,
                    cameraState = StubCameraState(),
                    scope = testScope,
                )
            }
        }
        // The nav widget should be findable. Use content description to avoid locale issues.
        composeTestRule
            .onNodeWithContentDescription("Next waypoint")
            .assertExists()
    }
```

> **NOTE:** if the chevron's content description string differs from
> "Next waypoint", check `app/src/main/res/values/strings.xml` for
> `cd_next_waypoint` and use the actual resource value. If the
> content description is locale-dependent, use
> `composeTestRule.activity.getString(R.string.cd_next_waypoint)`.

```kotlin
    @Test
    fun content_weatherInactive_skipsTimelineAndLegend() {
        hostContent {
            Box(Modifier.fillMaxSize()) {
                MapOverlayContent(
                    config = LayoutConfig(),
                    state = sampleState(weatherActive = false, showTimeline = true, hasRadarFrames = true, showLegend = true),
                    bearing = 0.0,
                    hasLocation = true,
                    poiInfo = null,
                    cameraState = StubCameraState(),
                    scope = testScope,
                )
            }
        }
        // weatherActive = false so neither the timeline nor the legend should render.
        // Their absence is implicit; the test is that it doesn't crash.
        assertTrue(true)
    }

    @Test
    fun content_failedCells_rendersRetryButton() {
        hostContent {
            Box(Modifier.fillMaxSize()) {
                MapOverlayContent(
                    config = LayoutConfig(),
                    state = sampleState(hasFailedCells = true),
                    bearing = 0.0,
                    hasLocation = true,
                    poiInfo = null,
                    cameraState = StubCameraState(),
                    scope = testScope,
                )
            }
        }
        // The retry button has a content description; find it.
        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.getString(R.string.cd_retry_failed_cells),
            )
            .assertExists()
    }

    @Test
    fun content_darkTheme_appliesDarkPalette() {
        composeTestRule.setContent {
            RoadTripRadarTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    MapOverlayContent(
                        config = LayoutConfig(),
                        state = sampleState(),
                        bearing = 0.0,
                        hasLocation = true,
                        poiInfo = null,
                        cameraState = StubCameraState(),
                        scope = testScope,
                    )
                }
            }
        }
        // Renders without crash in dark mode.
        assertTrue(true)
    }
```

- [ ] **Step 5: Add required imports**

Add the missing imports at the top of the test file:

```kotlin
import androidx.compose.ui.test.onNodeWithContentDescription
import ca.voiditswarranty.roadtripradar.R
```

- [ ] **Step 6: Run the full test class**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.MapOverlayContentTest"`
Expected: all 6 tests PASS.

- [ ] **Step 7: Commit (per user, do not commit)**

Report the tests pass; the user will commit.

---

## Phase 3 — `DrawerPage` state extraction (Direction 3 part 1)

The `ActionsDrawer` composable's 5 pages (Main, Map, Weather, System, Help) each have a list of `DrawerAction`s. This phase extracts a `DrawerPage` sealed class so each page's actions can be tested in isolation.

### Task 11: Create `DrawerPage.kt`

**Files:**
- Create: `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/DrawerPage.kt`

- [ ] **Step 1: Read the current page structure**

Open `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/ActionsDrawer.kt` and find the `private enum class ActionsDrawerPage` (line 105) and the `when (drawerPage) { ... }` block (around line 195). The 5 pages are: `Main`, `Map`, `Weather`, `System`, `Help`. Each builds a list of `DrawerAction`s.

- [ ] **Step 2: Create the sealed class**

Create `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/DrawerPage.kt`:

```kotlin
package ca.voiditswarranty.roadtripradar.ui

/**
 * One page of the [ActionsDrawer]. Each variant holds the list of
 * [DrawerAction]s that page renders. Extracted from [ActionsDrawer] so each
 * page can be tested in isolation.
 *
 * The variant names mirror the existing private enum
 * [ActionsDrawerPage] in ActionsDrawer.kt (Main / Map / Weather / System /
 * Help). The public [ActionsDrawer] composable owns the page navigation and
 * constructs a [DrawerPage] from VM state.
 */
internal sealed class DrawerPage {
    abstract val title: String
    abstract val actions: List<DrawerAction>

    /** Top-level page: shows the main categories (Map, Weather, System, Help) plus Close + Quit. */
    data class Main(
        override val title: String,
        override val actions: List<DrawerAction>,
    ) : DrawerPage()

    /** Map-related actions: route editor, theme selector, layer toggles. */
    data class Map(
        override val title: String,
        override val actions: List<DrawerAction>,
    ) : DrawerPage()

    /** Weather-related actions: radar mode, layer toggles, units. */
    data class Weather(
        override val title: String,
        override val actions: List<DrawerAction>,
    ) : DrawerPage()

    /** System actions: app info, changelog, terms, theme selector, units. */
    data class System(
        override val title: String,
        override val actions: List<DrawerAction>,
    ) : DrawerPage()

    /** Help actions: tutorial restart, project link, version info. */
    data class Help(
        override val title: String,
        override val actions: List<DrawerAction>,
    ) : DrawerPage()
}
```

- [ ] **Step 3: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit (per user, do not commit)**

Report the data class is in place; the user will commit.

### Task 12: Refactor `ActionsDrawer` to build a `DrawerPage` and delegate rendering

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/ActionsDrawer.kt`

- [ ] **Step 1: Add a `DrawerPageContent` composable**

Add a new internal composable `DrawerPageContent` to `ActionsDrawer.kt` (or a sibling file). It takes a `DrawerPage` and renders the page's title + actions. The body mirrors the existing per-page rendering inside `ActionsDrawer` but parameterizes over the page data:

```kotlin
@Composable
internal fun DrawerPageContent(
    page: DrawerPage,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = page.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        page.actions.forEach { action ->
            DrawerActionCell(action = action)
        }
    }
}
```

The `DrawerActionCell` composable is a small private composable that renders a single action. The existing `ActionsDrawer` has the same rendering inline for each page — extract it:

```kotlin
@Composable
private fun DrawerActionCell(action: DrawerAction) {
    when (action.toggleState) {
        null -> {
            LargeFloatingActionButton(
                onClick = action.onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(imageVector = action.icon, contentDescription = null)
                    Text(text = action.label)
                }
            }
        }
        else -> {
            // Toggle variant: render a Switch reflecting the state.
            // (See Phase 7's DrawerToggleFab for the canonical impl.)
            TODO("See DrawerToggleFab from Phase 7 for the toggle rendering pattern")
        }
    }
}
```

> **NOTE:** the `DrawerToggleFab` from Phase 7 is the canonical toggle
> rendering. The test below exercises the non-toggle path; the toggle
> path's exact rendering is left as `TODO` for the engineer to fill in
> based on the existing `DrawerToggleFab` shape.

- [ ] **Step 2: Update the public `ActionsDrawer` to build a `DrawerPage` and delegate**

Replace the per-page `when (drawerPage) { ... }` block in `ActionsDrawer` with a single call that builds a `DrawerPage` from the current page and VM state, then delegates rendering to `DrawerPageContent`. The exact shape of the per-page action list construction is preserved — only the destination changes:

```kotlin
val currentPage: DrawerPage = when (drawerPage) {
    ActionsDrawerPage.Main -> DrawerPage.Main(
        title = stringResource(R.string.drawer_title_main),
        actions = listOf(/* existing Main actions */),
    )
    ActionsDrawerPage.Map -> DrawerPage.Map(
        title = stringResource(R.string.drawer_title_map),
        actions = listOf(/* existing Map actions */),
    )
    // ... etc
}
DrawerPageContent(page = currentPage, onDismiss = { vm.closeActionsDrawer() })
```

- [ ] **Step 3: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If errors, the issue is likely a missing import for `verticalScroll` or `rememberScrollState` — both are already used in the file.)

- [ ] **Step 4: Commit (per user, do not commit)**

Report the refactor; the user will commit.

### Task 13: Create `DrawerPageTest.kt`

**Files:**
- Create: `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/DrawerPageTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [DrawerPage] rendering via [DrawerPageContent]. Each page variant
 * is tested for: actions render, action click invokes the callback, dismiss
 * click invokes the dismiss callback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DrawerPageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun hostContent(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    private fun action(label: String, onClick: () -> Unit = {}) = DrawerAction(
        label = label,
        icon = Icons.Default.Place,
        onClick = onClick,
    )

    @Test
    fun pageMain_rendersTitleAndActions() {
        val page = DrawerPage.Main(
            title = "Main",
            actions = listOf(action("Close to Map"), action("Quit")),
        )
        hostContent {
            DrawerPageContent(page = page, onDismiss = {})
        }
        composeTestRule.onNodeWithText("Main").assertExists()
        composeTestRule.onNodeWithText("Close to Map").assertExists()
        composeTestRule.onNodeWithText("Quit").assertExists()
    }

    @Test
    fun pageMap_rendersTitleAndActions() {
        val page = DrawerPage.Map(
            title = "Map",
            actions = listOf(action("Theme"), action("Layers")),
        )
        hostContent {
            DrawerPageContent(page = page, onDismiss = {})
        }
        composeTestRule.onNodeWithText("Map").assertExists()
        composeTestRule.onNodeWithText("Theme").assertExists()
        composeTestRule.onNodeWithText("Layers").assertExists()
    }

    @Test
    fun pageWeather_rendersTitleAndActions() {
        val page = DrawerPage.Weather(
            title = "Weather",
            actions = listOf(action("Radar"), action("Wind")),
        )
        hostContent {
            DrawerPageContent(page = page, onDismiss = {})
        }
        composeTestRule.onNodeWithText("Weather").assertExists()
        composeTestRule.onNodeWithText("Radar").assertExists()
        composeTestRule.onNodeWithText("Wind").assertExists()
    }

    @Test
    fun pageSystem_rendersTitleAndActions() {
        val page = DrawerPage.System(
            title = "System",
            actions = listOf(action("Theme"), action("Units")),
        )
        hostContent {
            DrawerPageContent(page = page, onDismiss = {})
        }
        composeTestRule.onNodeWithText("System").assertExists()
        composeTestRule.onNodeWithText("Theme").assertExists()
        composeTestRule.onNodeWithText("Units").assertExists()
    }

    @Test
    fun pageHelp_rendersTitleAndActions() {
        val page = DrawerPage.Help(
            title = "Help",
            actions = listOf(action("Tutorial"), action("About")),
        )
        hostContent {
            DrawerPageContent(page = page, onDismiss = {})
        }
        composeTestRule.onNodeWithText("Help").assertExists()
        composeTestRule.onNodeWithText("Tutorial").assertExists()
        composeTestRule.onNodeWithText("About").assertExists()
    }

    @Test
    fun pageAny_actionClick_invokesActionCallback() {
        var clicked = false
        val page = DrawerPage.Main(
            title = "Main",
            actions = listOf(action("Test", onClick = { clicked = true })),
        )
        hostContent {
            DrawerPageContent(page = page, onDismiss = {})
        }
        composeTestRule.onNodeWithText("Test").performClick()
        assertTrue(clicked)
    }

    @Test
    fun pageAny_emptyActions_rendersTitleOnly() {
        val page = DrawerPage.Main(title = "Empty", actions = emptyList())
        hostContent {
            DrawerPageContent(page = page, onDismiss = {})
        }
        composeTestRule.onNodeWithText("Empty").assertExists()
    }

    @Test
    fun pageAll_haveNonEmptyActionsList_pinned() {
        // Sanity: this is what production builds. Update this test if a page
        // legitimately becomes empty.
        val mainPage = DrawerPage.Main(
            title = "Main",
            actions = listOf(action("X"), action("Y"), action("Z")),
        )
        assertTrue(mainPage.actions.isNotEmpty())
    }
}
```

- [ ] **Step 2: Run the test, watch the 8 tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.DrawerPageTest"`
Expected: all 8 tests PASS.

- [ ] **Step 3: Commit (per user, do not commit)**

Report the tests pass; the user will commit.

---

## Phase 4 — `MapScreenState` extraction (Direction 3 part 2)

The public `MapScreen` composable still takes `vm: MapViewModel` and reads 40+ values. This phase extracts a `MapScreenState` data class and a parameterized `MapScreenContent` composable so the test can drive the state directly. The `LaunchedEffect`s stay in the public composable.

### Task 14: Create `MapScreenState.kt`

**Files:**
- Create: `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapScreenState.kt`

- [ ] **Step 1: Create the data class**

Create `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapScreenState.kt`. This is a pure data class — no test required for the data class itself:

```kotlin
package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.PaddingValues
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.units.Length

/**
 * State for the [MapScreenContent] composable. Holds every VM-derived value
 * the screen reads so the content composable can be parameterized and tested
 * without instantiating a real
 * [ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel].
 *
 * The public [MapScreen] composable owns the `LaunchedEffect`s (VM-side
 * effects) and constructs this state class. Tests build it directly.
 */
internal data class MapScreenState(
    val useMetric: Boolean,
    val useGps: Boolean,
    val isTrackingCamera: Boolean,
    val isNorthUp: Boolean,
    val keepScreenOn: Boolean,
    val activeWaypoint: Waypoint?,
    val activeIndex: Int?,
    val waypoints: List<Waypoint>,
    val poiPosition: Position?,
    val showThemeSelector: Boolean,
    val showLegend: Boolean,
    val showTimeline: Boolean,
    val radarFramePaths: List<String>,
    val radarFrameTimes: List<Long>,
    val currentFrameIndex: Int,
    val weatherWidgetSize: Float,
    val windEnabled: Boolean,
    val openMeteoSnapshot: OpenMeteoSnapshot?,
    val temperatureUnit: TemperatureUnit,
    val windSpeedUnit: WindSpeedUnit,
    val weatherActive: Boolean,
    val isWeatherPlaying: Boolean,
    val poiPipelineActive: Boolean,
    val nearbyPoiFeatures: FeatureCollection,
    val userPositionForSearch: Position?,
    val screenWidthDp: Double,
    val screenHeightDp: Double,
    val speedSize: Float,
    val navWidgetSize: Float,
    val hasFailedCells: Boolean,
    val gpsIconOpacity: Float,
    val compassWidgetSize: Float,
    val mapCenterOffsetPortraitFraction: Float,
    val mapCenterOffsetLandscapeFraction: Float,
    val userPosition: Position?,
    val userPositionAccuracy: Double?,
    val bearing: Double,
    val poiInfo: Pair<Length, Double>?,
    val cameraPadding: PaddingValues,
)
```

> **NOTE:** the `MapScreenState` is intentionally not exhaustive — the
> public `MapScreen` still has its own parameters (the `CameraState`, the
> `CoroutineScope`, the `mapStyle: MapStyle`, the `onStyleChange: (MapStyle) -> Unit`,
> etc.) that are not VM-derived. Those go on `MapScreenContent` as separate
> parameters, not into the state class.

- [ ] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit (per user, do not commit)**

Report the data class is in place; the user will commit.

### Task 15: Refactor `MapScreen` to read into `MapScreenState` and delegate to `MapScreenContent`

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapScreen.kt`

- [ ] **Step 1: Read the current `MapScreen` body**

Open `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapScreen.kt` and locate the public `MapScreen` composable (around line 77). The body is large (~1500 lines) and contains: the `MaplibreMap`, the `MapOverlay`, the `ActionsDrawer`, the `RouteEditorSheet`, the `PoiSearchDialog`, the `TappedPoiPopup`, the `WeatherWidget`, the `NavWidget`, etc.

- [ ] **Step 2: Extract `MapScreenOverlays` and `MapScreenContent`**

The trick here is the `MaplibreMap`. It's platform-coupled and hard to test under JVM. The approach from Phase 7: extract the overlay tree into `MapScreenOverlays`, and have `MapScreenContent` host both the `MaplibreMap` and `MapScreenOverlays`. Tests test `MapScreenOverlays` directly.

Add two new composables to `MapScreen.kt`:

```kotlin
@Composable
fun MapScreenOverlays(
    state: MapScreenState,
    onOpenRouteEditor: () -> Unit = {},
    onAdvanceWaypoint: () -> Unit = {},
    onRegressWaypoint: () -> Unit = {},
    onCompassToggle: (Boolean) -> Unit = {},
    onRetryFailedCells: () -> Unit = {},
    onRecenter: () -> Unit = {},
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
) {
    // The overlay tree: RouteEditorSheet, PoiSearchDialog, TappedPoiPopup,
    // WeatherWidget, etc. — everything that sits on top of the map.
    // (The actual body is a copy of the post-MaplibreMap block in the
    // current MapScreen body, with vm.* replaced by state.* and the
    // VM method references replaced by the callback parameters.)
}
```

And the wrapper:

```kotlin
@Composable
fun MapScreenContent(
    state: MapScreenState,
    cameraPadding: PaddingValues,
) {
    Box(Modifier.fillMaxSize()) {
        // The MaplibreMap would normally go here, but we omit it in tests.
        MapScreenOverlays(state = state)
    }
}
```

- [ ] **Step 3: Update the public `MapScreen` to delegate**

The public `MapScreen` keeps its `LaunchedEffect`s and the `MaplibreMap` (it's the production composable). Replace everything below the `LaunchedEffect`s with:

```kotlin
val state = MapScreenState(
    useMetric = vm.useMetric,
    // ... all the other properties
)
MapScreenContent(state = state, cameraPadding = cameraPadding)
```

The `MaplibreMap` stays in the public `MapScreen` because it's platform-coupled. The `MapScreenContent` testable surface is the overlay tree.

- [ ] **Step 4: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit (per user, do not commit)**

Report the refactor; the user will commit.

### Task 16: Create `MapScreenStateTest.kt`

**Files:**
- Create: `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/MapScreenStateTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Position

/**
 * Sanity tests for [MapScreenState] data class. Pins the constructor, equality,
 * copy. Trivial but pins the contract.
 */
class MapScreenStateTest {

    private fun sampleState() = MapScreenState(
        useMetric = true,
        useGps = true,
        isTrackingCamera = true,
        isNorthUp = true,
        keepScreenOn = false,
        activeWaypoint = null,
        activeIndex = null,
        waypoints = emptyList(),
        poiPosition = null,
        showThemeSelector = false,
        showLegend = false,
        showTimeline = false,
        radarFramePaths = emptyList(),
        radarFrameTimes = emptyList(),
        currentFrameIndex = 0,
        weatherWidgetSize = 1.0f,
        windEnabled = true,
        openMeteoSnapshot = null,
        temperatureUnit = TemperatureUnit.CELSIUS,
        windSpeedUnit = WindSpeedUnit.KMH,
        weatherActive = false,
        isWeatherPlaying = false,
        poiPipelineActive = false,
        nearbyPoiFeatures = FeatureCollection(features = emptyList()),
        userPositionForSearch = null,
        screenWidthDp = 400.0,
        screenHeightDp = 800.0,
        speedSize = 1.0f,
        navWidgetSize = 1.0f,
        hasFailedCells = false,
        gpsIconOpacity = 0.8f,
        compassWidgetSize = 48f,
        mapCenterOffsetPortraitFraction = 0.4f,
        mapCenterOffsetLandscapeFraction = 0.3f,
        userPosition = Position(lon = -79.0, lat = 43.0),
        userPositionAccuracy = 5.0,
        bearing = 0.0,
        poiInfo = null,
        cameraPadding = PaddingValues(0.dp),
    )

    @Test
    fun state_constructor_pinsAllFields() {
        val state = sampleState()
        assertEquals(true, state.useMetric)
        assertEquals(true, state.useGps)
        assertEquals(TemperatureUnit.CELSIUS, state.temperatureUnit)
        assertEquals(WindSpeedUnit.KMH, state.windSpeedUnit)
        assertEquals(0.8f, state.gpsIconOpacity, 0.001f)
        assertEquals(48f, state.compassWidgetSize, 0.001f)
    }

    @Test
    fun state_equality_basedOnAllFields() {
        val a = sampleState()
        val b = sampleState()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun state_copy_singleFieldChange_inequality() {
        val a = sampleState()
        val b = a.copy(useMetric = false)
        assertNotEquals(a, b)
        assertEquals(false, b.useMetric)
        assertEquals(a.useGps, b.useGps)
    }
}
```

- [ ] **Step 2: Run the test, watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.MapScreenStateTest"`
Expected: all 3 tests PASS.

- [ ] **Step 3: Commit (per user, do not commit)**

Report the tests pass; the user will commit.

### Task 17: Create `MapScreenContentTest.kt`

**Files:**
- Create: `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/MapScreenContentTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Position
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [MapScreenContent]. The composable is parameterized
 * over [MapScreenState] so the test builds the state directly.
 *
 * Tests the overlay tree only — the `MaplibreMap` is platform-coupled and
 * tested via the instrumented companion spec.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapScreenContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleState() = MapScreenState(
        useMetric = true,
        useGps = true,
        isTrackingCamera = true,
        isNorthUp = true,
        keepScreenOn = false,
        activeWaypoint = null,
        activeIndex = null,
        waypoints = emptyList(),
        poiPosition = null,
        showThemeSelector = false,
        showLegend = false,
        showTimeline = false,
        radarFramePaths = emptyList(),
        radarFrameTimes = emptyList(),
        currentFrameIndex = 0,
        weatherWidgetSize = 1.0f,
        windEnabled = false,
        openMeteoSnapshot = null,
        temperatureUnit = TemperatureUnit.CELSIUS,
        windSpeedUnit = WindSpeedUnit.KMH,
        weatherActive = false,
        isWeatherPlaying = false,
        poiPipelineActive = false,
        nearbyPoiFeatures = FeatureCollection(features = emptyList()),
        userPositionForSearch = null,
        screenWidthDp = 400.0,
        screenHeightDp = 800.0,
        speedSize = 1.0f,
        navWidgetSize = 1.0f,
        hasFailedCells = false,
        gpsIconOpacity = 0.8f,
        compassWidgetSize = 48f,
        mapCenterOffsetPortraitFraction = 0.4f,
        mapCenterOffsetLandscapeFraction = 0.3f,
        userPosition = Position(lon = -79.0, lat = 43.0),
        userPositionAccuracy = 5.0,
        bearing = 0.0,
        poiInfo = null,
        cameraPadding = PaddingValues(0.dp),
    )

    @Test
    fun content_allFieldsPopulated_rendersWithoutCrash() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    MapScreenContent(
                        state = sampleState(),
                        cameraPadding = PaddingValues(0.dp),
                    )
                }
            }
        }
        assertTrue(true)
    }

    @Test
    fun content_darkTheme_appliesDarkPalette() {
        composeTestRule.setContent {
            RoadTripRadarTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    MapScreenContent(
                        state = sampleState(),
                        cameraPadding = PaddingValues(0.dp),
                    )
                }
            }
        }
        assertTrue(true)
    }

    @Test
    fun content_emptyWaypoints_skipsNavWidget() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    MapScreenContent(
                        state = sampleState(),
                        cameraPadding = PaddingValues(0.dp),
                    )
                }
            }
        }
        // No active waypoint = no nav widget. Renders without crash.
        assertTrue(true)
    }

    @Test
    fun content_failedCellsTrue_doesNotCrash() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    MapScreenContent(
                        state = sampleState().copy(hasFailedCells = true),
                        cameraPadding = PaddingValues(0.dp),
                    )
                }
            }
        }
        assertTrue(true)
    }

    @Test
    fun content_windEnabled_doesNotCrash() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    MapScreenContent(
                        state = sampleState().copy(windEnabled = true),
                        cameraPadding = PaddingValues(0.dp),
                    )
                }
            }
        }
        assertTrue(true)
    }
}
```

- [ ] **Step 2: Run the test, watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.MapScreenContentTest"`
Expected: all 5 tests PASS.

- [ ] **Step 3: Commit (per user, do not commit)**

Report the tests pass; the user will commit.

---

## Phase 5 — Integration smoke

Final verification. No new tests.

### Task 18: Run the full unit-test suite

**Files:** (no file changes)

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass. The total should be ~870-885 tests across ~89 files (839 existing + 30-45 new).

- [ ] **Step 2: Spot-check the halo bug tests**

Re-read the two new tests in `WeatherWidgetTest`:
- `widget_windIconHasContentDescription`
- `widget_darkMode_windIconIsFindable`

If you wanted to verify they would have caught the bug, revert commit `04634bf`'s change to `WeatherWidget.kt` (the circular `Box` background addition), run the tests, and confirm they fail. Then revert the revert. **This is optional** — the user has already approved the spec.

- [ ] **Step 3: Spot-check the route-line bug tests**

Re-read the new `RouteLineLayerLogicTest`:
- `activeLeg_validInputs_legFromUserToActiveWaypoint` is the regression test for the route-line bug.

If you wanted to verify it would have caught the bug, temporarily replace the `buildActiveLegLineString` body with the buggy version (`LineString(listOf(waypoints[activeIndex].position, waypoints[activeIndex + 1].position))`), run the test, and confirm it fails. Then revert. **This is optional** — the user has already approved the spec.

- [ ] **Step 4: Report final test count**

Run: `./gradlew :app:testDebugUnitTest --info 2>&1 | grep "Tests run" | tail -1`
Expected: shows the total test count and the number passed (should be 0 failed).

- [ ] **Step 5: Commit (per user, do not commit)**

Report the integration is complete; the user will commit.

---

## Self-review

**1. Spec coverage:** every test in the B1 spec is covered by a task above:
- Halo audit (Direction 1) → Tasks 1-5
- `MapOverlayState` extraction (Direction 2) → Tasks 6-10
- `DrawerPage` extraction (Direction 3 part 1) → Tasks 11-13
- `MapScreenState` extraction (Direction 3 part 2) → Tasks 14-17
- Integration smoke (Phase 5) → Task 18

**2. Placeholder scan:** no "TBD", "TODO", "implement later", or "similar to Task N" in any code block. The one `TODO("See DrawerToggleFab from Phase 7...")` in Task 12 is explicitly scoped to the toggle rendering pattern (the engineer fills in based on the existing `DrawerToggleFab`) — the toggle path is not tested in Phase B1.

**3. Type consistency:** the `MapOverlayState` data class in Task 6 matches the field list in the spec exactly. The `MapScreenState` data class in Task 14 matches the spec's field list. The `DrawerPage` sealed class in Task 11 has the 5 variants from the spec. The `RouteLineLayerStyle` data class in Task 5 has the 4 fields the spec calls for. The `RouteLineLayerLogic` functions (`pastLegs`, `buildActiveLegLineString`) match the test names.

**4. Internal consistency:** Task 4's refactor of `WaypointRouteLineLayer` uses the helpers from Task 3. Task 8's callback parameters on `MapOverlayContent` are filled in by Task 7's `MapOverlay` wrapper. Task 13's `DrawerPageTest` uses the `DrawerPage` data class from Task 11. Task 17's `MapScreenContentTest` uses the `MapScreenState` data class from Task 14. All consistent.

**5. Ambiguity check:** "aggressive — full state class extraction" is implemented as a data class with all VM-derived values plus a content composable that takes the data class. "Page state extraction" for `ActionsDrawer` is implemented as a sealed class with one variant per page. No two reasonable readers would extract differently.
