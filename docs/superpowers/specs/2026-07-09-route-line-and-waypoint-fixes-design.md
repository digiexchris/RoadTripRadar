# Route Line and Waypoint Markers — Bug Fixes and Car Parity

## Context

Three bugs have existed since the start of the AA (Android Auto) branch
(`e55ab51 Initial Android Auto attempt`):

1. **Phone dashed green line is missing the active→next leg** (and some
   earlier planned legs depending on `activeIndex`). The phone uses a
   buggy `ui.RouteLineLayerLogic.pastLegs` helper instead of the shared
   `data.RouteGeometry.inactiveRouteLegs` that the car uses correctly.
2. **Car route line is a different shade of green** (`0xFF2E7D32` /
   Material Green 800) than the phone (`Color.Green` = `0xFF00FF00`).
   The car color appears to have been copy-pasted from the phone's
   *active-marker-ring* stroke (which does use `0xFF2E7D32` at
   `MapLayers.kt:340, 420`), not the phone's route line.
3. **Car surface doesn't render waypoint circles and numbers at all.**
   The phone's `WaypointMarkersLayer` (MapLibre Compose `CircleLayer` +
   `SymbolLayer` at `MapLayers.kt:267-449`) was never ported to the
   car, which uses raw MapLibre style additions
   (`style.addSource(...)` + `style.addLayer(...)`) instead of Compose
   layer composables.

**Goal:** fix all three bugs in one pass, extract a shared route-style
constant to prevent the hue from diverging again, and bring the car's
waypoint visualization up to phone parity (with click-to-set-active
behavior).

**Decisions from this interview (the user answered in sequence):**
- **Scope:** all three bugs in one pass (not phased).
- **Hue fix:** extract a shared `RouteStyle` constant (not inline match).
- **Dead code:** delete `ui/RouteLineLayerLogic.kt` and
  `ui/RouteLineLayerStyle.kt`; phone uses `data.RouteGeometry` directly.
- **Shared style location:** new file `data/RouteStyle.kt`.
- **Car circles scope:** just numbered circles (phone-matching), not
  maki icons for POI waypoints. **Caveat:** the phone *does* have a
  separate maki-icon path for waypoints with an `iconName` set, but
  the user explicitly chose "just numbered circles" — so iconName
  waypoints on the car render as numbered circles, not as maki icons.
  This is a deliberate phone-divergence; documented in the spec.
- **Car click semantics:** tap a circle → set that waypoint as the
  active target (calls `vm.setActiveWaypoint(id)`).
- **Car hit target:** wrap each circle in an invisible 24px hit area
  (above the visible 12-14px circle) for driving-friendly touch.

## Scope

**In scope:**
- New file `app/src/main/java/ca/voiditswarranty/roadtripradar/data/RouteStyle.kt` — shared visual constants.
- Edit `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapLayers.kt` — use `RouteStyle.COLOR`, use `data.RouteGeometry.inactiveRouteLegs` + `data.RouteGeometry.activeRouteLeg`.
- Delete `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogic.kt` (consolidated into `data.RouteGeometry`).
- Delete `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyle.kt` (consolidated into `data.RouteStyle`).
- Delete `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogicTest.kt` (5 `pastLegs` tests pinned wrong behavior; the 5 `buildActiveLegLineString` tests are covered by the existing `activeRouteLeg_*` tests in `RouteGeometryTest.kt` — verify during planning, add any missing).
- Delete `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyleTest.kt` if it exists (the file it tested is deleted).
- Edit `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainer.kt` — use `RouteStyle.ARGB`, add 8 waypoint-marker layers + 2 sources + `updateWaypointMarkers` + click handler.
- New test: `app/src/test/java/ca/voiditswarranty/roadtripradar/data/RouteStyleTest.kt` — pin the shared constants.
- Add a changelog bullet to the `next` release in `app/src/main/assets/changelog.json`.

