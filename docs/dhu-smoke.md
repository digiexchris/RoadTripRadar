# DHU Smoke Runbook

> A manual smoke checklist for the phone surface, run before each release
> on a physical Android Auto head unit (or the Desktop Head Unit
> emulator). Each step cross-references the relevant automated test so a
> release engineer can verify "this smoke step is covered by test X" at
> a glance.

## When to run

- Before every public release.
- After any change to `MapViewModel`, `MapScreen`, `ActionsDrawer`, or
  the theme/style pipeline.
- After a halo-theme regression is reported.

## Pre-flight

- [ ] Build the release APK: `./build.sh`
- [ ] Install on a physical Android Auto head unit (or DHU).
- [ ] Pair the phone with the head unit.
- [ ] Open the RoadTripRadar app.
- [ ] Grant location permission when prompted.

## Map surface smoke

- [ ] Verify the map loads.
- [ ] Pan / zoom / tilt the map, verify smooth.
- [ ] **Reference:** `MapScreenContentTest.content_allFieldsPopulated_rendersWithoutCrash` (unit), `CameraGpsTrackingTest.gps_fixInjected_cameraFollows` (instrumented).

## Theme smoke

- [ ] Open the theme picker (Settings → Theme).
- [ ] Switch through LIBERTY, DARK, COLOR_DARK, AUTO.
- [ ] Verify the map reloads and the overlays match the theme (light
  icons on dark map, dark icons on light map).
- [ ] Verify the wind widget has a visible background badge (the
  "halo" fix).
- [ ] **Reference:** `MapStyleSwitchingTest` (all tests), `WeatherWidgetTest.widget_darkMode_windIconIsFindable` (unit).

## Route smoke

- [ ] Tap a POI on the map.
- [ ] Tap "Add to route" — verify the waypoint appears in the route
  editor.
- [ ] Tap a second POI, add to route.
- [ ] Advance / regress the active waypoint via the chevrons in the nav
  widget.
- [ ] Set auto-advance to 100m, simulate driving within range (or use
  the GPS injection from `BasePhoneScreenTest.injectGpsLocation`).
- [ ] **Reference:** `RouteOperationsTest` (all tests), `RouteGeometryTest` (unit).

## Weather smoke

- [ ] Open the weather drawer.
- [ ] Turn on the radar.
- [ ] Verify the timeline scrolls, the legend renders, and the wind
  widget shows current data.
- [ ] **Reference:** `WeatherWidgetTest` (unit), `WeatherLegendTest`
  (unit), `WeatherTimelineTest` (unit).

## Search smoke

- [ ] Tap the search button.
- [ ] Type a place name.
- [ ] Tap a result.
- [ ] Verify the POI is highlighted and the popup appears.
- [ ] **Reference:** `PoiSearchDialogTest` (unit).

## Permissions smoke

- [ ] Revoke location permission, verify the gate appears.
- [ ] Grant permission, verify the gate disappears.
- [ ] **Reference:** `LocationPermissionGate` is not unit-tested; this
  is the manual fallback.

## Sign-off

- [ ] All sections pass: ✅ release.
- [ ] Any failure: file an issue, block the release.

## Notes

- Range rings (dashed `LineLayer` with dasharray) do not render on the
  Android emulator — verify on physical hardware. (See `CLAUDE.md`,
  "Known Quirks".)
- The debug variant includes a "Preview What's New" button in Help &
  Info for testing changelog upgrades.