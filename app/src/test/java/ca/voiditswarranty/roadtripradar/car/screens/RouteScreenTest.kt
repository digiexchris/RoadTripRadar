package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.OnDoneCallback
import androidx.car.app.model.Action
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.data.Waypoint
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
 * Tests for [RouteScreen] — the car surface that lists the user's waypoints, lets
 * them pick the active waypoint, and offers per-row move-up / remove actions plus
 * quick advance / regress controls. The screen builds a [ListTemplate] whose
 * structure depends on whether the route is empty:
 *
 * - Empty route: a single "No waypoints" row.
 * - Non-empty route: a "Clear route" row, optional "Next" / "Prev" advance/regress
 *   rows (only when more than one waypoint), then one row per waypoint. The active
 *   waypoint's title is prefixed with the active badge. Each waypoint row has two
 *   parked-only actions (move up, remove).
 *
 * The test pins both the structural contract (which rows appear, active badge
 * placement) and the click-wiring (the per-row action delegates call the right VM
 * mutator).
 *
 * Uses Robolectric + `TestCarContext` so [ListTemplate] / [Row] / [Action] get
 * built with a real `Context` (their CarText spans need one). Pinned to SDK 33
 * because the real [MapViewModel] registers a default network callback in `init`
 * that Robolectric 4.16.1's `ConnectivityManager` shadow only implements on
 * SDK 33.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RouteScreenTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun vm() = CarViewModelHolder.ensureInitialized(context)

    private fun buildScreen(): ListTemplate {
        val carContext = TestCarContext.createCarContext(context)
        val screen = RouteScreen(carContext)
        return screen.onGetTemplate() as ListTemplate
    }

    private fun rowsOf(template: ListTemplate): List<Row> =
        template.singleList!!.items.filterIsInstance<Row>()

    private fun rowTitles(template: ListTemplate): List<String> =
        rowsOf(template).map { it.title!!.toCharSequence().toString() }

    private fun actionTitles(row: Row): List<String> =
        row.actions.map { it.title.toString() }

    private fun sendClick(action: Action) {
        action.onClickDelegate!!.sendClick(object : OnDoneCallback {})
    }

    /**
     * Reset waypoints / active selection between tests. (See [HomeScreenTest.resetVmState]
     * for the rationale: the VM is a process-wide singleton, so state bleeds.)
     */
    @Before
    fun resetRouteState() {
        val v = vm()
        v.waypoints.clear()
        v.setActiveWaypoint("")
    }

    // -------- empty route --------

    @Test
    fun routeScreen_emptyWaypoints_showsEmptyStringRow() {
        // vm.waypoints is empty by default (cleared in @Before). The first (and
        // only) row title is the localized "No waypoints" string.
        val template = buildScreen()
        val titles = rowTitles(template)
        val expected = context.getString(R.string.car_route_empty)
        assertEquals(
            "empty route must show only the no-waypoints row; got $titles",
            listOf(expected),
            titles,
        )
    }

    @Test
    fun routeScreen_emptyWaypoints_hasBackHeaderAction() {
        // Even with an empty list, the route screen is a pushed screen and must
        // have a BACK header action.
        val template = buildScreen()
        assertNotNull(
            "RouteScreen must always have a BACK header action",
            template.headerAction,
        )
    }

    // -------- non-empty route --------

    @Test
    fun routeScreen_nonEmptyWaypoints_showsClearRow() {
        val v = vm()
        v.waypoints.add(
            Waypoint.create(
                position = org.maplibre.spatialk.geojson.Position(
                    longitude = -75.7,
                    latitude = 45.4,
                ),
                name = "Stop A",
            )
        )
        val template = buildScreen()
        val titles = rowTitles(template)
        val expected = context.getString(R.string.car_route_clear)
        assertTrue(
            "non-empty route must show the Clear route row; got $titles",
            titles.contains(expected),
        )
    }

    @Test
    fun routeScreen_singleWaypoint_omitsAdvanceAndRegressRows() {
        // With one waypoint there is no "next" or "prev" — those rows only make
        // sense when there are at least 2. Assert the advance / regress strings
        // are NOT in the list.
        val v = vm()
        v.waypoints.add(
            Waypoint.create(
                position = org.maplibre.spatialk.geojson.Position(
                    longitude = -75.7,
                    latitude = 45.4,
                ),
                name = "Solo",
            )
        )
        val template = buildScreen()
        val titles = rowTitles(template)
        val advance = context.getString(R.string.car_action_advance)
        val regress = context.getString(R.string.car_action_regress)
        assertFalse(
            "single-waypoint route must NOT show the advance row; got $titles",
            titles.contains(advance),
        )
        assertFalse(
            "single-waypoint route must NOT show the regress row; got $titles",
            titles.contains(regress),
        )
    }

    @Test
    fun routeScreen_twoWaypoints_showsAdvanceAndRegressRows() {
        val v = vm()
        v.waypoints.add(
            Waypoint.create(
                position = org.maplibre.spatialk.geojson.Position(
                    longitude = -75.7,
                    latitude = 45.4,
                ),
                name = "A",
            )
        )
        v.waypoints.add(
            Waypoint.create(
                position = org.maplibre.spatialk.geojson.Position(
                    longitude = -75.6,
                    latitude = 45.5,
                ),
                name = "B",
            )
        )
        val template = buildScreen()
        val titles = rowTitles(template)
        val advance = context.getString(R.string.car_action_advance)
        val regress = context.getString(R.string.car_action_regress)
        assertTrue(
            "two-waypoint route must show the advance row; got $titles",
            titles.contains(advance),
        )
        assertTrue(
            "two-waypoint route must show the regress row; got $titles",
            titles.contains(regress),
        )
    }

    @Test
    fun routeScreen_perWaypointRow_hasMoveUpAndRemoveActions() {
        // Each waypoint row exposes two actions: Move up and Remove. The pinned
        // test reads the row's actions and asserts both labels are present.
        val v = vm()
        val wp = Waypoint.create(
            position = org.maplibre.spatialk.geojson.Position(
                longitude = -75.7,
                latitude = 45.4,
            ),
            name = "Stop A",
        )
        v.waypoints.add(wp)
        val template = buildScreen()
        val waypointRow = rowsOf(template).first { it.title!!.toCharSequence().toString() == "Stop A" }
        val actions = actionTitles(waypointRow)
        val moveUp = context.getString(R.string.car_route_move_up)
        val remove = context.getString(R.string.car_route_remove)
        assertTrue(
            "waypoint row must have a Move up action; got $actions",
            actions.contains(moveUp),
        )
        assertTrue(
            "waypoint row must have a Remove action; got $actions",
            actions.contains(remove),
        )
    }

    @Test
    fun routeScreen_activeWaypointRow_titleHasActiveBadge() {
        // When a waypoint is the active one, its row title is prefixed with the
        // active badge (e.g. "▶ Stop A"). The test pins the contract: the
        // active waypoint's title starts with the badge.
        val v = vm()
        val a = Waypoint.create(
            position = org.maplibre.spatialk.geojson.Position(
                longitude = -75.7,
                latitude = 45.4,
            ),
            name = "Stop A",
        )
        val b = Waypoint.create(
            position = org.maplibre.spatialk.geojson.Position(
                longitude = -75.6,
                latitude = 45.5,
            ),
            name = "Stop B",
        )
        v.waypoints.add(a)
        v.waypoints.add(b)
        v.setActiveWaypoint(a.id)
        val template = buildScreen()
        val titles = rowTitles(template)
        val activeBadge = context.getString(R.string.car_route_active_badge)
        val expectedActiveTitle = activeBadge + "Stop A"
        assertTrue(
            "active waypoint row must have the active badge prefix; got $titles",
            titles.contains(expectedActiveTitle),
        )
        // The non-active row's title is the plain name (no badge).
        assertTrue(
            "non-active waypoint row must NOT have the active badge prefix; got $titles",
            titles.contains("Stop B"),
        )
    }

    @Test
    fun routeScreen_perWaypointRow_moveUpActionInvokesMoveWaypoint() {
        // Click the move-up action on a waypoint row. The listener calls
        // `vm.moveWaypoint(index, (index - 1).coerceAtLeast(0))`. With two
        // waypoints and index=1, the call should be moveWaypoint(1, 0).
        // We can't easily inspect the call without a spy, so we assert the
        // side effect: after the click, the waypoint at index 1 has moved
        // to index 0.
        val v = vm()
        val a = Waypoint.create(
            position = org.maplibre.spatialk.geojson.Position(
                longitude = -75.7,
                latitude = 45.4,
            ),
            name = "A",
        )
        val b = Waypoint.create(
            position = org.maplibre.spatialk.geojson.Position(
                longitude = -75.6,
                latitude = 45.5,
            ),
            name = "B",
        )
        v.waypoints.add(a)
        v.waypoints.add(b)
        // Snapshot order before the move.
        assertEquals("A", v.waypoints[0].name)
        assertEquals("B", v.waypoints[1].name)
        val template = buildScreen()
        val rowB = rowsOf(template).first { it.title!!.toCharSequence().toString() == "B" }
        val moveUp = rowB.actions.first {
            it.title.toString() == context.getString(R.string.car_route_move_up)
        }
        sendClick(moveUp)
        assertEquals(
            "Move up on row 1 should swap it to position 0",
            "B",
            v.waypoints[0].name,
        )
        assertEquals(
            "Move up on row 1 should put A at position 1",
            "A",
            v.waypoints[1].name,
        )
    }
}
