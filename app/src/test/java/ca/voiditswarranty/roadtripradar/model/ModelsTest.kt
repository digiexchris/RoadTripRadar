package ca.voiditswarranty.roadtripradar.model

import ca.voiditswarranty.roadtripradar.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.units.extensions.inKilometers
import org.maplibre.spatialk.units.extensions.inMeters
import org.maplibre.spatialk.units.extensions.kilometers
import org.maplibre.spatialk.units.extensions.meters

/**
 * Tests for the pure helpers in `Models.kt` — `ringDistancesForZoom`,
 * `formatDistanceLabel`, `cleanString`, and `buildRadarRingsData`. Plus pins on the
 * `POI_CATEGORIES` / `MAX_POI_CATEGORIES` / `PrefsDefaults` public constants.
 *
 * The `MapStyle` enum is exercised in `MapStyleTest` (with the resolution helpers in
 * `MapStyleResolutionTest`).
 */
class ModelsTest {

    // -------- ringDistancesForZoom --------

    @Test
    fun ringDistancesForZoom_zoom0_returnsLongDistances() {
        // The "else" branch (zoom < 4) returns the longest distances.
        val rings = ringDistancesForZoom(0.0)
        assertEquals(3, rings.size)
        assertEquals(200.0, rings[0].inKilometers, 0.0)
    }

    @Test
    fun ringDistancesForZoom_zoom4_returnsVeryLong() {
        val rings = ringDistancesForZoom(4.0)
        assertEquals(4, rings.size)
        // 100, 250, 500, 1000 km
        assertEquals(100.0, rings[0].inKilometers, 0.0)
        assertEquals(250.0, rings[1].inKilometers, 0.0)
        assertEquals(500.0, rings[2].inKilometers, 0.0)
        assertEquals(1000.0, rings[3].inKilometers, 0.0)
    }

    @Test
    fun ringDistancesForZoom_zoom6_returnsLong() {
        val rings = ringDistancesForZoom(6.0)
        assertEquals(4, rings.size)
        // 25, 50, 100, 200 km
        assertEquals(25.0, rings[0].inKilometers, 0.0)
    }

    @Test
    fun ringDistancesForZoom_zoom8_returnsMedium() {
        val rings = ringDistancesForZoom(8.0)
        // 10, 25, 50 km — only 3 rings
        assertEquals(3, rings.size)
        assertEquals(10.0, rings[0].inKilometers, 0.0)
        assertEquals(25.0, rings[1].inKilometers, 0.0)
        assertEquals(50.0, rings[2].inKilometers, 0.0)
    }

    @Test
    fun ringDistancesForZoom_zoom10_returnsSmall() {
        val rings = ringDistancesForZoom(10.0)
        // 2, 5, 10, 25 km
        assertEquals(4, rings.size)
        assertEquals(2.0, rings[0].inKilometers, 0.0)
    }

    @Test
    fun ringDistancesForZoom_zoom12_returnsSmaller() {
        val rings = ringDistancesForZoom(12.0)
        // 1, 2, 5, 10 km
        assertEquals(4, rings.size)
        assertEquals(1.0, rings[0].inKilometers, 0.0)
    }

    @Test
    fun ringDistancesForZoom_zoom14_returnsSmallKm() {
        val rings = ringDistancesForZoom(14.0)
        // 500m, 1, 2, 5 km
        assertEquals(4, rings.size)
        assertEquals(500.0, rings[0].inMeters, 0.0)
        assertEquals(1.0, rings[1].inKilometers, 0.0)
    }

    @Test
    fun ringDistancesForZoom_zoom16_returnsTiny() {
        val rings = ringDistancesForZoom(16.0)
        // 250m, 500m, 1, 2 km
        assertEquals(4, rings.size)
        assertEquals(250.0, rings[0].inMeters, 0.0)
    }

    @Test
    fun ringDistancesForZoom_zoom22_returnsSmallest() {
        // Same as zoom 16+ — the highest bracket.
        val rings = ringDistancesForZoom(22.0)
        assertEquals(4, rings.size)
        assertEquals(250.0, rings[0].inMeters, 0.0)
    }

    // -------- formatDistanceLabel --------

    @Test
    fun formatDistanceLabel_metricUnder1km_showsMeters() {
        // 500 m → "500 m".
        assertEquals("500 m", formatDistanceLabel(500.0.meters, useMetric = true))
    }

    @Test
    fun formatDistanceLabel_metricOver1km_showsKm() {
        // 1500 m → "1.5 km".
        assertEquals("1.5 km", formatDistanceLabel(1500.0.meters, useMetric = true))
    }

    @Test
    fun formatDistanceLabel_metricWholeKm_showsIntKm() {
        // 5 km → "5 km" (cleanString strips the .0).
        assertEquals("5 km", formatDistanceLabel(5.0.kilometers, useMetric = true))
    }

    @Test
    fun formatDistanceLabel_imperial_showsMiles() {
        // 1609.34 m ≈ 1.0 mi.
        val result = formatDistanceLabel(1609.34.meters, useMetric = false)
        assertTrue("expected miles output, got: $result", result.endsWith(" mi"))
    }

    // -------- cleanString --------

