package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.OnDoneCallback
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.model.MAX_POI_CATEGORIES
import ca.voiditswarranty.roadtripradar.model.POI_CATEGORIES
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [PoiScreen] — the car surface that lets the user toggle which POI
 * categories are active. The screen builds a [GridTemplate] of [GridItem]s (one
 * per [POI_CATEGORIES] entry) whose state depends on the VM:
 *
 * - **Enabled** categories: have a click listener + a primary-dot `Badge` on
 *   the icon. Tapping toggles them off.
 * - **Below-cap, not enabled**: have a click listener, no badge. Tapping
 *   enables them.
 * - **At-cap, not enabled**: have a click listener disabled (text shows the
 *   "max reached" string) so tapping is a no-op.
 *
 * The test pins all three branches plus the action-strip search push, the
 * status text, and that enabling 5 categories clamps the rest.
 *
 * Uses Robolectric + `TestCarContext` so the `GridTemplate` builds with a real
 * `Context` (the maki icon rasterizer needs one). Pinned to SDK 33 because the
 * real [MapViewModel] registers a default network callback in `init` that
 * Robolectric 4.16.1's `ConnectivityManager` shadow only implements on SDK 33.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PoiScreenTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun vm() = CarViewModelHolder.ensureInitialized(context)

    private fun buildScreen(): GridTemplate {
        val carContext = TestCarContext.createCarContext(context)
        val screen = PoiScreen(carContext)
        return screen.onGetTemplate() as GridTemplate
    }

    private fun itemsOf(template: GridTemplate): List<GridItem> =
        template.singleList!!.items.filterIsInstance<GridItem>()

    private fun clickItem(item: GridItem) {
        item.onClickDelegate!!.sendClick(object : OnDoneCallback {})
    }

    /**
     * Reset the POI state to defaults. The VM is a process-wide singleton, so
     * each test starts from a known state.
     */
    @Before
    fun resetPoiState() {
        val v = vm()
        v.setEnabledPoiCategoriesForTest(emptySet())
        v.setFailedCellBoundsForTest(emptyList())
        v.setPoiPipelineActiveForTest(false)
        v.pendingCameraInfo = null
        v.setNearbyPoiFeaturesForTest(org.maplibre.spatialk.geojson.FeatureCollection(emptyList()))
    }

    // -------- structural contract --------

    @Test
    fun poiScreen_gridHasOneItemPerCategory() {
        // 29 categories, 29 grid items. Pinned so a future addition / removal of
        // a category is surfaced as a test diff.
        val template = buildScreen()
        assertEquals(
            "grid must have one item per POI_CATEGORIES entry",
            POI_CATEGORIES.size,
            itemsOf(template).size,
        )
    }

    @Test
    fun poiScreen_titleIncludesPoiTitleString() {
        // The template title is "<car_poi_title> · <status>". Pin the
        // car_poi_title portion to the resource.
        val template = buildScreen()
        val title = template.title!!.toCharSequence().toString()
        val expected = context.getString(R.string.car_poi_title)
        assertTrue(
            "title must start with the localized POI title; got '$title'",
            title.startsWith(expected),
        )
    }

    @Test
    fun poiScreen_hasBackHeaderAction() {
        val template = buildScreen()
        assertNotNull(
            "PoiScreen is a pushed screen; must have a BACK header action",
            template.headerAction,
        )
    }

    // -------- enabled state --------

    @Test
    fun poiScreen_enabledCategory_hasBadgeAndClickListener() {
        val v = vm()
        val category = POI_CATEGORIES.first()  // "fuel"
        v.setEnabledPoiCategoriesForTest(setOf(category.query))
        val template = buildScreen()
        val item = itemsOf(template).first {
            it.title!!.toCharSequence().toString() == context.getString(category.labelRes)
        }
        assertNotNull(
            "enabled category must have a primary-dot badge",
            item.badge,
        )
        assertNotNull(
            "enabled category must be tappable",
            item.onClickDelegate,
        )
    }

    @Test
    fun poiScreen_notEnabled_belowCap_isTappable() {
        // empty enabledPoiCategories, at cap = false. Item must be tappable
        // (no badge yet).
        val template = buildScreen()
        val item = itemsOf(template).first()  // any category
        assertNotNull(
            "below-cap non-enabled item must be tappable",
            item.onClickDelegate,
        )
        assertNull(
            "below-cap non-enabled item must not have a badge",
            item.badge,
        )
    }

    @Test
    fun poiScreen_notEnabled_atCap_isNotTappableAndShowsMaxReached() {
        val v = vm()
        // Fill 5 categories to the cap.
        v.setEnabledPoiCategoriesForTest(POI_CATEGORIES.take(MAX_POI_CATEGORIES).map { it.query }.toSet())
        // The 6th category is at-cap.
        val atCap = POI_CATEGORIES[MAX_POI_CATEGORIES]
        val template = buildScreen()
        val item = itemsOf(template).first {
            it.title!!.toCharSequence().toString() == context.getString(atCap.labelRes)
        }
        assertNull(
            "at-cap item must NOT be tappable",
            item.onClickDelegate,
        )
        val text = item.text?.toCharSequence()?.toString()
        assertEquals(
            "at-cap item must show the max-reached text",
            context.getString(R.string.car_poi_max_reached),
            text,
        )
    }

    // -------- click behavior --------

    @Test
    fun poiScreen_clickingDisabledItem_enablesIt() {
        // Start with no categories enabled. Click the first item; the VM should
        // gain that category.
        val v = vm()
        val first = POI_CATEGORIES.first()
        val template = buildScreen()
        val item = itemsOf(template).first {
            it.title!!.toCharSequence().toString() == context.getString(first.labelRes)
        }
        assertFalse(
            "test setup: first category should not be enabled yet",
            first.query in v.enabledPoiCategories,
        )
        clickItem(item)
        assertTrue(
            "clicking a disabled item should enable its category",
            first.query in v.enabledPoiCategories,
        )
    }

    @Test
    fun poiScreen_clickingEnabledItem_disablesIt() {
        val v = vm()
        val first = POI_CATEGORIES.first()
        v.setEnabledPoiCategoriesForTest(setOf(first.query))
        val template = buildScreen()
        val item = itemsOf(template).first {
            it.title!!.toCharSequence().toString() == context.getString(first.labelRes)
        }
        clickItem(item)
        assertFalse(
            "clicking an enabled item should disable its category",
            first.query in v.enabledPoiCategories,
        )
    }

    // -------- status text --------

    @Test
    fun poiScreen_statusNoMap_whenNoPendingCameraInfo() {
        // @Before leaves pendingCameraInfo = null and no pipeline activity. The
        // status should be the "no map" string directing the user to the phone.
        val template = buildScreen()
        val title = template.title!!.toCharSequence().toString()
        val noMap = context.getString(R.string.car_poi_no_map)
        assertTrue(
            "no-map status should appear in the title; got '$title'",
            title.contains(noMap),
        )
    }

    @Test
    fun poiScreen_statusIdle_whenNoPipelineAndHasCamera() {
        // @Before leaves pendingCameraInfo = null → status would be
        // car_poi_no_map. To exercise the idle branch, set a camera info
        // (no pipeline, no failures, no loading).
        val v = vm()
        v.pendingCameraInfo = MapViewModel.CameraInfo(lat = 45.4, lon = -75.7, zoom = 12.0)
        val template = buildScreen()
        val title = template.title!!.toCharSequence().toString()
        val idle = context.getString(R.string.car_poi_idle, 0)
        assertTrue(
            "idle status should appear in the title; got '$title'",
            title.contains(idle),
        )
    }

    @Test
    fun poiScreen_statusLoading_whenIsLoadingPois() {
        val v = vm()
        v.setPoiPipelineActiveForTest(true)
        // isLoadingPois is a getter derived from cellsRemaining. Setting
        // cellsRemaining > 0 flips it to true.
        v.setCellsRemainingForTest(1)
        val template = buildScreen()
        val title = template.title!!.toCharSequence().toString()
        val loading = context.getString(R.string.car_poi_loading, 0)
        assertTrue(
            "loading status should appear in the title; got '$title'",
            title.contains(loading),
        )
    }

    @Test
    fun poiScreen_statusFailed_whenHasFailedCells() {
        val v = vm()
        v.setPoiPipelineActiveForTest(true)
        v.setFailedCellBoundsForTest(
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
        val title = template.title!!.toCharSequence().toString()
        val failed = context.getString(R.string.car_poi_failed, 0)
        assertTrue(
            "failed status should appear in the title; got '$title'",
            title.contains(failed),
        )
    }
}
