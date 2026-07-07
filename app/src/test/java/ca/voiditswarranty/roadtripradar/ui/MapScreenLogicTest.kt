package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.spatialk.units.extensions.inMeters

/**
 * Unit tests for the pure math helpers in [MapScreenLogic]. These
 * helpers were extracted from `MapScreen` to be testable in isolation
 * (the composable is dominated by Compose state management that's
 * impractical to test in JVM unit tests).
 */
class MapScreenLogicTest {

    // ---- computeCameraOffsetPadding ----

    @Test
    fun padding_zeroOffset_returnsZeroTopPadding() {
        val (top, _) = computeCameraOffsetPadding(usableHeight = 1000.dp, centerOffsetFraction = 0f)
        assertEquals(1000.dp, top)
    }

    @Test
    fun padding_quarterOffset_returnsSymmetricPadding() {
        // usableHeight=1000, fraction=0.25 → desiredBottomOffset=250
        // top = 1000 - 250*2 = 500
        // bottom = 250*2 - 1000 = -500 → coerced to 0
        val (top, bottom) = computeCameraOffsetPadding(1000.dp, 0.25f)
        assertEquals(500.dp, top)
        assertEquals(0.dp, bottom)
    }

    @Test
    fun padding_negativeResult_isCoercedToZero() {
        // For very high offset fractions, top can be negative → coerce to 0
        val (top, _) = computeCameraOffsetPadding(1000.dp, 1.0f)
        // desiredBottomOffset = 1000; top = 1000 - 2000 = -1000 → 0
        assertEquals(0.dp, top)
    }

    @Test
    fun padding_zeroHeight_returnsZeroTopPadding() {
        val (top, _) = computeCameraOffsetPadding(0.dp, 0.5f)
        assertEquals(0.dp, top)
    }

    // ---- computePoiInfo ----

    @Test
    fun poiInfo_nullUser_returnsNull() {
        val poi = org.maplibre.spatialk.geojson.Position(longitude = -75.0, latitude = 45.0)
        assertEquals(null, computePoiInfo(userPosition = null, poiPosition = poi))
    }

    @Test
    fun poiInfo_nullPoi_returnsNull() {
        val user = org.maplibre.spatialk.geojson.Position(longitude = -75.0, latitude = 45.0)
        assertEquals(null, computePoiInfo(userPosition = user, poiPosition = null))
    }

    @Test
    fun poiInfo_bothNull_returnsNull() {
        assertEquals(null, computePoiInfo(userPosition = null, poiPosition = null))
    }

    @Test
    fun poiInfo_validInputs_returnsPair() {
        val user = org.maplibre.spatialk.geojson.Position(longitude = -75.0, latitude = 45.0)
        val poi = org.maplibre.spatialk.geojson.Position(longitude = -75.01, latitude = 45.01)
        val result = computePoiInfo(userPosition = user, poiPosition = poi)
        assertTrue("expected non-null POI info", result != null)
        // Distance is positive (in meters, but unit is Length)
        assertTrue("distance should be > 0", result!!.first.inMeters > 0)
    }

    // ---- hasGoodGpsFix ----

    @Test
    fun gpsFix_gpsDisabled_returnsFalse() {
        assertEquals(false, hasGoodGpsFix(useGps = false, hasLocation = true, accuracyMeters = 5.0))
    }

    @Test
    fun gpsFix_noLocation_returnsFalse() {
        assertEquals(false, hasGoodGpsFix(useGps = true, hasLocation = false, accuracyMeters = 5.0))
    }

    @Test
    fun gpsFix_nullAccuracy_returnsFalse() {
        assertEquals(false, hasGoodGpsFix(useGps = true, hasLocation = true, accuracyMeters = null))
    }

    @Test
    fun gpsFix_accuracyUnder50_returnsTrue() {
        assertEquals(true, hasGoodGpsFix(useGps = true, hasLocation = true, accuracyMeters = 25.0))
    }

    @Test
    fun gpsFix_accuracyExactly50_returnsFalse() {
        // The check is `<`, so 50.0 is not "good enough"
        assertEquals(false, hasGoodGpsFix(useGps = true, hasLocation = true, accuracyMeters = 50.0))
    }

    @Test
    fun gpsFix_accuracyOver50_returnsFalse() {
        assertEquals(false, hasGoodGpsFix(useGps = true, hasLocation = true, accuracyMeters = 100.0))
    }
}
