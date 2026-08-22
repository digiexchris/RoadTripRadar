# Route Line and Waypoint Markers — Bug Fixes and Car Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix three bugs from the AA branch: phone missing dashed legs, car wrong green hue, car missing waypoint markers. Extract a shared `RouteStyle` constant. Phone uses shared `data.RouteGeometry` helpers directly. Car gets a `WaypointMarkersLayer` port with click-to-set-active.

**Architecture:** Two surfaces (phone Compose, car raw MapLibre Android) share a `data.RouteStyle` for color/dash/opacity and `data.RouteGeometry` for line geometry. Phone's dead `ui.RouteLineLayer*` files are deleted. Car's `CarMapContainerLogic` gains a pure `buildWaypointMarkerFeatures` helper; `CarMapContainer.setupOverlays` adds 8 new layers; `refreshFromVm` calls a new `updateWaypointMarkers`. Car adds its first `addOnMapClickListener` for the click-to-set-active behavior.

**Tech Stack:** Compose + MapLibre Compose (phone), Android Views + MapLibre Android (car), JUnit 4 + Robolectric (tests), `org.maplibre.spatialk.geojson` (shared geometry types).

**Spec:** `docs/superpowers/specs/2026-07-09-route-line-and-waypoint-fixes-design.md`

**User standing constraints (must preserve):**
- "Don't commit, I will do that" — subagents produce diffs only, never run `git commit`.
- "When porting a feature from phone to car, never skip a phone-side feature without asking" — both surfaces get the same route line + waypoint markers.
- TDD discipline: every new test added before its production code (where the production code is testable).
- The working tree is on branch `aa`, not `main`. Commits go on `aa`.

---

## File structure

**New (production):**
- `app/src/main/java/ca/voiditswarranty/roadtripradar/data/RouteStyle.kt` — shared visual constants for the route line.

**New (tests):**
- `app/src/test/java/ca/voiditswarranty/roadtripradar/data/RouteStyleTest.kt` — pins the 5 shared constants.

**Modified (production):**
- `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapLayers.kt` — use `RouteStyle.COLOR` and `data.RouteGeometry` helpers; remove `pastLegs`/`buildActiveLegLineString` calls.
- `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainer.kt` — use `RouteStyle.ARGB`; add 8 waypoint-marker layers + 2 sources; add `updateWaypointMarkers`; add `addOnMapClickListener`; add new constants.
- `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainerLogic.kt` — add pure `buildWaypointMarkerFeatures(waypoints, activeId)` and `featureCollectionGeoJson(features)` helpers.
- `app/src/main/assets/changelog.json` — append a bullet.

**Modified (tests):**
- `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarMapContainerLogicTest.kt` — add tests for the new pure helpers.

**Deleted (production):**
- `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogic.kt`.
- `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyle.kt`.

**Deleted (tests):**
- `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogicTest.kt`.
- `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyleTest.kt`.

---

## Task 1: Create `data/RouteStyle.kt` with the 5 shared constants

**Files:**
- Create: `app/src/main/java/ca/voiditswarranty/roadtripradar/data/RouteStyle.kt`
- Test: `app/src/test/java/ca/voiditswarranty/roadtripradar/data/RouteStyleTest.kt`

- [ ] **Step 1: Create the new file with the constants**

Create `app/src/main/java/ca/voiditswarranty/roadtripradar/data/RouteStyle.kt`:

```kotlin
package ca.voiditswarranty.roadtripradar.data

import androidx.compose.ui.graphics.Color

/**
 * Visual style for the two halves of the route line, shared by the
 * phone (ui/MapLayers.WaypointRouteLineLayer) and the car
 * (car/CarMapContainer.setupOverlays). The single source of truth so
 * the phone and car can't drift apart again.
 *
 * The phone reads [COLOR]; the car reads [ARGB] (raw int, no Compose).
 * They are the same color: Compose `Color.Green` is `0xFF00FF00` with
 * full alpha.
 */
object RouteStyle {
    val COLOR: Color = Color.Green
    val ARGB: Int = 0xFF00FF00.toInt()
    const val ACTIVE_OPACITY: Float = 0.95f
    const val INACTIVE_OPACITY: Float = 0.6f
    val INACTIVE_DASH: List<Int> = listOf(2, 3)
}
```

- [ ] **Step 2: Create the test file with 5 tests**

Create `app/src/test/java/ca/voiditswarranty/roadtripradar/data/RouteStyleTest.kt`:

