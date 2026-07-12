package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.OnDoneCallback
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Toggle
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.PrefsDefaults
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [SettingsScreen] — the car surface that surfaces unit toggles (metric,
 * wind speed, temperature), keep-screen-on, and auto-advance + threshold presets. Map-style
 * cycling and reset-to-defaults are phone-only and are intentionally absent from the car
 * settings screen. The screen builds a [ListTemplate] whose row structure changes with two
 * VM-state inputs:
 *
 * - Whether the user has accepted the current [PrefsDefaults.TERMS_VERSION] (controls
 *   the "terms not accepted" parked-only row at the top of the list).
 * - The current [MapViewModel.useMetric] / wind / temperature / etc. values (control
 *   each row's text + the click delegate's effect).
 *
 * The test pins:
 * - the structure (which rows appear, terms-row present/absent, map-style + reset absent),
 * - the toggle initial state (metric toggle reflects `vm.useMetric`),
 * - the unit-row click behavior (cycles to the next enum entry),
 * - the threshold click behavior (cycles through 25/50/100/200/500 m).
 *
 * Uses Robolectric + `TestCarContext` so [ListTemplate] / [Row] / [Toggle] get
 * built with a real `Context`. Pinned to SDK 33 because the real [MapViewModel]
 * registers a default network callback in `init` that Robolectric 4.16.1's
 * `ConnectivityManager` shadow only implements on SDK 33.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsScreenTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun vm() = CarViewModelHolder.ensureInitialized(context)

    private fun buildScreen(): ListTemplate {
        val carContext = TestCarContext.createCarContext(context)
        val screen = SettingsScreen(carContext)
        return screen.onGetTemplate() as ListTemplate
    }

    private fun rowsOf(template: ListTemplate): List<Row> =
        template.singleList!!.items.filterIsInstance<Row>()

    private fun rowTitles(template: ListTemplate): List<String> =
        rowsOf(template).map { it.title!!.toCharSequence().toString() }

    private fun rowByTitle(template: ListTemplate, title: String): Row =
        rowsOf(template).first { it.title!!.toCharSequence().toString() == title }

    private fun clickRow(row: Row) {
        row.onClickDelegate!!.sendClick(object : OnDoneCallback {})
    }

    private fun toggleRow(row: Row, checked: Boolean) {
        // Toggle's checked-change delegate: call sendCheckedChange with the new
        // state to invoke the OnCheckedChangeListener wired at row-build time.
        row.toggle!!.onCheckedChangeDelegate.sendCheckedChange(
            checked,
            object : OnDoneCallback {},
        )
    }

    /**
     * Reset VM state that the SettingsScreen reads. (The VM is a process-wide
     * singleton; state from a prior test bleeds unless we restore defaults here.)
     */
    @Before
    fun resetSettings() {
        val v = vm()
        v.updateUseMetric(PrefsDefaults.USE_METRIC)
        v.updateWindSpeedUnit(WindSpeedUnit.valueOf(PrefsDefaults.WIND_SPEED_UNIT))
        v.updateTemperatureUnit(TemperatureUnit.valueOf(PrefsDefaults.TEMPERATURE_UNIT))
        v.updateMapStyle(MapStyle.LIBERTY)
        v.updateKeepScreenOn(PrefsDefaults.KEEP_SCREEN_ON)
        v.updateAutoAdvanceEnabled(PrefsDefaults.AUTO_ADVANCE_ENABLED)
        v.updateAutoAdvanceThreshold(PrefsDefaults.AUTO_ADVANCE_THRESHOLD_M)
        v.prefsRepo.acceptedTermsVersion = PrefsDefaults.TERMS_VERSION
    }

    // -------- terms row --------

    @Test
    fun settingsScreen_termsNotAccepted_showsTermsRow() {
        val v = vm()
        v.prefsRepo.acceptedTermsVersion = PrefsDefaults.TERMS_VERSION - 1
        val template = buildScreen()
        val titles = rowTitles(template)
        val expected = context.getString(R.string.car_terms_not_accepted)
        assertTrue(
            "terms-not-accepted state must show the terms row; got $titles",
            titles.contains(expected),
        )
    }

    @Test
    fun settingsScreen_termsAccepted_omitsTermsRow() {
        // Default state from @Before: terms accepted. The terms row must NOT be
        // present.
        val template = buildScreen()
        val titles = rowTitles(template)
        val termsTitle = context.getString(R.string.car_terms_not_accepted)
        assertFalse(
            "terms-accepted state must NOT show the terms row; got $titles",
            titles.contains(termsTitle),
        )
    }

    // -------- metric toggle --------

    @Test
    fun settingsScreen_metricToggle_reflectsVmUseMetric() {
        val v = vm()
        v.updateUseMetric(true)
        val template = buildScreen()
        val metricRow = rowByTitle(template, context.getString(R.string.car_settings_metric))
        assertTrue("metric toggle should be checked when useMetric=true", metricRow.toggle!!.isChecked)
        v.updateUseMetric(false)
        val template2 = buildScreen()
        val metricRow2 = rowByTitle(template2, context.getString(R.string.car_settings_metric))
        assertEquals("metric toggle should be unchecked when useMetric=false", false, metricRow2.toggle!!.isChecked)
    }

    @Test
    fun settingsScreen_metricToggle_changeInvokesVmUpdateUseMetric() {
        val v = vm()
        v.updateUseMetric(false)  // start off
        val template = buildScreen()
        val metricRow = rowByTitle(template, context.getString(R.string.car_settings_metric))
        toggleRow(metricRow, checked = true)
        assertTrue("toggling metric on must set vm.useMetric=true", v.useMetric)
    }

    // -------- wind unit cycle row --------

    @Test
    fun settingsScreen_windUnitRow_clickCyclesToNext() {
        val v = vm()
        v.updateWindSpeedUnit(WindSpeedUnit.KMH)
        val template = buildScreen()
        val windRow = rowByTitle(template, context.getString(R.string.car_settings_wind_unit))
        clickRow(windRow)
        assertEquals(
            "wind unit click must cycle to the next enum entry",
            WindSpeedUnit.MPH,
            v.windSpeedUnit,
        )
    }

    @Test
    fun settingsScreen_windUnitRow_textShowsCurrentUnit() {
        val v = vm()
        v.updateWindSpeedUnit(WindSpeedUnit.KNOTS)
        val template = buildScreen()
        val windRow = rowByTitle(template, context.getString(R.string.car_settings_wind_unit))
        // windUnitLabel renders "kn" for knots; the row's first text is the
        // localized label.
        val text = windRow.texts[0].toCharSequence().toString()
        assertNotNull(text)
        assertTrue(
            "wind unit row text should include 'kn' for KNOTS; got '$text'",
            text.contains("kn", ignoreCase = true),
        )
    }

    // -------- temperature unit cycle row --------

    @Test
    fun settingsScreen_tempUnitRow_clickCyclesToNext() {
        val v = vm()
        v.updateTemperatureUnit(TemperatureUnit.CELSIUS)
        val template = buildScreen()
        val tempRow = rowByTitle(template, context.getString(R.string.car_settings_temp_unit))
        clickRow(tempRow)
        assertEquals(
            "temperature unit click must cycle to the next enum entry",
            TemperatureUnit.FAHRENHEIT,
            v.temperatureUnit,
        )
    }

    // -------- map style row (removed — phone-only) --------

    // The map-style row's absence is pinned by settingsScreen_retainedRowsAreExact
    // below (an exact-list assertion that would fail if the row were re-added), so
    // no separate absence test is needed here.

    // -------- auto-advance threshold row --------

    @Test
    fun settingsScreen_thresholdRow_clickCyclesToNextPreset() {
        // The default threshold is 100 m (per PrefsDefaults); the next preset in
        // the cycle is 200 m. The click also calls `saveAutoAdvanceThreshold` —
        // pin only the unit change here, since `save` is a persistence side
        // effect.
        val v = vm()
        v.updateAutoAdvanceThreshold(25)
        val template = buildScreen()
        val thresholdRow = rowByTitle(template, context.getString(R.string.car_settings_threshold))
        clickRow(thresholdRow)
        assertEquals(
            "threshold click from 25m must cycle to 50m",
            50,
            v.autoAdvanceThresholdMeters,
        )
    }

    @Test
    fun settingsScreen_thresholdRow_textShowsCurrentMeters() {
        val v = vm()
        v.updateAutoAdvanceThreshold(200)
        val template = buildScreen()
        val thresholdRow = rowByTitle(template, context.getString(R.string.car_settings_threshold))
        val text = thresholdRow.texts[0].toCharSequence().toString()
        assertTrue(
            "threshold text should include '200' and 'm'; got '$text'",
            text.contains("200") && text.contains("m"),
        )
    }

    // -------- keep-screen-on toggle --------

    @Test
    fun settingsScreen_keepScreenOnToggle_reflectsVmState() {
        val v = vm()
        v.updateKeepScreenOn(true)
        val template = buildScreen()
        val row = rowByTitle(template, context.getString(R.string.car_settings_keep_screen_on))
        assertTrue("keep-screen-on toggle should be checked when enabled", row.toggle!!.isChecked)
    }

    // -------- reset row (removed — phone-only) --------

    // The reset row's absence is pinned by settingsScreen_retainedRowsAreExact below
    // (an exact-list assertion that would fail if the row were re-added), so no
    // separate absence test is needed here.

    // -------- retained rows (exact list) --------

    @Test
    fun settingsScreen_retainedRowsAreExact() {
        // With terms accepted (@Before default), the screen surfaces exactly the six
        // retained adjustable rows, in order: metric, wind unit, temp unit,
        // keep-screen-on, auto-advance, auto-advance threshold. This exact-list check
        // is the regression guard against re-adding the removed map-style and reset
        // rows — either would change the list.
        val titles = rowTitles(buildScreen())
        val expected = listOf(
            R.string.car_settings_metric,
            R.string.car_settings_wind_unit,
            R.string.car_settings_temp_unit,
            R.string.car_settings_keep_screen_on,
            R.string.car_settings_auto_advance,
            R.string.car_settings_threshold,
        ).map { context.getString(it) }
        assertEquals(
            "settings must have exactly the retained rows (no map-style, no reset)",
            expected,
            titles,
        )
    }
}
