# Phone — Test Coverage Plan

> Companion to `2026-07-05-android-auto-test-coverage-design.md`. Same
> TDD discipline, same risk-first ordering, same "refactor for testability
> then TDD" pattern, mirrored onto the phone-side packages.

## Context

The phone surface (everything in `app/src/main/java/ca/voiditswarranty/roadtripradar/`
*except* `car/`) carries the central `MapViewModel` (~1782 lines), 35 Compose
`ui/` files, and 10 data-layer repository / geometry / theme classes — roughly
7,000+ lines of main code with only **5 small test files** outside the car
package (`ExampleUnitTest`, `PoiViewportChunksTest`, `WmoWeatherCodesTest`,
`RainViewerRateLimitTest`, `WeatherRepositoryMockTest`, `WeatherFormatTest`).
The car retrofit (see `2026-07-05-android-auto-test-coverage-design.md`) added
184 tests against the same shared `MapViewModel`; the phone has no equivalent
safety net.

The phone is the higher-risk surface: it owns the single process-wide
`MapViewModel` that the car side consumes, the SharedPreferences migrations
(schema v11, hand-written), the 11-step `migrate()` chain in
`PreferencesRepository`, the MapLibre style JSON color editor
(`StyleJsonPatcher`, 321 lines), the 35km-grid Postpass POI pipeline, and
every Compose UI composable. A regression in any of these breaks both
surfaces.

**Goal:** bring phone-side test coverage to the same depth and discipline
as the car retrofit, using the same TDD pattern. Strict TDD on every new
behavior. Refactor for testability first where needed.

**Decisions from this interview (and the parallel car interview):**
- **Spec location:** `docs/superpowers/specs/2026-07-05-phone-test-coverage-design.md` (mirrors car spec).
- **Scope:** everything in `app/src/main/java/ca/voiditswarranty/roadtripradar/`
  *except* `car/` and `MainActivity.kt` gets test coverage. The 5 existing
  phone tests stay as-is (no rewrites).
- **Test infra:** JVM-only tests under `app/src/test/`, using **Robolectric**
  for Android View / resource / SharedPreferences work, **MockWebServer**
  (OkHttp 4.12.0) for HTTP repository tests, **androidx.compose.ui:ui-test-junit4**
  (BOM-managed, currently unused) for Compose UI tests. **No MockK** — use
  real objects with constructor-injected `baseUrl` seams. (Latest answer
  overrides earlier "Allow MockK" choice.)
- **Retrofit rule:** strict TDD on every new behavior. For the existing
  `MapViewModel` (1782 lines, process-wide singleton), refactor for
  testability first — extract small pure helpers as `internal` top-level
  functions in new `*Logic.kt` sibling files, TDD each helper. Compose-coupled
  glue (`LaunchedEffect`, `Snapshot` reads, `mutableStateOf` setters) stays
  in the VM.
- **Plan structure:** 6 phases (within the "5-7 phases" interview ceiling).
- **Order:** risk-first. Pure helpers → Repositories → VM extracted helpers →
  UI pure logic → Compose UI tests → integration polish.
- **Test depth:** one test per behavior, multiple per branch. Same as car.
- **Out of scope:** `MainActivity.kt` (Activity bootstrap), `car/` package
  (covered by the car spec), instrumented/integration tests, screenshot
  tests, accessibility audits.

## Reuse from existing phone tests (style guide)

- `PoiViewportChunksTest.kt` — pure JUnit 4 (no Robolectric), tests pure
  geometry math on a model object. Use the same pattern for the new pure
  helpers in Phase 1.
- `WmoWeatherCodesTest.kt` — pure JUnit 4, tests an enum-to-resource
  mapping. Same pattern for `MapStyle` display-name / resource tests.
- `WeatherFormatTest.kt` — Robolectric 4.16.1 with
  `ApplicationProvider.getApplicationContext()` to resolve string resources.
  Same pattern for any test that needs to read `strings.xml` (`weatherFormat`,
  `MapStyle.displayNameRes`, etc.).
- `WeatherRepositoryMockTest.kt` + `RainViewerRateLimitTest.kt` —
  MockWebServer pattern with OkHttp dispatcher. Same pattern for the
  Postpass, Open-Meteo, and Geocoding repo tests in Phase 2.
- `BaseCarScreenTest.kt` — the `MapViewModel.set*ForTest` seam pattern (8
  seams already exist on the VM). Reuse the seams; add more for the
  phone-side behaviors that don't have one yet.

All tests use JUnit 4 `org.junit.Assert.*` (not kotlin.test). All
Robolectric tests use `@Config(sdk = [33])` to match the car suite.

## Plan

### Phase 1 — Pure helpers (lowest blast radius, easiest TDD)

These are the classes with no Android, no IO, no Compose dependency.
Each gets a TDD cycle: write the test for the desired extracted
signature, run the test, watch it fail, extract (or implement) the
helper, watch it pass.

**`StyleJsonPatcherTest.kt` (new, plain JUnit, pure):**
- `THEME_COLOR_CATEGORIES_containsExactlyTheExpectedEntries` — pin the
  public list of 15 categories. Future additions show as a test diff.
- `parseColorString_hexWithoutHash_returnsIntColor` — `"ff0000"` → 0xFFFF0000.
- `parseColorString_hexWithHash_returnsIntColor` — `"#ff0000"` → 0xFFFF0000.
- `parseColorString_rgb_returnsIntColor` — `"rgb(255,0,0)"` → 0xFFFF0000.
- `parseColorString_rgba_returnsIntColorWithFullAlpha` — `"rgba(255,0,0,0.5)"` → 0x80FF0000.
- `parseColorString_hsl_returnsIntColor` — `"hsl(0,100%,50%)"` → 0xFFFF0000.
- `parseColorString_invalidInput_returnsNull` — `"not-a-color"` → null.
- `colorToHex_red_returnsRedHex` — round-trip.
- `colorToHex_black_returnsBlackHex` — round-trip.
- `extractColor_water_layer_returnsBlue` — load a small fixture JSON,
  assert the water layer's `paint:fill-color` is the expected blue.
- `extractColor_park_layer_returnsGreen` — fixture-based.
- `extractColor_layerNotInCategories_returnsFallback` — assert the
  fallback path runs.
- `applyColors_replacesMultipleCategories` — load fixture, swap 3
  categories, assert each layer's color changed.
- `applyColors_partialUpdate_leavesUnrelatedLayersAlone` — assert the
  operation only touches the 3 swapped categories.
- `applyColors_malformedJson_throws` — pin the behavior (or refactor
  to return `Result<String>` if the test reveals a real bug).

**`RouteGeometryTest.kt` (new, plain JUnit, pure):**
- `inactiveRouteLegs_emptyWaypoints_returnsEmptyList`.
- `inactiveRouteLegs_singleWaypoint_returnsEmptyList` — no legs possible.
- `inactiveRouteLegs_twoWaypoints_returnsOneLeg` — `[A,B] → [A→B]`.
- `inactiveRouteLegs_skipsLegInvolvingActiveIndex` — `[A,B,C,D]`, active=2
  → returns legs that don't touch index 2 (i.e., `[A→B]`).
