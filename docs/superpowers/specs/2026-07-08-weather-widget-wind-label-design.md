# Weather Widget — Replace Wind Icon with Label

## Context

The wind-direction icon on the weather card (both phone and car
surfaces) currently uses a small "air" badge overlaid on the
arrow. The badge was added in the recent halo-theme commit
(04634bf) to fix a dark-mode contrast issue, but the result is
visually noisy and the user has judged the icon-in-circle
treatment as not reading well. They want the badge removed and
replaced with a plain text label "Wind" under the arrow, with
the arrow itself slightly shrunk to make room.

**Goal:** drop the wind badge on both surfaces, add a localized
"Wind" label under the arrow, shrink the arrow by ~20%, and
keep tests in sync with the new structure.

**Decisions from this interview (the user stepped away — these
are the defaults I picked, with confirmation on return):**
- **Layout:** "Directly under the arrow" — 6-line column:
  `temp / trend / arrow / Wind / speed / unit`. (The
  original layout is 5 lines with the badge overlapping the
  arrow; the new layout is 6 lines with a standalone "Wind"
  label between the arrow and the speed.) The original
  proposal I asked about had 3 options: 4-line column under
  arrow, replacing the unit label, or inline with the speed
  value.
- **Arrow shrink:** ~20% smaller. Phone: `weatherWidgetSize *
  0.27f.dp` → `weatherWidgetSize * 0.22f.dp` (the arrow inside
  the box). Car: 80dp box → 64dp box.