```kotlin
package ca.voiditswarranty.roadtripradar.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the shared route line visual constants. Phone reads [RouteStyle.COLOR];
 * car reads [RouteStyle.ARGB]. These tests pin the contract that both surfaces
 * see the same green, and that the dash/opacity values are stable.
 */
class RouteStyleTest {

    @Test
    fun COLOR_isComposeGreen() {
        assertEquals(Color.Green, RouteStyle.COLOR)
    }

    @Test
    fun ARGB_isOpaqueBrightGreen() {
        assertEquals(0xFF00FF00.toInt(), RouteStyle.ARGB)
    }

    @Test
    fun ARGB_matchesColorGreenArgb() {
        // The phone's Compose color and the car's raw ARGB int must be the same color.
        assertEquals(Color.Green.toArgb(), RouteStyle.ARGB)
    }

    @Test
    fun ACTIVE_OPACITY_isHigherThanInactive() {
        // Active (solid user→target) is more opaque than inactive (dashed planned).
        assertTrue(RouteStyle.ACTIVE_OPACITY > RouteStyle.INACTIVE_OPACITY)
    }

    @Test
    fun INACTIVE_DASH_hasTwoEntries() {
        assertEquals(listOf(2, 3), RouteStyle.INACTIVE_DASH)
    }
}
```

- [ ] **Step 3: Run the tests to verify they pass**

Run: `cd /home/chris/repos/RoadTripRadar2 && ./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.data.RouteStyleTest"`
Expected: 5 tests, 0 failures, 0 errors. (These tests are pure constants, so they pass on first run — the value is in pinning the contract for future changes.)

- [ ] **Step 4: Hand off the diff**

Show the user the `git diff` for the two new files. Do NOT run `git commit` (the user handles all commits).

---

## Task 2: Wire phone `MapLayers.kt` to use `RouteStyle` and `data.RouteGeometry`

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapLayers.kt:3-30, 232-264`

- [ ] **Step 1: Read the current `MapLayers.kt` imports and the `WaypointRouteLineLayer` block**

Confirm the exact line numbers before editing. The imports section is at the top; the `WaypointRouteLineLayer` is around lines 219-264. The `pastLegs` and `buildActiveLegLineString` imports come from `ca.voiditswarranty.roadtripradar.ui.RouteLineLayerLogic` (since `RouteLineLayerLogic.kt` is in the same package, no import is needed; if there's an explicit import, remove it).

- [ ] **Step 2: Add the new imports**

In the imports block of `MapLayers.kt`, add these three lines (placed in alphabetical order with the other `ca.voiditswarranty.roadtripradar.data` imports):

```kotlin
import ca.voiditswarranty.roadtripradar.data.RouteStyle
import ca.voiditswarranty.roadtripradar.data.activeRouteLeg
import ca.voiditswarranty.roadtripradar.data.inactiveRouteLegs
```

If there's an explicit import for `ca.voiditswarranty.roadtripradar.ui.pastLegs` or `ca.voiditswarranty.roadtripradar.ui.buildActiveLegLineString`, remove it. (They may not be imported if same-package — `MapLayers.kt` and `RouteLineLayerLogic.kt` are both in `ca.voiditswarranty.roadtripradar.ui`.)

- [ ] **Step 3: Replace the inactive geometry call (lines 232-238)**

From:
```kotlin
    val inactiveFc = remember(waypoints.toList(), activeIndex) {
        FeatureCollection(
            pastLegs(waypoints, activeIndex).map {
                Feature(geometry = it, properties = buildJsonObject {})
            },
        )
    }
```

To:
```kotlin
    val inactiveFc = remember(waypoints.toList()) {
        FeatureCollection(
            inactiveRouteLegs(waypoints).map {
                Feature(geometry = it, properties = buildJsonObject {})
            },
        )
    }
```

- [ ] **Step 4: Replace the active geometry call (lines 239-244)**

From:
```kotlin
    val activeFc = remember(waypoints.toList(), activeIndex, userPosition) {
        val leg = buildActiveLegLineString(waypoints, activeIndex, userPosition)
        FeatureCollection(
            if (leg != null) listOf(Feature(geometry = leg, properties = buildJsonObject {})) else emptyList(),
        )
    }
```

To:
```kotlin
    val activeFc = remember(waypoints.toList(), activeIndex, userPosition) {
        val leg = activeRouteLeg(waypoints, activeIndex, userPosition)
        FeatureCollection(
            if (leg != null) listOf(Feature(geometry = leg, properties = buildJsonObject {})) else emptyList(),
        )
    }
```

- [ ] **Step 5: Replace the `Color.Green` and dash/opacity literals with `RouteStyle` constants (lines 249-263)**

From:
```kotlin
    LineLayer(
        id = "waypoint-route-inactive",
        source = inactiveSource,
        color = const(Color.Green),
        width = const(5.dp),
        opacity = const(0.6f),
        dasharray = const(listOf(2, 3)),
    )
    LineLayer(
        id = "waypoint-route-active",
        source = activeSource,
        color = const(Color.Green),
        width = const(5.dp),
        opacity = const(0.95f),
    )
```

To:
```kotlin
    LineLayer(
        id = "waypoint-route-inactive",
        source = inactiveSource,
        color = const(RouteStyle.COLOR),
        width = const(5.dp),
        opacity = const(RouteStyle.INACTIVE_OPACITY),
        dasharray = const(RouteStyle.INACTIVE_DASH),
    )
    LineLayer(
        id = "waypoint-route-active",
        source = activeSource,
        color = const(RouteStyle.COLOR),
        width = const(5.dp),
        opacity = const(RouteStyle.ACTIVE_OPACITY),
    )