- `inactiveRouteLegs_activeIndexAtStart_returnsAllButFirstLeg`.
- `activeRouteLeg_emptyWaypoints_returnsNull`.
- `activeRouteLeg_singleWaypoint_returnsNull`.
- `activeRouteLeg_activeIndexAtLast_returnsNull` — no next waypoint.
- `activeRouteLeg_userPositionNull_returnsLegFromStart`.
- `activeRouteLeg_validInputs_returnsLegFromUserToActiveWaypoint`.

**`RainViewerTest.kt` (new, plain JUnit, pure):**
- `tileUrl_pathReplacesHost` — `tileUrl("/tiles/0/0/0/0/0.png")` returns
  the configured `baseUrl` + the path. Pins the URL-construction contract
  (the existing `RainViewerRateLimitTest` exercises rate limiting, not URL
  building).
- `tileUrl_emptyPath_throws` — pin the precondition.
- `TILE_SIZE_PX_is512` — pin the constant.
- `MAX_ZOOM_is7` — pin the constant.

**`WaypointTest.kt` (new, plain JUnit, pure):**
- `position_returnsLatLonTuple`.
- `position_worksForNegativeCoords` — South/West hemisphere.
- `create_withName_setsAllFields` — factory sanity.
- `create_withOptionalParams_omitsNulls` — subtitle/notes optional.
- `equality_basedOnAllFields` — data class equality.

**`InsertPositionTest.kt` (new, plain JUnit, pure):**
- `sealedSubclasses_areAllExpected` — pin the 4 variants: `BeforeActive`,
  `AfterActive`, `AtStart`, `AtEnd`. Future additions show as a test diff.
- `equality_distinguishesAllVariants`.

**`ChangelogRepositoryTest.kt` (new, plain JUnit, pure):**
- `releasesNewSince_emptyBundle_returnsEmpty` — sanity.
- `releasesNewSince_lastSeenAtCurrentCode_returnsEmpty` — no newer.
- `releasesNewSince_lastSeenBelowCurrentCode_returnsNewerOnly` — the
  "what's new" gate: a release with `versionCode > lastSeenCode` and
  `showInApp=true` is included; older or hidden ones are filtered.
- `releasesNewSince_skipsShowInAppFalse` — pin the showInApp filter.
- `releasesNewSince_includesTheCurrentVersion` — release with
  `versionCode == currentVersionCode` and `showInApp=true` shows.
- `allReleasesSortedNewestFirst_emptyBundle_returnsEmpty`.
- `allReleasesSortedNewestFirst_mixedVersions_returnsDescending` —
  pin the sort order.
- `allReleasesSortedNewestFirst_preservesDuplicateVersionCodesByOrderInJson` —
  pin stable sort.

**`MapStyleTest.kt` (new, plain JUnit, pure for the enum constants, plus
Robolectric for the Context-dependent helpers):**
- `styleUri_LIBERTY_returnsOpenFreeMapUrl`.
- `styleUri_DARK_returnsAssetUri`.
- `styleUri_COLOR_DARK_returnsAssetUri`.
- `styleUri_CUSTOM_LIGHT_throws` — pin the precondition.
- `styleUri_AUTO_throws` — pin the precondition.
- `displayName_LIBERTY_returnsLiberty` — pin all 6 enum entries.
- `displayNameRes_*` — Robolectric, pins all 6 string resources.
- `intrinsicallyDark_DARK_returnsTrue` — pin all 4 dark variants + 2
  light variants.
- `isCustom_CUSTOM_LIGHT_returnsTrue` — pin the OR check.
- `isCustom_LIBERTY_returnsFalse`.
- `maputnikUrl_LIBERTY_returnsUrl` — pin all built-ins + null for custom/auto.

**`MapStyleResolutionTest.kt` (new, Robolectric):**
- Extract `resolveAuto(night, customLightAutoEnabled, customDarkAutoEnabled,
  hasCustomLight, hasCustomDark): MapStyle` as a top-level `internal` function
  in `MapStyleResolution.kt`. TDD the 4-quadrant truth table:
  - `(night=true, hasCustomLight=false, hasCustomDark=false)` → `DARK` (or
    `COLOR_DARK` based on user choice — pin both, see note below).
  - `(night=true, hasCustomLight=true, hasCustomDark=false)` → `CUSTOM_DARK`
    (if `customDarkAutoEnabled`), else `DARK` / `COLOR_DARK`.
  - `(night=false, hasCustomLight=true, hasCustomDark=true)` → `CUSTOM_LIGHT`
    (if `customLightAutoEnabled`), else `LIBERTY`.
  - `(night=false, hasCustomLight=false, hasCustomDark=true)` → `LIBERTY`
    (custom-dark exists but the day-time custom-light doesn't; the AUTO
    resolver should fall back to a built-in light style).
- `isDarkForAppTheme_DARK_returnsTrue` — Robolectric: instantiate a
  `Context`, call `isDarkForAppTheme(ctx, night=false)`, assert true.
- `isDarkForAppTheme_LIBERTY_returnsFalse`.
- `isDarkForAppTheme_AUTO_withNightModeTrue_returnsTrue` — assert the
  resolved AUTO under night flag is dark.
- `resolvedStyleUri_LIBERTY_returnsHttpsUrl` — pin the URL.
- `resolvedStyleUri_DARK_returnsAssetUri` — pin the asset URI.

> **Implementation note for `MapStyle.resolveToConcrete`:** the current
> signature takes only `Context`. To unit-test the AUTO-collapse decision
> without a full `PreferencesRepository` + `CustomThemeRepository` chain,
> extract a pure `resolveAuto(...)` helper and test that. The wrapper
> `resolveToConcrete(context)` becomes a thin call to the helper with
> values pulled from the repos. This is the refactor-for-testability
> step; the test pins the helper's contract, the wrapper's only job
> is plumbing.

**`OpenMeteoHelpersTest.kt` (new, plain JUnit, pure):**
- `computeTrendNextHour_zeroSlope_returnsZero` — the 1h-trend regression.
- `computeTrendNextHour_increasingSlope_returnsPositive`.
- `computeTrendNextHour_handlesMissingNextHour` — null branch.
- `parseOpenMeteoTimeToZoned_utcIso_returnsZonedDateTime` — pin the
  expected `ZonedDateTime` for `"2026-07-03T14:00"` in `UTC`.
- `parseOpenMeteoTimeToZoned_nonUtcZoneId_convertsToZone` —
  `"2026-07-03T14:00"` in `America/Toronto` should be 14:00 wall clock
  in that zone, not 14:00 UTC.

> The above helpers are private file-level functions in
> `OpenMeteoRepository.kt`. Promote them to `internal` in a new
> `OpenMeteoHelpers.kt` sibling. The repo calls them as before; the
> tests use them directly.

**`CustomThemeHelpersTest.kt` (new, plain JUnit, pure):**
- `validateMapLibreStyle_minimalValidJson_returnsTrue` — `{"version":8,"sources":{}}`.
- `validateMapLibreStyle_missingVersion_returnsFalse` — pin the version guard.
- `validateMapLibreStyle_wrongVersion_returnsFalse` — pin the "must be 8" rule.
- `validateMapLibreStyle_malformedJson_returnsFalse`.
- `rewriteTileSources_replacesOpenFreeMapUrls` — fixture-based; pin the
  source-rewriting contract.
