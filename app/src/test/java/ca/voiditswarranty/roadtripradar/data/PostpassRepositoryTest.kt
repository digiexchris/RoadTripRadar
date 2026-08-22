package ca.voiditswarranty.roadtripradar.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLDecoder

/**
 * HTTP integration tests for [PostpassRepository]. The `baseUrl` ctor seam (added
 * in Phase 1's refactor) points at a local MockWebServer so the Postpass SQL endpoint
 * is fully exercised. The category-label resolution goes through the real
 * Application context (Robolectric) so we can also assert the labels get into the
 * feature properties.
 *
 * The pure helpers (`buildPostpassQuery`, `parsePoi`, etc.) are tested in
 * `PostpassHelpersTest` — this file is only the IO boundary.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PostpassRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repo: PostpassRepository

    private val ottawaBounds = BoundingBox(
        southwest = Position(latitude = 45.0, longitude = -76.0),
        northeast = Position(latitude = 45.5, longitude = -75.5),
    )

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        repo = PostpassRepository(
            context = ApplicationProvider.getApplicationContext(),
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun postpassFeature(
        osmId: Long = 1L,
        lon: Double = -75.7,
        lat: Double = 45.4,
        tagsJson: String = """{"amenity": "fuel", "name": "Joe's Gas"}""",
    ): String = """
        {
          "type": "Feature",
          "id": $osmId,
          "properties": { "osm_id": $osmId, "osm_type": "n", "tags": $tagsJson },
          "geometry": { "type": "Point", "coordinates": [$lon, $lat] }
        }
    """.trimIndent()

    private fun postpassCollection(vararg features: String): String {
        val body = features.joinToString(",\n")
        return """
            { "type": "FeatureCollection", "features": [ $body ] }
        """.trimIndent()
    }

    // -------- Empty categories short-circuit --------

    @Test
    fun fetchPois_emptyCategories_returnsEmptyWithoutHttp() = runTest {
        // No MockResponse enqueued — the repo should short-circuit before hitting the server.
        val result = repo.fetchPoisForTile(ottawaBounds, categories = emptySet())

        assertTrue("expected Success, got: $result", result is TileFetchResult.Success)
        val success = result as TileFetchResult.Success
        assertEquals(0, success.features.features.size)
        // And the server received no requests.
        assertEquals(0, server.requestCount)
    }

    // -------- Happy path --------

    @Test
    fun fetchPois_singleFeature_returnsOnePoi() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                postpassCollection(postpassFeature()),
            ),
        )

        val result = repo.fetchPoisForTile(ottawaBounds, categories = setOf("fuel"))

        assertTrue("expected Success, got: $result", result is TileFetchResult.Success)
        val features = (result as TileFetchResult.Success).features.features
        assertEquals(1, features.size)
        val props = features[0].properties!!
        assertEquals("Joe's Gas", props["name"]!!.jsonPrimitive.content)
        assertEquals("fuel", props["categoryQuery"]!!.jsonPrimitive.content)
    }

    @Test
    fun fetchPois_multipleFeatures_returnsAll() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                postpassCollection(
                    postpassFeature(osmId = 1L, tagsJson = """{"amenity": "fuel", "name": "Gas A"}"""),
                    postpassFeature(osmId = 2L, tagsJson = """{"amenity": "fuel", "name": "Gas B"}"""),
                    postpassFeature(osmId = 3L, tagsJson = """{"amenity": "restaurant", "name": "Diner"}"""),
                ),
            ),
        )

        val result = repo.fetchPoisForTile(ottawaBounds, categories = setOf("fuel", "restaurant"))

        assertTrue(result is TileFetchResult.Success)
        val features = (result as TileFetchResult.Success).features.features
        assertEquals(3, features.size)
    }

    // -------- Disabled categories are filtered server-side via SQL and client-side via parse --------

    @Test
    fun fetchPois_disabledCategoryFiltered_clientSide() = runTest {
        // The repo builds SQL that includes only the enabled categories. If the server
        // returns a feature for a *disabled* category, `findMatchedCategory` should
        // drop it (returning null), so the feature is filtered out client-side.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                postpassCollection(
                    postpassFeature(osmId = 1L, tagsJson = """{"amenity": "fuel", "name": "Gas"}"""),
                    postpassFeature(osmId = 2L, tagsJson = """{"amenity": "restaurant", "name": "Diner"}"""),
                ),
            ),
        )

        val result = repo.fetchPoisForTile(ottawaBounds, categories = setOf("fuel"))

        assertTrue(result is TileFetchResult.Success)
        val features = (result as TileFetchResult.Success).features.features
        // Only the fuel POI is enabled → the restaurant must be dropped.
        assertEquals(1, features.size)
        assertEquals("fuel", features[0].properties!!["categoryQuery"]!!.jsonPrimitive.content)
    }

    // -------- Malformed responses --------

    @Test
    fun fetchPois_malformedJson_returnsFailed() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))

        val result = repo.fetchPoisForTile(ottawaBounds, categories = setOf("fuel"))

        assertTrue("expected Failed on malformed JSON, got: $result", result is TileFetchResult.Failed)
    }

    @Test
    fun fetchPois_emptyBody_returnsFailed() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val result = repo.fetchPoisForTile(ottawaBounds, categories = setOf("fuel"))

        assertTrue("expected Failed on empty body, got: $result", result is TileFetchResult.Failed)
    }

    // -------- HTTP errors --------

    @Test
    fun fetchPois_404_returnsFailed() = runTest {
        // 4xx (not 429, not 5xx) is non-retriable → returns Failed directly.
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        val result = repo.fetchPoisForTile(ottawaBounds, categories = setOf("fuel"))

        assertTrue("expected Failed on 404, got: $result", result is TileFetchResult.Failed)
    }

    @Test
    fun fetchPois_400_returnsFailed() = runTest {
        // 4xx (not 429) is non-retriable.
        server.enqueue(MockResponse().setResponseCode(400).setBody("bad request"))

        val result = repo.fetchPoisForTile(ottawaBounds, categories = setOf("fuel"))

        assertTrue("expected Failed on 400, got: $result", result is TileFetchResult.Failed)
    }

    // -------- Request shape: SQL query --------

    @Test
    fun fetchPois_requestIsPost() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(postpassCollection()))

        repo.fetchPoisForTile(ottawaBounds, categories = setOf("fuel"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
    }

    @Test
    fun fetchPois_requestBodyContainsSql() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(postpassCollection()))

        repo.fetchPoisForTile(ottawaBounds, categories = setOf("fuel"))

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        val decoded = URLDecoder.decode(body, "UTF-8")
        // Strip the "data=" prefix the repo sets when form-encoding.
        assertTrue("body must contain FROM clause, got: $decoded", decoded.contains("FROM postpass_pointpolygon"))
        assertTrue("body must filter on amenity=fuel, got: $decoded", decoded.contains("tags->>'amenity' = 'fuel'"))
        assertTrue("body must include the bounds, got: $decoded", decoded.contains("ST_MakeEnvelope(-76.0, 45.0, -75.5, 45.5, 4326)"))
        assertTrue("body must include LIMIT, got: $decoded", decoded.contains("LIMIT 2000"))
    }

    @Test
    fun fetchPois_multipleCategories_joinsWithOr() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(postpassCollection()))

        repo.fetchPoisForTile(ottawaBounds, categories = setOf("fuel", "restaurant"))

        val body = server.takeRequest().body.readUtf8()
        val decoded = URLDecoder.decode(body, "UTF-8")
        assertTrue("body must include fuel filter, got: $decoded", decoded.contains("tags->>'amenity' = 'fuel'"))
        assertTrue("body must include restaurant filter, got: $decoded", decoded.contains("tags->>'amenity' = 'restaurant'"))
        assertTrue("body must OR-join the two conditions, got: $decoded", decoded.contains(" OR "))
    }

    // -------- Feature collection parsing --------

    @Test
    fun fetchPois_emptyResponse_returnsEmptyCollection() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"type":"FeatureCollection","features":[]}"""))

        val result = repo.fetchPoisForTile(ottawaBounds, categories = setOf("fuel"))

        assertTrue(result is TileFetchResult.Success)
        assertEquals(0, (result as TileFetchResult.Success).features.features.size)
    }

    @Test
    fun fetchPois_featureMissingTags_returnsEmpty() = runTest {
        // Feature with no "tags" property → parsePoi returns null → filtered out.
        val noTags = """
            {
              "type": "Feature",
              "properties": { "osm_id": 1, "osm_type": "n" },
              "geometry": { "type": "Point", "coordinates": [-75.7, 45.4] }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(postpassCollection(noTags)))

        val result = repo.fetchPoisForTile(ottawaBounds, categories = setOf("fuel"))

        assertTrue(result is TileFetchResult.Success)
        assertEquals(0, (result as TileFetchResult.Success).features.features.size)
    }

    @Test
    fun fetchPois_featureMissingGeometry_returnsEmpty() = runTest {
        // Feature with no "geometry" property → parsePoi returns null → filtered out.
        val noGeom = """
            {
              "type": "Feature",
              "properties": { "osm_id": 1, "osm_type": "n", "tags": {"amenity": "fuel", "name": "X"} }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(postpassCollection(noGeom)))

        val result = repo.fetchPoisForTile(ottawaBounds, categories = setOf("fuel"))

        assertTrue(result is TileFetchResult.Success)
        assertEquals(0, (result as TileFetchResult.Success).features.features.size)
    }

    // -------- Sanity --------

    @Test
    fun fetchPois_success_carriesFeatureCollection() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                postpassCollection(postpassFeature()),
            ),
        )

        val result = repo.fetchPoisForTile(ottawaBounds, categories = setOf("fuel"))

        assertTrue(result is TileFetchResult.Success)
        val success = result as TileFetchResult.Success
        assertNotNull("FeatureCollection must not be null on success", success.features)
    }
}
