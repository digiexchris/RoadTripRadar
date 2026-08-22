package ca.voiditswarranty.roadtripradar.car

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the car ring/label color resolver. Mirrors the phone's palette in
 * [ca.voiditswarranty.roadtripradar.ui.MapLayers.RadarRingsLayers] (line 97-98):
 *   - ringColor: LightGray on dark, Black on light
 *   - haloColor: DarkGray on dark, White on light
 *
 * The car does NOT use Compose `Color` (those carry a ColorSpace). It uses raw ARGB ints
 * for the native `LineLayer.lineColor(...)` and `TextView.setShadowLayer(..., color)` calls,
 * so the resolver returns `Int`. The ARGB values must match the Compose `Color` constants
 * exactly so the car and phone agree when the user runs the same theme on both surfaces.
 */
class CarRangeRingColorsTest {

    @Test
    fun ringColor_onDarkStyle_isLightGray() {
        // Compose Color.LightGray = 0xFFC0C0C0. Phone passes this to LineLayer.color on dark.
        assertEquals(0xFFC0C0C0.toInt(), carRingColor(isDarkStyle = true))
    }

    @Test
    fun ringColor_onLightStyle_isBlack() {
        // Compose Color.Black = 0xFF000000. Phone passes this to LineLayer.color on light.
        assertEquals(0xFF000000.toInt(), carRingColor(isDarkStyle = false))
    }

    @Test
    fun haloColor_onDarkStyle_isDarkGray() {
        // Compose Color.DarkGray = 0xFF404040. Phone passes this to SymbolLayer.textHaloColor
        // on dark styles.
        assertEquals(0xFF404040.toInt(), carLabelHaloColor(isDarkStyle = true))
    }

    @Test
    fun haloColor_onLightStyle_isWhite() {
        // Compose Color.White = 0xFFFFFFFF. Phone passes this to SymbolLayer.textHaloColor
        // on light styles.
        assertEquals(0xFFFFFFFF.toInt(), carLabelHaloColor(isDarkStyle = false))
    }
}