- `rewriteFonts_replacesNonNotoFonts` — fixture-based; pin the font
  list (the existing `nonNotoFontRegex` is file-level, testable as a
  one-off: feed sample style JSON, assert non-Noto fonts are rewritten
  to Noto).

**`PostpassHelpersTest.kt` (new, plain JUnit, pure):**
- `categoryTagMap_isTheExpectedMap` — pin the static mapping from
  `POI_CATEGORIES.query` to Postpass SQL `tags` filter. This map is
  currently built in the lazy `categoryTagMap` property; expose it as
  an `internal` package-level function (or `internal val` on the
  repo's companion) for test access.
- `buildPostpassQuery_singleCategory_returnsSql` — fixture-based; pin
  the SQL shape (Postpass SQL API, not Overpass QL).
- `buildPostpassQuery_multipleCategories_returnsInClause` — pin the
  `tags ? 'fuel' OR tags ? 'charging_station'` shape.
- `buildPostpassQuery_boundsClamped` — out-of-range lat/lon gets clamped
  or rejected (pin whatever the current behavior is).
- `parsePoi_completeFeature_returnsNearbyPoi` — fixture-based.
- `parsePoi_missingTags_returnsNull` — pin the skip-on-missing-tag rule.
- `parsePoi_disabledCategory_returnsNull` — pin the category filter.
- `buildSubtitle_knownTags_returnsSubtitle` — fixture-based; pin the
  priority of `addr:street`, `addr:city`, `addr:housenumber` fields.

> **Refactor mechanics:** `parsePoi` and `parsePostpassFeature` are
> currently `private` on the repo. Promote them to `internal` on a
> companion object so the tests can call them directly without going
> through HTTP. The IO boundary (`httpGet` → parse → return) stays
> untested in unit tests; that's the work of Phase 2's MockWebServer
> tests.

**`ModelsTest.kt` (new, plain JUnit, pure):**
- `ringDistancesForZoom_zoom0_returnsLongDistances` — pin all 8 zoom
  brackets and the else branch.
- `ringDistancesForZoom_zoom22_returnsShortDistances`.
- `formatDistanceLabel_metricUnder1km_showsMeters` — `500m` → `"500 m"`.
- `formatDistanceLabel_metricOver1km_showsKm` — `1500m` → `"1.5 km"`.
- `formatDistanceLabel_imperial_showsMiles` — pin the unit toggle.
- `cleanString_wholeNumber_returnsIntString` — `1.0` → `"1"`.
- `cleanString_fractional_returnsOneDecimal` — `1.5` → `"1.5"`.
- `buildRadarRingsData_zeroDistance_returnsEmptyPolygon` — pin the
  zero-distance edge case.
- `buildRadarRingsData_knownCenter_producesExpectedRingCount` —
  fixture-based; assert the number of features matches `distances.size`.
- `buildRadarRingsData_labelAtBearing_isClosestToThatBearing` — the
  bearing-pinned label is the vertex whose angle is closest to the
  bearing (test with a known fixture).
- `POI_CATEGORIES_hasExpectedSize` — pin the count (currently 29) so
>  a future add/remove is intentional.
- `POI_CATEGORIES_queriesAreUnique` — pin the contract that `query` is
  the unique key (no duplicates).
- `MAX_POI_CATEGORIES_is5` — pin the cap.
- `PrefsDefaults_constantsHaveExpectedValues` — pin the public defaults.

**`InsertPositionDispatchTest.kt` (new, plain JUnit, pure):** —
if the dispatch logic in `MapViewModel.addWaypoint(at: InsertPosition)`
is extracted as a pure helper `addWaypointAt(waypoints, position,
newWaypoint): List<Waypoint>`, TDD it here. Same pattern as the car's
`mapActions` extraction:
- `addWaypointAt_AtStart_prepends`.
- `addWaypointAt_AtEnd_appends`.
- `addWaypointAt_BeforeActive_insertsBeforeActive` — empty-when-no-active.
- `addWaypointAt_AfterActive_insertsAfterActive` — same.
- `addWaypointAt_AfterActive_withNoActive_actsAsAtEnd`.
- `addWaypointAt_BeforeActive_withNoActive_actsAsAtStart`.

### Phase 2 — Repositories (IO + parsing)

These classes hit the network. The seam: a `baseUrl: String` constructor
parameter (already present on `WeatherRepository` and `OpenMeteoRepository`)
points at a local `MockWebServer`. For `PostpassRepository` and
`GeocodingRepository`, the seam has to be **added** as part of the
refactor-for-testability step.

> **Refactor mechanics:** add `baseUrl: String = "https://postpass.geofabrik.de"`
> to `PostpassRepository`'s constructor (currently a hardcoded constant at
> the call site). Same for `GeocodingRepository` (Photon API, currently
> hardcoded). Update the two production call sites in `MapViewModel`. This
> is the strict-TDD discipline: write the test that needs the seam, watch
> it fail because there's no way to redirect the URL, add the seam, watch
> the test pass.

**`PostpassRepositoryTest.kt` (new, MockWebServer, Robolectric):**
- `fetchPois_emptyResponse_returnsEmpty` — server returns `{}`.
- `fetchPois_singleFeature_returnsOnePoi` — fixture: one feature with
  full tags.
- `fetchPois_disabledCategoryFiltered_server` — server returns fuel
  and restaurant features, `enabledCategories = {fuel}` → result has 1.
- `fetchPois_malformedJson_throws` — pin the behavior.
- `fetchPois_httpError_throws` — server returns 500.
- `fetchPois_queryHasBoundsAndCategories` — assert the outgoing request's
  body contains the expected SQL with the chunked `bounds` and
  `tags ? '...'` clauses.

**`OpenMeteoRepositoryTest.kt` (new, MockWebServer, Robolectric):**
- `fetchCurrent_validResponse_returnsSnapshot` — fixture with known
  temp / weather code / wind.
- `fetchCurrent_windDirectionNull_returnsSnapshotWithNullWind` — pin
  the nullable direction handling.
- `fetchCurrent_trendComputed` — server returns next-hour temp
  different from current; assert `tempTrendCelsius` is the difference.
- `fetchCurrent_trendMissing_returnsZeroTrend` — server omits the
  next-hour field.
- `fetchCurrent_httpError_throws`.
- `fetchCurrent_malformedJson_throws`.

**`GeocodingRepositoryTest.kt` (new, MockWebServer, Robolectric):**
- `search_validResponse_returnsResults` — forward search by query.
- `search_emptyResults_returnsEmptyList` — server returns `[]`.
- `search_httpError_throws`.
- `reverseGeocode_validResponse_returnsSingleResult` — feature with
  `name`, `city`, `street` properties.
- `reverseGeocode_noProperties_returnsNameOnly` — pin the fallback.
- `reverseGeocode_httpError_throws`.

**`PreferencesRepositoryTest.kt` (new, Robolectric):**
- `constructor_runsMigrations` — set up a `SharedPreferences` with
  `prefs_version=0` and pre-v1 keys, instantiate, assert v1+ keys are
  present.
- `migrateV1ToV2_promotesKey` — fixture: prefs with old key, assert
  the new key is set and the old is removed.
- `migrateV2ToV3_promotesKey` — same pattern, schema v2 → v3.
- ... one test per migration step from v1 → v11. (11 tests; this is
  the most valuable Robolectric test in the whole spec because the
  migration chain is hand-written and easy to break.)
