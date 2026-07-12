package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [HomeScreen] (and its shared [carMenuRows] builder) — the menu hub the user
 * lands on from the map screen's toolbar Menu action. The car surface is deliberately lean:
 * the menu is a clean two-row hub — **Places** (→ [PoiScreen]) and **Settings**
 * (→ [SettingsScreen]). Everything glanceable (weather, active waypoint, radar status) lives
 * on the map widgets/toolbar, and everything adjustable (radar settings, route management,
 * POI-pipeline utilities) lives either on the phone or behind [PoiScreen]'s "More" action —
 * not on this menu.
 *
 * The test pins the exact two-row contents (Places, Settings, in order). That exact-list
 * assertion is the regression guard: if a future change re-adds one of the removed rows
 * (weather, active waypoint, radar status, or a POI-pipeline utility row), the list no
 * longer equals exactly [Places, Settings] and the test fails.
 *
 * Uses Robolectric + `TestCarContext` so [ListTemplate] / [Row] get built with a real
 * `Context` (their CarText spans need one). Pinned to SDK 33 because the real
 * [ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel] registers a default network
 * callback in `init` that Robolectric 4.16.1's `ConnectivityManager` shadow only implements
 * on SDK 33.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeScreenTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun buildHomeScreen(): ListTemplate {
        val carContext = TestCarContext.createCarContext(context)
        val screen = HomeScreen(carContext)
        return screen.onGetTemplate() as ListTemplate
    }

    private fun rowsOf(template: ListTemplate): List<Row> =
        template.singleList!!.items.filterIsInstance<Row>()

    private fun rowTitles(template: ListTemplate): List<String> =
        rowsOf(template).map { it.title!!.toCharSequence().toString() }

    // -------- template structure --------

    @Test
    fun homeScreen_titleMatchesCarHomeTitleString() {
        // The header's title is the source of truth for what the car host shows in
        // the header. Pinned to the string resource so the test references the
        // source of truth — adding a new locale won't drift the contract.
        val template = buildHomeScreen()
        val expected = context.getString(R.string.car_home_title)
        assertEquals(expected, template.header!!.title!!.toCharSequence().toString())
    }

    @Test
    fun homeScreen_hasBackHeaderAction() {
        // HomeScreen is a pushed screen; the host requires a BACK header action so
        // the user can return to the caller (the map screen).
        val template = buildHomeScreen()
        assertNotNull(
            "pushed HomeScreen must have a header action (BACK)",
            template.header!!.startHeaderAction,
        )
    }

    // -------- the two retained rows --------

    @Test
    fun homeScreen_hasExactlyTwoRows_placesAndSettings() {
        // The lean car menu is a two-row hub: Places then Settings, in that order.
        // Pinned to the string resources so a future i18n change surfaces as a test
        // diff rather than silently breaking the contract. This exact-list check is
        // the regression guard against re-adding the removed weather / active-waypoint
        // / radar / POI-pipeline-utility rows — any of those would change the list.
        val template = buildHomeScreen()
        val titles = rowTitles(template)
        assertEquals(
            "menu must have exactly two rows",
            listOf(
                context.getString(R.string.car_poi_title),
                context.getString(R.string.car_settings_title),
            ),
            titles,
        )
    }
}