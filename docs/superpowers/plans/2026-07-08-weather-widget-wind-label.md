# Weather Widget — Replace Wind Icon with Label Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the small "air" badge overlaid on the weather wind-direction arrow with a plain "Wind" text label under the arrow on both phone and car surfaces; shrink the arrow ~20% to make room.

**Architecture:** Two parallel surfaces (Compose phone, Android-Views car) get the same visual treatment. New i18n string `wind_label` shared by both. Tests on both surfaces pin the new label renders and themed. Pure TDD on the testable parts; surgical refactor on the cleanup.

**Tech Stack:** Jetpack Compose (phone), Android Views + layout XML (car), JUnit 4 + Robolectric + `createComposeRule` (tests), i18n via `values/strings.xml` + `values-en-rUS/strings.xml`.

**Spec:** `docs/superpowers/specs/2026-07-08-weather-widget-wind-label-design.md`

**User standing constraints (must preserve):**
- "Don't commit, I will do that" — subagents produce diffs only, never run `git commit`.
- "When porting a feature from phone to car, never skip a phone-side feature without asking" — both surfaces get the label, no skipping.
- TDD discipline: every new test added before its production code.

---

## File structure

**Modified (production):**
- `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidget.kt` — drop Air badge, shrink arrow, add Wind label
- `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidget.kt` — drop badge/companion constants, add wind label
- `app/src/main/res/layout/car_weather_widget.xml` — drop badge `FrameLayout`, shrink arrow box, add wind label `TextView`
- `app/src/main/res/values/strings.xml` — add `wind_label`
- `app/src/main/res/values-en-rUS/strings.xml` — add `wind_label`
- `app/src/main/assets/changelog.json` — append a bullet

**Modified (tests):**
- `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidgetTest.kt` — rename 2 tests, add 1 new
- `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidgetTest.kt` — replace 1 helper, swap 2 palette assertions, add 1 new test

**Deleted:**
- `app/src/main/res/drawable/ic_car_wind.xml`

---

## Task 1: Add the `wind_label` i18n string

**Files:**
- Modify: `app/src/main/res/values/strings.xml:323-326`
- Modify: `app/src/main/res/values-en-rUS/strings.xml:292-295`

- [ ] **Step 1: Add to `values/strings.xml`**

Open `app/src/main/res/values/strings.xml` and add the line below after `<string name="wind_unit_kn">kn</string>` (line 326):

```xml
    <!-- Label below the weather widget wind arrow (phone + Android Auto) -->
    <string name="wind_label">Wind</string>
```

- [ ] **Step 2: Add to `values-en-rUS/strings.xml`**

Open `app/src/main/res/values-en-rUS/strings.xml` and add the same line after `<string name="wind_unit_kn">kn</string>` (line 295):

```xml
    <!-- Label below the weather widget wind arrow (phone + Android Auto) -->
    <string name="wind_label">Wind</string>
```

- [ ] **Step 3: Verify the strings compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. The new string is a resource; not yet referenced, but the resources must compile.

- [ ] **Step 4: Hand off the diff**

Show the user the `git diff` for the two string files. Do NOT commit.

---

## Task 2: Phone — write the failing test for the "Wind" label

**Files:**
- Modify: `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidgetTest.kt:49-63` (add new test)

- [ ] **Step 1: Add the failing test**

Open `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidgetTest.kt` and add this new test method at the end of the class (after `widget_darkMode_windIconIsFindable` on line 188). Add the `import androidx.compose.ui.test.onNodeWithText` import if not already present (it is — line 5):

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

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.WeatherWidgetTest.widget_withSnapshot_rendersWindLabel"`
Expected: FAIL with "Cannot find a node with text 'Wind'" (or similar compose-semantics failure). The label doesn't exist yet, so the assertion fails for the right reason.

- [ ] **Step 3: Hand off the failing test diff**

Show the user the `git diff` for the test file. Do NOT commit.

---