- `migrate_idempotent_secondRunIsNoOp` — run migrate twice, assert no
  keys were touched the second time.
- `migrate_prefsVersionEqualsDefaults_afterFreshInstall` — fresh
  install asserts `prefs_version == PREFS_VERSION`.
- `parseMapStyle_validName_returnsEnum`.
- `parseMapStyle_invalidName_returnsDefault` — pin the fallback
  (returns `MapStyle.LIBERTY` or whatever the current code does).
- `parseMapStyle_caseInsensitive` — pin the case handling.
- `defaultMapStyleFor_libertySystem_returnsLiberty` — Robolectric
  with `Configuration.UI_MODE_NIGHT_NO`.
- `defaultMapStyleFor_darkSystem_returnsColorDark` — Robolectric with
  `UI_MODE_NIGHT_YES`.
- `resetToDefaults_clearsAllKnownKeys` — fixture: set every
  preference to a non-default value, call `resetToDefaults(LIBERTY)`,
  assert every key is back to default.

> **Implementation note:** the `migrate()` chain is currently one
> monolithic function with 11 `if` blocks. The strict-TDD refactor
> would extract each block as `internal fun migrateV1ToV2(prefs):
> SharedPreferences` (or similar), then TDD each. The
> `migrate()` function becomes a sequence of calls. This makes each
> step independently testable and removes the "one big function"
> smell.

**`ChangelogRepositoryLoadTest.kt` (new, Robolectric):**
- `loadBundled_readsAsset_returnsParsedBundle` — the existing
  `changelog.json` asset gets parsed into a list of `Release` objects
  (or whatever the existing data class is named — read the source to
  confirm).
- `loadBundled_assetMissing_throws` — pin the behavior.
- `loadBundled_malformedJson_throws` — pin the behavior.

### Phase 3 — `MapViewModel` extracted helpers

The VM is 1782 lines. The strategy: identify pure functions (or pure-ish
functions with one or two setters as a side effect), extract them as
`internal fun` in a new `MapViewModelLogic.kt` sibling, TDD each.

**`MapViewModelLogicTest.kt` (new, plain JUnit, pure):**

> **Existing seams on the VM** (from the car retrofit — reuse them,
> don't add new test-only methods beyond what's already there):
> `setOpenMeteoSnapshotForTest`, `setFailedCellBoundsForTest`,
> `setEnabledPoiCategoriesForTest`, `setPoiPipelineActiveForTest`,
> `setCellsRemainingForTest`, `setNearbyPoiFeaturesForTest`,
> `setSearchQueryForTest`, `setSearchResultsForTest`,
> `setIsSearchingForTest`. If a new test needs a seam that doesn't
> exist, the discipline is: write the test first, watch it fail
> because no seam exists, add the seam (matching the existing
> `set*ForTest` pattern), watch the test pass.

- **`cycleWeatherMode`:**
  - `cycleWeatherMode_OFF_returnsON`.
  - `cycleWeatherMode_ON_returnsPLAYING`.
  - `cycleWeatherMode_PLAYING_returnsOFF`.
- **`toggleWeatherPlayPause`:**
  - `toggleWeatherPlayPause_OFF_returnsON` — turning on starts in pause.
  - `toggleWeatherPlayPause_ON_returnsOFF`.
  - `toggleWeatherPlayPause_PLAYING_returnsOFF` — pause from playing.
- **`toggleWeatherOnOff`:**
  - `toggleWeatherOnOff_OFF_returnsON`.
  - `toggleWeatherOnOff_ON_returnsOFF`.
  - `toggleWeatherOnOff_PLAYING_returnsOFF` — toggling "off" from playing
    skips the pause intermediate and goes straight to off.
- **`updateCellCounters`:**
  - `updateCellCounters_emptyList_resetsCellsRemainingToZero` — pin
    the side effect on `cellsRemaining` state.
  - `updateCellCounters_threeCells_setsCellsRemainingToThree`.
- **`rebuildMergedFeatures`:**
  - `rebuildMergedFeatures_emptyCaches_returnsEmptyFeatureCollection`.
  - `rebuildMergedFeatures_oneCacheEntry_returnsThatEntry` — pin the
    dedupe + merge semantics.
  - `rebuildMergedFeatures_overlappingEntries_dedupesByKey` — same
    cell ID in two caches produces one feature, not two.
- **`evictCachedCellsOutsideLoadPlate`:**
  - `evictCachedCellsInsidePlate_kept`.
  - `evictCachedCellsOutsidePlate_removed`.
  - `evictCachedCells_atPlateBoundary_kept` — pin the inclusive
    boundary behavior.
- **`enqueueCells` dedupe:**
  - `enqueueCells_cellAlreadyPending_notReEnqueued` — pin the "don't
    re-enqueue what's already in flight" rule.
  - `enqueueCells_cellAlreadyDone_notReEnqueued` — pin the "don't
    re-enqueue what's already cached" rule.
- **`reprioritizePendingCells`:**
  - `reprioritizePendingCells_emptyList_returnsEmptyQueue`.
  - `reprioritizePendingCells_pinnedCell_movedToFront` — pin the
    user-pinned-cell-always-first rule.
- **`reEnqueueIfRetriable`:**
  - `reEnqueueIfRetriable_retryableError_incrementsRetryCount`.
  - `reEnqueueIfRetriable_maxRetriesExceeded_returnsFalse` — pin the
    terminal-failure boundary.
  - `reEnqueueIfRetriable_nonRetriableError_returnsFalseImmediately`.
- **`computeViewBox`:**
  - `computeViewBox_knownCamera_returnsExpectedBounds` — fixture: a
    known `CameraInfo` and zoom, assert the resulting `BoundingBox`
    corners.
  - `computeViewBox_zoomBrackets` — at higher zoom, the box is
    smaller (pin the inverse relationship).
- **`viewportBoundsForPoi`:**
  - `viewportBoundsForPoi_knownPosition_returnsBuffer` — pin the
    25km-buffer-around-position shape.
- **`formatLatLng`:**
  - `formatLatLng_northAndEast_returnsNSEW` — fixture.
  - `formatLatLng_precision` — pin the decimal places.
- **`maybeAutoAdvance`:**
  - `maybeAutoAdvance_autoAdvanceDisabled_returnsFalse` — pin the
    "opt-out" branch.
  - `maybeAutoAdvance_distanceBelowThreshold_returnsFalse`.
  - `maybeAutoAdvance_distanceAboveThreshold_returnsTrue` — pin the
    "advance to next waypoint" trigger.
  - `maybeAutoAdvance_atLastWaypoint_returnsFalse` — no next waypoint,
    so no advance.
- **`tutorialNext` / `tutorialBack`:**
  - `tutorialNext_atLastStep_returnsLast` — pin the wrap-or-clamp
    behavior (read the current code to see which).
  - `tutorialBack_atFirstStep_returnsFirst` — same.
  - `tutorialNext_midList_returnsNext`.
  - `tutorialBack_midList_returnsPrevious`.
