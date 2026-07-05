# Android Auto — Test Coverage Plan

> **Note on file location:** the brainstorming skill specifies the spec lives at
> `docs/superpowers/specs/YYYY-MM-DD-android-auto-test-coverage-design.md`. That path is
> outside the current plan-mode allowed-edit set, so this same content should be copied to
> that file on approval. The plan file is the working draft.

## Context

The Android Auto (car) surface was built without test-driven discipline — only the
range-ring feature (which was added in this session) has unit tests. The user asked for a
plan to add TDD-style test coverage across the entire car package, since the car code
contains the kinds of subtle bugs (theme regressions, geometry math, label rendering)
that TDD catches best. The car surface is a process-shared `MapViewModel` with native
Views (not Compose), running on a `Presentation`-backed virtual display, with the host
rendering it via a `SurfaceCallback` from the car app library.

**Decisions from this interview:**
- **Scope:** everything in `app/src/main/java/ca/voiditswarranty/roadtripradar/car/` (and
  `car/screens/`) gets test coverage, not just the easy bits.
- **Infra:** JVM-only tests under `app/src/test/`, using **Robolectric** for Android
  View / resource work and **androidx.car.app.testing** for car-context / template
  tests. No androidTest/ instrumented tests. `androidx.car.app.testing` is already on
  the classpath but currently unused; we'll be its first consumer.
- **Retrofit rule:** strict TDD on every new behavior. For the existing hub
  (`CarMapContainer`, 791 lines), this is interpreted as **refactor-for-testability
  first** — extract small pure helpers (zoom-bracket change detection, offset padding
  math, driving-threshold logic, GeoJSON serialization for radar/route/puck, frame
  index selection for the car-local radar animation) and TDD each helper. The
  MapLibre-coupled glue (style.addLayer, getSource, etc.) stays in `CarMapContainer` and
  is *not* unit-tested.
- **Order:** risk-first. `CarMapContainer` first (highest blast radius, recent
  halo-theme bug lived here), then overlays (`CarRouteWidget`, `CarWeatherWidget`),
  then helpers (`CarUi`, `CarViewModelHolder`, `MakiIcons`), then screens
  (`HomeScreen`, `RouteScreen`, `SettingsScreen`, `PoiScreen`, `WeatherScreen`,
  `SearchScreen`, `CarNavMapScreen`).
- **Out of scope (per scope decision):** `CarMapRenderer` (real VirtualDisplay, real
  `SurfaceCallback` — needs DHU), `RoadTripRadarSession` (needs `SessionInfo` and full
  lifecycle plumbing), `RoadTripRadarCarAppService` (host validator is a thin one-liner
  that calls the library).

## Reuse from existing car tests (style guide)

- `CarRangeRingsTest.kt`, `CarRangeRingColorsTest.kt`, `CarRangeRingsGeoJsonTest.kt` —
  pure JUnit 4, no Robolectric, no mocks. Use the same style for `CarUi`, the
  `CarMapContainer` pure helpers, and the new `MapScreen`-style template tests.
- `CarRangeRingLabelsTest.kt` — Robolectric 4.16.1 with `@RunWith(RobolectricTestRunner)`,
  uses `ApplicationProvider.getApplicationContext()`. Use the same pattern for
  `CarRouteWidget`, `CarWeatherWidget`, and any test that touches inflated XML
  layouts (`car_route_widget.xml`, `car_weather_widget.xml`).
- All tests use JUnit 4 `org.junit.Assert.*` (not kotlin.test).

## Plan

### Phase 1 — `CarMapContainer` (the hub)

The hub is the highest-blast-radius file. The strategy: identify the
**already-pure or easy-to-purify** helper functions, extract them as `internal` top-level
functions in a new `CarMapContainerLogic.kt` (or as `companion object` / `internal`
functions on the class), and TDD each one. The MapLibre-coupled state machine
(`setupOverlays`, `refreshFromVm`, `updateRadar`, `updateRoute`, `updatePuck`) stays
in `CarMapContainer` and is exercised by manual DHU runs.

