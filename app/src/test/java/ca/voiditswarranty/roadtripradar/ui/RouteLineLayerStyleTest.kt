package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the visual style of the route-line layers so a future "let's make the
 * line thicker" or "let's change the color" change is intentional, not
 * accidental. The values are taken from the current production render in
 * [WaypointRouteLineLayer] (MapLayers.kt) — the bug fix in commit `04634bf`
 * landed on these values.
 */
class RouteLineLayerStyleTest {

    @Test
    fun style_inactiveLayer_usesExpectedColorWidthAndDasharray() {
        val style = RouteLineLayerStyle.INACTIVE
        assertEquals(Color.Green, style.color)
        assertEquals(5.dp, style.width)
        assertEquals(0.6f, style.opacity, 0.001f)
        assertEquals(listOf(2, 3), style.dasharray)
    }

    @Test
    fun style_activeLayer_usesExpectedColorWidthAndOpacity() {
        val style = RouteLineLayerStyle.ACTIVE
        assertEquals(Color.Green, style.color)
        assertEquals(5.dp, style.width)
        assertEquals(0.95f, style.opacity, 0.001f)
        assertNull(style.dasharray)
    }
}