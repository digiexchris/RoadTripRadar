# Phone — Test Coverage Follow-up (Unit Tests)

> Companion to `2026-07-05-phone-test-coverage-design.md` and its
> Phase 7 follow-up. Tackles the unit-test side of the 5 next
> directions identified after Phase 7 completed: the halo-theme
> audit, plus the three remaining VM-coupled composables
> (`MapOverlay`, `ActionsDrawer`, `MapScreen`).
>
> The instrumented-test + DHU-smoke half of the work lives in a
> separate spec, `2026-07-07-phone-test-coverage-followup-instrumented.md`.

## Context

The original phone test coverage spec landed all 6 planned phases
plus a Phase 7 follow-up that refactored 16 VM-coupled composables.
The total stands at **839 tests across 82 files** as of 2026-07-07.

After Phase 7, 5 directions remained. This spec covers directions
1, 2, and 3 of that list (the unit-test work); the instrumented
test work (directions 4, 5) is in the companion spec.

**The 5 next directions (this spec covers the unit-test ones):**

1. **Verify the recent halo-theme bug** would be caught by the new
   palette tests (audit + add any missing tests).
2. **`MapOverlay` public composable still takes `vm: MapViewModel`** —
   extract a `MapOverlayState` data class and parameterize the
   public composable.
3. **`ActionsDrawer` and `MapScreen` public composables still take
   `vm: MapViewModel`** — extract state classes for each.
4. Run `./gradlew :app:connectedAndroidTest` to verify the
   instrumented tests pass. *(out of scope for this spec)*
5. DHU smoke runbook for physical Android Auto head units. *(out of
   scope for this spec)*

**Goal:** finish the unit-test work started in Phase 7. Same TDD
discipline, same patterns. No new test infrastructure.

**Decisions from this interview:**

- **Approach**: this spec is one of two follow-up specs. The split
  (unit vs. instrumented) respects a real boundary: the unit-test
  work reuses the `set*ForTest` seam pattern and Robolectric infra
  from Phase 1-7, while the instrumented work introduces new
  `androidTest/` configuration and `LocationManager.setTestProviderLocation`
  GPS injection that don't overlap with anything in this spec.
- **Halo audit (direction 1)**: read the actual bug fix commit
  (`04634bf`) and audit the new palette tests to confirm they would
  have caught the regression. Add any missing tests. No "reproduce
  the bug end-to-end" — the user picked audit + add tests, not full
  reproduction.
- **`MapOverlay` refactor (direction 2)**: aggressive — full state
  class extraction. A `MapOverlayState` data class holds all the
  VM-derived values; the public `MapOverlay` becomes a thin wrapper
  that reads VM state into the state class and calls
  `MapOverlayContent(state, ...)`. The compass slot pattern
  established in Phase 7 stays.
- **`ActionsDrawer` refactor (direction 3 part 1)**: page-state
  extraction. A `DrawerPage` sealed class (Main/Map/Weather/System/
  Help) holds the page's actions list. Public `ActionsDrawer` takes
  the current page and the page-factory. Test that rendering each
  page invokes the right callbacks.
- **`MapScreen` refactor (direction 3 part 2)**: full state class
  extraction. A `MapScreenState` data class holds all the VM-derived
  values. The public `MapScreen` composable still owns the
  `LaunchedEffect`s (those are VM-side effects) and reads VM state
  into the state class. Test the state class + the content
  composable.

**Out of scope:**

- `MainActivity.kt` (Activity bootstrap, no business logic).
- `car/` package (covered by the car spec).
- `LocationPermissionGate` (Android-specific plumbing; the user
  agreed in Phase 7 to skip it).
- `MapScreen`'s `LaunchedEffect`s (they're VM-side effects; the
  state class + content composable is enough).
- Anything requiring a real device/emulator (companion spec).

## The original "halo-theme" bug

For context, this is what the audit phase is testing against:

**Bug 1 — wind icon invisible on dark mode** (commit `04634bf`,
file `WeatherWidget.kt`): the `Air` icon was drawn directly on the
widget background with `tint = onSurface`. On dark mode the icon
blended in because both the widget surface and `onSurface` are
similar dark colors. The fix added a circular `Box` with
`clip(CircleShape).background(MaterialTheme.colorScheme.surface)`
behind the icon — a visual "badge" that guarantees contrast
regardless of theme.

