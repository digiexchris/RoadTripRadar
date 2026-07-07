# Phone — Test Coverage Follow-up (Instrumented Tests + DHU Smoke)

> Companion to `2026-07-07-phone-test-coverage-followup-unit.md`.
> Tackles the instrumented-test half of the 5 next directions
> identified after Phase 7 of the original phone test coverage
> spec completed: instrumented test infrastructure, instrumented
> tests for the three fragile areas (camera+GPS, map style+themes,
> route+waypoints), and a DHU smoke runbook.

## Context

After Phase 7, the phone surface has **839 unit tests across 82
files**, all passing. But unit tests on a phone-only Android app
are not enough. The fragile parts of the app — camera tracking,
GPS injection, native MapLibre rendering, theme reload timing,
Compose semantics under real device conditions — can't be
exercised under Robolectric. They need a real device or
emulator.

This spec adds the `androidTest/` infrastructure, three
instrumented test files for the fragile areas, and a DHU smoke
runbook the team follows before each release.

**The 5 next directions (this spec covers 4 and 5):**

1. Halo-theme audit + missing tests. *(covered in the unit-test
   follow-up spec)*
2. `MapOverlay` state-class extraction. *(covered in the
   unit-test follow-up spec)*
3. `ActionsDrawer` / `MapScreen` state-class extraction.
   *(covered in the unit-test follow-up spec)*
4. **Run instrumented tests for fragile areas: camera+GPS, map
   style+theme switching, route+waypoint operations.**
5. **DHU smoke runbook for physical Android Auto head units.**

**Decisions from this interview:**

- **Scope split**: this spec is one of two follow-up specs. The
  split (unit vs. instrumented) respects a real boundary:
  the unit-test work reuses the existing `set*ForTest` seam
  pattern; the instrumented work introduces new `androidTest/`
  configuration, real MapLibre native, and `LocationManager`
  GPS injection that don't overlap.