- **Label source:** new i18n string `wind_label` in
  `values/strings.xml` and `values-en-rUS/strings.xml` only.
  Other locales fall through to the English value until Crowdin
  is updated (per CLAUDE.md's "other locales are managed via
  Crowdin" rule).
- **Keep `cd_wind_direction`** content description on the
  arrow — accessibility for the rotated arrow stays.

## Scope

**In scope:**
- `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidget.kt` —
  remove the Air badge, shrink the arrow, add the "Wind" label.
- `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidget.kt` —
  drop the `badge` and `airIcon` fields, drop the `*AirTint`
  and `*BadgeBg` companion constants, add a `windLabelText`
  field, theme it as a secondary text color.
- `app/src/main/res/layout/car_weather_widget.xml` — drop the
  inner badge `FrameLayout`, shrink the arrow box, add a new
  `car_weather_wind_label` `TextView`.
- `app/src/main/res/values/strings.xml` + `values-en-rUS/strings.xml` —
  add `<string name="wind_label">Wind</string>`.
- `app/src/main/res/drawable/ic_car_wind.xml` — **delete**
  (the asset is no longer referenced).
- `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidgetTest.kt` —
  rename two "windIcon" tests (they actually look for the
  arrow's content description), add a new test asserting the
  "Wind" label is rendered.
- `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidgetTest.kt` —
  remove the badge-background-tint assertions, add a new test
  asserting the "Wind" label is rendered and themed.
- `app/src/main/assets/changelog.json` — append a bullet to
  the `next` release.

**Out of scope:**
- Visual `@Preview` composables (the `WeatherWidget` doesn't
  have one; not adding one).
- `WeatherLegend` (the radar color legend — a different widget
  with a different purpose).
- `car/screens/WeatherScreen.kt` (the car list view, not the
  card overlay).
- `WeatherDrawerSettingsContent.kt` (the settings panel).
- Translations for non-English locales (managed via Crowdin).

## Phone surface changes

### `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidget.kt`

**Layout (before → after):**
```
Temp
Trend
[arrow + Air badge in circle]   →   [arrow (smaller)]
                                     Wind
Speed
Unit
```

**Specific edits:**

1. **Remove the Air badge block** (the `Box { Icon(Navigation)
   + Box(circle background) { Icon(Air) } }`).
2. **Shrink the arrow.** Replace the outer `Box(
   contentAlignment = Alignment.Center, modifier =
   Modifier.size(iconSize))` wrapper with a plain
   `Icon(Navigation, modifier = Modifier
   .size(arrowSize)
   .rotate(windArrowRotationDeg),
   tint = MaterialTheme.colorScheme.primary)`.
   The new size is `arrowSize = (weatherWidgetSize *
   0.22f).dp` (the old `iconSize` was `weatherWidgetSize.dp`,
   so this is a ~78% retention, i.e. a 22% shrink). The
   `iconSize` local is no longer used by the arrow; delete
   it. The `iconSize` was also used by the unavailable
   branch's CloudOff icon — replace with a new local
   `unavailableIconSize = (weatherWidgetSize * 0.22f).dp`.
3. **Add a "Wind" label** between the arrow and the speed
   value: `Text(text = stringResource(R.string.wind_label),
   fontSize = (weatherWidgetSize * 0.20f).sp, lineHeight =
   (weatherWidgetSize * 0.20f).sp, color =
   MaterialTheme.colorScheme.onSurfaceVariant, textAlign =
   TextAlign.Center)`. The font size matches the existing
   trend/unit label style (a secondary caption).
4. **Delete unused locals and imports:**
   - Locals: `windIconSize`, `windBadgeSize`.
   - Imports: `androidx.compose.foundation.background`,
     `androidx.compose.foundation.shape.CircleShape`,
     `androidx.compose.foundation.layout.Box`,
     `androidx.compose.material.icons.filled.Air`,
     `androidx.compose.ui.draw.clip`.
5. **Keep `cd_wind_direction`** content description on the
   arrow (accessibility label for the rotated glyph).

## Car surface changes

### `app/src/main/res/layout/car_weather_widget.xml`

**Layout (before → after):**
```
Temp (28sp)
Trend (20sp)
FrameLayout 80dp {
  ImageView 80dp arrow        →   FrameLayout 64dp {
  FrameLayout 27dp {                   ImageView 64dp arrow
    ImageView 22dp ic_car_wind       }
  }                                }
                                 Wind (20sp, new)
Speed (28sp)
Unit (20sp)
```

**Specific edits:**

1. **`car_weather_wind_box`**: change `layout_width` and
   `layout_height` from `80dp` to `64dp`.
2. **`car_weather_arrow`**: change `layout_width` and
   `layout_height` from `80dp` to `64dp`.
3. **Delete the inner `FrameLayout` badge** containing
   `car_weather_air` entirely.
4. **Add a new `TextView`** between `car_weather_wind_box`
   and `car_weather_wind_speed`:
   ```xml
   <TextView
       android:id="@+id/car_weather_wind_label"
       android:layout_width="wrap_content"
       android:layout_height="wrap_content"
       android:layout_marginTop="2dp"
       android:textSize="20sp"
       android:gravity="center" />
   ```
   The `android:text` is set programmatically (from
   `R.string.wind_label`).

### `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidget.kt`

**Specific edits:**

1. **Drop fields** `badge: FrameLayout` and `airIcon: ImageView`
   (the inflated views no longer exist).
2. **Add field** `windLabelText: TextView`. Wire it in
   `init`: `windLabelText = view.findViewById(
   R.id.car_weather_wind_label)`.
3. **In `update`**, set `windLabelText.text =
   context.getString(R.string.wind_label)`.
4. **In `applyColors`**, set the label's text color:
   `windLabelText.setTextColor(darkTextSecondary)` in the
   dark branch and `windLabelText.setTextColor(lightTextSecondary)`
   in the light branch (matches the trend and unit label
   treatment).
5. **Drop companion constants** `darkAirTint`,
   `lightAirTint`, `darkBadgeBg`, `lightBadgeBg` (no longer
   referenced).
6. **Update KDoc** at the top of the class to drop the
   "with an 'air' badge" mention and add "with a 'Wind'
   label below".

### `app/src/main/res/drawable/ic_car_wind.xml`

**Delete the file.** It's no longer referenced. The companion
arrow drawable `ic_car_wind_arrow.xml` stays.

## Strings + i18n

Add to both:
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en-rUS/strings.xml`

```xml
<string name="wind_label">Wind</string>
```

Place it next to the existing wind-related strings
(`wind_unit_kmh`, `wind_unit_mph`, `wind_unit_kn`) for
discoverability. Other locales (fr, es-rES, nb-rNO, no-rNO)
fall through to the English value until Crowdin picks up
the new key.

## Changelog

Append a bullet to the `next` release in
`app/src/main/assets/changelog.json`:

> "Weather card: removed the wind icon, added a 'Wind' label
> below the arrow, and slightly reduced the arrow size for a
> cleaner look."

## Test updates

### `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidgetTest.kt`

1. **Rename** `widget_windIconHasContentDescription` →
   `widget_windArrowHasContentDescription`. Update the KDoc
   to say "the wind arrow" instead of "the wind icon". The
   assertion still passes — it looks for the
   `cd_wind_direction` content description, which is on the
   arrow.
2. **Rename** `widget_darkMode_windIconIsFindable` →
   `widget_darkMode_windArrowIsFindable`. Same change.
3. **Add** `widget_withSnapshot_rendersWindLabel`:
   ```kotlin
   @Test
   fun widget_withSnapshot_rendersWindLabel() {
       composeTestRule.setContent {
           RoadTripRadarTheme {
               WeatherWidget(
                   snapshot = snapshot(),
                   temperatureUnit = TemperatureUnit.CELSIUS,
                   windSpeedUnit = WindSpeedUnit.KMH,
                   weatherWidgetSize = 100f,
                   cameraBearing = 0.0,
               )
           }
       }
       composeTestRule.onNodeWithText("Wind").assertIsDisplayed()
   }
   ```
4. **Update** `widget_withoutSnapshot_rendersUnavailableState`
   if the CloudOff branch breaks. The CloudOff icon used
   `windIconSize` — that constant is gone. Replace with
   `unavailableIconSize` (new local, sized to `(weatherWidgetSize
   * 0.22f).dp`). If the test only asserts the
   `contentDescription` (not the size), no change is needed.

### `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidgetTest.kt`

1. **Replace `badgeOf(w)` with `windLabelTextOf(w)`** in the
   helper section. New helper:
   ```kotlin
   private fun windLabelTextOf(w: CarWeatherWidget): TextView =
       w.view.findViewById(R.id.car_weather_wind_label)
   ```
2. **Update `update_dark_appliesDarkPalette`**: drop the
   `dark badge bg` assertion block. Add a new assertion
   that the wind label's text color is the dark secondary
   text color:
   ```kotlin
   assertEquals(
       "dark wind label text color",
       CarWeatherWidget.darkTextSecondary,
       windLabelTextOf(w).currentTextColor,
   )
   ```
3. **Update `update_light_appliesLightPalette`**: same change
   with `lightTextSecondary`.
4. **Add** `update_withSnapshot_setsWindLabelText`:
   ```kotlin
   @Test
   fun update_withSnapshot_setsWindLabelText() {
       val vm = freshVm()
       vm.updateWindEnabled(true)
       vm.setOpenMeteoSnapshotForTest(
           OpenMeteoSnapshot(
               temperatureCelsius = 20.0,
               weatherCode = 0,
               windSpeedKmh = 10.0,
               windDirectionDeg = 0,
               windGustsKmh = 15.0,
               tempTrendCelsius = 1.0,
           )
       )
       val w = widget(vm)
       w.update(cameraBearingDegrees = 0.0)
       assertEquals("Wind", windLabelTextOf(w).text.toString())
   }
   ```

## Verification

1. `./gradlew :app:compileDebugKotlin` — clean compile.
2. `./gradlew :app:testDebugUnitTest` — all tests pass.
   Expected: 905 (current) + 1 new phone test + 1 new car
   test = **907 tests, 0 failures**.
3. `./gradlew :app:compileDebugAndroidTestKotlin` — clean
   compile (the androidTest sourceset shouldn't be affected,
   but verify).
4. Spot-check that `cd_wind_direction` is still referenced
   (the arrow's content description).
5. Spot-check that `ic_car_wind.xml` is no longer referenced
   anywhere in the codebase (`grep -r ic_car_wind app/src/main`
   should return no source code matches).
6. Manual visual check on phone + car DHU after build —
   confirm the "Wind" label is readable, the arrow is
   visibly smaller, and the dark/light palettes still read
   correctly.

## Critical files

**Modified (production):**
- `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidget.kt`
- `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidget.kt`
- `app/src/main/res/layout/car_weather_widget.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en-rUS/strings.xml`
- `app/src/main/assets/changelog.json`

**Modified (tests):**
- `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidgetTest.kt`
- `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidgetTest.kt`

**Deleted (assets):**
- `app/src/main/res/drawable/ic_car_wind.xml`

## TDD discipline

- **Add the new tests first.** Write
  `widget_withSnapshot_rendersWindLabel` and
  `update_withSnapshot_setsWindLabelText` before changing
  the production code. Watch them fail (the label doesn't
  exist yet).
- **Implement the label.** Add the new `Text` on the phone
  and the new `TextView` on the car. Watch the tests pass.
- **Then do the cleanup** (remove the badge, shrink the
  arrow, delete the drawable, update the existing tests).
  Each of these is a separate, reviewable change.

## Spec self-review

- **Placeholder scan:** no TBDs/TODOs. Every edit is concrete
  with file paths and code snippets.
- **Internal consistency:** the spec describes a single
  change (wind badge → text label + arrow shrink) on two
  surfaces; the phone and car changes mirror each other
  (badge removed, label added in the same position, arrow
  shrunk by the same proportion). The test changes mirror
  the production changes (one new test per surface, two
  renames on the phone, palette assertion swap on the car).
- **Scope check:** focused on the weather card on both
  surfaces. Doesn't touch the radar legend, the car
  `WeatherScreen` list, the settings panel, or any other
  widget.
- **Ambiguity check:** the only soft constraint is "shrink
  slightly" — the spec pins it to 22% on the phone (0.27 →
  0.22) and 20% on the car (80dp → 64dp), with a single
  rationale (matches the user's "~20% smaller" answer to
  the question I asked before they stepped away).