## Task 3: Phone — implement the "Wind" label (make the test pass)

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidget.kt:80-108` (replace arrow block + add label)
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidget.kt:46-49` (size constants)

- [ ] **Step 1: Add the size constants**

In `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidget.kt`, replace lines 45-49:

```kotlin
    val iconSize = weatherWidgetSize.dp
    val distFontSize = (weatherWidgetSize * 0.35f).sp
    val nameFontSize = (weatherWidgetSize * 0.25f).sp
    val windIconSize = (weatherWidgetSize * 0.27f).dp
    val windBadgeSize = (weatherWidgetSize * 0.34f).dp
```

with:

```kotlin
    val arrowSize = (weatherWidgetSize * 0.22f).dp
    val distFontSize = (weatherWidgetSize * 0.35f).sp
    val nameFontSize = (weatherWidgetSize * 0.25f).sp
    val windLabelFontSize = (weatherWidgetSize * 0.20f).sp
    val unavailableIconSize = (weatherWidgetSize * 0.22f).dp
```

- [ ] **Step 2: Replace the arrow + badge block with just the arrow, and add the label**

Replace lines 80-108 (the `val windArrowRotationDeg = ...` through the closing brace of the `Box` containing the badge):

```kotlin
            val windArrowRotationDeg =
                windArrowRotationDeg(snapshot.windDirectionDeg, cameraBearing)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(iconSize),
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = stringResource(R.string.cd_wind_direction),
                    modifier = Modifier
                        .size(iconSize)
                        .rotate(windArrowRotationDeg),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(windBadgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = null,
                        modifier = Modifier.size(windIconSize),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
```

with:

```kotlin
            val windArrowRotationDeg =
                windArrowRotationDeg(snapshot.windDirectionDeg, cameraBearing)
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = stringResource(R.string.cd_wind_direction),
                modifier = Modifier
                    .size(arrowSize)
                    .rotate(windArrowRotationDeg),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.wind_label),
                fontSize = windLabelFontSize,
                lineHeight = windLabelFontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
```

- [ ] **Step 3: Update the CloudOff (unavailable) branch to use the new size constant**

In the same file, in the `else` branch (around line 123-141), replace the `Icon` for `CloudOff` (it currently uses `windIconSize`):

```kotlin
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = stringResource(R.string.cd_weather_unavailable),
                    modifier = Modifier.size(windIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
```

with:

```kotlin
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = stringResource(R.string.cd_weather_unavailable),
                    modifier = Modifier.size(unavailableIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
```

- [ ] **Step 4: Remove unused imports**

The following imports are no longer used. Remove them from `app/src/main/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidget.kt`:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Air
import androidx.compose.ui.draw.clip
```

(`androidx.compose.foundation.layout.Box` is still used by the `Column` — keep it. `Icons.Default.Navigation` is still used by the arrow — keep it. `androidx.compose.ui.draw.rotate` is still used by the arrow — keep it.)

- [ ] **Step 5: Run the new test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.WeatherWidgetTest.widget_withSnapshot_rendersWindLabel"`
Expected: PASS. The "Wind" label is now rendered.

- [ ] **Step 6: Run the full phone weather widget test class to verify no regressions**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.WeatherWidgetTest"`
Expected: All tests pass. The existing tests (`widget_withSnapshot_rendersTemperature`, `widget_withSnapshot_rendersWindSpeedAndGusts`, `widget_withSnapshot_rendersTrend`, `widget_withNullTrend_rendersDash`, `widget_rendersWindUnitLabel`, `widget_withoutSnapshot_rendersUnavailableState`, `widget_windIconHasContentDescription`, `widget_darkMode_windIconIsFindable`) all still pass — they don't assert anything about the removed Air badge, only about the temp, trend, speed, unit, and arrow content description.

- [ ] **Step 7: Hand off the production diff**

Show the user the `git diff` for `WeatherWidget.kt`. Do NOT commit.

---

## Task 4: Phone — rename the two "windIcon" tests for clarity

