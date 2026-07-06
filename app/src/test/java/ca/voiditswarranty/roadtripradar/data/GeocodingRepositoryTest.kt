package ca.voiditswarranty.roadtripradar.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.units.extensions.inMeters
import java.net.URLDecoder

/**
 * HTTP integration tests for [GeocodingRepository]. The `baseUrl` ctor seam
 * (added in Phase 2's refactor — the production class previously hardcoded the
 * Photon API URL) points at a local MockWebServer so the search and reverse-geocode
 * paths are fully exercised.
 *
 * The repo's failure semantics are: `searchByName` returns an empty list on any
 * error; `reverseGeocode` returns null. The tests pin both behaviors.
 */
class GeocodingRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repo: GeocodingRepository

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        // The baseUrl replaces the hardcoded photon.komoot.io. Trim trailing slashes
        // so we get a clean origin.
        repo = GeocodingRepository(baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    // -------- Forward search: happy path --------

    @Test
    fun searchByName_validResponse_returnsResults() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "type": "FeatureCollection",
                      "features": [
                        {
                          "type": "Feature",
                          "geometry": { "type": "Point", "coordinates": [-75.7, 45.4] },
                          "properties": {
                            "name": "Coffee Shop",
                            "street": "Main St",
                            "housenumber": "123",
                            "city": "Ottawa",
                            "state": "ON",
                            "country": "Canada"
                          }
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )

        val results = repo.searchByName(
            query = "coffee",
            centerLat = 45.4,
            centerLon = -75.7,
            bbox = ViewBox(-76.0, 45.0, -75.5, 45.5),
            userPosition = null,
        )

        assertEquals(1, results.size)
        val r = results[0]
        assertEquals("Coffee Shop", r.name)
        assertEquals("123 Main St, Ottawa, ON, Canada", r.subtitle)
        assertEquals(45.4, r.position.latitude, 0.0)
        assertEquals(-75.7, r.position.longitude, 0.0)
    }

    @Test
    fun searchByName_multipleFeatures_returnsAll() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "type": "FeatureCollection",
                      "features": [
                        {
                          "type": "Feature",
                          "geometry": { "type": "Point", "coordinates": [-75.7, 45.4] },
                          "properties": { "name": "Place A" }
                        },
                        {
                          "type": "Feature",
                          "geometry": { "type": "Point", "coordinates": [-75.6, 45.5] },
                          "properties": { "name": "Place B" }
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )

        val results = repo.searchByName(
            "p", 45.4, -75.7, ViewBox(-76.0, 45.0, -75.5, 45.5), null,
        )

        assertEquals(2, results.size)
        assertEquals("Place A", results[0].name)
        assertEquals("Place B", results[1].name)
    }

    @Test
    fun searchByName_emptyResults_returnsEmptyList() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "type": "FeatureCollection", "features": [] }""",
            ),
        )

        val results = repo.searchByName(
            "nothing", 45.4, -75.7, ViewBox(-76.0, 45.0, -75.5, 45.5), null,
        )

        assertTrue(results.isEmpty())
    }

    // -------- Forward search: name fallback chain --------

    @Test
    fun searchByName_streetOnly_noHousenumber() = runTest {
        // No housenumber → street alone in the subtitle.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "type": "FeatureCollection",
                      "features": [{
                        "type": "Feature",
                        "geometry": { "type": "Point", "coordinates": [-75.7, 45.4] },
                        "properties": { "name": "Park", "street": "Main St", "city": "Ottawa" }
                      }]
                    }
                """.trimIndent(),
            ),
        )

        val results = repo.searchByName(
            "park", 45.4, -75.7, ViewBox(-76.0, 45.0, -75.5, 45.5), null,
        )

        assertEquals(1, results.size)
        assertEquals("Main St, Ottawa", results[0].subtitle)
    }

    @Test
    fun searchByName_nameOnly_subtitleIsEmpty() = runTest {
        // Only `name` set → subtitle is empty.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "type": "FeatureCollection",
                      "features": [{
                        "type": "Feature",
                        "geometry": { "type": "Point", "coordinates": [-75.7, 45.4] },
                        "properties": { "name": "Spot" }
                      }]
                    }
                """.trimIndent(),
            ),
        )

        val results = repo.searchByName(
            "spot", 45.4, -75.7, ViewBox(-76.0, 45.0, -75.5, 45.5), null,
        )

        assertEquals(1, results.size)
        assertEquals("Spot", results[0].name)
        assertEquals("", results[0].subtitle)
    }

    // -------- Forward search: distance computation --------

    @Test
    fun searchByName_withUserPosition_populatesDistance() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "type": "FeatureCollection",
                      "features": [{
                        "type": "Feature",
                        "geometry": { "type": "Point", "coordinates": [-75.71, 45.41] },
                        "properties": { "name": "Nearby" }
                      }]
                    }
                """.trimIndent(),
            ),
        )

        val results = repo.searchByName(
            "near", 45.4, -75.7, ViewBox(-76.0, 45.0, -75.5, 45.5),
            userPosition = Position(latitude = 45.4, longitude = -75.7),
        )

        assertEquals(1, results.size)
        // Distance is a turf-typed value; convert to meters for the assertion.
        val dist = results[0].distance
        assertNotNull("distance must be set when userPosition is provided", dist)
        assertTrue("distance must be non-negative", dist!!.inMeters >= 0.0)
    }

    @Test
    fun searchByName_noUserPosition_distanceIsNull() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "type": "FeatureCollection",
                      "features": [{
                        "type": "Feature",
                        "geometry": { "type": "Point", "coordinates": [-75.7, 45.4] },
                        "properties": { "name": "Place" }
                      }]
                    }
                """.trimIndent(),
            ),
        )

        val results = repo.searchByName(
            "p", 45.4, -75.7, ViewBox(-76.0, 45.0, -75.5, 45.5), userPosition = null,
        )

        assertEquals(1, results.size)
        assertNull(results[0].distance)
    }

    // -------- Forward search: error paths --------

    @Test
    fun searchByName_malformedJson_returnsEmpty() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))

        val results = repo.searchByName(
            "x", 45.4, -75.7, ViewBox(-76.0, 45.0, -75.5, 45.5), null,
        )

        assertTrue("malformed JSON must return empty list, got: $results", results.isEmpty())
    }

    @Test
    fun searchByName_httpError_returnsEmpty() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))

        val results = repo.searchByName(
            "x", 45.4, -75.7, ViewBox(-76.0, 45.0, -75.5, 45.5), null,
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun searchByName_404_returnsEmpty() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        val results = repo.searchByName(
            "x", 45.4, -75.7, ViewBox(-76.0, 45.0, -75.5, 45.5), null,
        )

        assertTrue(results.isEmpty())
    }

    // -------- Forward search: feature filtering --------

    @Test
    fun searchByName_featureWithoutName_isDropped() = runTest {
        // mapNotNull drops features without a name.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "type": "FeatureCollection",
                      "features": [
                        {
                          "type": "Feature",
                          "geometry": { "type": "Point", "coordinates": [-75.7, 45.4] },
                          "properties": { "name": "Real" }
                        },
                        {
                          "type": "Feature",
                          "geometry": { "type": "Point", "coordinates": [-75.6, 45.5] },
                          "properties": { "street": "No Name Ave" }
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )

        val results = repo.searchByName(
            "x", 45.4, -75.7, ViewBox(-76.0, 45.0, -75.5, 45.5), null,
        )

        assertEquals(1, results.size)
        assertEquals("Real", results[0].name)
    }

    // -------- Reverse geocode: happy path --------

    @Test
    fun reverseGeocode_validResponse_returnsAddressString() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "type": "FeatureCollection",
                      "features": [{
                        "type": "Feature",
                        "properties": {
                          "name": "Spot",
                          "housenumber": "123",
                          "street": "Main St",
                          "city": "Ottawa",
                          "state": "ON",
                          "country": "Canada"
                        }
                      }]
                    }
                """.trimIndent(),
            ),
        )

        val result = repo.reverseGeocode(45.4, -75.7)

        assertEquals("123 Main St, Ottawa, ON, Canada", result)
    }

    @Test
    fun reverseGeocode_nameOnly_justReturnsNull() = runTest {
        // Only `name` set; the address parts (street/city/state/country) are all null,
        // so the joinToString result is empty, and the `ifEmpty { null }` maps it to null.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "type": "FeatureCollection",
                      "features": [{
                        "type": "Feature",
                        "properties": { "name": "Spot" }
                      }]
                    }
                """.trimIndent(),
            ),
        )

        val result = repo.reverseGeocode(45.4, -75.7)

        assertNull(result)
    }

    @Test
    fun reverseGeocode_streetOnly() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "type": "FeatureCollection",
                      "features": [{
                        "type": "Feature",
                        "properties": { "street": "Main St" }
                      }]
                    }
                """.trimIndent(),
            ),
        )

        val result = repo.reverseGeocode(45.4, -75.7)

        assertEquals("Main St", result)
    }

    // -------- Reverse geocode: error paths --------

    @Test
    fun reverseGeocode_httpError_returnsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))

        val result = repo.reverseGeocode(45.4, -75.7)

        assertNull(result)
    }

    @Test
    fun reverseGeocode_malformedJson_returnsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))

        val result = repo.reverseGeocode(45.4, -75.7)

        assertNull(result)
    }

    @Test
    fun reverseGeocode_emptyFeatures_returnsNull() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "type": "FeatureCollection", "features": [] }""",
            ),
        )

        val result = repo.reverseGeocode(45.4, -75.7)

        assertNull(result)
    }

    // -------- Request shape --------

    @Test
    fun searchByName_requestIsGet() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "type": "FeatureCollection", "features": [] }""",
            ),
        )

        repo.searchByName("coffee", 45.4, -75.7, ViewBox(-76.0, 45.0, -75.5, 45.5), null)

        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun searchByName_requestIncludesQueryAndBbox() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "type": "FeatureCollection", "features": [] }""",
            ),
        )

        repo.searchByName("coffee shop", 45.4, -75.7, ViewBox(-76.0, 45.0, -75.5, 45.5), null)

        val path = server.takeRequest().path!!
        val params = URLDecoder.decode(path.substringAfter('?', ""), "UTF-8")
        assertTrue("query must include q param, got: $path", params.contains("q=coffee shop"))
        assertTrue("query must include lat, got: $path", params.contains("lat=45.4"))
        assertTrue("query must include lon, got: $path", params.contains("lon=-75.7"))
        assertTrue("query must include bbox, got: $path", params.contains("bbox=-76.0,45.0,-75.5,45.5"))
        assertTrue("query must include limit, got: $path", params.contains("limit=10"))
    }

    @Test
    fun reverseGeocode_requestIncludesLatLon() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "type": "FeatureCollection", "features": [] }""",
            ),
        )

        repo.reverseGeocode(45.4, -75.7)

        val path = server.takeRequest().path!!
        val params = URLDecoder.decode(path.substringAfter('?', ""), "UTF-8")
        assertTrue("query must include lat, got: $path", params.contains("lat=45.4"))
        assertTrue("query must include lon, got: $path", params.contains("lon=-75.7"))
        assertTrue("query must include limit=1, got: $path", params.contains("limit=1"))
    }
}
