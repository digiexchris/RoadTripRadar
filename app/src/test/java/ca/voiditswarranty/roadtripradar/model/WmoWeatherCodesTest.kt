package ca.voiditswarranty.roadtripradar.model

import ca.voiditswarranty.roadtripradar.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WmoWeatherCodesTest {

    @Test
    fun documentedCodesMapToSpecificLabels() {
        // Every WMO code the Open-Meteo API can return must map to its own label,
        // never to the generic "unknown" fallback.
        val documented = listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57,
            61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
        for (code in documented) {
            assertNotEquals(
                "WMO code $code should map to a specific label, not wmo_unknown",
                R.string.wmo_unknown,
                WmoWeatherCodes.labelRes(code),
            )
        }
    }

    @Test
    fun unknownCodesFallBackToUnknown() {
        assertEquals(R.string.wmo_unknown, WmoWeatherCodes.labelRes(-1))
        assertEquals(R.string.wmo_unknown, WmoWeatherCodes.labelRes(4))
        assertEquals(R.string.wmo_unknown, WmoWeatherCodes.labelRes(100))
    }

    @Test
    fun distinctCodesHaveDistinctLabels() {
        // Sanity: a couple of well-known codes resolve to different string resources.
        assertNotEquals(WmoWeatherCodes.labelRes(0), WmoWeatherCodes.labelRes(61))
        assertNotEquals(WmoWeatherCodes.labelRes(0), WmoWeatherCodes.labelRes(95))
    }
}