package ca.voiditswarranty.roadtripradar.car

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import ca.voiditswarranty.roadtripradar.R
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
 * [android.app.Presentation] backed by a virtual display whose context is a [CarContext]-derived
 * [Context], where hosting a ComposeView would need non-Activity lifecycle/saved-state wiring.
 * Colors follow the *resolved* map style's dark/light so the card reads against the map behind
 * it; the arrow rotation tracks the native map's camera bearing via [CarMapContainer]'s camera
 * listeners, which call [update] on move/idle.
 *
 * The [isDark] flag is supplied by [CarMapContainer], which already computes it from the
 * resolved map style and the host [androidx.car.app.CarContext.isDarkMode] state. The widget
 * itself only needs a [Context] for resource/layout access (no `CarContext` API is touched),
 * which keeps it unit-testable under Robolectric.
 *
 * Geometry/distance/bearing reuse the phone's exact computation (`distance(user, poi)` +
 * `(user.bearingTo(poi) - Bearing.North).inDegrees`), and the label reuses the shared
 * [waypointDisplayLabel] so the car shows the same "#2 Tim Hortons" / "Waypoint 2" text as the
 * phone `NavWidget`.
 */
class CarRouteWidget(
    private val context: Context,
    private val vm: MapViewModel,
    initialIsDark: Boolean,
) {
    val view: View

    private val root: LinearLayout
    private val arrow: ImageView
    private val distanceText: TextView
    private val nameText: TextView
    private val stopsText: TextView

    private var isDark: Boolean = initialIsDark

    init {
        view = LayoutInflater.from(context).inflate(R.layout.car_route_widget, null)
        root = view.findViewById(R.id.car_route_root)
        arrow = view.findViewById(R.id.car_route_arrow)
        distanceText = view.findViewById(R.id.car_route_distance)
        nameText = view.findViewById(R.id.car_route_name)
        stopsText = view.findViewById(R.id.car_route_stops)
    }

    /**
     * Update the theme flag. [CarMapContainer] calls this on style reload (the underlying
     * `MapViewModel.mapStyle` may have changed). No-op when the value is unchanged — callers
     * can invoke this on every style reload without paying for repeated re-themes.
     */
    fun setDark(dark: Boolean) {
        if (isDark == dark) return
        isDark = dark
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
        applyColors(isDark)

        val dist = distance(userPos, active.position)
        val bearingDeg = (userPos.bearingTo(active.position) - Bearing.North).inDegrees
        // Same rotation as NavWidget: poiBearing - cameraBearing.
        arrow.rotation = (bearingDeg - cameraBearingDegrees).toFloat()
        distanceText.text = formatDistanceLabel(dist, vm.useMetric)
        nameText.text = waypointDisplayLabel(context, active, activeIdx)

        val stopCount = vm.waypoints.size
        if (stopCount > 0) {
            stopsText.visibility = View.VISIBLE
            stopsText.text = context.getString(R.string.route_stop_count_plural, stopCount)
        } else {
            stopsText.visibility = View.GONE
        }
    }

    private fun applyColors(dark: Boolean) {
        if (dark) {
            root.backgroundTintList = ColorStateList.valueOf(darkBackground)
            arrow.imageTintList = ColorStateList.valueOf(darkArrowTint)
            distanceText.setTextColor(darkTextMain)
            nameText.setTextColor(darkTextSecondary)
            stopsText.setTextColor(darkTextSecondary)
        } else {
            root.backgroundTintList = ColorStateList.valueOf(lightBackground)
            arrow.imageTintList = ColorStateList.valueOf(lightArrowTint)
            distanceText.setTextColor(lightTextMain)
            nameText.setTextColor(lightTextSecondary)
            stopsText.setTextColor(lightTextSecondary)
        }
    }

    companion object {
        // Dark palette — drawn on top of a dark map. Background is 70% black so the
        // underlying map shows through, text is high-contrast white, arrow is a soft
        // light blue that stays readable without being garish.
        @JvmField val darkBackground: Int = 0xB3000000.toInt()
        @JvmField val darkTextMain: Int = 0xFFFFFFFF.toInt()
        @JvmField val darkTextSecondary: Int = 0xB3FFFFFF.toInt()
        @JvmField val darkArrowTint: Int = 0xFF90CAF9.toInt()

        // Light palette — drawn on top of a light map. Background is 80% white,
        // primary text is near-black, secondary is mid-grey, arrow is a saturated
        // dark blue that pops against the white card.
        @JvmField val lightBackground: Int = 0xCCFFFFFF.toInt()
        @JvmField val lightTextMain: Int = 0xFF212121.toInt()
        @JvmField val lightTextSecondary: Int = 0xFF616161.toInt()
        @JvmField val lightArrowTint: Int = 0xFF1565C0.toInt()
    }
}
