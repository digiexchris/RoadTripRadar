package ca.voiditswarranty.roadtripradar.car

import ca.voiditswarranty.roadtripradar.model.RadarRingsData
import ca.voiditswarranty.roadtripradar.model.buildRadarRingsData
import ca.voiditswarranty.roadtripradar.model.ringDistancesForZoom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position

/**
 * Tests for the GeoJSON serializer used to push the car range rings geometry to the
 * MapLibre native SDK's `GeoJsonSource.setGeoJson(...)`.
 *
 * The phone renders rings via a Compose `LineLayer` that consumes the
 * `FeatureCollection<LineString, JsonObject>` directly (no string round-trip); the car
 * renders the same geometry but via the native Android SDK, which takes a GeoJSON string.
 * The serializer must:
 *   1. Emit a valid `FeatureCollection` JSON.
 *   2. Preserve each feature's `LineString` coordinates (long, lat order — GeoJSON spec).
 *   3. Round-trip cleanly through `Json.parseToJsonElement` so the SDK will accept it.
 *   4. Produce empty `FeatureCollection` for an empty input (no NPE, no null entries).
 */
class CarRangeRingsGeoJsonTest {

    private val center = Position(longitude = -75.0, latitude = 45.0)

    @Test
    fun emptyRingsData_serializesAsEmptyFeatureCollection() {
        val empty = RadarRingsData(
            ringsFeatures = FeatureCollection(features = emptyList<org.maplibre.spatialk.geojson.Feature<LineString, kotlinx.serialization.json.JsonObject>>()),
            labelsFeatures = org.maplibre.spatialk.geojson.FeatureCollection(
                features = emptyList(),
            ),
        )
        val out = serializeRingsFeatures(empty.ringsFeatures)
        assertEquals("""{"type":"FeatureCollection","features":[]}""", out)
    }

    @Test
    fun ringsFeatures_fromBuildRadarRingsData_roundTripsToValidGeoJson() {
        // Use a known zoom bracket so we can assert exact feature count.
        val distances = ringDistancesForZoom(14.0)
        assertEquals(4, distances.size)
        val data = buildRadarRingsData(
            center = center,
            distances = distances,
            bearing = 0.0,
            useMetric = true,
        )
        val out = serializeRingsFeatures(data.ringsFeatures)
        // Round-trip: must be valid JSON, parseable as a JsonObject.
        val parsed = Json.parseToJsonElement(out).jsonObject
        assertEquals("FeatureCollection", parsed["type"]?.jsonPrimitive?.content)
        val features = parsed["features"]!!
            .let { (it as kotlinx.serialization.json.JsonArray) }
        assertEquals("expected one feature per ring", 4, features.size)
        // Each feature must have a LineString geometry with > 3 coordinates (a ring).
        for ((i, featureEl) in features.withIndex()) {
            val feature = featureEl.jsonObject
            val geom = feature["geometry"]!!.jsonObject
            assertEquals(
                "feature $i is not a LineString",
                "LineString",
                geom["type"]!!.jsonPrimitive.content,
            )
            val coords = geom["coordinates"]!!
                .let { it as kotlinx.serialization.json.JsonArray }
            assertTrue("ring $i should have > 3 coordinates", coords.size > 3)
            // GeoJSON coordinate order is [lon, lat]. The ring is generated from the center;
            // verify each coordinate has the correct sign (lon negative for -75, lat positive
            // for 45) — exact values vary because the circle is interpolated.
            for ((j, coordEl) in coords.withIndex()) {
                val coord = coordEl as kotlinx.serialization.json.JsonArray
                val lon = coord[0].jsonPrimitive.content.toDouble()
                val lat = coord[1].jsonPrimitive.content.toDouble()
                assertTrue("coord $j of ring $i has positive lon $lon (expected near -75)", lon < 0)
                assertTrue("coord $j of ring $i has zero/negative lat $lat (expected near 45)", lat > 0)
            }
        }
    }

    @Test
    fun ringsFeatures_preservesFeatureCountAcrossEmptyAndNonEmpty() {
        val empty: FeatureCollection<LineString, kotlinx.serialization.json.JsonObject> =
            FeatureCollection(features = emptyList())
        val nonEmpty = buildRadarRingsData(
            center = center,
            distances = ringDistancesForZoom(10.0),
            bearing = 0.0,
            useMetric = true,
        ).ringsFeatures

        assertEquals("""{"type":"FeatureCollection","features":[]}""", serializeRingsFeatures(empty))
        val nonEmptyJson = Json.parseToJsonElement(serializeRingsFeatures(nonEmpty)).jsonObject
        val featureCount = (nonEmptyJson["features"]!! as kotlinx.serialization.json.JsonArray).size
        // Bracket 10..11.99 has 4 rings (2km, 5km, 10km, 25km).
        assertEquals(4, featureCount)
        // Original feature count is preserved (not double-counted, not dropped).
        assertEquals(nonEmpty.features.size, featureCount)
    }

    @Test
    fun ringsFeatures_propertyDistance_isPreservedOnEachFeature() {
        // The SDK doesn't use this property today, but a future expression filter might. Lock the
        // behavior down so a refactor doesn't silently drop it.
        val data = buildRadarRingsData(
            center = center,
            distances = ringDistancesForZoom(12.0),
            bearing = 0.0,
            useMetric = true,
        )
        val out = serializeRingsFeatures(data.ringsFeatures)
        val features = (Json.parseToJsonElement(out).jsonObject["features"]!!
            as kotlinx.serialization.json.JsonArray)
        for ((i, featureEl) in features.withIndex()) {
            val props = featureEl.jsonObject["properties"]?.jsonObject
            assertNotNull("feature $i missing properties", props)
            // `properties` is a JsonObject — its "distance" key should be a number (in meters).
            val dist = props!!["distance"]
            assertNotNull("feature $i missing distance property", dist)
        }
    }
}