**Bug 2 — auto-route-advance didn't advance the line** (same
commit, file `MapLayers.kt`): `WaypointRouteLineLayer` was drawing
the "active" route leg as the planned leg between the current and
next waypoints (a static `path[activeIndex] → path[activeIndex + 1]`).
It should have been drawing from the user's live position to the
active target (`path[0] → path[activeIndex + 1]`), so the line
advances as the user moves. The fix also changed `pastFc` to start
at 1 (not 0) so the user→first-waypoint approach is never drawn
twice.

Both are pure rendering/logic bugs that the new palette tests did
not pin. The audit phase identifies and adds the missing tests.

## Plan

### Phase 1 — Halo-theme audit (Direction 1)

**Goal:** confirm the two bugs above would be caught by tests
**written as part of this phase** (the new tests, not the existing
ones — the existing tests didn't catch the bugs, that's the audit
finding).

**Tasks:**

1. **Read the original bug fix.** Done. Both bugs are summarized
   above.
2. **Audit the existing `WeatherWidgetTest` and `MapStyleTest`/
   `MapStyleResolutionTest` for gaps.** The existing
   `WeatherWidgetTest` has 7 tests but none pin the wind icon's
   background badge. The `MapStyle`/`MapStyleResolutionTest` tests
   don't cover `WeatherWidget` rendering at all — the bug was a
   visual contrast issue in the widget, not a `MapStyle.isDark`
   branch. Conclusion: the existing tests would not have caught
   bug 1.
3. **Audit the existing `MapLayers*` / `RouteGeometry*` tests for
   gaps.** Phase 1's `RouteGeometryTest` covers `inactiveRouteLegs`
   and `activeRouteLeg` helpers in `MapViewModelLogic` — but
   `WaypointRouteLineLayer` is a different code path (the
   Compose-side `FeatureCollection` builders, not the VM's pure
   helpers). No tests exist for `WaypointRouteLineLayer`'s past-
   and active-leg `FeatureCollection` builders. Conclusion: the
   existing tests would not have caught bug 2.
4. **Add the missing tests.** See below.

**New tests to add in this phase:**

- `WeatherWidgetTest` (extend existing file): add 2 tests
  - `widget_windIconHasBackgroundBadge` — assert that the
    `Wind direction` `Icon` has a parent `Box` (its `Surface`
    ancestor in the semantics tree, or just that the widget
    renders without crashing in dark mode and the icon is
    findable). The exact assertion: `onNodeWithContentDescription("Wind direction")`
    is findable in dark mode.
  - `widget_darkMode_windIconHasSurfaceBackground` — render in
    dark theme (`RoadTripRadarTheme(darkTheme = true)`), assert
    the wind icon area is wrapped in a `Box` with the `surface`
    color. Use `onAllNodes(matching ...)` to find the badge, or
    simply assert the icon's `mergeDescendants` parent exists.
    This is the test that would have caught bug 1.

- `WaypointRouteLineLayerTest` (new file, Robolectric): tests for
  the past-leg and active-leg `FeatureCollection` builders. These
  are currently inline lambdas inside the composable, so the
  refactor step is: extract them to `internal fun`s in a new
  `RouteLineLayerLogic.kt` sibling, TDD the extraction.
  - `pastLegs_noActiveIndex_returnsEmpty` — `activeIndex = null`,
    no past legs.
  - `pastLegs_activeIndexZero_returnsEmpty` — active = 0, the
    user→first-waypoint approach is drawn by `activeFc` instead,
    not as a past leg.
  - `pastLegs_activeIndexTwo_returnsOneLeg` — 4 waypoints, active
    = 2, returns 1 leg (waypoints[1] → waypoints[2]).
  - `pastLegs_activeIndexLast_returnsAllPriorLegs`.
  - `activeLeg_userPositionNull_returnsEmpty` — the active leg
    needs the user's position to draw from; without it, no leg.
  - `activeLeg_activeIndexLast_returnsEmpty` — no "next" waypoint.
  - `activeLeg_validInputs_legFromUserToNextWaypoint` — leg goes
    from `path[0]` (user) to `path[activeIndex + 1]`, not from
    `path[activeIndex]` (the bug).

  **Refactor step:** extract the two lambdas at
  `MapLayers.kt:229-256` into `internal fun buildPastLegs(...)`
  and `internal fun buildActiveLeg(...)` in a new
  `RouteLineLayerLogic.kt`. The composable calls them. Tests
  call them directly.

