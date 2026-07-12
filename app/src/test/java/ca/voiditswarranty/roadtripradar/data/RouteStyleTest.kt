package ca.voiditswarranty.roadtripradar.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the shared route line visual constants. Phone reads [RouteStyle.COLOR];
 * car reads [RouteStyle.ARGB]. These tests pin the contract that both surfaces
 * see the same green, and that the dash/opacity values are stable.
 */
class RouteStyleTest {

    @Test
    fun COLOR_isComposeGreen() {
        assertEquals(Color.Green, RouteStyle.COLOR)
    }

    @Test
    fun ARGB_isOpaqueBrightGreen() {
        assertEquals(0xFF00FF00.toInt(), RouteStyle.ARGB)
    }

    @Test
    fun ARGB_matchesColorGreenArgb() {
        // The phone's Compose color and the car's raw ARGB int must be the same color.
        assertEquals(Color.Green.toArgb(), RouteStyle.ARGB)
    }

    @Test
    fun ACTIVE_OPACITY_isHigherThanInactive() {
        // Active (solid user→target) is more opaque than inactive (dashed planned).
        assertTrue(RouteStyle.ACTIVE_OPACITY > RouteStyle.INACTIVE_OPACITY)
    }

    @Test
    fun INACTIVE_DASH_hasTwoEntries() {
        assertEquals(listOf(2, 3), RouteStyle.INACTIVE_DASH)
    }
}