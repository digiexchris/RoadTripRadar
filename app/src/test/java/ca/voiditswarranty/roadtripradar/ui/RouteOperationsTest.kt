package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository
import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.spatialk.geojson.Position
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for the route / waypoint state machine in [MapViewModel].
 *
 * The route editor's visual map line is MapLibre-coupled and not testable on the JVM, but the
 * underlying state machine — `waypoints`, `activeWaypointId`/`activeIndex`, advance/regress,
 * `clearRoute`, and `maybeAutoAdvance` — is plain Compose state plus public methods. The
 * `RouteEditorSheet` itself is a pure `@Composable` over `(waypoints, activeWaypointId, callbacks)`,
 * so it is driven directly here (its waypoint rows are backed by an `AndroidView`/`RecyclerView`
 * which is not visible to Compose semantics — see `RouteEditorSheetTest` — so the rendering
 * assertions cover the title/empty-state, and per-waypoint coverage is asserted via VM state).
 *
 * The VM is constructed directly (not via `MapViewModelFactory`) to avoid the process-wide
 * `CarViewModelHolder` singleton leaking state between tests — same pattern as
 * `MapStyleSwitchingTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RouteOperationsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Fresh VM per test — bypasses the `CarViewModelHolder` singleton. */
    private fun freshVm(): MapViewModel = MapViewModel(
        appContext = context,
        prefsRepo = PreferencesRepository(context),
        externalScope = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun waypointAt(lat: Double, lon: Double, name: String? = null): Waypoint =
        Waypoint.create(position = Position(latitude = lat, longitude = lon), name = name)

    // ------------------------------------------------------------------
    // Rendering: empty state and waypoint-in-editor
    // ------------------------------------------------------------------

    @Test
    fun route_noWaypoints_routeEditorEmpty() {
        val vm = freshVm()
        vm.clearRoute()

        composeTestRule.setContent {
            RoadTripRadarTheme {
                RouteEditorSheet(
                    visible = true,
                    waypoints = vm.waypoints.toList(),
                    activeWaypointId = vm.activeWaypointId,
                    onSetActive = { vm.setActiveWaypoint(it) },
                    onRemove = {},
                    onMoveCommit = { _, _ -> },
                    onClearRoute = { vm.clearRoute() },
                    onClose = {},
                )
            }
        }
        // The empty-state sheet shows the title and the Clear Route button with no rows.
        composeTestRule.onNodeWithText("Route").assertIsDisplayed()
        assertTrue(vm.waypoints.isEmpty())
        assertNull(vm.activeIndex)
    }

    @Test
    fun route_addWaypointViaVm_rendersInEditor() {
        val vm = freshVm()
        vm.clearRoute()
        val wp = waypointAt(45.0, -75.0, name = "Start")
        vm.waypoints.add(wp)

        composeTestRule.setContent {
            RoadTripRadarTheme {
                RouteEditorSheet(
                    visible = true,
                    waypoints = vm.waypoints.toList(),
                    activeWaypointId = vm.activeWaypointId,
                    onSetActive = { vm.setActiveWaypoint(it) },
                    onRemove = {},
                    onMoveCommit = { _, _ -> },
                    onClearRoute = { vm.clearRoute() },
                    onClose = {},
                )
            }
        }
        // The sheet still renders its title. The waypoint name itself is rendered inside the
        // AndroidView-backed RecyclerView (not visible to Compose semantics), so assert the
        // added waypoint via the VM state it was added to.
        composeTestRule.onNodeWithText("Route").assertIsDisplayed()
        assertEquals(1, vm.waypoints.size)
        assertEquals("Start", vm.waypoints[0].name)
    }

    // ------------------------------------------------------------------
    // advance / regress
    // ------------------------------------------------------------------

    @Test
    fun route_advanceActiveWaypoint_advances() {
        val vm = freshVm()
        vm.clearRoute()
        val a = waypointAt(45.0, -75.0, "A")
        val b = waypointAt(45.001, -75.0, "B")
        val c = waypointAt(45.002, -75.0, "C")
        vm.waypoints.addAll(listOf(a, b, c))
        vm.setActiveWaypoint(a.id)

        assertEquals(0, vm.activeIndex)
        vm.advanceActiveWaypoint()
        assertEquals(1, vm.activeIndex)
    }

    @Test
    fun route_regressActiveWaypoint_regresses() {
        val vm = freshVm()
        vm.clearRoute()
        val a = waypointAt(45.0, -75.0, "A")
        val b = waypointAt(45.001, -75.0, "B")
        val c = waypointAt(45.002, -75.0, "C")
        vm.waypoints.addAll(listOf(a, b, c))
        vm.setActiveWaypoint(b.id)

        assertEquals(1, vm.activeIndex)
        vm.regressActiveWaypoint()
        assertEquals(0, vm.activeIndex)
    }

    @Test
    fun route_advanceAtLastWaypoint_noOp() {
        val vm = freshVm()
        vm.clearRoute()
        val a = waypointAt(45.0, -75.0, "A")
        val b = waypointAt(45.001, -75.0, "B")
        vm.waypoints.addAll(listOf(a, b))
        vm.setActiveWaypoint(b.id)
        assertEquals(1, vm.activeIndex)

        vm.advanceActiveWaypoint()
        // No wrap: still at the last waypoint.
        assertEquals(1, vm.activeIndex)
    }

    @Test
    fun route_regressAtFirstWaypoint_noOp() {
        val vm = freshVm()
        vm.clearRoute()
        val a = waypointAt(45.0, -75.0, "A")
        val b = waypointAt(45.001, -75.0, "B")
        vm.waypoints.addAll(listOf(a, b))
        vm.setActiveWaypoint(a.id)
        assertEquals(0, vm.activeIndex)

        vm.regressActiveWaypoint()
        // No wrap: still at the first waypoint.
        assertEquals(0, vm.activeIndex)
    }

    // ------------------------------------------------------------------
    // clearRoute
    // ------------------------------------------------------------------

    @Test
    fun route_clearRoute_emptiesWaypoints() {
        val vm = freshVm()
        vm.clearRoute()
        val a = waypointAt(45.0, -75.0, "A")
        val b = waypointAt(45.001, -75.0, "B")
        vm.waypoints.addAll(listOf(a, b))
        vm.setActiveWaypoint(a.id)
        assertEquals(2, vm.waypoints.size)

        vm.clearRoute()

        assertTrue(vm.waypoints.isEmpty())
        assertNull(vm.activeIndex)
    }

    // ------------------------------------------------------------------
    // maybeAutoAdvance
    // ------------------------------------------------------------------

    @Test
    fun route_autoAdvance_advancesAtThreshold() {
        val vm = freshVm()
        vm.clearRoute()
        // Two waypoints ~500m apart (0.0045 deg latitude ≈ 500m).
        val a = waypointAt(45.0, -75.0, "A")
        val b = waypointAt(45.0045, -75.0, "B")
        vm.waypoints.addAll(listOf(a, b))
        vm.setActiveWaypoint(a.id)
        vm.updateAutoAdvanceEnabled(true)
        // Set a 50m threshold via the test seam (bypasses the 25..500 clamp + persistence).
        vm.setAutoAdvanceThresholdMetersForTest(50)
        assertEquals(0, vm.activeIndex)

        // User position is exactly at the active waypoint → distance 0 < 50m → advance to B.
        vm.maybeAutoAdvance(a.position)

        assertEquals(1, vm.activeIndex)
    }
}