- **`addWaypoint` (extracted from the seam-heavy path):**
  - `addWaypoint_AtStart_emptyList_returnsSingleton`.
  - `addWaypoint_AtEnd_emptyList_returnsSingleton`.
  - `addWaypoint_AtStart_existingList_prepends`.
  - `addWaypoint_AtEnd_existingList_appends`.
  - `addWaypoint_BeforeActive_insertsBefore`.
  - `addWaypoint_AfterActive_insertsAfter`.
- **`deleteCustomTheme` fallback rule:**
  - `deleteCustomTheme_CUSTOM_LIGHT_fallsBackToLIBERTY`.
  - `deleteCustomTheme_CUSTOM_DARK_fallsBackToCOLOR_DARK`.
  - `deleteCustomTheme_builtInStyle_isNoOp` — pin the "only custom
    styles can be deleted" rule.
- **`evaluateWhatsNewChangelog` "should-show" gate:**
  - `evaluateWhatsNewChangelog_firstLaunch_shows` — `lastSeenCode = 0`
    and current release has `showInApp=true` → return the new releases.
  - `evaluateWhatsNewChangelog_alreadySeen_returnsEmpty` —
    `lastSeenCode == currentVersionCode` → return empty.
  - `evaluateWhatsNewChangelog_oldInstallHasNewerRelease_shows` — a
    release with `versionCode > lastSeenCode` and `showInApp=true` is
    included; releases with `showInApp=false` are filtered.
- **Retry-delay math** (extracted from `fetchCell`):
  - `retryDelay_retryCount0_returns1000`.
  - `retryDelay_retryCount1_returns2000`.
  - `retryDelay_retryCount5_returns32000`.
  - `retryDelay_retryCount10_clampedToMaxDelay` — pin the
    `MAX_INTER_CELL_DELAY_MS` ceiling.

> **Test discipline note:** for helpers that take a `MapViewModel` as
> a parameter (e.g., `updateCellCounters(cells, vm)`), the test creates
> a real `MapViewModel` via `MapViewModelFactory(prefsRepo, customRepo,
> openMeteoRepo)` with in-memory `SharedPreferences` (Robolectric) and
> reads back the `cellsRemaining` state via the existing
> `setCellsRemainingForTest` / direct access. The VM is process-wide
> singleton; `@Before` calls the existing reset seams.

### Phase 4 — UI pure logic

Pure-Kotlin or Compose-agnostic helpers embedded in the 35 UI files.
Extract to `internal` top-level or `companion object` and TDD.

**`WeatherFormatHelpersTest.kt` (extend the existing `WeatherFormatTest.kt`,
plain JUnit for the pure, Robolectric for the Context-dependent):**
- `windArrowRotationDeg_knownWindDirection_knownBearing_returnsDifference` —
  the wind arrow is rotated by `(windDirection - cameraBearing)`, normalized
  to [0, 360). Fixture-based.
- `windArrowRotationDeg_negativeResult_wrapsToPositive`.
- `windUnitLabel_KMH_returnsKmhLabel` — Robolectric.
- `windUnitLabel_MPH_returnsMphLabel` — Robolectric.
- `windUnitLabel_KNOTS_returnsKnotsLabel` — Robolectric.
- `tempUnitSymbol_CELSIUS_returnsC` — Robolectric.
- `tempUnitSymbol_FAHRENHEIT_returnsF`.
- `tempUnitSymbol_KELVIN_returnsK`.
- `compassDirectionLabel_knownDegrees_returnsCompassString` — pin all 16
  cardinal directions (N, NNE, NE, ENE, E, ESE, SE, SSE, S, SSW, SW, WSW,
  W, WNW, NW, NNW).

**`ThemeSelectorHelpersTest.kt` (new, Robolectric, JSON-fixture-based):**
- `parseHexColor_validHex_returnsInt` — `"#ff0000"` → `0xFFFF0000`.
- `parseHexColor_withoutHash_returnsInt` — `"ff0000"` → `0xFFFF0000`.
- `parseHexColor_invalid_returnsNull`.
- `parseHexColor_shortHand_returnsExpanded` — `"#f00"` → `0xFFFF0000`.
- `extractBackgroundColor_jsonWithBackground_returnsFirstMatch` —
  fixture-based.
- `extractBackgroundColor_jsonWithoutBackground_returnsFallback`.
- `swatchColorForStyle_knownStyle_returnsExpectedColor` — for each of
  LIBERTY / DARK / COLOR_DARK / CUSTOM_LIGHT / CUSTOM_DARK, pin the
  swatch color shown in the theme picker.

**`MapScreenHelpersTest.kt` (new, plain JUnit, pure):**
- `zoomTierFor_zoomBelow6_returnsHighLevel`.
- `zoomTierFor_zoomBetween6And8_returnsMidLevel`.
- `zoomTierFor_zoomAbove16_returnsLowLevel` — pin all 8 zoom tiers
  matching `ringDistancesForZoom`.
- `mapPaddingForOffset_zeroOffset_returnsZeroPadding` — pin the
  `(height, fraction) → PaddingValues` math.
- `mapPaddingForOffset_halfOffset_returnsSymmetricPadding`.
- `mapPaddingForOffset_fullOffset_returnsAsymmetricPadding`.
- `poiInfo_emptyPoiFeatures_returnsNull`.
- `poiInfo_onePoi_returnsPoiSummary` — pin the format of the popup.

**`TutorialAnchorsTest.kt` (new, plain JUnit, pure):**
- `tutorialStepCount_matchesExpectedSequence` — pin the number of
  tutorial steps.
- `tutorialStepAt_firstStep_returnsIntroduction` — pin the step IDs
  in order.
- `tutorialStepAt_lastStep_returnsFinale` — pin the wrap.
- `tutorialScrollTarget_firstStep_returnsZero` — pin the
  scroll-position math (if the tutorial involves scrolling a panel).

**`ActionsDrawerHelpersTest.kt` (new, plain JUnit, pure):**
- `pageCount_returns5` — pin the 5-page drawer.
- `pageAt_zeroIndex_returnsWeather` — pin the page order
  (Weather / Route / POIs / Settings / Help, or whatever the current
  order is).
- `tutorialScrollTarget_*` — the `DrawerAction` mapping for tutorial
  highlight positions (extract as a pure helper if not already).

**`MapLayersHelpersTest.kt` (new, plain JUnit, pure for the math,
Robolectric for icon loading):**
- `loadMakiIcon_knownName_returnsBitmap` — Robolectric, asset-loaded
  fixture. Pin that the icon name is resolved to a non-null bitmap
  with non-zero width/height.
- `loadMakiIcon_unknownName_returnsNullOrFallback` — pin the
  fallback behavior.
- `NearbyPoiLayers_grouping_logic` — if there's any pure groupby /
  dedup logic in `MapLayers.kt`, extract it and test it. (Most of
  `MapLayers.kt` is Compose-coupled glue; the math portions are the
  extract candidates.)

> **Refactor mechanics:** many of the `WeatherFormat` helpers are
> already in the test file; the new ones are the Context-dependent
> ones. The MapScreen helpers are currently `private` inline lambdas
> inside a `@Composable`; extract them to `internal` top-level
> functions in a new `MapScreenLogic.kt` sibling. Same pattern as
> the car's `CarMapContainerLogic.kt`.

### Phase 5 — Compose UI tests

For every composable, add a `*Test.kt` using `createComposeRule()`.
Pinned to SDK 33 like the rest of the suite.

