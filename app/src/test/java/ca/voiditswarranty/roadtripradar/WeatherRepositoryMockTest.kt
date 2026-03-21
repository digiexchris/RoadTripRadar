package ca.voiditswarranty.roadtripradar

import ca.voiditswarranty.roadtripradar.data.WeatherRepository
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests using MockWebServer to prove WeatherRepository silently swallows
 * rate-limit (429) responses and cannot distinguish them from other failures.
 */
class WeatherRepositoryMockTest {

    private lateinit var server: MockWebServer
    private lateinit var repo: WeatherRepository

    private val validMetadataJson = """
        {
            "version": "2.0",
            "generated": 1700000000,
            "host": "https://tilecache.rainviewer.com",
            "radar": {
                "past": [
                    {"time": 1699999200, "path": "/v2/radar/1699999200"},
                    {"time": 1699999500, "path": "/v2/radar/1699999500"},
                    {"time": 1699999800, "path": "/v2/radar/1699999800"}
                ],
                "nowcast": []
            }
        }
    """.trimIndent()

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        repo = WeatherRepository(baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    /**
     * Test D: A 429 response from the metadata endpoint causes fetchFrames()
     * to return null — the rate limit is silently swallowed.
     */
    @Test
    fun `fetchFrames returns null on 429`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("Too Many Requests"))

        val result = repo.fetchFrames(0)

        assertNull("Expected null when server returns 429, but got $result", result)
    }

    /**
     * Test E: After a 429 failure, fetchFrames does NOT retry — it makes
     * exactly one request and gives up.
     */
    @Test
    fun `fetchFrames does not retry after 429`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("Too Many Requests"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(validMetadataJson))

        val result = repo.fetchFrames(0)

        assertNull("Expected null on 429, but got $result", result)
        assertEquals(
            "Expected exactly 1 request (no retry), but got ${server.requestCount}",
            1,
            server.requestCount,
        )
    }

    /**
     * Test F: fetchFrames cannot distinguish a 429 (rate limit) from a 500
     * (server error) — both return null identically.
     */
    @Test
    fun `fetchFrames cannot distinguish 429 from server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("Too Many Requests"))
        val result429 = repo.fetchFrames(0)

        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        val result500 = repo.fetchFrames(0)

        server.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))
        val result503 = repo.fetchFrames(0)

        assertNull("429 should return null", result429)
        assertNull("500 should return null", result500)
        assertNull("503 should return null", result503)
    }

    /**
     * Sanity check: fetchFrames works correctly when the server returns valid data.
     */
    @Test
    fun `fetchFrames parses valid response correctly`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validMetadataJson))

        val result = repo.fetchFrames(0)

        assertNotNull("Expected valid result but got null", result)
        assertEquals(1700000000L, result!!.generated)
        assertEquals(3, result.paths.size)
        assertEquals("/v2/radar/1699999200", result.paths[0])
    }

    /**
     * Verify the deduplication logic — same generated timestamp returns null.
     */
    @Test
    fun `fetchFrames returns null when generated matches lastGenerated`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validMetadataJson))

        val result = repo.fetchFrames(1700000000L)

        assertNull("Expected null when generated matches lastGenerated", result)
    }
}