**Files:**
- Modify: `app/src/test/java/ca/voiditswarranty/roadtripradar/ui/WeatherWidgetTest.kt:150-188`

These two tests assert that the `cd_wind_direction` content description is present (i.e. the rotated arrow is findable). They were named "windIcon" which was misleading — the content description is on the arrow, not on the badge. Rename for accuracy.

- [ ] **Step 1: Rename `widget_windIconHasContentDescription`**

Replace the test method name on line 150:

```kotlin
    @Test
    fun widget_windIconHasContentDescription() {
```

with:

```kotlin
    @Test
    fun widget_windArrowHasContentDescription() {
```

- [ ] **Step 2: Rename `widget_darkMode_windIconIsFindable`**

Replace the test method name on line 171:

```kotlin
    @Test
    fun widget_darkMode_windIconIsFindable() {
```

with:

```kotlin
    @Test
    fun widget_darkMode_windArrowIsFindable() {
```

- [ ] **Step 3: Run the renamed tests to verify they still pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.ui.WeatherWidgetTest.widget_windArrowHasContentDescription" --tests "ca.voiditswarranty.roadtripradar.ui.WeatherWidgetTest.widget_darkMode_windArrowIsFindable"`
Expected: Both tests pass. The renames don't change the assertion.

- [ ] **Step 4: Hand off the rename diff**

Show the user the `git diff` for the test file. Do NOT commit.

---

## Task 5: Car — write the failing test for the "Wind" label

**Files:**
- Modify: `app/src/main/res/layout/car_weather_widget.xml` (add the new `TextView` so the test compiles)
- Modify: `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidgetTest.kt:60-74` (add helper + test)

- [ ] **Step 1: Add the new `TextView` to the layout XML**

Open `app/src/main/res/layout/car_weather_widget.xml`. Add a new `TextView` between `car_weather_wind_box` (ends at line 74) and `car_weather_wind_speed` (starts at line 76). Insert this block:

```xml
        <TextView
            android:id="@+id/car_weather_wind_label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textSize="20sp"
            android:gravity="center" />
```

- [ ] **Step 2: Run the layout compilation to verify the XML is well-formed**

Run: `./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Add the failing test**

Open `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidgetTest.kt` and add this test method at the end of the class (after `setDark_sameValue_isNoOp` on line 362). Also add the new helper alongside the existing helpers (after `windUnitTextOf` on line 71):

```kotlin
    private fun windLabelTextOf(w: CarWeatherWidget): TextView =
        w.view.findViewById(R.id.car_weather_wind_label)
```

And this new test at the end of the class:

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

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.car.CarWeatherWidgetTest.update_withSnapshot_setsWindLabelText"`
Expected: FAIL with "expected 'Wind' but was '' " (the production `CarWeatherWidget.update` doesn't yet set the label text, so the TextView is empty by default).

- [ ] **Step 5: Hand off the failing test diff**

Show the user the `git diff` for both files (XML and test). Do NOT commit.

---

## Task 6: Car — implement the "Wind" label in `CarWeatherWidget.kt` (make the test pass)

**Files:**
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidget.kt:53-78` (add field + init wire)
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidget.kt:95-117` (set text in update)
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidget.kt:120-143` (theme the text in applyColors)

- [ ] **Step 1: Add the field declaration**

In `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidget.kt`, add this line to the private field declarations (after `windUnitText: TextView` on line 57, before `offIcon: ImageView` on line 58):

```kotlin
    private val windLabelText: TextView
```

- [ ] **Step 2: Wire the field in `init`**

In the `init` block, add this line after the existing `windUnitText` wire (after line 75, before `offIcon`):

```kotlin
        windLabelText = view.findViewById(R.id.car_weather_wind_label)
```

- [ ] **Step 3: Set the label text in `update`**

In the `update` function, add this line in the `if (snap != null)` branch (after `arrow.rotation = ...` on line 113):

```kotlin
            windLabelText.text = context.getString(R.string.wind_label)
```

- [ ] **Step 4: Theme the label in `applyColors`**

In the `applyColors` function, add the label text color in the dark branch (after `windUnitText.setTextColor(darkTextSecondary)` on line 126):

```kotlin
            windLabelText.setTextColor(darkTextSecondary)
```

And in the light branch (after `windUnitText.setTextColor(lightTextSecondary)` on line 137):

```kotlin
            windLabelText.setTextColor(lightTextSecondary)
```

- [ ] **Step 5: Run the new test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.car.CarWeatherWidgetTest.update_withSnapshot_setsWindLabelText"`
Expected: PASS. The label is now populated with "Wind".

- [ ] **Step 6: Run the full car weather widget test class to verify no regressions**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.car.CarWeatherWidgetTest"`
Expected: All existing tests pass. The two palette tests (`update_dark_appliesDarkPalette`, `update_light_appliesLightPalette`) still pass because they don't yet reference the badge tint (we'll update them in Task 7 after removing the badge).