    @Test
    fun cleanString_wholeNumber_returnsIntString() {
        assertEquals("1", 1.0.cleanString())
        assertEquals("100", 100.0.cleanString())
    }

    @Test
    fun cleanString_fractional_returnsOneDecimal() {
        assertEquals("1.5", 1.5.cleanString())
        assertEquals("100.7", 100.7.cleanString())
    }

    // -------- buildRadarRingsData --------

    private val ottawa = Position(latitude = 45.4, longitude = -75.7)

    @Test
    fun buildRadarRingsData_emptyDistances_returnsEmptyFeatureCollections() {
        val data = buildRadarRingsData(
            center = ottawa,
            distances = emptyList(),
            bearing = 0.0,
            useMetric = true,
        )
        assertEquals(0, data.ringsFeatures.features.size)
        assertEquals(0, data.labelsFeatures.features.size)
    }

    @Test
    fun buildRadarRingsData_knownCenter_producesExpectedRingCount() {
        val data = buildRadarRingsData(
            center = ottawa,
            distances = listOf(1.0.kilometers, 2.0.kilometers),
            bearing = 0.0,
            useMetric = true,
        )
        // One feature per distance.
        assertEquals(2, data.ringsFeatures.features.size)
        assertEquals(2, data.labelsFeatures.features.size)
    }

    @Test
    fun buildRadarRingsData_ringFeatureCarriesDistanceProperty() {
        val data = buildRadarRingsData(
            center = ottawa,
            distances = listOf(1.0.kilometers),
            bearing = 0.0,
            useMetric = true,
        )
        val feature = data.ringsFeatures.features.first()
        // The `distance` property is set to the distance in meters.
        val dist = feature.properties!!["distance"]!!
        assertEquals(1000.0, dist.toString().toDouble(), 0.0)
    }

    @Test
    fun buildRadarRingsData_labelFeatureCarriesFormattedLabel() {
        val data = buildRadarRingsData(
            center = ottawa,
            distances = listOf(1.0.kilometers, 5.0.kilometers),
            bearing = 0.0,
            useMetric = true,
        )
        // Each label is a stringified `formatDistanceLabel`.
        val labels = data.labelsFeatures.features.map { it.properties!!["label"]!!.toString().trim('"') }
        assertEquals(listOf("1 km", "5 km"), labels)
    }

    @Test
    fun buildRadarRingsData_labelAtBearing_isClosestToThatBearing() {
        // The label point is the vertex of the ring polygon whose bearing from the center
        // is closest to the requested bearing. With a north bearing (0°), the label should
        // be on the topmost point of the ring (max latitude, near-zero longitude offset).
        val data = buildRadarRingsData(
            center = ottawa,
            distances = listOf(1.0.kilometers),
            bearing = 0.0,
            useMetric = true,
        )
        val label = data.labelsFeatures.features.first()
        val pt = label.geometry as org.maplibre.spatialk.geojson.Point
        // The label should be north of center. 1 km ≈ 0.009° latitude.
        assertTrue(
            "expected label north of center, got lat=${pt.coordinates.latitude}",
            pt.coordinates.latitude > ottawa.latitude,
        )
    }

    // -------- POI_CATEGORIES / MAX_POI_CATEGORIES --------

    @Test
    fun poiCategories_hasExpectedSize() {
        // Pin the 29-entry list. A future add / remove is intentional only if the test
        // is updated alongside it.
        assertEquals(29, POI_CATEGORIES.size)
    }

    @Test
    fun poiCategories_queriesAreUnique() {
        val queries = POI_CATEGORIES.map { it.query }
        assertEquals("category query strings must be unique", queries.size, queries.toSet().size)
    }

    @Test
    fun poiCategories_eachEntryHasLabelAndIcon() {
        for (c in POI_CATEGORIES) {
            assertTrue("category ${c.query} must have a non-empty iconName", c.iconName.isNotEmpty())
            // The labelRes is a generated R.string id. We can't check the string content
            // here, but it should be non-zero (the R class assigns non-zero ids for
            // existing resources).
            assertTrue(
                "category ${c.query} must have a non-zero labelRes",
                c.labelRes != 0,
            )
        }
    }

    @Test
    fun maxPoiCategories_is5() {
        assertEquals(5, MAX_POI_CATEGORIES)
    }

    // -------- PrefsDefaults --------

    @Test
    fun prefsDefaults_zoomLevel_is9() {
        assertEquals(9.0f, PrefsDefaults.ZOOM_LEVEL, 0.0f)
    }

    @Test
    fun prefsDefaults_radarOpacity_is0_6() {
        assertEquals(0.6f, PrefsDefaults.RADAR_OPACITY, 0.0f)
    }

    @Test
    fun prefsDefaults_useMetric_isTrue() {
        assertEquals(true, PrefsDefaults.USE_METRIC)
    }

    @Test
    fun prefsDefaults_autoAdvanceThreshold_is100() {
        assertEquals(100, PrefsDefaults.AUTO_ADVANCE_THRESHOLD_M)
    }

    @Test
    fun prefsDefaults_prefsVersion_isCurrent() {
        // The current schema version. Pin the value so a future bump is intentional.
        assertEquals(11, PrefsDefaults.PREFS_VERSION)
    }
}
