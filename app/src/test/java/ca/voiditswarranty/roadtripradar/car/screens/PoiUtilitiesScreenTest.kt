package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.OnDoneCallback
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [PoiUtilitiesScreen] — the POI-pipeline utilities relocated off the HomeScreen
 * menu and behind PoiScreen's "More" end-header action. It is a [ListTemplate] with three
 * rows:
 *
 * - **Search area** (parked-only; enabled only when `vm.pendingCameraInfo != null`) — re-runs
 *   the POI pipeline against the phone's current map camera via [MapViewModel.searchVisibleArea].
 * - **Clear** (parked-only) — drops all loaded POIs via [MapViewModel.clearNearbyPois].
 * - **Retry failed** (driving-ok; present only when `vm.hasFailedCells`) — retries the
 *   permanently-failed pipeline cells via [MapViewModel.retryFailedCells].
 *
 * The test pins each row's presence/enabled state and the Clear row's side effect (it stops
 * the pipeline). The Search/Retry listeners are asserted to be wired + dispatch without
 * throwing, matching the style of the old HomeScreen utility-row tests.
 *
 * Uses Robolectric + `TestCarContext` so [ListTemplate] / [Row] build with a real `Context`.
 * Pinned to SDK 33 because the real [MapViewModel] registers a default network callback in
 * `init` that Robolectric 4.16.1's `ConnectivityManager` shadow only implements on SDK 33.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PoiUtilitiesScreenTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun vm() = CarViewModelHolder.ensureInitialized(context)

    private fun buildScreen(): ListTemplate {
        val carContext = TestCarContext.createCarContext(context)
        return PoiUtilitiesScreen(carContext).onGetTemplate() as ListTemplate
    }

    private fun rowsOf(template: ListTemplate): List<Row> =
        template.singleList!!.items.filterIsInstance<Row>()

    private fun titlesOf(template: ListTemplate): List<String> =
        rowsOf(template).map { it.title!!.toCharSequence().toString() }

    private fun rowByTitle(template: ListTemplate, title: String): Row =
        rowsOf(template).first { it.title!!.toCharSequence().toString() == title }

    private fun clickRow(row: Row) {
        row.onClickDelegate!!.sendClick(object : OnDoneCallback {})
    }

    /**
     * Reset the POI-pipeline state the screen reads. The VM is a process-wide singleton,
     * so each test starts from a known state.
     */
    @Before
    fun resetPoiState() {
        val v = vm()
        v.setFailedCellBoundsForTest(emptyList())
        v.setPoiPipelineActiveForTest(false)
        v.pendingCameraInfo = null
    }

    // -------- template structure --------

    @Test
    fun poiUtilities_hasBackHeaderAction() {
        val template = buildScreen()
        assertNotNull(
            "PoiUtilitiesScreen is a pushed screen; must have a BACK header action",
            template.header!!.startHeaderAction,
        )
    }

    // -------- Search area row --------

    @Test
    fun poiUtilities_searchRow_isDisabledWhenNoPendingCameraInfo() {
        // @Before leaves pendingCameraInfo = null. The Search row mirrors the old
        // HomeScreen utility row: disabled until there is a camera to search against.
        val template = buildScreen()
        val row = rowByTitle(template, context.getString(R.string.car_poi_search))
        assertFalse(
            "Search row must be disabled when no pending camera info",
            row.isEnabled,
        )
    }

    @Test
    fun poiUtilities_searchRow_isEnabledWhenPendingCameraInfoSet() {
        vm().pendingCameraInfo = MapViewModel.CameraInfo(lat = 45.4, lon = -75.7, zoom = 12.0)
        val template = buildScreen()
        val row = rowByTitle(template, context.getString(R.string.car_poi_search))
        assertTrue(
            "Search row must be enabled when pending camera info is set",
            row.isEnabled,
        )
    }

    @Test
    fun poiUtilities_searchRow_clickIsWiredAndDoesNotThrow() {
        // The Search row's listener is `ParkedOnlyOnClickListener.create { vm.searchVisibleArea() }`.
        // With no enabled POI categories the pipeline early-returns, but the click
        // must dispatch without throwing.
        val template = buildScreen()
        val row = rowByTitle(template, context.getString(R.string.car_poi_search))
        assertNotNull("Search row must have a click listener", row.onClickDelegate)
        clickRow(row)
    }

    // -------- Clear row --------

    @Test
    fun poiUtilities_clearRow_isPresent() {
        assertTrue(
            "Clear row must always be present; got ${titlesOf(buildScreen())}",
            titlesOf(buildScreen()).contains(context.getString(R.string.car_poi_clear)),
        )
    }

    @Test
    fun poiUtilities_clearRow_clickStopsPipeline() {
        // clearNearbyPois() cancels the worker and sets poiPipelineActive = false.
        // Start the pipeline, click Clear, and assert it stopped — a real side
        // effect that pins the listener to clearNearbyPois (not some other method).
        val v = vm()
        v.setPoiPipelineActiveForTest(true)
        assertTrue("test setup: pipeline should be active", v.poiPipelineActive)
        val template = buildScreen()
        clickRow(rowByTitle(template, context.getString(R.string.car_poi_clear)))
        assertFalse(
            "Clear must stop the POI pipeline (poiPipelineActive = false)",
            v.poiPipelineActive,
        )
    }

    // -------- Retry failed row --------

    @Test
    fun poiUtilities_retryRow_isAbsentWhenNoFailedCells() {
        // @Before leaves failed cells empty. The Retry row must not appear.
        assertFalse(
            "Retry row must be absent when no failed cells; got ${titlesOf(buildScreen())}",
            titlesOf(buildScreen()).contains(context.getString(R.string.car_poi_retry)),
        )
    }

    @Test
    fun poiUtilities_retryRow_isPresentWhenFailedCellsNonEmpty() {
        vm().setFailedCellBoundsForTest(
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
        assertTrue(
            "Retry row must be present when failed cells are non-empty; got ${
                titlesOf(buildScreen())
            }",
            titlesOf(buildScreen()).contains(context.getString(R.string.car_poi_retry)),
        )
    }

    @Test
    fun poiUtilities_retryRow_clickIsWiredAndDoesNotThrow() {
        vm().setFailedCellBoundsForTest(
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
        val template = buildScreen()
        val row = rowByTitle(template, context.getString(R.string.car_poi_retry))
        assertNotNull("Retry row must have a click listener", row.onClickDelegate)
        clickRow(row)
    }
}