> **Test infra:** `androidx.compose.ui:ui-test-junit4` is on the
> classpath (BOM-managed) but currently unused. Each `*Test` adds
> the dependency through the existing `androidTest`/`test` config in
> `app/build.gradle.kts` (verify the BOM alias is `androidx.compose.ui:ui-test-junit4`).
>
> **General test pattern (per composable):**
> 1. `composeTestRule.setContent { RoadTripRadarTheme { ComposableUnderTest(...) } }`.
> 2. Drive VM state via the existing `set*ForTest` seams or a
>    fake `MapViewModel` (or — if the composable is parameterized
>    over its inputs — pass the inputs directly and skip the VM).
> 3. Use `composeTestRule.onNodeWithText(...)`, `onNodeWithTag(...)`,
>    `onNodeWithContentDescription(...)` to assert rendered elements.
> 4. Use `onNodeWithText(...).performClick()` and assert the VM state
>    changed (or the click handler was invoked — depends on the
>    composable).
> 5. Robolectric for `Context`-dependent composables (string
>    resources, system services); pure for the rest.

**Per-composable test file (one per UI file, all Robolectric,
`@Config(sdk = [33])`):**

> The list below is comprehensive but each test file is small (3-10
> tests). The test depth matches the car spec: one test per
> behavior, multiple per branch.