- [ ] **Step 7: Hand off the production diff**

Show the user the `git diff` for `CarWeatherWidget.kt`. Do NOT commit.

---

## Task 7: Car — shrink the arrow box (80dp → 64dp)

**Files:**
- Modify: `app/src/main/res/layout/car_weather_widget.xml:43-56`

- [ ] **Step 1: Shrink `car_weather_wind_box`**

In `app/src/main/res/layout/car_weather_widget.xml`, change lines 45-46 (the `car_weather_wind_box` dimensions):

```xml
            android:layout_width="80dp"
            android:layout_height="80dp"
```

to:

```xml
            android:layout_width="64dp"
            android:layout_height="64dp"
```

- [ ] **Step 2: Shrink `car_weather_arrow`**

Change lines 52-53 (the `car_weather_arrow` ImageView dimensions):

```xml
            android:layout_width="80dp"
            android:layout_height="80dp"
```

to:

```xml
            android:layout_width="64dp"
            android:layout_height="64dp"
```

- [ ] **Step 3: Run the car tests to verify nothing breaks**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.car.CarWeatherWidgetTest"`
Expected: All tests still pass. The arrow-rotation assertion is size-independent; the only change is the box dimension.

- [ ] **Step 4: Hand off the XML diff**

Show the user the `git diff` for `car_weather_widget.xml`. Do NOT commit.

---

## Task 8: Car — drop the badge (layout XML + Kotlin)

**Files:**
- Modify: `app/src/main/res/layout/car_weather_widget.xml:58-73` (delete badge `FrameLayout`)
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidget.kt:55-56` (drop `badge` and `airIcon` fields)
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidget.kt:71-73` (drop badge/airIcon wires)
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidget.kt:120-143` (drop badge/airIcon tint lines)
- Modify: `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidget.kt:154-165` (drop companion constants)
- Modify: `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidgetTest.kt` (drop `badgeOf`, drop badge tint assertions, add wind-label text color assertions)

- [ ] **Step 1: Delete the badge `FrameLayout` from the layout**

In `app/src/main/res/layout/car_weather_widget.xml`, delete lines 58-73 (the entire `FrameLayout` containing `car_weather_badge` and `car_weather_air`):

```xml
            <FrameLayout
                android:id="@+id/car_weather_badge"
                android:layout_width="27dp"
                android:layout_height="27dp"
                android:layout_gravity="center"
                android:background="@drawable/car_weather_widget_bg"
                android:backgroundTint="#00000000">

                <ImageView
                    android:id="@+id/car_weather_air"
                    android:layout_width="22dp"
                    android:layout_height="22dp"
                    android:layout_gravity="center"
                    android:scaleType="centerInside"
                    android:src="@drawable/ic_car_wind" />
            </FrameLayout>
```

- [ ] **Step 2: Drop the Kotlin field declarations**

In `app/src/main/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidget.kt`, delete the `badge` and `airIcon` field declarations (lines 55-56):

```kotlin
    private val badge: FrameLayout
    private val airIcon: ImageView
