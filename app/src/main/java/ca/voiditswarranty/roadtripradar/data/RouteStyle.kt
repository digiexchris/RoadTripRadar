package ca.voiditswarranty.roadtripradar.data

import androidx.compose.ui.graphics.Color

/**
 * Visual style for the two halves of the route line, shared by the
 * phone (ui/MapLayers.WaypointRouteLineLayer) and the car
 * (car/CarMapContainer.setupOverlays). The single source of truth so
 * the phone and car can't drift apart again.
 *
 * The phone reads [COLOR]; the car reads [ARGB] (raw int, no Compose).
 * They are the same color: Compose `Color.Green` is `0xFF00FF00` with
 * full alpha.
 */
object RouteStyle {
    val COLOR: Color = Color.Green
    val ARGB: Int = 0xFF00FF00.toInt()
    const val ACTIVE_OPACITY: Float = 0.95f
    const val INACTIVE_OPACITY: Float = 0.6f
    val INACTIVE_DASH: List<Int> = listOf(2, 3)
}