```

- [ ] **Step 6: Run the phone test classes to verify no regressions**

Run: `cd /home/chris/repos/RoadTripRadar2 && ./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.WeatherWidgetTest" --tests "ca.voiditswarranty.roadtripradar.ui.MapStyleSwitchingTest" --tests "ca.voiditswarranty.roadtripradar.ui.CameraGpsTrackingTest" --tests "ca.voiditswarranty.roadtripradar.ui.RouteOperationsTest"`
Expected: All tests pass. (None of these directly test the route line layer, but the build + phone UI tests should all be unaffected.)

- [ ] **Step 7: Run the data tests to verify the shared helpers still work**

Run: `cd /home/chris/repos/RoadTripRadar2 && ./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.data.RouteGeometryTest"`
Expected: All tests pass. The phone now calls `inactiveRouteLegs` and `activeRouteLeg` from `data.RouteGeometry`; the existing tests there pin the correct behavior.

- [ ] **Step 8: Hand off the diff**

Show the user the `git diff -- app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapLayers.kt`. Do NOT run `git commit`.

---

## Task 3: Delete the dead `ui/RouteLineLayer*` files

**Files:**
- Delete: `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogic.kt`
- Delete: `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyle.kt`
- Delete: `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogicTest.kt`
- Delete: `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyleTest.kt`

- [ ] **Step 1: Verify no remaining references to the deleted symbols**

