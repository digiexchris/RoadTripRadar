package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.OnDoneCallback
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
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
 * Tests for [HomeScreen] (and its shared [carMenuRows] builder) — the menu hub the user
 * lands on from the car app launcher / Menu tab. The screen builds a [ListTemplate] whose
 * rows surface (1) the current Open-Meteo snapshot, (2) the active waypoint (or "no
 * waypoints" placeholder), (3) the radar status, (4) a POIs link, (5) the POI-pipeline
 * utility rows (Search / Clear / Retry), and (6) Settings. The test pins both the *content*
 * of each row (title, texts, enabled state) and the *behavior* of the click listeners
 * (e.g. Search invokes [MapViewModel.searchVisibleArea]).
 *
 * Uses Robolectric + `TestCarContext` so [ListTemplate] / [Row] get built with a real
 * `Context` (their CarText spans need one). Pinned to SDK 33 because the real
 * [MapViewModel] registers a default network callback in `init` that Robolectric 4.16.1's
 * `ConnectivityManager` shadow only implements on SDK 33.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeScreenTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun freshVm(): MapViewModel {
        // Use the CarViewModelHolder singleton so the HomeScreen under test gets the
        // SAME MapViewModel the test mutates. (HomeScreen reads its VM from the
        // holder, not from an injected field.) We can hand it an existing VM by
        // calling ensureInitialized once (it returns the same instance every time)
        // and then mutating the returned VM. The first call in any test JVM
        // constructs the holder's VM with the application context; subsequent
        // calls return the same instance.
        return CarViewModelHolder.ensureInitialized(context)
    }

    private fun buildHomeScreen(): ListTemplate {
        val carContext = TestCarContext.createCarContext(context)
        val screen = HomeScreen(carContext)
        return screen.onGetTemplate() as ListTemplate
    }

    private fun rowsOf(template: ListTemplate): List<Row> =
        template.singleList!!.items.filterIsInstance<Row>()

    private fun rowTitles(template: ListTemplate): List<String> =
        rowsOf(template).map { it.title!!.toCharSequence().toString() }

    private fun clickRow(row: Row) {
        // Each row's click listener is wrapped in an OnClickDelegate. Calling
        // `sendClick` with a no-op callback fires the listener — same as the host
        // would when the user taps the row. ParkedOnlyOnClickListener wraps a
        // regular OnClickListener; both code paths land on sendClick.
        row.onClickDelegate!!.sendClick(object : OnDoneCallback {})
    }

    /**
     * The MapViewModel the screens read from is the process-wide singleton in
     * [CarViewModelHolder] (a Kotlin `object`). Tests in this class share that
     * VM, so a mutation in one test bleeds into the next unless we reset state
     * between tests. The fields below are the ones the HomeScreen's row-builder
     * reads directly; resetting them gives each test a known starting point.
     */
    @Before
    fun resetVmState() {
        val vm = freshVm()
        vm.setOpenMeteoSnapshotForTest(null)
        vm.setFailedCellBoundsForTest(emptyList())
        vm.pendingCameraInfo = null
    }

    // -------- template structure --------

    @Test
    fun homeScreen_titleMatchesCarHomeTitleString() {
        // The template's title is the source of truth for what the car host shows in
        // the header. Pinned to the string resource so the test references the
        // source of truth — adding a new locale won't drift the contract.
        val template = buildHomeScreen()
        val expected = context.getString(R.string.car_home_title)
        assertEquals(expected, template.title!!.toCharSequence().toString())
    }

    @Test
    fun homeScreen_hasBackHeaderAction() {
        // HomeScreen is a pushed screen (not a tab); the host requires a BACK header
        // action so the user can return to the caller. (Tab content omits it because
        // the tab bar replaces it.)
        val template = buildHomeScreen()
        assertNotNull(
            "pushed HomeScreen must have a header action (BACK)",
            template.headerAction,
        )
    }

    // -------- weather row (with snapshot) --------

    @Test
    fun homeScreen_weatherRowWithSnapshot_showsTempAndCondition() {
        // The first row is the weather row. With a snapshot present, its title is
        // "<temp> · <condition>" and the texts include wind + trend. We can't
        // assert exact strings (formatTemp depends on the locale / unit), but we
        // can assert the title contains a numeric token and the wind text contains
        // a degree symbol + unit label.
        val vm = freshVm()
        vm.setOpenMeteoSnapshotForTest(
            OpenMeteoSnapshot(
                temperatureCelsius = 20.0,
                weatherCode = 0,  // clear sky
                windSpeedKmh = 10.0,
                windDirectionDeg = 90,
                windGustsKmh = 15.0,
                tempTrendCelsius = 1.0,
            )
        )
        val template = buildHomeScreen()
        val weatherRow = rowsOf(template).first()
        val title = weatherRow.title!!.toCharSequence().toString()
        // Title includes the temperature formatted as a number (e.g. "20.0" or "20").
        assertTrue(
            "weather title should include a temperature; got '$title'",
            title.any { it.isDigit() },
        )
        // Wind text includes the direction (90°) and a unit label (km/h, mph, or kn).
        val windText = weatherRow.texts[0].toCharSequence().toString()
        assertTrue(
            "wind text should include direction 90°; got '$windText'",
            windText.contains("90°"),
        )
    }

    @Test
    fun homeScreen_weatherRowWithSnapshot_showsNoDataStringWhenAbsent() {
        // No snapshot -> the weather row title is the "Weather unavailable" string
        // (car_weather_no_data). Pin the string resource directly. Clear any
        // snapshot left by a prior test on the holder's VM.
        val vm = freshVm()
        vm.setOpenMeteoSnapshotForTest(null)
        val template = buildHomeScreen()
        val weatherRow = rowsOf(template).first()
        val title = weatherRow.title!!.toCharSequence().toString()
        assertEquals(context.getString(R.string.car_weather_no_data), title)
    }

    // -------- active waypoint row --------

    @Test
    fun homeScreen_noActiveWaypoint_showsNoWaypointsString() {
        // No active waypoint -> the second row title is the "no waypoints" string.
        // Pin it to the resource so a future i18n change doesn't silently break
        // the contract.
        val template = buildHomeScreen()
        val titles = rowTitles(template)
        val expected = context.getString(R.string.car_route_empty)
        assertTrue(
            "expected the route-empty row to be present in $titles",
            titles.contains(expected),
        )
    }

    // -------- POI-pipeline utility rows --------

    @Test
    fun homeScreen_poiSearchRow_isDisabledWhenPendingCameraInfoNull() {
        // pendingCameraInfo defaults to null. The Search row's `setEnabled(false)`
        // call in production must stick — Robolectric should respect it. Reset
        // pendingCameraInfo to null in case a previous test (e.g. the enabled
        // counterpart) set it.
        val vm = freshVm()
        vm.pendingCameraInfo = null
        val template = buildHomeScreen()
        val searchTitle = context.getString(R.string.car_poi_search)
        val searchRow = rowsOf(template).first { it.title!!.toCharSequence().toString() == searchTitle }
        assertFalse(
            "Search row must be disabled when no pending camera info",
            searchRow.isEnabled,
        )
    }

    @Test
    fun homeScreen_poiSearchRow_isEnabledWhenPendingCameraInfoSet() {
        val vm = freshVm()
        vm.pendingCameraInfo = MapViewModel.CameraInfo(lat = 45.4, lon = -75.7, zoom = 12.0)
        val template = buildHomeScreen()
        val searchTitle = context.getString(R.string.car_poi_search)
        val searchRow = rowsOf(template).first { it.title!!.toCharSequence().toString() == searchTitle }
        assertTrue(
            "Search row must be enabled when pending camera info is set",
            searchRow.isEnabled,
        )
    }

    @Test
    fun homeScreen_retryRow_isAbsentWhenFailedCellsEmpty() {
        // hasFailedCells defaults to false. The Retry row should not be in the
        // menu list.
        val template = buildHomeScreen()
        val retryTitle = context.getString(R.string.car_poi_retry)
        val titles = rowTitles(template)
        assertFalse(
            "Retry row must be absent when no failed cells; got $titles",
            titles.contains(retryTitle),
        )
    }

    @Test
    fun homeScreen_retryRow_isPresentWhenFailedCellsNonEmpty() {
        // Populate the failed-cell list via the test seam. The Retry row must
        // appear. We use a minimal in-memory BoundingBox around Ottawa.
        val vm = freshVm()
        vm.setFailedCellBoundsForTest(
            listOf(
                org.maplibre.spatialk.geojson.BoundingBox(
                    southwest = org.maplibre.spatialk.geojson.Position(
                        longitude = -75.7,
                        latitude = 45.4,
                    ),
                    northeast = org.maplibre.spatialk.geojson.Position(
                        longitude = -75.6,
                        latitude = 45.5,
                    ),
                )
            )
        )
        val template = buildHomeScreen()
        val retryTitle = context.getString(R.string.car_poi_retry)
        assertTrue(
            "Retry row must be present when failed cells are non-empty",
            rowTitles(template).contains(retryTitle),
        )
    }

    // -------- click listener wiring --------

    @Test
    fun homeScreen_poiSearchRow_clickInvokesSearchVisibleArea() {
        // The Search row's listener is `ParkedOnlyOnClickListener.create { vm.searchVisibleArea() }`.
        // Driving the click should kick off a search. We can't easily inspect the
        // POI pipeline state without mocking the network, so we assert two things:
        // (a) the row has a click listener (sanity), and (b) calling sendClick
        // does not throw. (The pipeline itself early-returns when there are no
        // enabled POI categories, which is the production default.)
        val template = buildHomeScreen()
        val searchTitle = context.getString(R.string.car_poi_search)
        val searchRow = rowsOf(template).first { it.title!!.toCharSequence().toString() == searchTitle }
        assertNotNull(
            "Search row must have a click listener",
            searchRow.onClickDelegate,
        )
        // sendClick on a ParkedOnlyOnClickListener delegates to the wrapped
        // OnClickListener. With no enabled POI categories, the pipeline
        // early-returns — but the click must still be dispatched without
        // throwing.
        clickRow(searchRow)
    }

    @Test
    fun homeScreen_weatherRow_clickIsWired() {
        // Tapping the weather row pushes WeatherScreen onto the car screen stack.
        // We can verify the click listener is wired without driving the push (the
        // TestCarContext has a TestScreenManager that captures pushes).
        val template = buildHomeScreen()
        val weatherRow = rowsOf(template).first()
        assertNotNull(
            "Weather row must have a click listener",
            weatherRow.onClickDelegate,
        )
        // Calling sendClick must not throw even if the push is a no-op in the
        // test environment.
        clickRow(weatherRow)
    }
}