```

- [ ] **Step 3: Drop the field wires in `init`**

Delete lines 72-73 (the `badge` and `airIcon` `findViewById` calls):

```kotlin
        badge = view.findViewById(R.id.car_weather_badge)
        airIcon = view.findViewById(R.id.car_weather_air)
```

- [ ] **Step 4: Drop the badge/airIcon tint lines in `applyColors`**

In the dark branch (line 129-131), delete:

```kotlin
            airIcon.imageTintList = ColorStateList.valueOf(darkAirTint)
            offIcon.imageTintList = ColorStateList.valueOf(darkTextSecondary)
            badge.backgroundTintList = ColorStateList.valueOf(darkBadgeBg)
```

And replace with just the `offIcon` line (which stays):

```kotlin
            offIcon.imageTintList = ColorStateList.valueOf(darkTextSecondary)
```

In the light branch (line 140-142), delete:

```kotlin
            airIcon.imageTintList = ColorStateList.valueOf(lightAirTint)
            offIcon.imageTintList = ColorStateList.valueOf(lightTextSecondary)
            badge.backgroundTintList = ColorStateList.valueOf(lightBadgeBg)
```

And replace with just the `offIcon` line:

```kotlin
            offIcon.imageTintList = ColorStateList.valueOf(lightTextSecondary)
```

- [ ] **Step 5: Drop the companion constants**

In the `companion object` (lines 154-165), delete these four lines (the entire block of badge/air tints):

```kotlin
        @JvmField val darkAirTint: Int = 0xFFFFFFFF.toInt()

        // Light palette — drawn on top of a light map. Background is 80% white,
        // the "air" badge is 90% white so the air icon reads strongly, primary
        // text is near-black, secondary is mid-grey, arrow is a saturated dark blue.
        @JvmField val lightBackground: Int = 0xCCFFFFFF.toInt()
        @JvmField val lightTextMain: Int = 0xFF212121.toInt()
        @JvmField val lightTextSecondary: Int = 0xFF616161.toInt()
        @JvmField val lightArrowTint: Int = 0xFF1565C0.toInt()
        @JvmField val lightBadgeBg: Int = 0xE6FFFFFF.toInt()
        @JvmField val lightAirTint: Int = 0xFF212121.toInt()
```

becomes (drop `darkAirTint` and `lightAirTint` and `darkBadgeBg` and `lightBadgeBg`):

```kotlin
        @JvmField val darkBackground: Int = 0xB3000000.toInt()
        @JvmField val darkTextMain: Int = 0xFFFFFFFF.toInt()
        @JvmField val darkTextSecondary: Int = 0xB3FFFFFF.toInt()
        @JvmField val darkArrowTint: Int = 0xFF90CAF9.toInt()

        // Light palette — drawn on top of a light map. Background is 80% white,
        // primary text is near-black, secondary is mid-grey, arrow is a saturated dark blue.
        @JvmField val lightBackground: Int = 0xCCFFFFFF.toInt()
        @JvmField val lightTextMain: Int = 0xFF212121.toInt()
        @JvmField val lightTextSecondary: Int = 0xFF616161.toInt()
        @JvmField val lightArrowTint: Int = 0xFF1565C0.toInt()
```

- [ ] **Step 6: Update the KDoc comments**

In the class KDoc (lines 19-25), drop "with an 'air' badge" and add "with a 'Wind' label below". The relevant text on line 22 currently reads:

```kotlin
 * [ca.voiditswarranty.roadtripradar.ui.WeatherWidget]: temperature + per-hour trend, a wind arrow
 * rotated by the wind direction (relative to the map bearing) with an "air" badge, then wind
```

Replace with:

```kotlin
 * [ca.voiditswarranty.roadtripradar.ui.WeatherWidget]: temperature + per-hour trend, a wind arrow
 * rotated by the wind direction (relative to the map bearing) with a "Wind" label below, then wind