**Pure helpers to extract (each becomes a TDD cycle):**

1. **`shouldRebuildRings(currentCenter, lastCenter, currentZoom, lastZoom): Boolean`**
   — extracted from `updateRangeRings` (`CarMapContainer.kt:621-636`). Encapsulates
   the "center changed OR zoom bracket changed" decision. Tests pin down:
   - `lastCenter == null` → true (first call).
   - `lastCenter == currentCenter && lastZoom == null` → true.
   - centers equal, zooms in same bracket (e.g. 14.0 vs 14.5) → false.
   - centers equal, zooms cross a bracket boundary (14.0 vs 13.9) → true.
   - centers differ, zooms equal → true.

2. **`carOffsetPadding(mapHostHeight: Int, fraction: Float): Pair<Double, Double>?`**
   — extracted from `carOffsetPadding` (`CarMapContainer.kt:340-349`). Pure input/output.
   Tests:
   - `height == 0` → null.
   - `fraction == 0.5f` → null.
   - `height = 400, fraction = 0.25f` → `(200.0, 0.0)`.
   - `height = 400, fraction = 0.75f` → `(0.0, 200.0)`.
   - `fraction = 0f, height = 400` → `(400.0, 0.0)` (clamped at 0 bottom).
   - `fraction = 1f, height = 400` → `(0.0, 400.0)`.
   - Fraction outside [0,1] is clamped (`coerceIn(0f, 1f)`).

3. **`isDriving(movedDeg: Double, threshold: Double = MOVEMENT_DEG_THRESHOLD): Boolean`**
   — extracted from `pollPosition` (`CarMapContainer.kt:686-688`). Tests:
   - `movedDeg = 0.0` → false.
   - `movedDeg = 0.00005` → false.
   - `movedDeg = 0.0001` → false (boundary, ≤ threshold).
   - `movedDeg = 0.00011` → true.
   - `movedDeg = 1.0` → true.

4. **`carRadarDisplayIndex(pathCount: Int, driving: Boolean, mode: WeatherMode,
   carFrameIndex: Int): Int`** — extracted from `updateRadar` (`CarMapContainer.kt:551-553`).
   Encapsulates the SA-1 freeze / paused / playing selection. Tests:
   - `pathCount = 0` → 0 regardless of mode.
   - `pathCount = 5, driving = true` → `paths.lastIndex` (4).
   - `pathCount = 5, mode = OFF` → `paths.lastIndex` (mode is anything other than PLAYING).
   - `pathCount = 5, mode = PLAYING, driving = false, carFrameIndex = 2` → 2.
   - `pathCount = 5, mode = PLAYING, driving = false, carFrameIndex = 7` → 7
     (current code does NOT clamp; clamp happens in the *next-frame* setter via
     `coerceAtLeast(0)` against `paths.lastIndex` at frame-set change time, line
     545 of `CarMapContainer.kt`). TDD the actual behavior; if we want clamping
     here as well, add it as part of the helper extraction and let the test
     drive the new behavior.