Run:
```bash
cd /home/chris/repos/RoadTripRadar2 && grep -rn "pastLegs\|buildActiveLegLineString\|RouteLineLayerStyle\|RouteLineLayerLogic" app/src/main app/src/test app/src/androidTest 2>/dev/null
```
Expected: no output. (Task 2 already removed the phone's references; this is the safety check.) If anything matches, stop and report BLOCKED.

- [ ] **Step 2: Delete the four files**

Run:
```bash
cd /home/chris/repos/RoadTripRadar2 && rm \
  app/src/main/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogic.kt \
  app/src/main/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyle.kt \
  app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogicTest.kt \
  app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyleTest.kt
```

- [ ] **Step 3: Verify the build still compiles**

Run: `cd /home/chris/repos/RoadTripRadar2 && ./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL. No dangling references to the deleted symbols.

- [ ] **Step 4: Hand off the deletion**

Show the user the `git status` and `git diff --stat`. Do NOT run `git commit`.

---

## Task 4: Car `ROUTE_COLOR` → `RouteStyle.ARGB`

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainer.kt:757` + add import

- [ ] **Step 1: Add the `RouteStyle` import**

In `CarMapContainer.kt`, add the import (alphabetically with the other `ca.voiditswarranty.roadtripradar.data` imports):

```kotlin
import ca.voiditswarranty.roadtripradar.data.RouteStyle
```

- [ ] **Step 2: Replace the `ROUTE_COLOR` constant (line 757)**

From:
```kotlin
        private const val ROUTE_COLOR = 0xFF2E7D32.toInt()
```

To:
```kotlin
        private val ROUTE_COLOR = RouteStyle.ARGB
```

The `const val` → `val` change is necessary because `RouteStyle.ARGB` is a non-const property (it's in an `object`).

- [ ] **Step 3: Run the car test class to verify no regressions**

Run: `cd /home/chris/repos/RoadTripRadar2 && ./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.car.*"`
Expected: All car tests pass. The route color is now `0xFF00FF00` (matching the phone), but no test pins the color value directly (the car tests test the geometry, not the paint properties).

- [ ] **Step 4: Hand off the diff**

Show the user the `git diff -- app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainer.kt`. Do NOT run `git commit`.

---

## Task 5: Add `buildWaypointMarkerFeatures` and `featureCollectionGeoJson` to `CarMapContainerLogic`

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainerLogic.kt` (add two helpers)
- Modify: `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarMapContainerLogicTest.kt` (add tests)

- [ ] **Step 1: Read the current `CarMapContainerLogic.kt` to find the right place for new helpers**

The file ends at the existing `lineStringGeoJson` function (around line 161). Add the new helpers after it.

- [ ] **Step 2: Add `featureCollectionGeoJson` helper**

Append this to the end of `CarMapContainerLogic.kt`:

```kotlin
/**
 * Serialize a list of point features to a GeoJSON `FeatureCollection`. Each feature is
 * a `Point` with the given `id` and `label` properties. Empty input → empty
 * collection so the caller doesn't have to null-check.
 *
 * Used by the car waypoint-marker layer to push inactive / active waypoint features
 * to their respective GeoJson sources.
 */
internal fun featureCollectionGeoJson(
    points: List<Position>,
    ids: List<String>,
    labels: List<String>,
): String {
    if (points.isEmpty()) return """{"type":"FeatureCollection","features":[]}"""
    val features = points.indices.joinToString(",") { i ->
        val p = points[i]
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${p.longitude},${p.latitude}]},"properties":{"id":"${ids[i]}","label":"${labels[i]"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}
```

- [ ] **Step 3: Add `buildWaypointMarkerFeatures` pure helper**

Append this after `featureCollectionGeoJson`:

```kotlin
/**
 * Split waypoints into inactive and active marker features for the car map.
 * Each feature carries a 1-based number label (from the position in the full
 * list, NOT a filtered index) and the waypoint's stable id (used to look it up
 * on click to set the active waypoint).
 *
 * Returns `(inactiveJson, activeJson)` as two GeoJSON strings ready to push
 * to the corresponding GeoJsonSources. Empty waypoints → empty collections.
 *
 * Per the design spec, the car renders ALL waypoints as numbered circles —
 * unlike the phone, it does not branch on `iconName` to render maki icons.
 * (The phone has a separate maki-icon path for iconName waypoints; the car
 * does not port that path.)
 */
internal fun buildWaypointMarkerFeatures(
    waypoints: List<Waypoint>,
    activeWaypointId: String?,
): Pair<String, String> {
    val inactivePoints = mutableListOf<Position>()
    val inactiveIds = mutableListOf<String>()
    val inactiveLabels = mutableListOf<String>()
    val activePoints = mutableListOf<Position>()
    val activeIds = mutableListOf<String>()
    val activeLabels = mutableListOf<String>()

    waypoints.forEachIndexed { idx, wp ->
        val label = (idx + 1).toString()
        if (wp.id == activeWaypointId) {
            activePoints += wp.position
            activeIds += wp.id
            activeLabels += label
        } else {
            inactivePoints += wp.position
            inactiveIds += wp.id
            inactiveLabels += label
        }
    }

    return Pair(
        featureCollectionGeoJson(inactivePoints, inactiveIds, inactiveLabels),
        featureCollectionGeoJson(activePoints, activeIds, activeLabels),
    )
}
```

- [ ] **Step 4: Add the necessary imports to `CarMapContainerLogic.kt`**

Add at the top of the file (alphabetically with other imports):

```kotlin
import ca.voiditswarranty.roadtripradar.model.Waypoint
```

(`Position` is already imported; check the existing imports to confirm.)

- [ ] **Step 5: Add the failing test for `buildWaypointMarkerFeatures`**

Open `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarMapContainerLogicTest.kt` and find the end of the class (before the final `}`). Add these new tests:

```kotlin
    // -------- buildWaypointMarkerFeatures --------

    private fun wp(id: String, lat: Double, lon: Double) = Waypoint(
        id = id,
        lat = lat,
        lon = lon,
    )

    @Test
    fun buildWaypointMarkerFeatures_emptyWaypoints_bothEmpty() {
        val (inactive, active) = buildWaypointMarkerFeatures(emptyList(), activeWaypointId = null)
        assertEquals("""{"type":"FeatureCollection","features":[]}""", inactive)
        assertEquals("""{"type":"FeatureCollection","features":[]}""", active)
    }

    @Test
    fun buildWaypointMarkerFeatures_oneWaypointNotActive_inactiveHasOne() {
        val wps = listOf(wp("a", 0.0, 0.0))
        val (inactive, active) = buildWaypointMarkerFeatures(wps, activeWaypointId = null)
        assertEquals(1, "features".count { _ -> true }.let { countFeatures(inactive) })
        assertEquals(0, countFeatures(active))
    }

    @Test
    fun buildWaypointMarkerFeatures_oneWaypointActive_activeHasOne() {
        val wps = listOf(wp("a", 0.0, 0.0))
        val (inactive, active) = buildWaypointMarkerFeatures(wps, activeWaypointId = "a")
        assertEquals(0, countFeatures(inactive))
        assertEquals(1, countFeatures(active))
    }

    @Test
    fun buildWaypointMarkerFeatures_fourWaypointsActiveIsThird_inactiveHasThreeActiveHasOne() {
        val wps = listOf(
            wp("a", 0.0, 0.0),
            wp("b", 1.0, 1.0),
            wp("c", 2.0, 2.0),
            wp("d", 3.0, 3.0),
        )
        val (inactive, active) = buildWaypointMarkerFeatures(wps, activeWaypointId = "c")
        assertEquals(3, countFeatures(inactive))
        assertEquals(1, countFeatures(active))
    }

    @Test
    fun buildWaypointMarkerFeatures_labelsAreOneBasedAndStableAcrossActiveChange() {
        // The label is the position in the FULL waypoint list, not a filtered index.
        // When active changes, the labels for the remaining inactive waypoints stay the same.
        val wps = listOf(
            wp("a", 0.0, 0.0),
            wp("b", 1.0, 1.0),
            wp("c", 2.0, 2.0),
        )
        val (inactive1, active1) = buildWaypointMarkerFeatures(wps, activeWaypointId = "b")
        // inactive1 has features for "a" (label "1") and "c" (label "3"); active1 has "b" (label "2").
        assertTrue(inactive1.contains(""""label":"1""""))
        assertTrue(inactive1.contains(""""label":"3""""))
        assertTrue(active1.contains(""""label":"2""""))
    }

    @Test
    fun buildWaypointMarkerFeatures_includesAllWaypointsEvenWithIconName() {
        // Per the spec, the car treats iconName waypoints as numbered circles —
        // unlike the phone, it does not branch them into a separate maki-icon path.
        val wps = listOf(
            Waypoint(id = "a", lat = 0.0, lon = 0.0, iconName = "restaurant"),
            Waypoint(id = "b", lat = 1.0, lon = 1.0, iconName = null),
        )
        val (inactive, active) = buildWaypointMarkerFeatures(wps, activeWaypointId = null)
        assertEquals(2, countFeatures(inactive))
    }

    @Test
    fun buildWaypointMarkerFeatures_idPropertyMatchesWaypointId() {
        val wps = listOf(wp("alpha", 0.0, 0.0), wp("beta", 1.0, 1.0))
        val (inactive, _) = buildWaypointMarkerFeatures(wps, activeWaypointId = null)
        assertTrue(inactive.contains(""""id":"alpha""""))
        assertTrue(inactive.contains(""""id":"beta""""))
    }

    /** Count the number of feature entries in a GeoJSON FeatureCollection string. */
    private fun countFeatures(geoJson: String): Int {
        val features = geoJson.substringAfter("\"features\":[", "").substringBefore("]}", "")
        if (features.isEmpty()) return 0
        // Each feature begins with `{"type":"Feature",` — count those.
        return features.split("\"type\":\"Feature\"").size - 1
    }
```

- [ ] **Step 6: Add the necessary imports to `CarMapContainerLogicTest.kt`**

Add at the top:

```kotlin
import ca.voiditswarranty.roadtripradar.model.Waypoint
```

(Other imports `assertEquals`, `assertTrue`, `Test` are already present.)

- [ ] **Step 7: Run the new tests to verify they pass**

Run: `cd /home/chris/repos/RoadTripRadar2 && ./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.car.CarMapContainerLogicTest.buildWaypointMarkerFeatures_*"`
Expected: 7 tests, 0 failures, 0 errors.

- [ ] **Step 8: Hand off the diff**

Show the user the `git diff -- app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainerLogic.kt app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarMapContainerLogicTest.kt`. Do NOT run `git commit`.

---

## Task 6: Add the car waypoint marker sources + 8 layers in `setupOverlays`

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainer.kt` (add constants around 757-767; add layers in `setupOverlays` after the puck)

- [ ] **Step 1: Read the current `setupOverlays` to confirm the puck line is the last layer added**

The puck layer is added at line 486. The new waypoint-marker layers go *above* the route but *around* the puck (active and inactive hit areas go above the puck; visible bg + label layers go below the puck but above the route).

- [ ] **Step 2: Add the new constants to the constants block (after line 767, before the range-rings comment at line 768)**

```kotlin
        // Waypoint markers (mirrors the phone's ui.MapLayers.WaypointMarkersLayer,
        // minus the maki-icon path for iconName waypoints — per user scope).
        private const val WAYPOINT_HIT_RADIUS_PX = 24f
        private const val WAYPOINT_VISIBLE_RADIUS_PX = 12f
        private const val WAYPOINT_ACTIVE_RADIUS_PX = 14f
        private const val WAYPOINT_BG_COLOR = 0xFFE53935.toInt()
        private const val WAYPOINT_ACTIVE_RING_COLOR = 0xFF2E7D32.toInt()
        private const val WAYPOINT_LABEL_COLOR = 0xFFFFFFFF.toInt()
        private const val WAYPOINT_INACTIVE_BG_SOURCE_ID = "car-waypoint-inactive-bg"
        private const val WAYPOINT_INACTIVE_BG_LAYER_ID = "car-waypoint-inactive-bg-layer"
        private const val WAYPOINT_INACTIVE_HIT_LAYER_ID = "car-waypoint-inactive-hit-layer"
        private const val WAYPOINT_INACTIVE_LABEL_LAYER_ID = "car-waypoint-inactive-label-layer"
        private const val WAYPOINT_ACTIVE_BG_SOURCE_ID = "car-waypoint-active-bg"
        private const val WAYPOINT_ACTIVE_BG_LAYER_ID = "car-waypoint-active-bg-layer"
        private const val WAYPOINT_ACTIVE_HIT_LAYER_ID = "car-waypoint-active-hit-layer"
        private const val WAYPOINT_ACTIVE_LABEL_LAYER_ID = "car-waypoint-active-label-layer"
```

- [ ] **Step 3: Add the 2 sources and 8 layers in `setupOverlays` after the puck (after line 492, before the closing `}` of `setupOverlays` at line 493)**

```kotlin
        // Waypoint markers — 2 sources (inactive + active), each with 4 layers:
        // hit (invisible 24px) on top for driving-friendly taps, then label (text),
        // then bg (visible red circle) below. Active differs from inactive in: slightly
        // larger radius, green stroke ring, and is drawn between inactive and the puck.
        // Layer order from top to bottom: puck → active hit → inactive hit → active label
        // → inactive label → active bg → inactive bg → route active → route inactive
        // → range rings. Use addLayerAbove to slot each layer into the right place
        // relative to existing layers.
        style.addSource(GeoJsonSource(WAYPOINT_INACTIVE_BG_SOURCE_ID, EMPTY_FEATURE_COLLECTION))
        style.addSource(GeoJsonSource(WAYPOINT_ACTIVE_BG_SOURCE_ID, EMPTY_FEATURE_COLLECTION))
        style.addLayerAbove(
            LineLayer("placeholder-never-rendered", RANGE_RINGS_SOURCE_ID),
            ROUTE_INACTIVE_LAYER_ID,
        )  // NOTE: removed below — this is a no-op in our design; we use addLayerAbove
            // against existing layer IDs. The actual layer additions follow.

        // Inactive bg layer (visible red circle) — above route active.
        style.addLayerAbove(
            CircleLayer(WAYPOINT_INACTIVE_BG_LAYER_ID, WAYPOINT_INACTIVE_BG_SOURCE_ID).withProperties(
                circleRadius(WAYPOINT_VISIBLE_RADIUS_PX),
                circleColor(WAYPOINT_BG_COLOR),
                circleStrokeColor(0xFFFFFFFF.toInt()),
                circleStrokeWidth(2f),
            ),
            ROUTE_ACTIVE_LAYER_ID,
        )
        // Active bg layer (visible red circle + green ring) — above inactive bg.
        style.addLayerAbove(
            CircleLayer(WAYPOINT_ACTIVE_BG_LAYER_ID, WAYPOINT_ACTIVE_BG_SOURCE_ID).withProperties(
                circleRadius(WAYPOINT_ACTIVE_RADIUS_PX),
                circleColor(WAYPOINT_BG_COLOR),
                circleStrokeColor(WAYPOINT_ACTIVE_RING_COLOR),
                circleStrokeWidth(3.5f),
            ),
            WAYPOINT_INACTIVE_BG_LAYER_ID,
        )
        // Inactive label layer (white number text) — above active bg.
        style.addLayerAbove(
            SymbolLayer(WAYPOINT_INACTIVE_LABEL_LAYER_ID, WAYPOINT_INACTIVE_BG_SOURCE_ID).withProperties(
                textField(Expression.get(Expression.toString("label"))),
                textFont(arrayOf("Noto Sans Regular")),
                textSize(14f),
                textColor(WAYPOINT_LABEL_COLOR),
                textAllowOverlap(true),
                textIgnorePlacement(true),
            ),
            WAYPOINT_ACTIVE_BG_LAYER_ID,
        )
        // Active label layer (white number text) — above inactive label.
        style.addLayerAbove(
            SymbolLayer(WAYPOINT_ACTIVE_LABEL_LAYER_ID, WAYPOINT_ACTIVE_BG_SOURCE_ID).withProperties(
                textField(Expression.get(Expression.toString("label"))),
                textFont(arrayOf("Noto Sans Regular")),
                textSize(14f),
                textColor(WAYPOINT_LABEL_COLOR),
                textAllowOverlap(true),
                textIgnorePlacement(true),
            ),
            WAYPOINT_INACTIVE_LABEL_LAYER_ID,
        )
        // Inactive hit layer (invisible 24px) — above inactive label, below active hit.
        style.addLayerAbove(
            CircleLayer(WAYPOINT_INACTIVE_HIT_LAYER_ID, WAYPOINT_INACTIVE_BG_SOURCE_ID).withProperties(
                circleRadius(WAYPOINT_HIT_RADIUS_PX),
                circleColor(0x00000000),
                circleStrokeColor(0x00000000),
                circleStrokeWidth(0f),
            ),
            WAYPOINT_INACTIVE_LABEL_LAYER_ID,
        )
        // Active hit layer (invisible 24px) — above inactive hit, below puck.
        style.addLayerAbove(
            CircleLayer(WAYPOINT_ACTIVE_HIT_LAYER_ID, WAYPOINT_ACTIVE_BG_SOURCE_ID).withProperties(
                circleRadius(WAYPOINT_HIT_RADIUS_PX),
                circleColor(0x00000000),
                circleStrokeColor(0x00000000),
                circleStrokeWidth(0f),
            ),
            WAYPOINT_INACTIVE_HIT_LAYER_ID,
        )
        // Active hit layer ends up directly below the puck (puck was added last with
        // addLayer, which puts it at the very top).
```

**Note on `addLayerAbove` vs `addLayer`:** the existing route layers and puck layer are added with `style.addLayer(...)` which places them at the top of the style (z-order). To insert the new waypoint layers in the *middle* of the existing order, we use `style.addLayerAbove(newLayer, existingLayerId)` which places the new layer just above the existing one. The order is constructed bottom-up: route active → inactive bg → active bg → inactive label → active label → inactive hit → active hit → (puck was already at top).

- [ ] **Step 4: Verify the build compiles**

Run: `cd /home/chris/repos/RoadTripRadar2 && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. The new layers are added to the style; no source data is pushed yet, so the layers render as empty (no markers) until `updateWaypointMarkers` runs.

- [ ] **Step 5: Hand off the diff**

Show the user the `git diff -- app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainer.kt`. Do NOT run `git commit`.

---

## Task 7: Add `updateWaypointMarkers` and call it from `refreshFromVm`

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainer.kt` (add `updateWaypointMarkers` near `updateRoute`; call from `refreshFromVm`)

- [ ] **Step 1: Add the `updateWaypointMarkers` method**

In `CarMapContainer.kt`, add this method directly after `updateRoute` (after line 614, before `updatePuck` at line 616):

```kotlin
    private fun updateWaypointMarkers(style: Style) {
        val (inactiveJson, activeJson) = buildWaypointMarkerFeatures(
            waypoints = vm.waypoints,
            activeWaypointId = vm.activeWaypointId,
        )
        (style.getSource(WAYPOINT_INACTIVE_BG_SOURCE_ID) as? GeoJsonSource)
            ?.setGeoJson(inactiveJson)
        (style.getSource(WAYPOINT_ACTIVE_BG_SOURCE_ID) as? GeoJsonSource)
            ?.setGeoJson(activeJson)
    }
```

- [ ] **Step 2: Call `updateWaypointMarkers` from `refreshFromVm`**

In `refreshFromVm` (around line 496-517), add the new call after `updateRoute(style)` at line 512. The block becomes:

```kotlin
        updateRoute(style)
        updateWaypointMarkers(style)
        updatePuck(style)
```

- [ ] **Step 3: Run the car test class to verify no regressions**

Run: `cd /home/chris/repos/RoadTripRadar2 && ./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.car.*"`
Expected: All car tests pass. The new `updateWaypointMarkers` is not yet tested directly (Task 5 already tests the pure helper it depends on).

- [ ] **Step 4: Hand off the diff**

Show the user the `git diff -- app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainer.kt`. Do NOT run `git commit`.

---

## Task 8: Add the click handler for car waypoint markers

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainer.kt` (add `addOnMapClickListener`)

- [ ] **Step 1: Find the existing `MapView` setup**

Look for where `MapView` is created (around `createMapView()` at line 739). The click listener should be registered once, in the same place where the map is set up. Use `mapView.addOnMapClickListener { ... }` (or `mapLibreMap.addOnMapClickListener { ... }` — check the existing pattern).

- [ ] **Step 2: Add the click handler**

In the location where the map is initialized (likely inside `createMapView()` or the `onMapReady` callback), add this listener registration. Read the existing `createMapView` function (lines 739+) to find the right spot.

The pattern (add after the MapView is set up but before the function returns):

```kotlin
    mapView.addOnMapClickListener { point ->
        val features = mapView.queryRenderedFeatures(
            point,
            WAYPOINT_INACTIVE_HIT_LAYER_ID,
            WAYPOINT_ACTIVE_HIT_LAYER_ID,
        )
        val first = features.firstOrNull() ?: return@addOnMapClickListener false
        val id = first.properties()?.get("id")?.asString
            ?: return@addOnMapClickListener false
        vm.setActiveWaypoint(id)
        true  // consume
    }
```

If the car already has an `addOnMapClickListener` registered for some other purpose (e.g., tap-to-recenter), add the waypoint check at the top of that existing handler and return `true` (consume) if a waypoint feature was hit. If there's no existing handler, the above is the entire addition.

- [ ] **Step 3: Verify the build compiles**

Run: `cd /home/chris/repos/RoadTripRadar2 && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. The new click handler wires up; no compile errors.

- [ ] **Step 4: Hand off the diff**

Show the user the `git diff -- app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainer.kt`. Do NOT run `git commit`.

---

## Task 9: Update the changelog

**Files:**
- Modify: `app/src/main/assets/changelog.json` (append a bullet to the `next` release)

- [ ] **Step 1: Read the current `changelog.json` to find the `next` release's `items` array**

- [ ] **Step 2: Append the new bullet to the `next` release's `items` array**

Add as the last item (no trailing comma since it's the last element):

```json
        "Route line on Android Auto now matches the phone (bright green), and waypoint circles + numbers now render on the car map. Fixed a regression on the phone where the dashed planned-route segments between waypoints were missing."
```

- [ ] **Step 3: Verify the JSON parses**

Run: `python3 -c "import json; json.load(open('/home/chris/repos/RoadTripRadar2/app/src/main/assets/changelog.json'))" && echo OK`
Expected: prints `OK`.

- [ ] **Step 4: Hand off the diff**

Show the user the `git diff -- app/src/main/assets/changelog.json`. Do NOT run `git commit`.

---

## Task 10: Final verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit test suite**

Run: `cd /home/chris/repos/RoadTripRadar2 && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Expected total: current count + 5 new `RouteStyleTest` tests + 7 new `buildWaypointMarkerFeatures_*` tests = **~919 tests, 0 failures, 0 errors** (912 baseline after the wind-label work, +12 new tests, accounting for the 5 deleted `pastLegs_*` tests = 907 + 12 = 919). Verify by aggregating XMLs:

```bash
grep -h "tests=" /home/chris/repos/RoadTripRadar2/app/build/test-results/testDebugUnitTest/*.xml | grep -oE 'tests="[0-9]+" skipped="[0-9]+" failures="[0-9]+" errors="[0-9]+"' | awk -F'"' '{tests+=$2; skipped+=$4; failures+=$6; errors+=$8} END {print "Total tests:", tests, "| Skipped:", skipped, "| Failures:", failures, "| Errors:", errors}'
```

- [ ] **Step 2: Run the androidTest compile**

Run: `cd /home/chris/repos/RoadTripRadar2 && ./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verify no dangling references**

Run:
```bash
cd /home/chris/repos/RoadTripRadar2 && grep -rn "pastLegs\|buildActiveLegLineString\|RouteLineLayerStyle\|RouteLineLayerLogic\|0xFF2E7D32" app/src/main app/src/test app/src/androidTest 2>/dev/null
```
Expected: only the 2 phone active-marker-ring stroke matches at `MapLayers.kt:340, 420` (which are intentional and stay), and 1 car `WAYPOINT_ACTIVE_RING_COLOR = 0xFF2E7D32` (intentional — the active waypoint's green ring is Material Green 800). No `ROUTE_COLOR` literal in `CarMapContainer`.

- [ ] **Step 4: Hand off the final summary**

Show the user:
- `cd /home/chris/repos/RoadTripRadar2 && git status` — full list of changed/new/deleted files.
- `cd /home/chris/repos/RoadTripRadar2 && git diff --stat` — line-count summary.
- A short summary of all 10 tasks completed.

Do NOT run `git commit` (per the user's "Don't commit, I will do that" rule).

---

## Self-review

**1. Spec coverage:** every section of the spec maps to a task:
- `data/RouteStyle.kt` + tests → Task 1
- Phone `MapLayers.kt` switch to `RouteStyle` + `data.RouteGeometry` → Task 2
- Delete dead `ui/RouteLineLayer*` files → Task 3
- Car `ROUTE_COLOR` → `RouteStyle.ARGB` → Task 4
- Car pure `buildWaypointMarkerFeatures` + tests → Task 5
- Car 8 waypoint layers + 2 sources in `setupOverlays` → Task 6
- Car `updateWaypointMarkers` + `refreshFromVm` call → Task 7
- Car `addOnMapClickListener` → Task 8
- Changelog → Task 9
- Verification → Task 10

**2. Placeholder scan:** no "TBD", "TODO", "fill in details" placeholders. Every step has exact file paths, exact code blocks, exact commands.

**3. Type consistency:**
- `RouteStyle` introduced in Task 1; used in Tasks 2, 4.
- `inactiveRouteLegs` and `activeRouteLeg` from `data.RouteGeometry` (existing) used in Task 2.
- `buildWaypointMarkerFeatures(waypoints, activeWaypointId)` introduced in Task 5; used in Task 7.
- `featureCollectionGeoJson(points, ids, labels)` introduced in Task 5; used by `buildWaypointMarkerFeatures`.
- 8 new constants in Task 6 (`WAYPOINT_HIT_RADIUS_PX`, etc.); used in Tasks 6, 7, 8.
- 8 new source/layer IDs in Task 6; used in Tasks 6, 7, 8.
- `setActiveWaypoint(id)` in Task 8 — verify this method exists on the VM during Task 8 implementation (it does — see `MapViewModel.kt:1192` and other lines).

**4. Layer order consistency:** Task 6's `addLayerAbove` chain is:
- `WAYPOINT_INACTIVE_BG_LAYER_ID` above `ROUTE_ACTIVE_LAYER_ID` → inactive bg just above route active.
- `WAYPOINT_ACTIVE_BG_LAYER_ID` above `WAYPOINT_INACTIVE_BG_LAYER_ID` → active bg just above inactive bg.
- `WAYPOINT_INACTIVE_LABEL_LAYER_ID` above `WAYPOINT_ACTIVE_BG_LAYER_ID` → inactive label just above active bg.
- `WAYPOINT_ACTIVE_LABEL_LAYER_ID` above `WAYPOINT_INACTIVE_LABEL_LAYER_ID` → active label just above inactive label.
- `WAYPOINT_INACTIVE_HIT_LAYER_ID` above `WAYPOINT_INACTIVE_LABEL_LAYER_ID` → inactive hit just above inactive label.
- `WAYPOINT_ACTIVE_HIT_LAYER_ID` above `WAYPOINT_INACTIVE_HIT_LAYER_ID` → active hit just above inactive hit.

Top of style (highest z-order, drawn last): puck (added with `addLayer`, on top of everything). Then the new waypoint layers in the order above (top-down: active hit, inactive hit, active label, inactive label, active bg, inactive bg). Then the existing route layers and range rings (added first with `addLayer`, now at the bottom).
