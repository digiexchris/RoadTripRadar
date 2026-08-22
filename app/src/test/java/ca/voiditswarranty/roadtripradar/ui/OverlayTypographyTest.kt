package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [OverlayTypography]. The values are exposed as `sp` [TextUnit]s;
 * we read the `.value` (raw Float) for the assertion. These are
 * reference values consumed by [WeatherLegend] and [WeatherTimeline].
 */
class OverlayTypographyTest {

    @Test
    fun timelineLegendLabelFontSize_isNineSp() {
        assertEquals(9f, OverlayTypography.timelineLegendLabelFontSize.value, 0.0001f)
    }

    @Test
    fun timelineLegendLabelLineHeight_isTenSp() {
        assertEquals(10f, OverlayTypography.timelineLegendLabelLineHeight.value, 0.0001f)
    }

    @Test
    fun timelineLegendLabelFontSize_isSp() {
        // Sanity check that the units are sp (not dp) — `.type` is
        // a TextUnitType enum. This guards against accidental changes
        // that would break the legend's vertical alignment with the
        // markers.
        assertEquals(androidx.compose.ui.unit.TextUnitType.Sp, OverlayTypography.timelineLegendLabelFontSize.type)
        assertEquals(androidx.compose.ui.unit.TextUnitType.Sp, OverlayTypography.timelineLegendLabelLineHeight.type)
    }
}