```

- [ ] **Step 7: Update the car tests — drop `badgeOf`, swap palette assertions**

In `app/src/test/java/ca/voiditswarranty/roadtripradar/car/CarWeatherWidgetTest.kt`:

(a) Delete the `badgeOf` helper (lines 73-74):

```kotlin
    private fun badgeOf(w: CarWeatherWidget): FrameLayout =
        w.view.findViewById(R.id.car_weather_badge)
```

(b) Remove the `import android.widget.FrameLayout` if no longer needed — grep the file to confirm no other reference (after the deletion, FrameLayout should not be referenced). Delete the import line if unused.

(c) In `update_dark_appliesDarkPalette` (around line 257-289), delete the `dark badge bg` assertion block (the last assertEquals in that test):

```kotlin
        assertEquals(
            "dark badge bg",
            CarWeatherWidget.darkBadgeBg,
            badgeOf(w).backgroundTintList?.defaultColor,
        )
```

And add a new assertion right after the existing `dark arrow tint` assertion:

```kotlin
        assertEquals(
            "dark wind label text color",
            CarWeatherWidget.darkTextSecondary,
            windLabelTextOf(w).currentTextColor,
        )
```

(d) In `update_light_appliesLightPalette` (around line 291-323), do the same:

Delete:

```kotlin
        assertEquals(
            "light badge bg",
            CarWeatherWidget.lightBadgeBg,
            badgeOf(w).backgroundTintList?.defaultColor,
        )
```

Add:

```kotlin
        assertEquals(
            "light wind label text color",
            CarWeatherWidget.lightTextSecondary,
            windLabelTextOf(w).currentTextColor,
        )
```

- [ ] **Step 8: Run the full car test class to verify everything passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.car.CarWeatherWidgetTest"`
Expected: All tests pass, including the modified palette tests and the new `update_withSnapshot_setsWindLabelText`.

- [ ] **Step 9: Verify the production code compiles cleanly**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. No dangling references to `badge`, `airIcon`, or the deleted companion constants.

- [ ] **Step 10: Hand off the diff**

Show the user the `git diff` for the three files (`car_weather_widget.xml`, `CarWeatherWidget.kt`, `CarWeatherWidgetTest.kt`). Do NOT commit.

---

## Task 9: Delete the now-unused `ic_car_wind.xml` drawable

**Files:**
- Delete: `app/src/main/res/drawable/ic_car_wind.xml`

- [ ] **Step 1: Verify the drawable is no longer referenced**

Run: `grep -rn "ic_car_wind" /home/chris/repos/RoadTripRadar2/app/src/main /home/chris/repos/RoadTripRadar2/app/src/test /home/chris/repos/RoadTripRadar2/app/src/androidTest 2>/dev/null`
Expected: Only one match — the file itself, `app/src/main/res/drawable/ic_car_wind.xml`. (The arrow drawable `ic_car_wind_arrow.xml` is a different file and stays.) If anything else references `ic_car_wind`, stop and investigate — something was missed.

- [ ] **Step 2: Delete the file**

Run: `rm /home/chris/repos/RoadTripRadar2/app/src/main/res/drawable/ic_car_wind.xml`

- [ ] **Step 3: Verify the build still compiles**

Run: `./gradlew :app:compileDebugKotlin :app:processDebugResources`
Expected: BUILD SUCCESSFUL. Removing the unused drawable shouldn't break anything.

- [ ] **Step 4: Hand off the deletion**

Show the user the `git status` and the file deletion. Do NOT commit.

---

## Task 10: Update the changelog

**Files:**
- Modify: `app/src/main/assets/changelog.json:7-15` (append a bullet to the `next` release)

- [ ] **Step 1: Append a bullet to the `next` release**

In `app/src/main/assets/changelog.json`, add a new bullet to the `next` release's `items` array. The current list has 8 bullets (lines 8-15). Add this as a new bullet (9th) at the end of the array (before the closing `]` on line 16):