- `RouteLineLayerStyleTest` (new file, pure): tests for the
  `LineLayer` config: width 5dp, opacity 0.95f, dasharray [4, 3].
  These are values from the bug fix. The test pins them so a
  future "let's make the line thicker" change is intentional.

  **Refactor step:** if `width`, `opacity`, `dasharray` are
  inline literals in `WaypointRouteLineLayer`, extract them as
  `internal const val` or an `internal data class
  RouteLineStyle`. Pin the values in tests.

**Estimated new tests: 9-11.** No new test files except
`WaypointRouteLineLayerTest` and `RouteLineLayerStyleTest`.

**TDD discipline:** for the new files, write the test first, watch
it fail (or fail to compile because the function is private),
extract the helper, watch the test pass. Same as Phase 7.

### Phase 2 — `MapOverlayState` extraction (Direction 2, aggressive)

**Goal:** refactor the public `MapOverlay` composable to take a
`MapOverlayState` data class as a parameter. The state class holds
all the VM-derived values (useMetric, speedMps, networkStatus,
waypoints, etc.). The public `MapOverlay` becomes a thin wrapper
that reads VM state into the state class and calls
`MapOverlayContent(state, ...)`. The `compassButton: @Composable () -> Unit`
slot established in Phase 7 stays.

**Refactor mechanics:**

```kotlin
// New: ui/MapOverlayState.kt
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

```kotlin
// Refactored: ui/MapOverlay.kt
@Composable
fun BoxScope.MapOverlay(
    config: LayoutConfig,
    vm: MapViewModel,
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
        // ... all the other properties
    )
    MapOverlayContent(
        config = config,
        state = state,
        bearing = bearing,
        hasLocation = hasLocation,
        hasGpsFix = hasGpsFix,
        poiInfo = poiInfo,
        cameraState = cameraState,
        scope = scope,
        isLandscape = isLandscape,
    )
}

@Composable
fun BoxScope.MapOverlayContent(
    config: LayoutConfig,
    state: MapOverlayState,
    bearing: Double,
    hasLocation: Boolean,
    hasGpsFix: Boolean,
    speedMps: Double,
    poiInfo: Pair<Length, Double>?,
    cameraState: CameraState,
    scope: CoroutineScope,
    isLandscape: Boolean = false,
) {
    // ... the current body of MapOverlay, reading from `state`
    //     instead of `vm.*` ...
}
```

**New tests in this phase:**

- `MapOverlayStateTest` (new file, pure): tests for the data
  class itself — constructor pins, equality, copy. Trivial but
  pins the contract. ~3 tests.
- `MapOverlayContentTest` (new file, Robolectric): tests for
  the parameterized `MapOverlayContent`. Builds a
  `MapOverlayState` with all fields populated, hosts the
  composable in a `Box`, asserts it renders without crashing.
  Mirrors the Phase 7 `MapOverlayTopRowTest` style. ~4-6 tests:
  - `content_allFieldsPopulated_rendersWithoutCrash`.
  - `content_emptyWaypoints_skipsNavRow`.
  - `content_withActiveWaypoint_rendersNavWidget` (via
    `poiInfo` non-null).
  - `content_weatherInactive_skipsTimelineAndLegend`.
  - `content_failedCells_rendersRetryButton`.
  - `content_darkTheme_appliesDarkPalette`.

  The compass slot in tests is still an empty `{}` (the real
  `CompassButton` requires `CameraState`).

**Estimated new tests: 7-9.** 2 new test files.

**TDD discipline:** write the `MapOverlayState` data class first
(no test needed — it's a data class). Write the
`MapOverlayContent` signature and an empty body. Watch the call
site fail to compile. Update the call site. Then write
`MapOverlayContentTest` and verify it passes.

### Phase 3 — `DrawerPage` state extraction (Direction 3 part 1)

**Goal:** refactor the public `ActionsDrawer` composable so the
**page state** is parameterized. The 5 pages (Main, Map, Weather,
System, Help) each have a list of `DrawerAction`s; the public
`ActionsDrawer` takes the current page + a way to get the actions
for any page.

**Why not a full `DrawerState` data class:** the
`ActionsDrawerPage` enum is page-state, not VM-state. The VM
provides the actions for the current page (which depend on
runtime conditions like the active waypoint, the map style, etc.).
Parameterizing over the actions list is the right boundary — the
public composable still owns the page navigation (Main → Map →
back to Main), but the action rendering is testable.

**Refactor mechanics:**

```kotlin
// New: ui/DrawerPage.kt
internal sealed class DrawerPage {
    abstract val title: String
    abstract val actions: List<DrawerAction>