- **Fragile areas to cover**: camera+GPS tracking, map style
  + theme switching, route+waypoint operations (all three
  multiSelect'd by the user). "Other edge cases" was not
  selected.
- **New instrumented tests (not just smoke)**: the user
  chose "add new instrumented tests for fragile areas" over
  "smoke only — record results."
- **DHU smoke runbook**: a written document, not tests. Each
  smoke step cross-references the relevant automated test
  from the unit-test or instrumented work.

**Out of scope:**

- Unit-test refactors (companion spec).
- `MainActivity.kt` (Activity bootstrap).
- `car/` package (covered by the car spec).
- Performance benchmarks (no `androidx.benchmark`).
- Screenshot / visual regression tests.
- DHU smoke steps that are already covered by the instrumented
  tests — those are automation, not manual smoke.

## The instrumented test infrastructure (the unlock)

The existing `app/build.gradle.kts` has the `androidTest` config
for the car surface. The phone surface needs the same. The
changes:

1. **Add `androidTest` config to `app/build.gradle.kts`** if not
   already present. Verify the `targetSdk` matches the main
   module.
2. **Add Compose UI test infra**: `androidx.compose.ui:ui-test-junit4`
   (BOM-managed) + `androidx.compose.ui:ui-test-manifest` (the
   latter provides the test activity).
3. **Add `androidx.test.ext:junit` + `androidx.test:runner` +
   `androidx.test:rules`** for `ActivityScenarioRule` and
   similar.
4. **Add a `BasePhoneScreenTest` helper** in
   `app/src/androidTest/java/.../ui/`. Handles:
   - `createComposeRule()` lifecycle.
   - `runOnUiThread` for VM mutations from test threads.
   - `awaitMapIdle()` — polls until MapLibre's native map
     reports the camera is settled (uses
     `MaplibreMap.cameraState.moveReason`).
   - `awaitThemeLoaded()` — polls until the VM's
     `customThemeVersion` increments after a theme change.
   - `LocationManager.setTestProviderLocation()` injection —
     a small helper that takes lat/lon/accuracy and pushes
     the fix into the test provider. Falls back to a no-op
     on devices/emulators without a test provider (and
     asserts the fallback ran).

**No new tests in this phase.** Just the harness.

**TDD discipline:** the helper is testable in isolation
(it's just utility code). Write a `BasePhoneScreenTestSmokeTest`
that asserts the helper's no-op fallback runs, the
`runOnUiThread` blocks until the action runs, etc. ~3-4
sanity tests.

## Plan

### Phase 1 — Test infrastructure (the unlock)

**Goal:** the `androidTest` config works, the helper is in
place, a "no-op smoke" test passes.

**Tasks:**

1. **Add `androidTest` config + deps to `app/build.gradle.kts`.**
   - `androidTestImplementation("androidx.compose.ui:ui-test-junit4")`
   - `androidTestImplementation("androidx.compose.ui:ui-test-manifest")`
   - `androidTestImplementation("androidx.test.ext:junit:1.1.5")`
   - `androidTestImplementation("androidx.test:runner:1.5.2")`
   - `androidTestImplementation("androidx.test:rules:1.5.0")`
   - `androidTestImplementation("org.robolectric:robolectric:4.11.1")`
     *(already in main; verify it's in androidTest too if
     needed for shadow classes)*
   - Verify the manifest merger includes the test activity
     from `ui-test-manifest`.

2. **Create `BasePhoneScreenTest`** in
   `app/src/androidTest/java/ca/voiditswarranty/roadtripradar/ui/`.
   The base class:
   ```kotlin
   abstract class BasePhoneScreenTest {
       @get:Rule
       val composeTestRule = createComposeRule()

       protected val context: Context
           get() = ApplicationProvider.getApplicationContext()

       protected fun runOnMain(action: () -> Unit) { ... }

       protected suspend fun awaitMapIdle(timeoutMs: Long = 5000L) { ... }

       protected suspend fun awaitThemeLoaded(
           before: Int,
           timeoutMs: Long = 5000L,
       ): Int { ... }

       protected fun injectGpsLocation(
           lat: Double,
           lon: Double,
           accuracyMeters: Double = 5.0,
       ) { ... }
   }
   ```

3. **Create `BasePhoneScreenTestSmokeTest`** that extends the
   base and asserts the helpers behave. ~3-4 sanity tests:
   - `smoke_runOnMain_runsOnMainThread`.
   - `smoke_awaitMapIdle_returnsWithinTimeout` (with a
     no-op map).
   - `smoke_injectGpsLocation_fallsBackGracefully` (when no
     test provider is registered).
   - `smoke_awaitThemeLoaded_returnsInitialValue` (when no
     theme change is pending).

**Estimated new tests: 3-4.** 1 new test file +
`BasePhoneScreenTest.kt`.

**TDD discipline:** write the helper signatures, write the
sanity tests, watch them fail (helper is a stub), implement
the helper, watch them pass.

### Phase 2 — Camera + GPS tracking instrumented tests (Direction 4 part 1)

**Goal:** exercise the camera-follows-GPS state machine
against a real device. The unit tests cover the
`hasGoodGpsFix` math; the instrumented tests cover the
end-to-end "inject a fix, verify the camera recenters, verify
`isTrackingCamera` toggles" path.

**`CameraGpsTrackingTest.kt`** (new file, ~6-8 tests):

- `gps_noFix_cameraShowsLastKnownPosition` — start with no
  GPS, verify the camera shows `vm.prefsRepo.lastKnownPosition`.
- `gps_fixInjected_cameraFollows` — inject a fix, wait for
  the camera to recenter, verify the rendered position is
  near the fix.
- `gps_fixInjected_isTrackingCameraTrue` — inject a fix,
  verify `vm.isTrackingCamera` is `true` after a brief
  settle.
- `gps_userPansCamera_isTrackingCameraFalse` — inject a
  fix, wait for tracking, simulate a user pan gesture
  (via `performTouchInput.swipe()` on the map), verify
  `vm.isTrackingCamera` becomes `false`.
- `gps_recenterButton_isTrackingCameraTrue` — simulate the
  recenter button click, verify `vm.isTrackingCamera` is
  `true` again.
- `gps_lowAccuracyFix_cameraDoesNotTrack` — inject a fix
  with `accuracy=200m`, verify the camera does not recenter
  (the `< 50m` rule from the unit-test work).
- `gps_poorFixThenGoodFix_cameraRecovers` — inject a 200m
  fix (no track), then a 10m fix (track), verify the
  camera recovers.
- `gps_fixDisappears_cameraKeepsLastPosition` — inject a
  fix, wait for tracking, clear the fix, verify the camera
  stays at the last known position (no jump to default).

**Refactor step:** the `isTrackingCamera` toggle is
currently in the `LaunchedEffect` inside `MapScreen` (the
`cameraState.moveReason == CameraMoveReason.GESTURE` block).
If this is testable from the instrumented test, no refactor
needed. If not, the camera-tracking logic should be
extracted to a `CameraTrackingEffect` composable that takes
`(cameraState, hasLocation, hasGpsFix, onTrackingChange)`
and is hostable in a test.

**Estimated new tests: 6-8.** 1 new test file.

**TDD discipline:** write the test, watch it fail (the
behavior is correct but the assertion is hard to make
without the right helper), add the helper or refactor as
needed, watch the test pass.

### Phase 3 — Map style + theme switching instrumented tests (Direction 4 part 2)

**Goal:** exercise the theme-switching state machine. This
is the "halo zone" — the bug was a visual contrast issue in
`WeatherWidget`, but the broader concern is the theme
plumbing. Cycle through every map style, verify the map
reloads, verify the overlays are dark/light correctly.

**`MapStyleSwitchingTest.kt`** (new file, ~6-8 tests):

- `style_libertyToDark_overlaysBecomeDark` — start with
  LIBERTY (light), switch to DARK, verify
  `mapOverlaysDark` is `true` after the transition.
- `style_darkToLiberty_overlaysBecomeLight` — opposite.
- `style_colorDarkToLiberty_overlaysBecomeLight` — same
  but starting from COLOR_DARK.
- `style_autoWithSystemLight_resolvesToLight` — set the
  system to light mode, set the user's map style to AUTO,
  verify the resolved style is LIBERTY (or a custom light
  if one exists) and the overlays are light.
- `style_autoWithSystemDark_resolvesToDark` — same with
  system dark.
- `style_customLightImported_rendersCorrectly` — import a
  custom light theme (via `vm.importCustomTheme()` with a
  fixture file), verify the map reloads and the overlays
  are light.
- `style_customLightDeleted_fallsBackToLiberty` — delete
  the custom light, verify the map falls back to LIBERTY
  if AUTO was set, or to the previous style if explicit.
- `style_switch_doesNotLoseUserPosition` — cycle through
  3 styles, verify `vm.userPosition` and the camera target
  are preserved.

**Refactor step:** the "switch style + verify map reloads"
behavior is currently coupled to `MapScreen`'s
`LaunchedEffect` on `(mapStyleUri, customThemeVersion)`. If
the instrumented test can't easily observe the map reload,
extract the style-applier to a `MapStyleApplier` composable
or a helper function that takes `(maplibreMap, styleUri,
version)` and is hostable in a test.

**Estimated new tests: 6-8.** 1 new test file.

**TDD discipline:** same as Phase 2.

### Phase 4 — Route + waypoint operations instrumented tests (Direction 4 part 3)

**Goal:** exercise the route and waypoint state machine
end-to-end. The unit tests cover `RouteGeometry` math; the
instrumented tests cover the Compose-side rendering and the
VM's add/clear/advance/regress operations.

**`RouteOperationsTest.kt`** (new file, ~6-8 tests):

- `route_noWaypoints_routeEditorEmpty` — start with no
  waypoints, open the route editor, verify the empty
  state.
- `route_addWaypointViaTapPopup_rendersInEditor` — tap a
  POI, tap "Add to route", verify the waypoint shows in
  the editor.
- `route_addWaypointViaSearch_rendersInEditor` — search
  for a place, tap a result, tap "Add to route", verify.
- `route_advanceActiveWaypoint_advances` — add 3
  waypoints, click "Next" in the nav widget, verify
  `activeIndex` becomes 1.
- `route_regressActiveWaypoint_regresses` — same, click
  "Previous", verify `activeIndex` becomes 0.
- `route_advanceAtLastWaypoint_disablesNext` — same,
  click "Next" until last, verify the "Next" button is
  disabled.
- `route_clearRoute_emptiesWaypoints` — add 2 waypoints,
  open route editor, click "Clear Route", confirm,
  verify `waypoints` is empty.
- `route_autoAdvance_advancesAtThreshold` — add 2
  waypoints, set `autoAdvanceDistanceMeters = 100`, move
  the simulated user position within 100m of the active
  waypoint, verify `activeIndex` advances to 1. (This
  exercises the auto-route-advance bug fix from the
  halo commit — the second bug.)

**Refactor step:** the auto-advance logic is in
`MapViewModelLogic.maybeAutoAdvance` (extracted in Phase 3
of the original spec). The instrumented test exercises the
end-to-end path; no refactor needed.

**Estimated new tests: 6-8.** 1 new test file.

**TDD discipline:** same as Phase 2.

### Phase 5 — DHU smoke runbook (Direction 5)

**Goal:** a written `docs/dhu-smoke.md` document the team
follows before each release. Each step cross-references the
relevant automated test.

**`docs/dhu-smoke.md`** (new doc, ~150-200 lines):

Sections:

1. **Pre-flight**
   - Build the release APK.
   - Install on a physical Android Auto head unit (or DHU).
   - Pair the phone.
   - Open the app.

2. **Map surface smoke**
   - Verify the map loads.
   - Pan / zoom / tilt the map, verify smooth.
   - *Reference: `MapScreenContentTest.rendersWithoutCrash`.*

3. **Theme smoke**
   - Open the theme picker.
   - Switch through LIBERTY, DARK, COLOR_DARK, AUTO.
   - Verify the map reloads and the overlays match the
     theme.
   - Verify the wind widget has a visible background badge.
   - *Reference: `MapStyleSwitchingTest` (all tests).*

4. **Route smoke**
   - Tap a POI.
   - Tap "Add to route" — verify the waypoint appears.
   - Tap a second POI, add to route.
   - Advance / regress the active waypoint via the
     chevrons.
   - Set auto-advance to 100m, simulate driving within
     range (or use a GPX file).
   - *Reference: `RouteOperationsTest` (all tests).*

5. **Weather smoke**
   - Open the weather drawer.
   - Turn on the radar.
   - Verify the timeline scrolls, the legend renders,
     and the wind widget shows current data.
   - *Reference: `WeatherWidgetTest` (unit tests).*

6. **Search smoke**
   - Tap the search button.
   - Type a place name.
   - Tap a result.
   - Verify the POI is highlighted and the popup appears.
   - *Reference: `PoiSearchDialogTest` (unit tests).*

7. **Permissions smoke**
   - Revoke location permission, verify the gate appears.
   - Grant permission, verify the gate disappears.
   - *Reference: `LocationPermissionGate` is not unit-
     tested; this is the manual fallback.*

8. **Sign-off**
   - All sections pass: ✅ release.
   - Any failure: file an issue, block the release.

**No new tests.** Just documentation.

## Critical files

- **Modified:**
  - `app/build.gradle.kts` — add `androidTest` config + deps.
  - `app/src/main/java/.../ui/MapScreen.kt` — possibly
    extract `CameraTrackingEffect` (Phase 2) or
    `MapStyleApplier` (Phase 3) if the test needs them.

- **New:**
  - `app/src/androidTest/java/.../ui/BasePhoneScreenTest.kt`
  - `app/src/androidTest/java/.../ui/BasePhoneScreenTestSmokeTest.kt`
  - `app/src/androidTest/java/.../ui/CameraGpsTrackingTest.kt`
  - `app/src/androidTest/java/.../ui/MapStyleSwitchingTest.kt`
  - `app/src/androidTest/java/.../ui/RouteOperationsTest.kt`
  - `docs/dhu-smoke.md`

**Total: 5 new test files + 1 doc.**

## Reuse

- **`MapViewModel.set*ForTest` seam pattern** (from the car
  retrofit and Phase 1-7 of the phone spec): the instrumented
  tests can set VM state directly via the existing seams.
- **`BaseCarScreenTest` pattern** (from the car spec): the
  `BasePhoneScreenTest` mirrors its structure — a single base
  class that all instrumented tests extend, with helper
  methods for common operations.
- **GPS injection via `LocationManager.setTestProviderLocation`**:
  standard Android pattern, well-documented in the
  `androidx.test` docs.
- **DHU smoke cross-references**: each smoke step in
  `docs/dhu-smoke.md` cites the relevant test file by name,
  so a release engineer can verify "the smoke step is
  covered by test X" at a glance.

## Verification

1. `./gradlew :app:connectedAndroidTest` — all instrumented
   tests pass on a real device or emulator.
2. The smoke runbook (`docs/dhu-smoke.md`) is committed.
3. A release engineer follows the runbook on a physical
   device; all sections pass.
4. No regression in `./gradlew :app:testDebugUnitTest` (still
   ~870-885 tests passing — the unit-test follow-up is a
   separate spec).
5. The instrumented tests run in <5 minutes total (so they're
   practical for CI).

## Out of scope (per scope decision)

- Unit-test refactors (companion spec).
- `MainActivity.kt`.
- `car/` package.
- Performance benchmarks.
- Screenshot / visual regression tests.
- DHU smoke steps that are already covered by instrumented
  tests.

## Order of implementation (risk-first)

1. **Phase 1** (infrastructure) — every later phase depends
   on this. The risk: a missing dep, a manifest merger
   issue, an `ActivityScenarioRule` quirk. Get it stable
   before writing the tests.
2. **Phase 2** (camera+GPS) — the most fragile user-facing
   state machine. If this works, the others will too.
3. **Phase 3** (themes) — the "halo zone". Important to
   catch theme regressions, but less brittle than GPS.
4. **Phase 4** (routes) — well-covered by unit tests, so
   the instrumented tests are confirming the integration.
5. **Phase 5** (DHU doc) — write last, after the tests
   exist, so the cross-references are concrete.

## Lessons learned from the unit-test follow-up (carry forward)

- **Audit before test**: in the unit-test spec, the halo
  audit found that the existing `WeatherWidgetTest` had 7
  tests but none pinned the wind icon's background badge.
  The same pattern applies here: audit the existing
  instrumented tests (in the car spec) for gaps before
  adding new ones.
- **State class extraction for testability**: the
  unit-test spec extracts `MapOverlayState` so the
  composable is testable. If the instrumented tests need
  to host `MapScreen` end-to-end, the `MapScreenState`
  extraction gives them a clear seam.
- **No MockK** — the unit-test spec reuses the `set*ForTest`
  seam pattern. The instrumented tests can use the same
  seams, plus `LocationManager.setTestProviderLocation` for
  real GPS injection (no mocking framework).
- **Pin constants and counts** — the unit-test spec pins
  `RouteLineLayerStyle` constants. The DHU smoke runbook
  pins "5 pages in the drawer", "3 fragile areas", etc.

## Spec self-review

- **Placeholder scan:** none. Every test name above is
  concrete. The new files are listed by name in the
  "Critical files" section.
- **Internal consistency:** the `BasePhoneScreenTest` is
  referenced by all 3 instrumented test files; the helper
  signatures in Phase 1 are consistent with the call sites
  in Phases 2-4. The DHU runbook's cross-references match
  the test file names exactly.
- **Scope check:** 5 phases, 5 new test files + 1 doc,
  ~25-30 new instrumented tests. Within the scope of
  "follow-up work" — not as large as the original Phase
  1-7, but bigger than the unit-test follow-up because
  it introduces new infrastructure.
- **Ambiguity check:** "real device or emulator" is
  defined as: any Android device or AVD with API 33+ and
  the test provider support. "GPS injection" is defined
  as: `LocationManager.setTestProviderLocation` with a
  test provider registered for `gps`. The DHU smoke
  runbook structure is the standard "checklist before
  release" pattern. No two reasonable readers would
  implement differently.