```json
        "Weather card: removed the wind icon, added a 'Wind' label below the arrow, and slightly reduced the arrow size for a cleaner look.",
```

Match the existing formatting: 8-space indent, double-quoted string, trailing comma (the existing bullets have trailing commas because they aren't the last item — but this new one IS the last item, so it has no trailing comma).

- [ ] **Step 2: Verify the JSON parses**

Run: `python3 -c "import json; json.load(open('/home/chris/repos/RoadTripRadar2/app/src/main/assets/changelog.json'))" && echo OK`
Expected: prints `OK`. If it fails, fix the JSON.

- [ ] **Step 3: Hand off the diff**

Show the user the `git diff` for `changelog.json`. Do NOT commit.

---

## Task 11: Final verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: All tests pass. Expected total: **907 tests, 0 failures, 0 errors** (905 baseline + 1 new phone test + 1 new car test).

Verify the result by aggregating XMLs:
```bash
grep -h "tests=" /home/chris/repos/RoadTripRadar2/app/build/test-results/testDebugUnitTest/*.xml | grep -oE 'tests="[0-9]+" skipped="[0-9]+" failures="[0-9]+" errors="[0-9]+"' | awk -F'"' '{tests+=$2; skipped+=$4; failures+=$6; errors+=$8} END {print "Total tests:", tests, "| Skipped:", skipped, "| Failures:", failures, "| Errors:", errors}'
```
Expected output: `Total tests: 907 | Skipped: 0 | Failures: 0 | Errors: 0`

- [ ] **Step 2: Run the androidTest compile**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL. (The androidTest sourceset shouldn't be affected, but verify.)

- [ ] **Step 3: Verify no dangling references**

Run: `grep -rn "windIcon\|windIconSize\|windBadgeSize\|darkAirTint\|lightAirTint\|darkBadgeBg\|lightBadgeBg\|car_weather_badge\|car_weather_air" /home/chris/repos/RoadTripRadar2/app/src/main /home/chris/repos/RoadTripRadar2/app/src/test /home/chris/repos/RoadTripRadar2/app/src/androidTest 2>/dev/null`
Expected: no output. If anything matches, the cleanup missed a reference.

- [ ] **Step 4: Hand off the final summary**

Show the user:
- `git status` (the full list of changed/new/deleted files)
- `git diff --stat` (line-count summary)
- A short summary of the 11 tasks completed

Do NOT commit (per the user's "Don't commit, I will do that" rule).

---

## Self-review

**1. Spec coverage:** every section of the spec maps to a task:
- Phone `WeatherWidget.kt` → Tasks 3, 4 (size constants, badge removal, label add, CloudOff branch fix, imports)
- Car `CarWeatherWidget.kt` + `res/layout/car_weather_widget.xml` → Tasks 5, 6, 7, 8 (label, shrink, drop badge)
- Strings → Task 1
- Changelog → Task 10
- Tests → Tasks 2, 4, 5, 8 (phone new test, phone renames, car new test, car palette swap)
- Asset deletion → Task 9
- Verification → Task 11

**2. Placeholder scan:** no "TBD", "TODO", "fill in" placeholders. Every step has exact file paths, exact code blocks, exact commands.

**3. Type consistency:**
- `arrowSize`, `windLabelFontSize`, `unavailableIconSize` introduced in Task 3 Step 1; used in Tasks 3 Step 2-3.
- `windLabelText: TextView` introduced in Task 6 Step 1; used in Tasks 6 Step 2-4 and Task 8 Step 7 (in tests).
- `R.id.car_weather_wind_label` introduced in Task 5 Step 1; used in Task 5 Step 3 and Task 6 Step 2.
- `R.string.wind_label` introduced in Task 1; used in Task 3 Step 2 and Task 6 Step 3.
- `windLabelTextOf()` helper introduced in Task 5 Step 3; used in Task 5 Step 3 (its own test) and Task 8 Step 7 (palette tests).
- Companion constants removed in Task 8 Step 5; tests referencing them removed in Task 8 Step 7.