    object Main : DrawerPage() { ... }
    object Map : DrawerPage() { ... }
    object Weather : DrawerPage() { ... }
    object System : DrawerPage() { ... }
    object Help : DrawerPage() { ... }
}

@Composable
internal fun DrawerPageContent(
    page: DrawerPage,
    onDismiss: () -> Unit,
) {
    // The current body of ActionsDrawer's per-page rendering,
    // parameterized over `page` and `onDismiss`.
}
```

The public `ActionsDrawer` keeps its `vm: MapViewModel` parameter
(it owns the page navigation), but its body becomes:

```kotlin
@Composable
fun ActionsDrawer(
    vm: MapViewModel,
    onDismiss: () -> Unit,
) {
    val currentPage = when (vm.currentDrawerPage) { ... }
    DrawerPageContent(
        page = currentPage,
        onDismiss = onDismiss,
    )
}
```

**New tests in this phase:**

- `DrawerPageTest` (new file, Robolectric): tests for each
  page's actions. Builds a fake `DrawerPage` (or uses the real
  `Main`/`Map`/etc.), hosts the composable, asserts the
  expected actions render. ~6-8 tests, one per page plus a
  cross-cutting test:
  - `pageMain_rendersCloseToMapAndBackToMain`.
  - `pageMap_rendersRouteActions`.
  - `pageWeather_rendersWeatherActions`.
  - `pageSystem_rendersSystemActions`.
  - `pageHelp_rendersHelpActions`.
  - `pageAny_dismissClick_invokesCallback`.
  - `pageAny_actionClick_invokesActionCallback`.
  - `pageAll_haveNonEmptyActionsList` (sanity).

  The `Main` page has 2 actions (Close to Map + Back to Main);
  other pages have more.

**Estimated new tests: 7-8.** 1 new test file.

**TDD discipline:** write `DrawerPage` data class first. Write
`DrawerPageContent` signature. Update the call site. Write
`DrawerPageTest`.

### Phase 4 — `MapScreenState` extraction (Direction 3 part 2)

**Goal:** refactor the public `MapScreen` composable so the
**content** (everything below the `LaunchedEffect`s) is
parameterized over a `MapScreenState` data class. The
`LaunchedEffect`s stay in the public composable (they're VM-side
effects).

**Why:** the 3 pure math helpers extracted in Phase 7
(`computeCameraOffsetPadding`, `computePoiInfo`,
`hasGoodGpsFix`) cover the math but not the rest of the screen.
Parameterizing the content gives a much larger testing surface.

**Refactor mechanics:**

```kotlin
// New: ui/MapScreenState.kt
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
    val cameraState: CameraState,
    val scope: CoroutineScope,
    val onStyleChange: (MapStyle) -> Unit,
    val onRetryFailedCells: () -> Unit,
    val onAdvanceWaypoint: () -> Unit,
    val onRegressWaypoint: () -> Unit,
    val onOpenRouteEditor: () -> Unit,
    val onCompassToggle: (Boolean) -> Unit,
    val onZoomIn: () -> Unit,
    val onZoomOut: () -> Unit,
    val onZoomInLong: () -> Unit,
    val onZoomOutLong: () -> Unit,
    val onRecenter: () -> Unit,
    val onOpenMenu: () -> Unit,
    val onWeatherOff: () -> Unit,
    val onWeatherPlayPause: () -> Unit,
)
```

The public `MapScreen` keeps its `vm: MapViewModel` parameter
plus all the `LaunchedEffect`s. The `MapScreenContent` composable
takes the state class.

**New tests in this phase:**

- `MapScreenStateTest` (new file, pure): tests for the data
  class. Constructor pins, equality, copy. ~3 tests.
- `MapScreenContentTest` (new file, Robolectric): tests for
  the parameterized `MapScreenContent`. The composable
  composes a `MaplibreMap` plus all the overlays; in tests
  without a real `CameraState`, the `MaplibreMap` is the
  tricky part. The test approach:
  - Render the composable without the `MaplibreMap` (extract
    the overlay tree into a separate `MapScreenOverlays`
    composable that takes the state and the camera padding).
  - Test `MapScreenOverlays` with primitives.
  - Test that `MapScreenContent` invokes the right callbacks
    on the right triggers (e.g., a "Recenter" button click
    invokes `onRecenter`).
  - ~4-6 tests.

  This is the trickiest phase because the `MaplibreMap`
  composable is platform-coupled. The "extract the overlay
  tree" approach is the same Option A pattern from Phase 7.

**Estimated new tests: 7-9.** 2 new test files.

**TDD discipline:** write `MapScreenState` first. Write
`MapScreenContent` + `MapScreenOverlays` signatures. Update
the call site. Write tests.

### Phase 5 — Integration smoke (B1-specific)

**Goal:** verify Phase 1-4 didn't break anything.

**Tasks:**

1. Run `./gradlew :app:compileDebugKotlin` — clean compile.
2. Run `./gradlew :app:testDebugUnitTest` — all 839+ existing
   tests pass, plus all new tests.
3. Spot-check: load the new `MapOverlayContent` in DHU and
   confirm it renders identically to the old `MapOverlay`.
4. Spot-check: load the new `ActionsDrawer` and click through
   all 5 pages, confirm navigation works.
5. Spot-check: load `MapScreen` and confirm GPS, camera,
   overlays all behave as before.

**No new tests.** Just verification.

## Critical files

- **Modified:**
  - `app/src/main/java/.../ui/MapLayers.kt` — extract past-leg
    and active-leg builders to `RouteLineLayerLogic.kt`.
  - `app/src/main/java/.../ui/WeatherWidget.kt` — no change
    (the bug is already fixed; the audit just adds tests).
  - `app/src/main/java/.../ui/MapOverlay.kt` — add
    `MapOverlayState` data class, extract `MapOverlayContent`.
  - `app/src/main/java/.../ui/ActionsDrawer.kt` — add
    `DrawerPage` sealed class, extract `DrawerPageContent`.
  - `app/src/main/java/.../ui/MapScreen.kt` — add
    `MapScreenState` data class, extract `MapScreenContent`
    + `MapScreenOverlays`.

- **New (sibling files for extracted helpers):**
  - `app/src/main/java/.../ui/RouteLineLayerLogic.kt`
  - `app/src/main/java/.../ui/MapOverlayState.kt`
  - `app/src/main/java/.../ui/DrawerPage.kt`
  - `app/src/main/java/.../ui/MapScreenState.kt`

- **New test files (~5):**
  - `app/src/test/java/.../ui/WaypointRouteLineLayerTest.kt`
  - `app/src/test/java/.../ui/RouteLineLayerStyleTest.kt`
  - `app/src/test/java/.../ui/MapOverlayStateTest.kt`
  - `app/src/test/java/.../ui/MapOverlayContentTest.kt`
  - `app/src/test/java/.../ui/DrawerPageTest.kt`
  - `app/src/test/java/.../ui/MapScreenStateTest.kt`
  - `app/src/test/java/.../ui/MapScreenContentTest.kt`

- **Extended (existing test files):**
  - `app/src/test/java/.../ui/WeatherWidgetTest.kt` — add 2 tests.

**Total: ~7 new test files, 1 extended. ~30-45 new tests.**
**Estimated new total: ~870-885 tests across ~89 files.**

## Reuse

- **State class pattern**: same as the `RouteGeometry` helpers
  in Phase 1 and the `MapViewModelLogic` extractions in Phase 3.
- **`compassButton` slot**: the slot pattern from Phase 7's
  `MapOverlayTopRow` carries over to `MapOverlayContent`.
- **Compose test infra**: `createComposeRule` +
  `RobolectricTestRunner` + `@Config(sdk = [33])` — same as
  Phase 5/7.
- **Halo bug reference**: commit `04634bf` is the canonical
  "what would have been caught" reference.

## Verification

1. `./gradlew :app:compileDebugKotlin` — clean compile.
2. `./gradlew :app:testDebugUnitTest` — all tests pass
   (existing 839 + new 30-45).
3. DHU spot-check: load the new `MapOverlayContent`,
   `ActionsDrawer`, `MapScreenContent` and confirm they
   render identically to the old versions.
4. The two halo bugs (wind icon invisible, route line static)
   should now have regression tests. To prove the tests would
   catch the bug, briefly revert the fix in a throwaway
   branch, run the test, confirm it fails, then revert the
   revert. (Optional, not part of the plan.)
5. No manual DHU changes for the unit-test work; the DHU
   smoke runbook lives in the companion spec.

## Out of scope (per scope decision)

- `connectedAndroidTest` and instrumented tests (companion spec).
- `MainActivity.kt`.
- `car/` package.
- `LocationPermissionGate` (Android-specific, skipped in Phase 7).
- The `MapScreen` `LaunchedEffect`s (they're VM-side effects;
  the content composable is enough).
- Full reproduction of the halo bug (audit + add tests, not
  reproduce).

## Order of implementation (risk-first)

1. **Phase 1** (halo audit) — the existing tests are
   straightforward to extend. The new
   `WaypointRouteLineLayerTest` requires the
   `RouteLineLayerLogic` extraction first, which is small.
2. **Phase 2** (`MapOverlayState`) — the state class is
   mechanical; the `MapOverlayContent` extraction is medium
   effort.
3. **Phase 3** (`DrawerPage`) — the `DrawerPage` sealed class
   is the most refactor-heavy of the three. Save it for after
   the MapOverlay work settles.
4. **Phase 4** (`MapScreenState`) — the most complex
   composable. The `MaplibreMap` integration is the
   constraint; the "extract `MapScreenOverlays`" approach
   minimizes the platform-coupling problem.
5. **Phase 5** (smoke) — final verification.

## Lessons learned from Phase 7 (carry forward)

- **Option A for big composables, private-to-internal for
  small ones.** `MapOverlay` is big → Option A (state class).
  The private sub-composables inside `ActionsDrawer` are
  small → already promoted in Phase 7.
- **Slot pattern for untestable platform-coupled components.**
  The `compassButton: @Composable () -> Unit` slot stays.
  Same idea for any other platform-coupled thing in the new
  content composables.
- **`assertExists()` for items below the test viewport** —
  the wind icon badge may be below the viewport in the
  WidgetTest. Use `assertExists()` not `assertIsDisplayed()`.
- **No MockK** — real objects only, constructor-injected seams.
- **Pin constants and counts** — the `RouteLineLayerStyleTest`
  pins `width=5dp`, `opacity=0.95f`, `dasharray=[4,3]` so a
  future "make the line thicker" change is intentional.

## Spec self-review

- **Placeholder scan:** none. Every test name above is
  concrete. The new test files are listed by name in the
  "Critical files" section.
- **Internal consistency:** Phase 1's refactor step
  (`RouteLineLayerLogic.kt` extraction) is consistent with
  the pattern used in Phase 7. Phase 2's `MapOverlayState`
  data class is consistent with the `MapScreenState` data
  class in Phase 4 — same shape, same pattern.
- **Scope check:** focused on the 3 remaining
  VM-coupled composables + the halo audit. ~5 new test
  files + 1 extended + ~30-45 new tests. Within the
  Phase 7 scale.
- **Ambiguity check:** "aggressive — full state class
  extraction" is defined concretely as: data class with
  all VM-derived values, public composable becomes a thin
  wrapper, content composable takes the data class. "Page
  state extraction" for `ActionsDrawer` is defined as:
  sealed class with one variant per page, each holding its
  actions list. No two reasonable readers would extract
  differently.