**Out of scope:**
- Maki-icon markers on the car for waypoints with an `iconName` (the
  phone's separate icon-style marker path). Per user decision, car
  waypoints render as numbered circles only.
- Visual redesign of the car markers (e.g., night-mode-aware colors,
  driving-friendly simplified styling). Phone styling is mirrored as-is.
- A clickable hit area on the phone (the phone already has a 12dp
  visible circle that's directly clickable). The hit-area treatment is
  car-specific.
- `MapScreen` or other phone changes beyond the `MapLayers.kt` line
  edits.
- `MainActivity.kt`, theme system, base map style changes.

## Design — shared route style

### `app/src/main/java/ca/voiditswarranty/roadtripradar/data/RouteStyle.kt` (new)

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

Both surfaces import `RouteStyle` and reference the appropriate field.
The dead `ui/RouteLineLayerStyle.kt` is deleted; its values are
migrated here.

## Design — phone side (bug fix 1: missing dashed legs)

### `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapLayers.kt`

The phone's `WaypointRouteLineLayer` (lines 219-264) currently calls:

```kotlin
val inactiveFc = remember(...) {
    FeatureCollection(
        pastLegs(waypoints, activeIndex).map { ... },
    )
}
val activeFc = remember(...) {
    val leg = buildActiveLegLineString(waypoints, activeIndex, userPosition)
    FeatureCollection(...)
}
```

Replace with calls to the shared `data.RouteGeometry` helpers:

```kotlin
import ca.voiditswarranty.roadtripradar.data.activeRouteLeg
import ca.voiditswarranty.roadtripradar.data.inactiveRouteLegs
import ca.voiditswarranty.roadtripradar.data.RouteStyle

val inactiveFc = remember(...) {
    FeatureCollection(
        inactiveRouteLegs(waypoints).map { ... },
    )
}
val activeFc = remember(...) {
    val leg = activeRouteLeg(waypoints, activeIndex, userPosition)
    FeatureCollection(...)
}
```

`inactiveRouteLegs(waypoints)` returns *all* consecutive waypoint legs
including `active → next`, matching the car's correct behavior. The
buggy `pastLegs` is gone (deleted along with `RouteLineLayerLogic.kt`).

`Color.Green` at lines 252, 260 becomes `RouteStyle.COLOR`. Width (5.dp)
and opacity (0.6 / 0.95) and dasharray (`listOf(2, 3)`) at lines
253-255, 261-262 become `RouteStyle.*` references:

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

### Files to delete

- `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogic.kt` — entire file. All callers (just `MapLayers.kt`) are updated.
- `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyle.kt` — entire file. Replaced by `data.RouteStyle`.
- `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogicTest.kt` — entire file. The 5 `pastLegs_*` tests pinned wrong behavior (they pass with the buggy code; the correct behavior is now pinned by `inactiveRouteLegs_*` tests in `data/RouteGeometryTest.kt`). The 5 `buildActiveLegLineString_*` tests are covered by existing `activeRouteLeg_*` tests in `data/RouteGeometryTest.kt` (verify during planning; add any missing).
- `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyleTest.kt` if it exists (the file it tested is deleted).

## Design — car route color (bug fix 2: hue divergence)

### `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainer.kt`

Line 757 changes:

```kotlin
// before
private const val ROUTE_COLOR = 0xFF2E7D32.toInt()

// after
private val ROUTE_COLOR = RouteStyle.ARGB
```

The `const val` → `val` change is necessary because `RouteStyle.ARGB`
is a non-const property (it's in an `object`, not a `const val`). Add
`import ca.voiditswarranty.roadtripradar.data.RouteStyle`. All 3
`lineColor(ROUTE_COLOR)` sites (lines 465, 476) are unchanged — they
continue to reference the renamed local val. The comment at line 460
("Both share the same green") is now literally true.

## Design — car waypoint circles + numbers (bug fix 3: missing markers)

### Source / layer architecture

The car adds 2 sources and 8 layers, all above the route line and
below the puck. The hit-area layer is on top of the visible layers so
a tap anywhere within the 24px hit area registers, even if the user
taps just outside the 12-14px visible circle.

| Source ID | Type | Holds | Updated by |
|---|---|---|---|
| `car-waypoint-inactive-bg` | GeoJsonSource | Features for inactive waypoints (id, label) | `updateWaypointMarkers` |
| `car-waypoint-active-bg` | GeoJsonSource | Features for the active waypoint (id, label) | `updateWaypointMarkers` |

| Layer ID | Source | Type | Visible? | Purpose |
|---|---|---|---|---|
| `car-waypoint-inactive-hit-layer` | inactive-bg | CircleLayer | No (opacity 0) | 24px hit area, fires clicks |
| `car-waypoint-active-hit-layer` | active-bg | CircleLayer | No (opacity 0) | 24px hit area, fires clicks |
| `car-waypoint-inactive-bg-layer` | inactive-bg | CircleLayer | Yes (12px red) | The visible red circle |
| `car-waypoint-inactive-label-layer` | inactive-bg | SymbolLayer | Yes | Number text label |
| `car-waypoint-active-bg-layer` | active-bg | CircleLayer | Yes (14px red + green ring) | The visible active circle |
| `car-waypoint-active-label-layer` | active-bg | SymbolLayer | Yes | Number text label |

**Note:** the hit layer shares the source with the visible background
layer (no separate hit-source) — MapLibre's click handler returns
features from the source, and the hit layer just provides the touch
geometry. The handler filters by `id` to identify the waypoint.

### Layer paint properties

**Inactive hit layer** (invisible, 24px):
```
circleRadius(24f)
circleColor(0x00000000)  // transparent
circleStrokeColor(0x00000000)
circleStrokeWidth(0f)
```

**Inactive background layer** (visible, 12px red):
```
circleRadius(12f)
circleColor(0xFFE53935)  // Material Red 600
circleStrokeColor(0xFFFFFFFF)  // white
circleStrokeWidth(2f)
```

**Inactive label layer** (number text):
```
textField = format("{label}")  // feature property
textFont = ["Noto Sans Regular"]
textSize = 14f  // ~0.9em
textColor = 0xFFFFFFFF
textAllowOverlap = true
textIgnorePlacement = true
```

**Active hit layer** (invisible, 24px) — same paint as inactive hit.

**Active background layer** (visible, 14px red + green ring):
```
circleRadius(14f)
circleColor(0xFFE53935)
circleStrokeColor(0xFF2E7D32)  // Material Green 800
circleStrokeWidth(3.5f)
```

**Active label layer** — same as inactive label.

### Layer order (top to bottom in style)

1. Puck (existing) — top
2. `car-waypoint-active-hit-layer` (new) — active hit area
3. `car-waypoint-inactive-hit-layer` (new) — inactive hit area
4. `car-waypoint-active-label-layer` (new) — active number text
5. `car-waypoint-inactive-label-layer` (new) — inactive number text
6. `car-waypoint-active-bg-layer` (new) — active red circle with ring
7. `car-waypoint-inactive-bg-layer` (new) — inactive red circle
8. Route active (existing) — under markers
9. Route inactive (existing) — under active
10. Range rings (existing) — at bottom

### `updateWaypointMarkers(style: Style)` (new method)

```kotlin
private fun updateWaypointMarkers(style: Style) {
    val waypoints = vm.waypoints
    val activeId = vm.activeWaypointId

    val inactiveFeatures = waypoints.mapIndexedNotNull { idx, wp ->
        if (wp.id == activeId) return@mapIndexedNotNull null
        Feature(
            geometry = Point(wp.position),
            properties = buildJsonObject {
                put("id", wp.id)
                put("label", (idx + 1).toString())
            },
        )
    }
    val activeFeatures = waypoints.mapIndexedNotNull { idx, wp ->
        if (wp.id != activeId) return@mapIndexedNotNull null
        Feature(
            geometry = Point(wp.position),
            properties = buildJsonObject {
                put("id", wp.id)
                put("label", (idx + 1).toString())
            },
        )
    }

    (style.getSource(WAYPOINT_INACTIVE_BG_SOURCE_ID) as? GeoJsonSource)
        ?.setGeoJson(FeatureCollection(inactiveFeatures).toMapLibreGeoJson())
    (style.getSource(WAYPOINT_ACTIVE_BG_SOURCE_ID) as? GeoJsonSource)
        ?.setGeoJson(FeatureCollection(activeFeatures).toMapLibreGeoJson())
}
```

The `toMapLibreGeoJson()` conversion is the same pattern used by
`updateRoute` (line 611: `lineStringsGeoJson(...)`); a new
`featureCollectionGeoJson(fc: FeatureCollection): String` helper is
added to `CarMapContainer` (or a new shared helper) — same shape as
`lineStringsGeoJson` but for `FeatureCollection<Point, JsonObject>`.

**Edge cases:**
- Empty `vm.waypoints`: both sources get an empty `FeatureCollection` (no features, no visible markers). Hits fall through to the map (no-op).
- All inactive: active source empty, inactive has all features.
- All in one (the active is the only waypoint): active has 1, inactive has 0.
- Waypoint with `iconName` set (e.g., from POI search): treated as a numbered circle on the car. **Phone-divergence by user choice.**

### Click handling

A single `MapView.addOnMapClickListener` is registered in the car's
existing map setup (or added to `CarMapContainer`'s `onMapReady` /
`createMapView` path). The handler:

1. Queries rendered features at the click point filtered by
   `WAYPOINT_INACTIVE_HIT_LAYER_ID` + `WAYPOINT_ACTIVE_HIT_LAYER_ID`
   (using `mapView.queryRenderedFeatures(...)`).
2. Takes the first feature (the topmost layer wins because we put hit
   layers at the top of the style).
3. Reads the `id` property from the feature.
4. Calls `vm.setActiveWaypoint(id)`.
5. Consumes the click (no further propagation).

If no waypoint features hit (a tap on empty map), the click is
**passed through** to the existing map click handler (which is already
wired for panning / etc.). We don't break existing behavior.

**Note:** the car already has some map click handling for the puck
tap-to-recenter (or doesn't — verify during planning). The new
waypoint click handler runs *before* the existing handler and consumes
clicks that hit a waypoint feature. If the existing handler is more
general (e.g., handles all map clicks), the new one is a pre-filter.

### Call sites

- `setupOverlays(style)` — add the 8 layers + 2 sources after the
  puck layer. All use `style.addSource(...)` / `style.addLayer(...)`
  (or `addLayerAbove(...)` to put layers above the puck — see
  section below).

  **Note on layer order with the puck:** since the puck should be
  *above* the waypoint labels (the user's position is the most
  important thing on the map), and the waypoint hit area should also
  be above the labels (so it catches taps), the order from top to
  bottom should be: **puck → active hit → inactive hit → active label
  → inactive label → active bg → inactive bg → route active → route
  inactive → range rings**.

  The existing route layers are added with `style.addLayer(...)`
  (which adds to the top of the style). The puck is added after the
  route layers, so it's on top. For the new waypoint layers, we need
  to put the bg + label layers *between* the route and the puck, and
  the hit layers *above* the puck. So:

  - Use `style.addLayerAbove(WAYPOINT_INACTIVE_BG_LAYER_ID, ROUTE_ACTIVE_LAYER_ID)` to put it just above the route active.
  - Use `style.addLayerAbove(WAYPOINT_INACTIVE_LABEL_LAYER_ID, WAYPOINT_INACTIVE_BG_LAYER_ID)`.
  - Use `style.addLayerAbove(WAYPOINT_ACTIVE_BG_LAYER_ID, WAYPOINT_INACTIVE_LABEL_LAYER_ID)`.
  - Use `style.addLayerAbove(WAYPOINT_ACTIVE_LABEL_LAYER_ID, WAYPOINT_ACTIVE_BG_LAYER_ID)`.
  - Use `style.addLayerAbove(WAYPOINT_INACTIVE_HIT_LAYER_ID, PUCK_LAYER_ID)`.
  - Use `style.addLayerAbove(WAYPOINT_ACTIVE_HIT_LAYER_ID, WAYPOINT_INACTIVE_HIT_LAYER_ID)`.

- `refreshFromVm()` — add `updateWaypointMarkers(style)` after
  `updateRoute(style)` (line 512).

- The click listener registration — verify the car already has an
  `addOnMapClickListener` and add the waypoint filter, or register a
  new one if not.

### `CarMapContainer` constants block (additions around line 757-767)

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

## Design — tests

### `app/src/test/java/ca/voiditswarranty/roadtripradar/data/RouteStyleTest.kt` (new, plain JUnit, pure)

- `COLOR_isComposeGreen` — `assertEquals(Color.Green, RouteStyle.COLOR)`.
- `ARGB_isOpaqueBrightGreen` — `assertEquals(0xFF00FF00.toInt(), RouteStyle.ARGB)`.
- `ACTIVE_OPACITY_isHigherThanInactive` — `assertTrue(RouteStyle.ACTIVE_OPACITY > RouteStyle.INACTIVE_OPACITY)`.
- `INACTIVE_DASH_hasTwoEntries` — `assertEquals(listOf(2, 3), RouteStyle.INACTIVE_DASH)`.
- `ARGB_matchesColorGreenArgb` — `assertEquals(Color.Green.toArgb(), RouteStyle.ARGB)`. Pins that the phone's Compose color and the car's ARGB int are the same color.

### `app/src/test/java/ca/voiditswarranty/roadtripradar/data/RouteGeometryTest.kt` (extend if needed)

The 5 `buildActiveLegLineString_*` tests from the deleted
`RouteLineLayerLogicTest.kt` map to:

| Old phone test | New location |
|---|---|
| `activeLeg_emptyWaypoints_returnsNull` | covered by existing `activeRouteLeg_emptyWaypoints_returnsNull` |
| `activeLeg_nullActiveIndex_returnsNull` | covered by existing `activeRouteLeg_nullActiveIndex_returnsNull` |
| `activeLeg_activeIndexOutOfRange_returnsNull` | covered by existing `activeRouteLeg_activeIndexOutOfRange_returnsNull` |
| `activeLeg_validInputs_legFromUserToActiveWaypoint` | covered by existing `activeRouteLeg_validInputs_returnsLegFromUserToActiveWaypoint` |
| (none — `pastLegs_singleWaypoint_returnsNull` deleted, no equivalent) | N/A |

Verify during planning by reading the existing
`data/RouteGeometryTest.kt`. If all 4 are covered, no new tests. If
any are missing, add them.

### Tests to delete

- `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogicTest.kt` — entire file.
- `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyleTest.kt` if it exists — entire file. Verify during planning.

## Design — changelog

Append to the `next` release in
`app/src/main/assets/changelog.json`:

> "Route line on Android Auto now matches the phone (bright green), and
> waypoint circles + numbers now render on the car map. Fixed a
> regression on the phone where the dashed planned-route segments
> between waypoints were missing."

## Design — click handler details

The car uses `MapView.addOnMapClickListener` (MapLibre Android native
API). On click, the handler:

```kotlin
mapView.addOnMapClickListener { point ->
    val hitFeatures = mapView.queryRenderedFeatures(
        point,
        WAYPOINT_INACTIVE_HIT_LAYER_ID,
        WAYPOINT_ACTIVE_HIT_LAYER_ID,
    )
    val first = hitFeatures.firstOrNull() ?: return@addOnMapClickListener false
    val id = first.properties()?.get("id")?.asString ?: return@addOnMapClickListener false
    vm.setActiveWaypoint(id)
    true  // consume
}
```

The return value is `true` to consume the click (no further
propagation to the existing map click handler) or `false` to pass
through. If the car already has an `addOnMapClickListener` registered,
the new one either replaces it (with a pre-filter) or is added in
addition. Verify the existing pattern during planning.

**Re-registration on style reload:** the `addOnMapClickListener` is
registered once in `createMapView()` or `onMapReady()` and survives
style reloads. The hit layer IDs are constant strings, so the
listener continues to work after `setupOverlays` rebuilds the style.

## Verification

1. `./gradlew :app:compileDebugKotlin` — clean compile.
2. `./gradlew :app:testDebugUnitTest` — all tests pass. Expected:
   current count + 5 new `RouteStyleTest` tests = **~912 tests, 0
   failures, 0 errors** (if no `RouteGeometryTest` additions are
   needed; +N if gaps are filled).
3. `grep -rn "0xFF2E7D32\|Color.Green" app/src/main` — the only
   matches should be the phone's *active-marker-ring stroke* (lines
   340, 420) and the car's *new* `WAYPOINT_ACTIVE_RING_COLOR`. No
   `ROUTE_COLOR` literal in `CarMapContainer`.
4. `grep -rn "pastLegs\|buildActiveLegLineString" app/src` — no
   matches (both deleted).
5. `grep -rn "WAYPOINT_\|car-waypoint-" app/src/main/java/.../car/` —
   exactly the new constants and source/layer IDs defined above.
6. Manual DHU verification on phone + car DHU:
   - Phone: 4 waypoints, active=2. Dashed line should show A→B, B→C,
     C→D (all three legs). Solid green line from user→C. Numbered red
     circles 1, 2, 3, 4 with circle 3 having a green ring.
   - Car: same 4 waypoints. Route line is bright green (`#00FF00`),
     not dark green. Dashed segments A→B, B→C, C→D visible. Solid
     user→C visible. Numbered red circles 1, 2, 3, 4 with circle 3
     having a green ring.
   - Car: tap circle 2 → active waypoint becomes 2. Solid line now
     user→2, circle 2 has the green ring, circle 3 is plain red.

## Critical files

**Modified (production):**
- `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapLayers.kt` — use `RouteStyle.COLOR`, use `data.RouteGeometry` helpers.
- `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainer.kt` — use `RouteStyle.ARGB`, add 8 waypoint layers + 2 sources + `updateWaypointMarkers` + click handler + `featureCollectionGeoJson` helper.
- `app/src/main/assets/changelog.json` — append a bullet.

**New:**
- `app/src/main/java/ca/voiditswarranty/roadtripradar/data/RouteStyle.kt` — shared visual constants.
- `app/src/test/java/ca/voiditswarranty/roadtripradar/data/RouteStyleTest.kt` — 5 pin-the-constant tests.

**Deleted (production):**
- `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogic.kt`.
- `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyle.kt`.

**Deleted (tests):**
- `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerLogicTest.kt`.
- `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/RouteLineLayerStyleTest.kt` (if it exists — verify).

**Possibly extended:**
- `app/src/test/java/ca/voiditswarranty/roadtripradar/data/RouteGeometryTest.kt` — only if the existing `activeRouteLeg_*` tests don't cover the 4 `buildActiveLegLineString_*` cases (verify during planning).

## TDD discipline

- **New tests first** for the shared `RouteStyle` (5 tests in
  `RouteStyleTest.kt`). They will pass trivially (constants exist),
  but the value is in pinning the contract.
- **Phone bug fix (1)** doesn't need a new test — the existing
  `inactiveRouteLegs_*` tests in `RouteGeometryTest.kt` already pin
  the correct behavior. The phone code just switches callers.
- **Car color fix (2)** is pinned by `RouteStyleTest.ARGB_*` — the
  car reads `RouteStyle.ARGB`, so as long as that constant is
  `0xFF00FF00`, the car is correct.
- **Car circles port (3)** — this is the only new feature with new
  logic. New tests in a new `CarWaypointMarkersTest.kt` (or extend
  `CarMapContainerTest.kt` if it exists) pin:
  - `updateWaypointMarkers_noWaypoints_emptySources` — empty list → empty `FeatureCollection` written to both sources.
  - `updateWaypointMarkers_oneInactiveWaypoint_inactiveSourceHasFeature` — 1 waypoint, no active → inactive source has 1 feature with label "1", active source empty.
  - `updateWaypointMarkers_oneActiveWaypoint_activeSourceHasFeature` — 1 waypoint, active = that one → active source has 1 feature with label "1", inactive empty.
  - `updateWaypointMarkers_fourWaypointsActiveIsThird_inactiveHasTwoActiveHasOne` — 4 waypoints, active is index 2 → inactive has waypoints 1, 2, 4 (label "1", "2", "4"), active has waypoint 3 (label "3").
  - `updateWaypointMarkers_waypointIdAndLabelPropertiesSet` — feature properties have `id` and `label` keys with the expected values.
  - The click handler is harder to unit-test in isolation (MapLibre
    Android's `MapView` is not easily mockable). The click path is
    covered by the test class and the manual DHU verification.

## Spec self-review

- **Placeholder scan:** no TBDs/TODOs. Every edit is concrete with
  file paths and code snippets.
- **Internal consistency:** the three bug fixes are independent
  (different files, different layers of the stack) but share the
  `RouteStyle` consolidation. The phone change reuses the same
  `data.RouteGeometry` helpers the car already uses, so the geometry
  is now provably identical (not just visually identical). The car
  circles port uses the same `vm.waypoints` / `vm.activeWaypointId`
  state the phone reads.
- **Scope check:** focused on three specific bugs plus the
  dead-code cleanup. Doesn't touch the radar, range rings, weather
  widget, POI markers, themes, or any other surface. The phone
  `WaypointMarkersLayer`'s maki-icon path is explicitly out of scope
  per user decision.
- **Ambiguity check:** the only soft constraint is the click
  handler's interaction with any existing `addOnMapClickListener` on
  the car — the spec says "verify during planning" and the
  implementation should match whichever pattern the car already uses.
  Layer-ordering is explicit (puck on top, then hits, then labels,
  then bgs, then route, then rings).
