package ca.voiditswarranty.roadtripradar.car

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.car.app.CarContext
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.data.isDarkForAppTheme
import ca.voiditswarranty.roadtripradar.model.formatDistanceLabel
import ca.voiditswarranty.roadtripradar.ui.waypointDisplayLabel
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import org.maplibre.spatialk.turf.measurement.bearingTo
import org.maplibre.spatialk.turf.measurement.distance
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.inDegrees

/**
 * The route overlay card drawn on the car map surface, mirroring the phone
 * [ca.voiditswarranty.roadtripradar.ui.NavWidget]: a bearing arrow pointing toward the active
 * waypoint, the distance to it, the waypoint label, and the stop count. The whole card is
 * hidden ([View.GONE]) when there is no active waypoint (or no user position to compute a
 * bearing/distance from) — matching the phone, which only renders `NavWidget` when an active
 * waypoint exists.
 *
 * Drawn with plain Android Views (not Compose): the car surface lives on a
 * [android.app.Presentation] backed by a virtual display whose context is a [CarContext], where
 * hosting a ComposeView would need non-Activity lifecycle/saved-state wiring. Colors follow the
 * *resolved* map style's dark/light so the card reads against the map behind it; the arrow
 * rotation tracks the native map's camera bearing via [CarMapContainer]'s camera listeners,
 * which call [update] on move/idle.
 *
 * Geometry/distance/bearing reuse the phone's exact computation (`distance(user, poi)` +
 * `(user.bearingTo(poi) - Bearing.North).inDegrees`), and the label reuses the shared
 * [waypointDisplayLabel] so the car shows the same "#2 Tim Hortons" / "Waypoint 2" text as the
 * phone `NavWidget`.
 */
class CarRouteWidget(
    private val carContext: CarContext,
    private val vm: MapViewModel,
) {
    val view: View

    private val root: LinearLayout
    private val arrow: ImageView
    private val distanceText: TextView
    private val nameText: TextView
    private val stopsText: TextView

    init {
        view = LayoutInflater.from(carContext).inflate(R.layout.car_route_widget, null)
        root = view.findViewById(R.id.car_route_root)
        arrow = view.findViewById(R.id.car_route_arrow)
        distanceText = view.findViewById(R.id.car_route_distance)
        nameText = view.findViewById(R.id.car_route_name)
        stopsText = view.findViewById(R.id.car_route_stops)
    }

    /**
     * Re-render from the VM. [cameraBearingDegrees] is the native map's current bearing, used to
     * keep the arrow pointing toward the target relative to the map (the phone gets this from
     * Compose map state — `(poiBearingDeg - cameraBearing)`, identical math). Safe to call on the
     * main thread from the refresh listener and camera listeners.
     */
    fun update(cameraBearingDegrees: Double) {
        val active = vm.activeWaypoint
        val activeIdx = vm.activeIndex
        val userPos = vm.userPositionForSearch ?: vm.lastKnownPosition
        // Mirror the phone: no active waypoint (or no user position to compute from) → no widget.
        if (active == null || activeIdx == null || userPos == null) {
            root.visibility = View.GONE
            return
        }
        root.visibility = View.VISIBLE
        val dark = vm.mapStyle.isDarkForAppTheme(carContext, carContext.isDarkMode())
        applyColors(dark)

        val dist = distance(userPos, active.position)
        val bearingDeg = (userPos.bearingTo(active.position) - Bearing.North).inDegrees
        // Same rotation as NavWidget: poiBearing - cameraBearing.
        arrow.rotation = (bearingDeg - cameraBearingDegrees).toFloat()
        distanceText.text = formatDistanceLabel(dist, vm.useMetric)
        nameText.text = waypointDisplayLabel(carContext, active, activeIdx)

        val stopCount = vm.waypoints.size
        if (stopCount > 0) {
            stopsText.visibility = View.VISIBLE
            stopsText.text = carContext.getString(R.string.route_stop_count_plural, stopCount)
        } else {
            stopsText.visibility = View.GONE
        }
    }

    private fun applyColors(dark: Boolean) {
        val bg: Int
        val textMain: Int
        val textSecondary: Int
        val arrowTint: Int
        if (dark) {
            bg = 0xB3000000.toInt()
            textMain = 0xFFFFFFFF.toInt()
            textSecondary = 0xB3FFFFFF.toInt()
            arrowTint = 0xFF90CAF9.toInt()
        } else {
            bg = 0xCCFFFFFF.toInt()
            textMain = 0xFF212121.toInt()
            textSecondary = 0xFF616161.toInt()
            arrowTint = 0xFF1565C0.toInt()
        }
        root.backgroundTintList = ColorStateList.valueOf(bg)
        arrow.imageTintList = ColorStateList.valueOf(arrowTint)
        distanceText.setTextColor(textMain)
        nameText.setTextColor(textSecondary)
        stopsText.setTextColor(textSecondary)
    }
}