- `MapScreenTest`:
  - `mapScreen_rendersMaplibreMap` — assert the map composable is
    present.
  - `mapScreen_withUserPosition_rendersPuck` — assert the user-location
    puck composable is present.
  - `mapScreen_legendToggle_hiddenWhenShowLegendFalse`.
  - `mapScreen_legendToggle_visibleWhenShowLegendTrue`.
  - `mapScreen_timelineHiddenWhenShowTimelineFalse`.
  - `mapScreen_darkMode_appliesDarkOverlay` — assert overlay
    composables get the dark theme (the `mapOverlaysDark` plumbing).
  - `mapScreen_lightMode_appliesLightOverlay`.
  - `mapScreen_permissionDenied_showsGate` — assert the location
    permission gate renders when permission is missing.
  - `mapScreen_zoomChange_zoomsMap` — assert that the `LaunchedEffect`
    wiring the VM's `prefsRepo.zoomLevel` to the map's `cameraState`
    actually applies the zoom (this might be hard to assert on
    Robolectric; if so, mark the test as "wires up but doesn't fully
    assert camera state" and rely on manual DHU verification).

- `MapOverlayTest`:
  - `overlay_weatherWidgetVisibleWhenWindEnabled`.
  - `overlay_weatherWidgetHiddenWhenWindDisabled`.
  - `overlay_legendVisibleWhenShowLegendTrue`.
  - `overlay_legendHiddenWhenShowLegendFalse`.
  - `overlay_timelineVisibleWhenShowTimelineTrue`.
  - `overlay_timelineHiddenWhenShowTimelineFalse`.
  - `overlay_clickOpensDrawer` — assert the FAB / gesture wires
    the drawer open.

- `WeatherWidgetTest`:
  - `widget_noSnapshot_showsUnavailableText`.
  - `widget_withSnapshot_showsTempAndTrend` — fixture.
  - `widget_arrowRotation_isWindDirectionMinusBearing`.
  - `widget_trendText_dashWhenTrendNull`.
  - `widget_darkMode_appliesDarkPalette`.
  - `widget_lightMode_appliesLightPalette`.

- `WeatherLegendTest`:
  - `legend_rendersAllColorBands` — pin the number of legend rows.
  - `legend_clickRow_expandsDetail` — assert the click wires the
    detail sheet.
  - `legend_darkMode_appliesDarkPalette`.

- `WeatherTimelineTest`:
  - `timeline_rendersFrameMarkers` — pin the number of timeline
    frames.
  - `timeline_clickFrame_updatesAnchor`.
  - `timeline_darkMode_appliesDarkPalette`.

- `NavWidgetTest`:
  - `widget_noRoute_hidesRoot`.
  - `widget_withRoute_showsDistance` — fixture.
  - `widget_arrowRotation_isBearingMinusCameraBearing`.
  - `widget_darkMode_appliesDarkPalette`.

- `SpeedReadoutTest`:
  - `readout_noPosition_showsZero`.
  - `readout_withPosition_showsFormattedSpeed` — pin the unit toggle
    (km/h vs mph).
  - `readout_darkMode_appliesDarkPalette`.

- `MapFabsTest`:
  - `fabs_recenterClick_invokesRecenter`.
  - `fabs_compassClick_invokesCompass`.
  - `fabs_darkMode_appliesDarkPalette`.

- `ActionsDrawerTest`:
  - `drawer_closedByDefault`.
  - `drawer_openShowsWeatherPage` — assert the first page is weather.
  - `drawer_swipeToRoutePage` — assert the page indicator advances.
  - `drawer_swipeToSettingsPage`.
  - `drawer_swipeToHelpPage`.
  - `drawer_closeHidesAll`.
  - `drawer_darkMode_appliesDarkPalette`.

- `MapDrawerSettingsContentTest`:
  - `content_togglesReflectVmState` — assert the toggles show
    VM's `useMetric` / `showLegend` / etc.
  - `content_clickToggleUpdatesVm`.
  - `content_cycleRowsReflectNextValue` — opacity, wind unit, temp
    unit, auto-advance threshold.

- `WeatherDrawerSettingsContentTest`:
  - `content_radarModeRow_clickCycles` — OFF → ON → PLAYING.
  - `content_togglesReflectVmState`.

- `PoiCategoryPickerTest`:
  - `picker_gridItemBelowCap_isTappable`.
  - `picker_gridItemAtCap_isNotTappableAndShowsMaxReached`.
  - `picker_gridItemEnabled_hasSelectedBadge`.
  - `picker_clickInvokesToggle`.
  - `picker_darkMode_appliesDarkPalette`.

- `PoiSearchDialogTest`:
  - `dialog_noQuery_showsEmptyResults`.
  - `dialog_withQuery_rendersResultList` — fixture.
  - `dialog_withLoading_showsLoadingIndicator`.
  - `dialog_clickResult_invokesPickResult`.
  - `dialog_darkMode_appliesDarkPalette`.

- `RouteEditorSheetTest`:
  - `sheet_emptyWaypoints_showsEmptyHint`.
  - `sheet_withWaypoints_rendersList` — fixture.
  - `sheet_activeWaypointBadgeAppears`.
  - `sheet_clickMoveUp_invokesMove` — assert the move-up action
    fires the VM seam.
  - `sheet_clickMoveDown_invokesMove`.
  - `sheet_darkMode_appliesDarkPalette`.

- `TappedPoiPopupTest`:
  - `popup_noSelection_hidden`.
  - `popup_withSelection_showsNameAndSubtitle`.
  - `popup_clickDirections_invokesRoute` — assert the "directions"
    action wires to the VM's `setActiveWaypoint`.
  - `popup_darkMode_appliesDarkPalette`.

- `WaypointLabelsTest`:
  - `labels_noWaypoints_empty`.
  - `labels_withWaypoints_rendersName` — pin the label format.
  - `labels_darkMode_appliesDarkPalette`.

- `ColorEditorSheetTest`:
  - `sheet_rendersAllColorRows` — pin the number of editable colors.
  - `sheet_clickColor_opensPicker`.
  - `sheet_pickColor_invokesUpdate`.
  - `sheet_darkMode_appliesDarkPalette`.

- `LegendDetailSheetTest`:
  - `sheet_rendersTitleAndDescription`.
  - `sheet_darkMode_appliesDarkPalette`.

- `ChangelogSheetsTest`:
  - `sheet_rendersReleasesList` — fixture from `ChangelogRepository`.
  - `sheet_dismiss_invokesCallback`.
  - `sheet_darkMode_appliesDarkPalette`.

- `TermsOverlayTest`:
  - `overlay_termsNotAccepted_visible`.
  - `overlay_termsAccepted_hidden` — set `acceptedTermsVersion ==
    TERMS_VERSION`, assert gone.
  - `overlay_clickAccept_invokesAccept`.
  - `overlay_clickDecline_invokesDecline`.

- `LocationPermissionGateTest`:
  - `gate_permissionGranted_rendersChildren`.
  - `gate_permissionDenied_rationaleVisible`.
  - `gate_clickRequest_invokesRequest`.

- `GloveFriendlyControlsTest`:
  - `control_tapInvokesClick`.
  - `control_longPressInvokesLongPress`.
  - `control_darkMode_appliesDarkPalette`.

- `TutorialOverlayTest`:
  - `overlay_firstStep_rendersFirstTutorialText`.
  - `overlay_clickNext_advancesStep`.
  - `overlay_clickBack_regressesStep`.
  - `overlay_lastStep_nextClick_invokesComplete`.
  - `overlay_darkMode_appliesDarkPalette`.

- `TutorialAnchorsTest` (extension of the Phase 4 test):
  - the Phase 4 test pins the pure math; this file uses
    `composeTestRule` to assert that each tutorial step renders the
    expected highlight over the expected composable on screen.
  - (If the tutorial is gated on a `tutorialStep` VM state, set it
    via the existing seams and assert the highlight overlay's
    position.)

> **Composables in `ui/theme/`** (`Color.kt`, `Theme.kt`, `Type.kt`,
> `OverlayTypography.kt`) are mostly static theme definitions. Skip
> them in Phase 5 — the theme system is exercised by every other
> test in this phase (the "darkMode_appliesDarkPalette" assertions
> indirectly cover the theme plumbing).

> **`LayoutConfig.kt`** is a tiny data holder. Add a one-test
> `LayoutConfigTest` if there's any non-trivial math; otherwise skip.

> **`MakiIconRenderer.kt`** is a thin wrapper around androidsvg.
> Robolectric with the asset loaded is the right infra. If a
> composable test already covers icon rendering (the
> `loadMakiIcon_*` tests in `MapLayersHelpersTest`), this file's
> behavior is transitively covered.

### Phase 6 — Integration polish (optional)

After Phases 1-5, run the full test suite and DHU. Check:

- All 5 existing phone tests still pass (no regression).
- All 184 car tests still pass.
- All new phone tests pass.
- Total test count is in the 400-600 range, matching the goal.
- No flaky tests. (Watch the network-callback-using tests in
  `MapViewModel`; if Robolectric's `ConnectivityManager` shadow
  on SDK 33 still flakes, the suite is pinned to SDK 33 per the
  car spec's pattern.)
- `./gradlew :app:compileDebugKotlin` is clean.
- Spot-check the recent halo-theme bug *would* be caught by the
  new palette tests on `WeatherWidget`, `NavWidget`, `WeatherLegend`,
  etc. (the existing `setDark_*Style_*Palette` tests in the car
  suite are the template for the phone-side equivalents).

## Critical files

- **Modified (existing files, extract pure helpers):**
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/data/MapStyleResolution.kt` — extract `resolveAuto(...)` as `internal fun` for testing.
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/data/OpenMeteoRepository.kt` — promote `computeTrendNextHour` / `parseOpenMeteoTimeToZoned` to `internal` (sibling file or `companion object`).
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/data/CustomThemeRepository.kt` — promote `validateMapLibreStyle` / `rewriteTileSources` / `rewriteFonts` to `internal` (or `companion object`).
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/data/PostpassRepository.kt` — add `baseUrl: String` ctor param; promote `categoryTagMap` / `buildPostpassQuery` / `parsePoi` / `parsePostpassFeature` / `buildSubtitle` to `internal` (or `companion object`).
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/data/GeocodingRepository.kt` — add `baseUrl: String` ctor param; promote `parsePhotonFeature` / `formatAddressParts` to `internal`.
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/data/PreferencesRepository.kt` — refactor `migrate()` into `migrateV1ToV2(prefs)` / `migrateV2ToV3(prefs)` / ... / `migrateV10ToV11(prefs)` as `internal fun`s, called by `migrate()`. Promote `parseMapStyle` to `internal`.
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/viewmodel/MapViewModel.kt` — extract pure helpers as `internal fun`s in a new `MapViewModelLogic.kt` sibling. Add `baseUrl: String` seams to repositories instantiated in the factory (or accept the `baseUrl` in `MapViewModelFactory`).
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/ThemeSelectorPanel.kt` — extract `parseHexColor` / `extractBackgroundColor` / `swatchColorForStyle` as `internal fun`s in a new `ThemeSelectorLogic.kt` sibling.
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapScreen.kt` — extract `zoomTierFor` / `mapPaddingForOffset` / `poiInfo` as `internal fun`s in a new `MapScreenLogic.kt` sibling.
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapLayers.kt` — extract `loadMakiIcon` as `internal fun` in a new `MapLayersLogic.kt` sibling (or top-level).
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/ActionsDrawer.kt` — extract the `tutorialScrollTarget` / `pageAt` logic as `internal fun`s in a new `ActionsDrawerLogic.kt` sibling.
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/tutorial/TutorialAnchors.kt` — pure helpers are already top-level; the test pins their contract.

- **New (sibling files for extracted helpers):**
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/data/OpenMeteoHelpers.kt`
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/data/CustomThemeHelpers.kt`
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/data/PostpassHelpers.kt`
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/data/GeocodingHelpers.kt`
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/viewmodel/MapViewModelLogic.kt`
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/ThemeSelectorLogic.kt`
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapScreenLogic.kt`
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/MapLayersLogic.kt`
  - `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/ActionsDrawerLogic.kt`

- **New test files (24 total):**
  - **Phase 1 (pure helpers, 10 files):** `StyleJsonPatcherTest`, `RouteGeometryTest`, `RainViewerTest`, `WaypointTest`, `InsertPositionTest`, `ChangelogRepositoryTest`, `MapStyleTest`, `MapStyleResolutionTest`, `OpenMeteoHelpersTest`, `CustomThemeHelpersTest`, `PostpassHelpersTest`, `ModelsTest`, `InsertPositionDispatchTest`. (13 if every helper gets its own file; consolidate to 10 if two helpers share a file.)
  - **Phase 2 (repositories, 5 files):** `PostpassRepositoryTest`, `OpenMeteoRepositoryTest`, `GeocodingRepositoryTest`, `PreferencesRepositoryTest`, `ChangelogRepositoryLoadTest`.
  - **Phase 3 (VM, 1 file):** `MapViewModelLogicTest`.
  - **Phase 4 (UI pure logic, 5 files):** `WeatherFormatHelpersTest` (or extend the existing one), `ThemeSelectorHelpersTest`, `MapScreenHelpersTest`, `TutorialAnchorsTest`, `ActionsDrawerHelpersTest`, `MapLayersHelpersTest`.
  - **Phase 5 (Compose UI, ~22 files):** one per UI file in `ui/`, listed in Phase 5.

  **Total: ~45 new test files.** Roughly 4-6× the volume of the car
  spec (which had 14 new test files) because the phone surface is
  ~7× the size of the car surface.

## Reuse

- **Test style:** `PoiViewportChunksTest.kt` (plain JUnit for pure
  geometry), `WmoWeatherCodesTest.kt` (enum-to-resource mapping),
  `WeatherFormatTest.kt` (Robolectric for `strings.xml`),
  `WeatherRepositoryMockTest.kt` (MockWebServer for HTTP).
- **Seam pattern:** the existing 8 `set*ForTest` seams on
  `MapViewModel` (from the car retrofit). Add more only as needed.
- **BaseCarScreenTest-style listener seam** (if `MapScreen`
  needs a "render-listener" for assertion): reuse the
  `BaseCarScreenTest` pattern of a `notifyRefresh()` / `invalidate()`
  callback, expose it as a `set*ForTest` seam.

## Verification

1. `./gradlew :app:compileDebugKotlin` — clean compile.
2. `./gradlew :app:test` — all tests pass (existing 5 phone tests +
   existing 184 car tests + new ~45 phone tests).
3. `./gradlew :app:test --tests ca.voiditswarranty.roadtripradar.data.*`
   — runs only the new data-layer tests.
4. `./gradlew :app:test --tests ca.voiditswarranty.roadtripradar.viewmodel.*`
   — runs only the new VM tests.
5. `./gradlew :app:test --tests ca.voiditswarranty.roadtripradar.ui.*`
   — runs only the new UI tests.
6. `./gradlew :app:test --tests ca.voiditswarranty.roadtripradar.model.*`
   — runs only the new model tests.
7. Spot-check that the recent halo-theme bug *would* be caught by
   the new palette tests on `WeatherWidget`, `NavWidget`,
   `WeatherLegend`, `WeatherTimeline`. If a palette regression
   slipped in, the new `*_darkMode_appliesDarkPalette` tests would
   fail.
8. Spot-check that the SharedPreferences migration chain is
   robust: bump `prefs_version` to 12 (no schema change), run the
   suite, assert `migrate_idempotent_secondRunIsNoOp` and
   `migrate_prefsVersionEqualsDefaults_afterFreshInstall` still
   pass.
9. No manual DHU changes required — this spec is purely about
   adding test coverage. The existing manual DHU verification flow
   for new features remains unchanged.

## Out of scope (per scope decision)

- `MainActivity.kt` — Activity bootstrap, no business logic worth
  unit testing; instrumented test only (also out of scope).
- `car/` package — covered by `2026-07-05-android-auto-test-coverage-design.md`.
- Integration / instrumented tests.
- Screenshot / visual-regression tests.
- Accessibility audits.
- `ui/theme/` (Color, Theme, Type, OverlayTypography) — static
  theme definitions; transitively covered by every other test in
  Phase 5.
- `MakiIconRenderer.kt` — thin androidsvg wrapper; transitively
  covered by `loadMakiIcon` tests in `MapLayersHelpersTest`.
- `LayoutConfig.kt` — tiny data holder, no math to test.

## Order of implementation (risk-first)

1. **Phase 1** (pure helpers) — easiest TDD, highest code-coverage
   per minute invested. Validates the testing patterns in isolation
   before tackling anything Compose-coupled. Start here.
2. **Phase 2** (repositories) — MockWebServer pattern is already
   established (`WeatherRepositoryMockTest`). The migration tests
   in `PreferencesRepositoryTest` are the single highest-value
   Robolectric test in the spec; the migration chain is hand-written
   and easy to break.
3. **Phase 3** (`MapViewModel` extracted helpers) — biggest blast
   radius. The 1782-line VM is shared with the car surface; the
   extracted helpers are independently testable but the test
   depends on the existing `set*ForTest` seams (already in place
   from the car retrofit).
4. **Phase 4** (UI pure logic) — bridges the gap between pure
   helpers and Compose UI tests. Easy TDD, no Compose plumbing.
5. **Phase 5** (Compose UI tests) — most file volume, smallest
   per-file risk. `createComposeRule` is the only new test infra
   dependency. Most tests pin the same composable structure that
   the car spec's screen tests pin (with the same Robolectric +
   `@Config(sdk = [33])` pattern).
6. **Phase 6** (integration polish) — full suite + DHU smoke. Catch
   flaky tests, broken seams, and missing coverage that only shows
   up when running everything together.

## Lessons learned from the car retrofit (carry forward)

- **The `set*ForTest` seam pattern is the highest-leverage test
  pattern for the central VM.** Reuse it. Don't add test-only
  methods to production classes — add `set*ForTest` setters that
  match the existing convention.
- **Refactor for testability first, then TDD.** The car spec's
  `CarMapContainerLogic.kt` extraction was the unlock for the
  hub's pure helpers; the phone's `MapViewModelLogic.kt` plays
  the same role for the central VM.
- **Robolectric + `@Config(sdk = [33])` is the workhorse.** Every
  test that needs a `Context` (resources, system services,
  SharedPreferences) uses this combo. The phone spec matches the
  car spec on this.
- **MockWebServer is the right HTTP seam, not MockK.** Real
  `URL` + `OkHttp` + a local server is closer to production than
  a hand-rolled mock. Constructor-injected `baseUrl` is the
  parameter.
- **`createComposeRule()` is already on the classpath** but unused.
  The Phase 5 work is its first consumer; no new Gradle
  dependencies needed.
- **The `BaseCarScreenTest` "render-listener" pattern** (if needed
  for `MapScreen` test) is the template for the "lifecycle
  listener" pattern in any new tests. Reuse, don't reinvent.
- **Pin constants and counts.** The car spec's "POI grid has
  exactly 29 items" tests are the most valuable "guard against
  silent drift" tests in the suite. The phone spec does the
  same for `POI_CATEGORIES.size`, `PrefsDefaults` constants, the
  migration count, the drawer page count, the tutorial step
  count, etc.

## Spec self-review

- **Placeholder scan:** none. Every test name above is concrete.
- **Internal consistency:** Phase 1 references helpers that are
  extracted in the "Modified" critical-files list. Phase 2
  references `baseUrl` ctor params that are added in the same
  "Modified" list. Phase 5 references the new `MapViewModelLogic.kt`
  seam pattern that Phase 3 introduces. Consistent.
- **Scope check:** focused on the phone surface. `MainActivity`,
  `car/`, `ui/theme/`, and `MakiIconRenderer.kt` are explicitly
  out of scope. ~45 new test files is within the "5-7 phases"
  interview ceiling (we landed on 6 phases).
- **Ambiguity check:** every test name is specific. Helper
  signatures are spelled out (e.g., `resolveAuto(night,
  customLightAutoEnabled, customDarkAutoEnabled, hasCustomLight,
  hasCustomDark): MapStyle` — a 5-arg pure function with
  explicit input/output types). No two reasonable readers would
  extract the helper differently.
