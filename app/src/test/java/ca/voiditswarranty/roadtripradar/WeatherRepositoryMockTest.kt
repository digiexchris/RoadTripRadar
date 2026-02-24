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
        println("Test D PASSED: 429 response silently returns null (rate limit invisible to caller)")
    }

    /**
     * Test E: After a 429 failure, fetchFrames does NOT retry — it makes
     * exactly one request and gives up.
     */
    @Test
    fun `fetchFrames does not retry after 429`() = runTest {
        // First request: 429. Second request: valid 200 (should never be reached).
        server.enqueue(MockResponse().setResponseCode(429).setBody("Too Many Requests"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(validMetadataJson))

        val result = repo.fetchFrames(0)

        assertNull("Expected null on 429, but got $result", result)
        assertEquals(
            "Expected exactly 1 request (no retry), but got ${server.requestCount}",
            1,
            server.requestCount,
        )
        println("Test E PASSED: No retry after 429 — only 1 request made, next attempt is 60s later")
    }

    /**
     * Test F: fetchFrames cannot distinguish a 429 (rate limit) from a 500
     * (server error) — both return null identically.
     */
    @Test
    fun `fetchFrames cannot distinguish 429 from server error`() = runTest {
        // Test with 429
        server.enqueue(MockResponse().setResponseCode(429).setBody("Too Many Requests"))
        val result429 = repo.fetchFrames(0)

        // Test with 500
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        val result500 = repo.fetchFrames(0)

        // Test with network-level error (connection reset)
        // Not easily done with MockWebServer, so we test 503 instead
        server.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))
        val result503 = repo.fetchFrames(0)

        assertNull("429 should return null", result429)
        assertNull("500 should return null", result500)
        assertNull("503 should return null", result503)

        println("Test F PASSED: All error types (429, 500, 503) return identical null — no differentiation")
        println("  This means the app cannot detect rate limiting or implement targeted retry/backoff")
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
        println("Sanity check PASSED: Valid response parsed correctly")
    }

    /**
     * Verify the deduplication logic — same generated timestamp returns null.
     */
    @Test
    fun `fetchFrames returns null when generated matches lastGenerated`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validMetadataJson))

        val result = repo.fetchFrames(1700000000L)

        assertNull("Expected null when generated matches lastGenerated", result)
        println("Dedup check PASSED: Skips processing when generated timestamp unchanged")
    }
}
