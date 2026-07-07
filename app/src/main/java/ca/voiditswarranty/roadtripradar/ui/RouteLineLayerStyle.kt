package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visual styling for the two halves of the route line, pinned by
 * [RouteLineLayerStyleTest] so a future "let's make the line thicker" change
 * is intentional, not accidental.
 *
 * - [INACTIVE] is drawn dashed and is the planned path *before* the active
 *   waypoint.
 * - [ACTIVE] is drawn solid and is the live user→target approach that
 *   advances as the user moves.
 */
internal data class RouteLineLayerStyle(
    val color: Color,
    val width: Dp,
    val opacity: Float,
    val dasharray: List<Int>?,
) {
    companion object {
        val INACTIVE = RouteLineLayerStyle(
            color = Color.Green,
            width = 5.dp,
            opacity = 0.6f,
            dasharray = listOf(2, 3),
        )
        val ACTIVE = RouteLineLayerStyle(
            color = Color.Green,
            width = 5.dp,
            opacity = 0.95f,
            dasharray = null,
        )
    }
}