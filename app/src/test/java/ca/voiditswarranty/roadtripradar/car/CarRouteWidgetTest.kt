package ca.voiditswarranty.roadtripradar.car

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository
import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.spatialk.geojson.Position
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behaviour tests for [CarRouteWidget] — the route overlay card drawn on top of the car
 * map. The widget reads [MapViewModel.activeWaypoint], [MapViewModel.userPositionForSearch],
 * and [MapViewModel.lastKnownPosition] on every [CarRouteWidget.update] call, formats the
 * distance + bearing to the active waypoint, and applies a theme-aware palette
 * (background tint, text colors, arrow tint).
 *
 * The widget uses a real [MapViewModel] (constructed with a real
 * [PreferencesRepository] backed by Robolectric's `SharedPreferences`) so the test
 * exercises the same code path as production. The `isDarkMode` value the widget needs
 * is passed in directly (it comes from `CarContext.isDarkMode()` in production) so the
 * test doesn't need a real `CarContext` — Robolectric's `ApplicationProvider` provides
 * the layout-inflater Context.
 *
 * Pinned to SDK 33 because Robolectric 4.16.1's `ConnectivityManager` shadow doesn't
 * implement `registerDefaultNetworkCallback` on the compileSdk (36), which the real
 * `MapViewModel.init` calls. SDK 33 is well-supported and exercises the same code paths
 * the widget touches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CarRouteWidgetTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Build a real MapViewModel; this is the same construction the holder does. */
    private fun freshVm(): MapViewModel {
        val prefs = PreferencesRepository(context)
        return MapViewModel(
            appContext = context,
            prefsRepo = prefs,
            externalScope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
    }

    private fun widget(vm: MapViewModel, isDark: Boolean = false) =
        CarRouteWidget(context, vm, isDark)

    private fun rootOf(w: CarRouteWidget): View = w.view.findViewById(R.id.car_route_root)
    private fun arrowOf(w: CarRouteWidget): ImageView = w.view.findViewById(R.id.car_route_arrow)
    private fun distanceOf(w: CarRouteWidget): TextView = w.view.findViewById(R.id.car_route_distance)
    private fun nameOf(w: CarRouteWidget): TextView = w.view.findViewById(R.id.car_route_name)
    private fun stopsOf(w: CarRouteWidget): TextView = w.view.findViewById(R.id.car_route_stops)

    private val userPos = Position(longitude = -79.0, latitude = 43.0)

    // -------- visibility --------

    @Test
    fun update_noActiveWaypoint_hidesRoot() {
        val vm = freshVm()
        // No active waypoint set → entire card is GONE. Matches the phone NavWidget
        // which also hides itself when there's no target.
        vm.userPositionForSearch = userPos
        val w = widget(vm)
        w.update(cameraBearingDegrees = 0.0)
        assertEquals(View.GONE, rootOf(w).visibility)
    }

    @Test
    fun update_withActiveWaypointAndUserPos_showsRoot() {
        val vm = freshVm()
        val waypoint = Waypoint.create(position = Position(latitude = 43.5, longitude = -79.0), name = "Target")
        vm.waypoints.add(waypoint)
        vm.setActiveWaypoint(waypoint.id)
        vm.userPositionForSearch = userPos
        val w = widget(vm)
        w.update(cameraBearingDegrees = 0.0)
        assertEquals(View.VISIBLE, rootOf(w).visibility)
    }

    // -------- text content --------

    @Test
    fun update_showsDistanceAndWaypointName() {
        val vm = freshVm()
        val waypoint = Waypoint.create(position = Position(latitude = 43.5, longitude = -79.0), name = "Tim Hortons")
        vm.waypoints.add(waypoint)
        vm.setActiveWaypoint(waypoint.id)
        vm.userPositionForSearch = userPos
        val w = widget(vm)
        w.update(cameraBearingDegrees = 0.0)
        // Distance text is non-empty (the user is somewhere on the planet; the actual
        // value depends on the waypoint/user positions and useMetric, but the field
        // must be populated).
        val distanceText = distanceOf(w).text.toString()
        assertNotEquals("", distanceText)
        // Waypoint name appears in the name TextView via waypointDisplayLabel, which
        // prefixes the waypoint index — assert the name is in there.
        val nameText = nameOf(w).text.toString()
        assertEquals(true, nameText.contains("Tim Hortons"))
    }

    // -------- arrow rotation --------

    @Test
    fun update_arrowRotation_isBearingMinusCameraBearing() {
        // The widget rotates the arrow so it points from the user toward the waypoint
        // relative to the current map bearing. The math is `poiBearing - cameraBearing`,
        // matching the phone NavWidget.
        val vm = freshVm()
        // Place the waypoint due north of the user so the bearing is 0° (north).
        val waypoint = Waypoint.create(position = Position(latitude = 44.0, longitude = -79.0))
        vm.waypoints.add(waypoint)
        vm.setActiveWaypoint(waypoint.id)
        vm.userPositionForSearch = Position(longitude = -79.0, latitude = 43.0)
        val w = widget(vm)
        w.update(cameraBearingDegrees = 45.0)
        // Expected arrow rotation: 0° (north) - 45° (camera) = -45°.
        assertEquals(-45f, arrowOf(w).rotation, 0.5f)
    }

    // -------- stops text --------

    @Test
    fun update_stopsText_hiddenWhenNoWaypoints() {
        // vm.waypoints.isEmpty() → stopsText is GONE (but the card itself is also
        // GONE because there's no active waypoint, so this verifies the
        // no-active-waypoint contract rather than the stops-text branch directly).
        val vm = freshVm()
        vm.userPositionForSearch = userPos
        val w = widget(vm)
        w.update(cameraBearingDegrees = 0.0)
        assertEquals(View.GONE, rootOf(w).visibility)
    }

    @Test
    fun update_stopsText_showsCountWhenWaypointsExist() {
        val vm = freshVm()
        val a = Waypoint.create(position = Position(latitude = 43.5, longitude = -79.0))
        val b = Waypoint.create(position = Position(latitude = 44.0, longitude = -79.0))
        val c = Waypoint.create(position = Position(latitude = 44.5, longitude = -79.0))
        vm.waypoints.add(a)
        vm.waypoints.add(b)
        vm.waypoints.add(c)
        vm.setActiveWaypoint(a.id)
        vm.userPositionForSearch = userPos
        val w = widget(vm)
        w.update(cameraBearingDegrees = 0.0)
        // Three waypoints → stopsText is VISIBLE and reads "3 stops" (the
        // `route_stop_count_plural` string in en-US).
        assertEquals(View.VISIBLE, stopsOf(w).visibility)
        val expected = context.getString(R.string.route_stop_count_plural, 3)
        assertEquals(expected, stopsOf(w).text.toString())
    }

    // -------- palette / theme --------

    @Test
    fun update_dark_appliesDarkPalette() {
        // Pinned to the companion-object constants so the test references the source
        // of truth (not a duplicated literal that could drift).
        val vm = freshVm()
        val waypoint = Waypoint.create(position = Position(latitude = 43.5, longitude = -79.0))
        vm.waypoints.add(waypoint)
        vm.setActiveWaypoint(waypoint.id)
        vm.userPositionForSearch = userPos
        vm.updateMapStyle(MapStyle.DARK)
        val w = widget(vm, isDark = true)
        w.update(cameraBearingDegrees = 0.0)
        // Root background tint should equal the dark background color.
        val root = rootOf(w) as LinearLayout
        assertEquals(
            "dark background tint",
            CarRouteWidget.darkBackground,
            root.backgroundTintList?.defaultColor,
        )
        // Arrow tint should equal the dark arrow tint.
        assertEquals(
            "dark arrow tint",
            CarRouteWidget.darkArrowTint,
            arrowOf(w).imageTintList?.defaultColor,
        )
    }

    @Test
    fun update_light_appliesLightPalette() {
        val vm = freshVm()
        val waypoint = Waypoint.create(position = Position(latitude = 43.5, longitude = -79.0))
        vm.waypoints.add(waypoint)
        vm.setActiveWaypoint(waypoint.id)
        vm.userPositionForSearch = userPos
        vm.updateMapStyle(MapStyle.LIBERTY)  // light
        val w = widget(vm, isDark = false)
        w.update(cameraBearingDegrees = 0.0)
        val root = rootOf(w) as LinearLayout
        assertEquals(
            "light background tint",
            CarRouteWidget.lightBackground,
            root.backgroundTintList?.defaultColor,
        )
        assertEquals(
            "light arrow tint",
            CarRouteWidget.lightArrowTint,
            arrowOf(w).imageTintList?.defaultColor,
        )
    }
}
