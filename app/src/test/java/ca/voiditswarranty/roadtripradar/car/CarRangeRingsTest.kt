package ca.voiditswarranty.roadtripradar.car

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [rangeRingsZoomBracket], the bracket-index lookup used to decide when the
 * car's range-ring geometry should be rebuilt. The bracket mapping mirrors
 * [ca.voiditswarranty.roadtripradar.model.ringDistancesForZoom] — every boundary in that
 * function (zoom 4 / 6 / 8 / 10 / 12 / 14 / 16) is a bracket transition.
 *
 * The function takes a raw `Double` zoom (not an integer tier) and returns an Int 0..7
 * for use as a cache key / change-detection value.
 */
class CarRangeRingsTest {

    @Test
    fun bracket_highestTier_atZoom16AndAbove() {
        assertEquals(0, rangeRingsZoomBracket(16.0))
        assertEquals(0, rangeRingsZoomBracket(18.5))
        assertEquals(0, rangeRingsZoomBracket(22.0))
    }

    @Test
    fun bracket_below16_butAtOrAbove14() {
        assertEquals(1, rangeRingsZoomBracket(15.9))
        assertEquals(1, rangeRingsZoomBracket(14.0))
        assertEquals(1, rangeRingsZoomBracket(14.5))
    }

    @Test
    fun bracket_below14_butAtOrAbove12() {
        assertEquals(2, rangeRingsZoomBracket(13.9))
        assertEquals(2, rangeRingsZoomBracket(12.0))
        assertEquals(2, rangeRingsZoomBracket(12.5))
    }

    @Test
    fun bracket_below12_butAtOrAbove10() {
        assertEquals(3, rangeRingsZoomBracket(11.9))
        assertEquals(3, rangeRingsZoomBracket(10.0))
        assertEquals(3, rangeRingsZoomBracket(10.5))
    }

    @Test
    fun bracket_below10_butAtOrAbove8() {
        assertEquals(4, rangeRingsZoomBracket(9.9))
        assertEquals(4, rangeRingsZoomBracket(8.0))
        assertEquals(4, rangeRingsZoomBracket(8.5))
    }

    @Test
    fun bracket_below8_butAtOrAbove6() {
        assertEquals(5, rangeRingsZoomBracket(7.9))
        assertEquals(5, rangeRingsZoomBracket(6.0))
        assertEquals(5, rangeRingsZoomBracket(6.5))
    }

    @Test
    fun bracket_below6_butAtOrAbove4() {
        assertEquals(6, rangeRingsZoomBracket(5.9))
        assertEquals(6, rangeRingsZoomBracket(4.0))
        assertEquals(6, rangeRingsZoomBracket(4.5))
    }

    @Test
    fun bracket_below4_lowestTier() {
        assertEquals(7, rangeRingsZoomBracket(3.9))
        assertEquals(7, rangeRingsZoomBracket(0.0))
        assertEquals(7, rangeRingsZoomBracket(-5.0))
    }
}