5. **`pointGeoJson(pos: Position): String`** — already a `private fun` in
   `CarMapContainer.kt:746-747`. Promote to `internal` in `CarMapContainerLogic.kt` and
   TDD the round-trip via parsing the output. Tests:
   - Output is valid GeoJSON `Feature<Point>`.
   - Coordinates match `[pos.longitude, pos.latitude]` (GeoJSON order, not LatLng).
   - No `properties` field (the puck doesn't carry any).

6. **`lineStringsGeoJson(legs: List<LineString>): String`** — same pattern, already
   private at `CarMapContainer.kt:750-757`. Promote, TDD:
   - Empty list → empty FeatureCollection.
   - Single leg → FeatureCollection with one feature, coordinates in GeoJSON order.
   - Multiple legs → features joined, no trailing comma.

7. **`lineStringGeoJson(leg: LineString?): String`** — same, `CarMapContainer.kt:760-764`.
   Tests:
   - `null` → empty FeatureCollection.
   - Non-null leg → FeatureCollection with one feature, GeoJSON-ordered coordinates.

**Refactor mechanics:** each helper moves as `internal fun` to
`car/CarMapContainerLogic.kt` (new file). `CarMapContainer` updates the call site
to use the helper. The new helper file is purely Kotlin + `Position`/`LineString`
from `org.maplibre.spatialk.geojson` — no MapLibre, no Android Views, no
`CarContext`. **Every** new helper gets a TDD test in a new `CarMapContainerLogicTest.kt`
under `app/src/test/java/ca/voiditswarranty/roadtripradar/car/`. TDD order for each
helper: write the test, watch it fail (or pass if it's a pure move of existing
behavior — but the user signed up for strict TDD, so characterize-then-extract is
**not** the chosen path; per the chosen option the production code goes in last and
the test drives its API), implement minimal helper, watch it go green, refactor
in place.

**Critical for the TDD purist reading:** the `shouldRebuildRings` and
`carOffsetPadding` helpers exist in the production code right now, just inlined or as
private member functions. The "refactor for testability" choice means treating the
inline code as "not yet a unit" and writing the test for the desired extracted
signature first, then extracting. The user explicitly approved this interpretation
("Refine: add characterization tests BEFORE extraction" was offered and rejected).

### Phase 2 — Overlays

`CarRouteWidget` and `CarWeatherWidget` inflate XML layouts and mutate them. The
`update(bearing)` method is the public behavior to test.

**`CarRouteWidgetTest.kt` (new, Robolectric):**
- `update_noActiveWaypoint_hidesRoot` — set VM with `activeWaypoint = null`,
  call `update(0.0)`, assert `view.findViewById<View>(R.id.car_route_root).visibility == GONE`.
- `update_noUserPosition_hidesRoot` — set active waypoint but `userPositionForSearch
  = null` and `lastKnownPosition = null`, call `update(0.0)`, assert GONE.
- `update_withActiveWaypointAndUserPos_showsAndSetsDistance` — set a known
  user position and active waypoint, call `update(0.0)`, assert visible, assert
  `distanceText.text` matches `formatDistanceLabel(expectedDistance, useMetric=true)`.
- `update_arrowRotation_isBearingMinusCameraBearing` — set positions producing a
  known bearing, call `update(45.0)`, assert `arrow.rotation` ≈ `bearing - 45.0` (modulo
  float precision).
- `update_stopsText_hiddenWhenNoWaypoints` — `vm.waypoints = emptyList()`, assert
  `stopsText.visibility == GONE`.
- `update_stopsText_showsCountWhenWaypointsExist` — `vm.waypoints = listOf(a, b, c)`,
  assert `stopsText.text == carContext.getString(R.string.route_stop_count_plural, 3)`.
- `update_darkMode_appliesDarkPalette` — set `vm.mapStyle = DARK` and
  `carContext.isDarkMode = true`, call `update`, snapshot `arrow.imageTintList` and
  `root.backgroundTintList` colors. Assert they match the dark-palette constants
  in `CarRouteWidget.applyColors` (extract those to a companion-object constant
  set so the test can reference them, OR snapshot once and assert against the
  dark-palette literal — the test should not duplicate the magic numbers, instead
  call a small `CarRouteWidget.darkPalette()` / `lightPalette()` extractor if
  needed). Mirror for light mode.

**`CarWeatherWidgetTest.kt` (new, Robolectric):**
- `update_windDisabled_hidesRoot` — set `vm.windEnabled = false`, assert GONE.
- `update_noSnapshot_showsUnavailableGroup` — `vm.windEnabled = true`,
  `vm.openMeteoSnapshot = null`, assert `unavailableGroup.visibility == VISIBLE`,
  `contentGroup.visibility == GONE`.
- `update_withSnapshot_showsContentGroupAndSetsText` — set a known
  `OpenMeteoSnapshot`, assert `tempText.text`, `trendText.text`,
  `windSpeedText.text`, `windUnitText.text` all match the formatted outputs.
- `update_arrowRotation_isWindArrowRotationDegWithCameraBearing` — assert
  `arrow.rotation` matches `windArrowRotationDeg(snap.windDirectionDeg, cameraBearing)`.
- `update_trendText_usesDashWhenTrendNull` — `snap.tempTrendCelsius = null`,
  assert `trendText.text == "—/h"`.
- `update_darkMode_appliesDarkPalette` — mirror the route-widget dark/light
  palette test.
- `update_lightMode_appliesLightPalette` — mirror.

**TDD order per test:** the new tests pin down behavior that already exists. Per
the strict-TDD rule, the user has accepted that we'll be writing tests for existing
behavior as part of the retrofit — the test is written first, then any code
adjustment needed to make it pass (likely none for these widgets — the production
code should already pass; the test is the new artifact).

### Phase 3 — Helpers

Pure-Kotlin files that don't need Robolectric.

**`CarUiTest.kt` (new, plain JUnit):**
- `radarModeLabel_off_returnsRadarOffString` — uses `RuntimeEnvironment.application`
  (Robolectric for the Context) or a fake Context. (The car app's `WeatherMode` enum
  has three values; this is straightforward.)
- `radarModeLabel_on_returnsRadarOnString`
- `radarModeLabel_playing_returnsRadarPlayingString`
- `nextCycle_windSpeedUnit_wrapsAround` — `WindSpeedUnit.MPH.nextCycle()` returns
  the next enum entry, the last wraps to the first.
- `nextCycle_temperatureUnit_wrapsAround` — same.
- `nextCycle_mapStyle_wrapsAround` — same.

**`MakiIconsTest.kt` (new, plain JUnit):**
- `forName_cacheHit_doesNotReRender` — call `forName(ctx, "restaurant", true)` twice,
  assert the returned `CarIcon` is the same instance. (Caching check.)
- `forName_cacheMiss_differentIconsPerSelectedState` — call with selected=true then
  selected=false, assert the returned icons are different instances (different
  cache keys).
- `forName_unknownIconName_returnsFallbackCircle` — pass an icon name that doesn't
  exist in the maki pack, assert the returned `CarIcon` is non-null and its
  underlying bitmap is non-empty (the fallback circle path runs).

  **Note on the last test:** this requires the maki icon pack to be present in the
  test assets, OR `MakiIcons.build` to handle the missing case without
  `renderMakiIcon` throwing. Inspect `renderMakiIcon` first; if it throws on
  unknown names, we'd need to either stub it (avoid unless unavoidable) or skip
  this specific test and rely on a "non-null return" check for any input.

**`CarViewModelHolderTest.kt` (new, plain JUnit):**
- `ensureInitialized_calledTwice_returnsSameInstance` — call twice with the same
  appContext, assert the returned `MapViewModel` is the same instance (singleton
  contract).
- `ensureInitialized_concurrentCalls_returnsSameInstance` — launch N parallel
  coroutines all calling `ensureInitialized`, assert all return the same
  instance (the `@Volatile` + `synchronized` double-check is correct).

  **TDD caveat:** the singleton pattern is hard to TDD if it's already written.
  The user's "refactor for testability" answer is the right hook here: if the
  current `CarViewModelHolder` exposes too much for a clean test, refactor the
  init function to take an injectable `MapViewModel` factory. If it doesn't,
  the parallel-call test is straightforward.

### Phase 4 — Screens

The car screens (`HomeScreen`, `RouteScreen`, `SettingsScreen`, `PoiScreen`,
`WeatherScreen`, `SearchScreen`, `CarNavMapScreen`) build `Template` objects
(`ListTemplate`, `MessageTemplate`, `GridTemplate`, `NavigationTemplate`). The
androidx.car.app.testing library lets us instantiate a fake `CarContext` and
inspect the built `Template`. Tests assert the structure of the template — the
rows, toggles, action listeners, conditional items.

**Per-screen test class (one file each, all Robolectric):**

For each screen, the test:
1. Creates a fake `CarContext` via `androidx.car.app.testing.TestCarContext`.
2. Initializes a real `MapViewModel` via `CarViewModelHolder.ensureInitialized(...)` —
   or, if that's too coupled, instantiates a small test-only `MapViewModel` via the
   existing `MapViewModelFactory` with a fake `PreferencesRepository` (in-memory).
3. Builds the screen's template via `screen.onGetTemplate()`.
4. Casts the `Template` to the expected subtype and asserts on rows/items/text.
5. Triggers click listeners (e.g. via the row's `OnClickListener`) and asserts the
   VM state changed (e.g. `vm.advanceActiveWaypoint` was called, or
   `vm.togglePoiCategory` was called).

**Specific test outlines:**

`HomeScreenTest`:
- `homeScreen_buildsListTemplateWithTitle` — assert `ListTemplate.getTitle().toString()
  .contains(carContext.getString(R.string.car_home_title))`.
- `homeScreen_weatherRow_whenSnapshotPresent_showsTempAndCondition` — set
  `vm.openMeteoSnapshot` to a known value, build template, assert the first row's
  title contains the formatted temp + condition.
- `homeScreen_weatherRow_whenSnapshotAbsent_showsNoDataString` — set
  `vm.openMeteoSnapshot = null`, build, assert the first row's title matches
  `R.string.car_weather_no_data`.
- `homeScreen_activeWaypointRow_showsNameAndDistance` — set an active waypoint
  near the user, assert row title = waypoint name, row text = formatted distance.
- `homeScreen_poiSearchRow_disabledWhenPendingCameraInfoNull` —
  `vm.pendingCameraInfo = null`, assert the search row's `Row.isEnabled() == false`.
- `homeScreen_poiSearchRow_enabledWhenPendingCameraInfoSet` — opposite.
- `homeScreen_retryRow_presentWhenFailedCellsTrue` — `vm.hasFailedCells = true`,
  assert the retry row exists.
- `homeScreen_retryRow_absentWhenFailedCellsFalse` — opposite.
- `homeScreen_poiSearchRow_invokesVmSearchVisibleArea` — trigger the click
  listener (the parked-only wrapper), assert `vm.searchVisibleArea` was called
  (we'd need to set `pendingCameraInfo` to a non-null value first, then click).

`RouteScreenTest`:
- `routeScreen_emptyWaypoints_showsEmptyStringRow` — `vm.waypoints = emptyList()`,
  assert the row's title matches `R.string.car_route_empty`.
- `routeScreen_nonEmptyWaypoints_showsClearRow` — assert the clear row is present.
- `routeScreen_advanceRegressRows_onlyWhenMoreThanOneWaypoint` — `vm.waypoints = listOf(a)`,
  assert advance/regress rows absent. With 2+, assert present.
- `routeScreen_perWaypointRow_activeBadgeAppears` — set one waypoint active, assert
  that row's title starts with the active badge string.
- `routeScreen_perWaypointRow_moveUpAction_invokesVmMoveWaypoint` — click the
  per-row move-up action, assert `vm.moveWaypoint(1, 0)` was called.

`SettingsScreenTest`:
- `settingsScreen_termsNotAccepted_showsTermsRow` —
  `vm.prefsRepo.acceptedTermsVersion = PrefsDefaults.TERMS_VERSION - 1`, assert
  the terms row is present.
- `settingsScreen_metricToggle_usesVmUseMetric` — set `vm.useMetric = true`,
  assert the metric toggle is `isChecked`.
- `settingsScreen_windUnitRow_clickCycles` — trigger the click, assert
  `vm.updateWindSpeedUnit(nextUnit)` was called.
- `settingsScreen_thresholdRow_clickCyclesThroughPresets` — assert
  `vm.updateAutoAdvanceThreshold(...)` was called with the next preset.
- `settingsScreen_resetRow_pushesResetConfirmScreen` — click the reset row, assert
  the next screen on the stack is a `ResetConfirmScreen`. (This requires
  `ScreenManager.push` capture; the test framework provides a fake screen manager
  — need to verify this against the actual `androidx.car.app.testing` API.)

`PoiScreenTest`:
- `poiScreen_gridItem_belowCap_isTappable` — `vm.enabledPoiCategories.size < MAX_POI_CATEGORIES`,
  `category.query not in enabledPoiCategories`, assert the item has an
  `OnClickListener`.
- `poiScreen_gridItem_atCap_isNotTappable` — fill to MAX, assert the item has no
  `OnClickListener` and has `R.string.car_poi_max_reached` as text.
- `poiScreen_gridItem_alreadyEnabled_hasSelectedBadge` — assert the item's
  `Badge` is non-null and has a dot.
- `poiScreen_gridItem_click_invokesTogglePoiCategory` — click, assert
  `vm.togglePoiCategory(category)` was called.
- `poiScreen_title_includesStatusText` — assert the template title contains one
  of the four status strings (`R.string.car_poi_loading`, `car_poi_failed`,
  `car_poi_ready`, `car_poi_idle`, `car_poi_no_map`).

`WeatherScreenTest`:
- `weatherScreen_*` — analogous; controls the radar play/pause and weather-mode
  toggles. Asserts on the rows present and the listeners wired.

`SearchScreenTest`:
- `searchScreen_*` — search input + result rows. Asserts on the result list
  contents based on `vm.searchQuery` and `vm.searchResults`.

`CarNavMapScreenTest`:
- `carNavMapScreen_actionStrip_hasMenuPlayPauseRecenter` — assert the
  `NavigationTemplate.getActionStrip()` has 3 actions.
- `carNavMapScreen_mapActionStrip_hasPan` — assert exactly `Action.PAN`.
- `carNavMapScreen_radarIconIsPauseWhenPlaying` — `vm.weatherMode = PLAYING`,
  assert the second action's icon is `R.drawable.ic_car_pause`.
- `carNavMapScreen_radarIconIsPlayWhenPaused` — `vm.weatherMode = OFF`, assert
  play.
- `carNavMapScreen_menuAction_pushesHomeScreen` — click, assert
  `HomeScreen` is on the stack.
- `carNavMapScreen_recenterAction_invokesRenderer` — click, assert
  `renderer.recenter()` was called. (This is the trickiest test — needs the
  `CarMapRenderer` to be mockable, or its method to be testable. The current
  design creates the renderer in the constructor; we may need to inject it or
  accept a small refactor.)

**`BaseCarScreenTest` (new, Robolectric):**
- `baseCarScreen_registersRefreshListenerOnCreate` — instantiate a
  `BaseCarScreen` subclass, push it through a fake lifecycle, assert the VM
  has a registered refresh listener (call `vm.notifyRefresh()` and observe the
  listener fires `invalidate` on the screen).
- `baseCarScreen_unregistersRefreshListenerOnDestroy` — destroy the screen's
  lifecycle, assert the listener is no longer registered (i.e. `vm.notifyRefresh()`
  doesn't cause a callback).
- `baseCarScreen_refreshListener_invalidatesOnMainThread` — assert
  `invalidate` is called via `carContext.mainExecutor.execute(...)` (need a
  fake main executor that captures the lambda).

**TDD order per screen:** the screens are the most "I'll write tests after" candidate
in this whole retrofit. The user's strict-TDD stance says we still write the test
first — pin down the desired row/column structure, watch it pass (because the
screens already build that structure), no implementation change needed for most
tests. The exception is `BaseCarScreen` where the test may surface a real bug
(e.g. listener registered on a non-main thread, or not unregistered on destroy).

## Lessons learned during Phase 1-2 (carry forward into Phase 3-4)

These are infra gotchas that bit during Phase 2 and need to be remembered for the
rest of the retrofit. Future phases should expect them.

**Robolectric SDK pin: `@Config(sdk = [33])`.** Robolectric 4.16.1's
`ConnectivityManager` shadow does **not** implement
`registerDefaultNetworkCallback` on the project's `compileSdk` (36). The real
`MapViewModel.init` registers a default network callback (for the
wind-conditions polling retry), which throws `NoSuchMethodError` on SDK 36. SDK
33 is well-supported by Robolectric 4.16.1 and exercises the same widget code
paths. Every Robolectric test that constructs a `MapViewModel` must include
`@Config(sdk = [33])` on the test class.

**`isIncludeAndroidResources = true` is required in `testOptions`.** The project
ships with no `android.testOptions.unitTests` block, and AGP 9's default does
not pull the merged Android resources into the JVM unit-test classpath on its
own. Without it, tests run in `org.robolectric.default`, `getString` throws
`Resources$NotFoundException`, and `LayoutInflater.inflate` can't resolve layout
IDs. The following is required in `app/build.gradle.kts`:

```kotlin
testOptions {
    unitTests {
        isIncludeAndroidResources = true
    }
}
```

This was added in the Phase 2 commit and is required for every Phase 3-4 test
that inflates a layout or reads a string resource.

**`MapViewModel` private setters + `internal` test seam.** Many `MapViewModel`
state properties (`activeWaypointId`, `mapStyle`, `windEnabled`,
`openMeteoSnapshot`, etc.) are `var x by mutableStateOf(...) private set` with
no public setter. Some have public `update*` methods (e.g. `updateWindEnabled`,
`setActiveWaypoint`); others do not (e.g. `openMeteoSnapshot` — the production
code sets it from inside the polling loop). Tests that need to set such state
have three options, in order of preference:

1. **Use the public `update*` / `set*` method** if one exists (e.g.
   `vm.setActiveWaypoint(id)`, `vm.updateMapStyle(style)`, `vm.updateWindEnabled(on)`).
2. **Add an `internal` test-only setter** visible to the test sourceset (e.g.
   `internal fun setOpenMeteoSnapshotForTest(snap: OpenMeteoSnapshot?)`). Name
   it `*ForTest` and document it as test-only. The test sourceset shares the
   module with `main`, so `internal` works.
3. **Reframe the test** if the state is genuinely unreadable from outside
   (e.g. the `noUserPosition_hidesRoot` test was deleted in Phase 2 because
   `PreferencesRepository.lastKnownPosition` defaults to Ottawa coordinates
   rather than `null`, so the "no user position" branch is unreachable in
   production — testing it would lie about the contract).

Do not reach for reflection.

**Construct real `MapViewModel`, do not mock.** mockk is not a project
dependency. The pattern is: build a real `MapViewModel` with a real
`PreferencesRepository` from Robolectric's `SharedPreferences`, set the
required state via public methods (or the `*ForTest` seam), construct the
widget, call `update(bearing)`, and assert on the inflated views. This is the
same pattern `CarRangeRingLabelsTest` already uses.

**`MapViewModel` reads `lastKnownPosition` from `PreferencesRepository`, not
from a `null`-default.** `lastKnownPosition` returns
`Position(45.4215, -75.6972)` (Ottawa) when no value is set — those are
`PrefsDefaults.LAST_KNOWN_LAT/LON`, not zero. Tests that want "no user
position" need to either (a) set `userPositionForSearch` directly (which
takes precedence), or (b) clear the underlying prefs keys via
`prefs.prefs.edit().remove("last_known_lat").remove("last_known_lon").apply()`.

**Car widget palette constants are exposed as companion-object `@JvmField`.**
The refactor for testability moved all color literals from inline magic
numbers in `applyColors` to `companion object` constants (e.g.
`CarRouteWidget.darkBackground`, `CarRouteWidget.lightArrowTint`). Tests pin
to the constants so they reference the source of truth rather than duplicating
the literals. The widgets still re-theme via a new `setDark(dark: Boolean)`
method (mirroring the existing `CarRangeRingLabels.setDark`); the
`initialIsDark` constructor param is read once at construction.

## Critical files

- **Modified:** `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainer.kt`
  — extract 7 pure helpers to a new sibling file. Net change: small reductions in
  the hub, no new behavior.
- **New:** `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarMapContainerLogic.kt`
  — extracted pure helpers (Phase 1).
- **New tests (10 files):**
  - `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarMapContainerLogicTest.kt`
  - `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarRouteWidgetTest.kt`
  - `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidgetTest.kt`
  - `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarUiTest.kt`
  - `app/src/test/java/ca/voiditswarranty/roadtripradar/car/MakiIconsTest.kt`
  - `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarViewModelHolderTest.kt`
  - `app/src/test/java/ca/voiditswarranty/roadtripradar/car/screens/BaseCarScreenTest.kt`
  - `app/src/test/java/ca/voiditswarranty/roadtripradar/car/screens/HomeScreenTest.kt`
  - `app/src/test/java/ca/voiditswarranty/roadtripradar/car/screens/RouteScreenTest.kt`
  - `app/src/test/java/ca/voiditswarranty/roadtripradar/car/screens/SettingsScreenTest.kt`
  - `app/src/test/java/ca/voiditswarranty/roadtripradar/car/screens/PoiScreenTest.kt`
  - `app/src/test/java/ca/voiditswarranty/roadtripradar/car/screens/WeatherScreenTest.kt`
  - `app/src/test/java/ca/voiditswarranty/roadtripradar/car/screens/SearchScreenTest.kt`
  - `app/src/test/java/ca/voiditswarranty/roadtripradar/car/screens/CarNavMapScreenTest.kt`
  - Total: 14 new test files. (`WeatherScreen`/`SearchScreen`/`CarNavMapScreen` may
  need their tests scoped down if `androidx.car.app.testing` doesn't support
  every assertion we want; will discover in Phase 4.)

## Reuse

- **Test style:** `CarRangeRingLabelsTest.kt` for the Robolectric pattern;
  `CarRangeRingsTest.kt` for the plain-JUnit pattern. Copy the imports and
  Robolectric runner declaration from the former.
- **Test infra:** `androidx.car.app.testing.TestCarContext` (already on the
  classpath, version from `libs/androidx-car-app-testing` in
  `gradle/libs.versions.toml`).
- **Existing test utility for car surface:** the existing 4 car test files are the
  pattern to follow.

## Verification

1. `./gradlew :app:compileDebugKotlin` — clean compile.
2. `./gradlew :app:test` — all tests pass.
3. `./gradlew :app:test --tests ca.voiditswarranty.roadtripradar.car.*` — runs only
   the new car tests.
4. Spot-check that the new tests would catch the recent halo-theme bug if it
   regressed: `setDark_darkStyle_appliesDarkPaletteToAllLabels` (existing) +
   `setDark_lightStyle_appliesLightPaletteToAllLabels` (existing) cover it. The
   new overlay tests add similar dark/light palette coverage for `CarRouteWidget`
   and `CarWeatherWidget`, which would catch a future theme-regression in those
   widgets.
5. No manual DHU changes required — this spec is purely about adding test
   coverage. The existing manual DHU verification flow for new features remains
   unchanged.

## Out of scope (per scope decision)

- `CarMapRenderer` (real VirtualDisplay + `SurfaceCallback`) — needs DHU.
- `RoadTripRadarSession` (lifecycle + `onSurfaceActive` counting) — needs
  `SessionInfo` plumbing.
- `RoadTripRadarCarAppService` — thin one-liner.
- Integration / instrumented tests.

## Order of implementation (risk-first)

1. **Phase 1** (hub pure helpers + tests) — single biggest blast radius. This
   is also where the recent halo-theme bug was rooted. Start here.
2. **Phase 2** (overlay widget tests) — Robolectric-only, no MapLibre, no car
   templates. Validates the testing pattern in isolation.
3. **Phase 3** (helper tests) — smallest, lowest risk. Good for warming up the
   test patterns before Phase 4.
4. **Phase 4** (screen tests) — most file volume, smallest per-file risk.
   `BaseCarScreenTest` first, then simple list screens, then the
   `NavigationTemplate`-based root screen.
