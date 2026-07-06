package ca.voiditswarranty.roadtripradar.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

/**
 * HTTP integration tests for [OpenMeteoRepository]. The `baseUrl` ctor seam
 * (added in Phase 1's refactor) points at a local MockWebServer so the
 * Open-Meteo URL is fully exercised without hitting the real API.
 *
 * The pure helpers (`computeTrendNextHour`, `parseOpenMeteoTimeToZoned`) are
 * tested in `OpenMeteoHelpersTest` — this file is only the IO boundary.
 */
class OpenMeteoRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repo: OpenMeteoRepository

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        // The repo does `URL("$baseUrl/forecast?$q")` so the baseUrl should not
        // include a trailing slash; trim it.
        repo = OpenMeteoRepository(baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun validBody(
        temp: Double = 20.0,
        weatherCode: Int = 1,
        windSpeed: Double = 10.0,
        windDir: Int = 90,
        gusts: Double = 15.0,
        currentTime: String = "2026-07-03T14:00",
        nextHourTemp: Double? = 22.0,
        nextHourTime: String = "2026-07-03T15:00",
        timezone: String = "UTC",
    ): String {
        val nextHourEntry = if (nextHourTemp != null) {
            """"time": ["$nextHourTime"], "temperature_2m": [$nextHourTemp]"""
        } else {
            """"time": [], "temperature_2m": []"""
        }
        return """
            {
              "latitude": 45.4,
              "longitude": -75.7,
              "timezone": "$timezone",
              "current": {
                "time": "$currentTime",
                "temperature_2m": $temp,
                "weather_code": $weatherCode,
                "wind_speed_10m": $windSpeed,
                "wind_direction_10m": $windDir,
                "wind_gusts_10m": $gusts
              },
              "hourly": { $nextHourEntry }
            }
        """.trimIndent()
    }

    // -------- Happy path: valid response --------

    @Test
    fun fetchCurrent_validResponse_returnsSnapshot() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validBody()))

        val result = repo.fetchCurrent(45.4, -75.7)

        assertTrue("expected success, got: $result", result.isSuccess)
        val snap = result.getOrNull()
        assertNotNull("snapshot must not be null on success", snap)
        assertEquals(20.0, snap!!.temperatureCelsius, 0.0)
        assertEquals(1, snap.weatherCode)
        assertEquals(10.0, snap.windSpeedKmh, 0.0)
        assertEquals(90, snap.windDirectionDeg)
        assertEquals(15.0, snap.windGustsKmh, 0.0)
        // 14:00=20C, 15:00=22C → trend = +2.
        assertEquals(2.0, snap.tempTrendCelsius!!, 0.001)
    }

    @Test
    fun fetchCurrent_requestHitsForecastPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validBody()))

        repo.fetchCurrent(45.4, -75.7)

        val request = server.takeRequest()
        assertEquals("/forecast", request.path!!.substringBefore('?'))
        assertEquals("GET", request.method)
    }

    @Test
    fun fetchCurrent_requestIncludesCurrentAndHourlyFields() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validBody()))

        repo.fetchCurrent(45.4, -75.7)

        val query = server.takeRequest().path!!.substringAfter('?', "")
        val params = URLDecoder.decode(query, "UTF-8")
        assertTrue("query must include latitude, got: $query", params.contains("latitude=45.4"))
        assertTrue("query must include longitude, got: $query", params.contains("longitude=-75.7"))
        assertTrue(
            "query must request current fields, got: $query",
            params.contains("current=temperature_2m") &&
                params.contains("weather_code") &&
                params.contains("wind_speed_10m") &&
                params.contains("wind_direction_10m") &&
                params.contains("wind_gusts_10m"),
        )
        assertTrue("query must request hourly temp, got: $query", params.contains("hourly=temperature_2m"))
        assertTrue("query must request forecast_hours=2, got: $query", params.contains("forecast_hours=2"))
    }

    // -------- Trend computation --------

    @Test
    fun fetchCurrent_trendCooling_returnsNegative() = runTest {
        // Current 20C, next hour 18C → trend = -2.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                validBody(temp = 20.0, nextHourTemp = 18.0, currentTime = "2026-07-03T14:00", nextHourTime = "2026-07-03T15:00"),
            ),
        )

        val result = repo.fetchCurrent(45.4, -75.7)

        assertTrue(result.isSuccess)
        val trend = result.getOrNull()!!.tempTrendCelsius
        assertNotNull("trend should be computed when next-hour slot exists", trend)
        assertEquals(-2.0, trend!!, 0.001)
    }

    @Test
    fun fetchCurrent_trendMissing_returnsNullTrend() = runTest {
        // Server omits the matching next-hour slot → trend stays null.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "latitude": 45.4, "longitude": -75.7, "timezone": "UTC",
                      "current": {
                        "time": "2026-07-03T14:00",
                        "temperature_2m": 20.0, "weather_code": 1,
                        "wind_speed_10m": 10.0, "wind_direction_10m": 90, "wind_gusts_10m": 15.0
                      },
                      "hourly": { "time": ["2026-07-03T16:00"], "temperature_2m": [24.0] }
                    }
                """.trimIndent(),
            ),
        )

        val result = repo.fetchCurrent(45.4, -75.7)

        assertTrue(result.isSuccess)
        // No matching 15:00 slot in the hourly list.
        assertNull(result.getOrNull()!!.tempTrendCelsius)
    }

    @Test
    fun fetchCurrent_trendZero_returnsZero() = runTest {
        // Current 20C, next hour 20C → trend = 0.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                validBody(temp = 20.0, nextHourTemp = 20.0, currentTime = "2026-07-03T14:00", nextHourTime = "2026-07-03T15:00"),
            ),
        )

        val result = repo.fetchCurrent(45.4, -75.7)

        assertTrue(result.isSuccess)
        assertEquals(0.0, result.getOrNull()!!.tempTrendCelsius!!, 0.001)
    }

    // -------- Required-field failures --------

    @Test
    fun fetchCurrent_missingCurrent_returnsFailure() = runTest {
        // No "current" key at all → IllegalStateException.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    { "latitude": 45.4, "longitude": -75.7, "timezone": "UTC",
                      "hourly": { "time": [], "temperature_2m": [] } }
                """.trimIndent(),
            ),
        )

        val result = repo.fetchCurrent(45.4, -75.7)

        assertTrue("expected failure when 'current' is missing, got: $result", result.isFailure)
    }

    @Test
    fun fetchCurrent_missingTemperature_returnsFailure() = runTest {
        // current has weather_code but no temperature_2m.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    { "latitude": 45.4, "longitude": -75.7, "timezone": "UTC",
                      "current": { "time": "2026-07-03T14:00", "weather_code": 1,
                                   "wind_speed_10m": 10.0, "wind_direction_10m": 90, "wind_gusts_10m": 15.0 },
                      "hourly": { "time": [], "temperature_2m": [] } }
                """.trimIndent(),
            ),
        )

        val result = repo.fetchCurrent(45.4, -75.7)

        assertTrue("expected failure when temperature is missing, got: $result", result.isFailure)
    }

    @Test
    fun fetchCurrent_missingWindDirection_returnsFailure() = runTest {
        // wind_direction_10m is null/missing → must fail (the model requires it).
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    { "latitude": 45.4, "longitude": -75.7, "timezone": "UTC",
                      "current": { "time": "2026-07-03T14:00", "temperature_2m": 20.0, "weather_code": 1,
                                   "wind_speed_10m": 10.0, "wind_gusts_10m": 15.0 },
                      "hourly": { "time": [], "temperature_2m": [] } }
                """.trimIndent(),
            ),
        )

        val result = repo.fetchCurrent(45.4, -75.7)

        assertTrue("expected failure when wind_direction is missing, got: $result", result.isFailure)
    }

    // -------- Malformed JSON / HTTP errors --------

    @Test
    fun fetchCurrent_malformedJson_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json at all"))

        val result = repo.fetchCurrent(45.4, -75.7)

        assertTrue("expected failure on malformed JSON, got: $result", result.isFailure)
    }

    @Test
    fun fetchCurrent_emptyBody_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val result = repo.fetchCurrent(45.4, -75.7)

        assertTrue("expected failure on empty body, got: $result", result.isFailure)
    }

    @Test
    fun fetchCurrent_serverError_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = repo.fetchCurrent(45.4, -75.7)

        assertTrue("expected failure on 500, got: $result", result.isFailure)
    }

    @Test
    fun fetchCurrent_404_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        val result = repo.fetchCurrent(45.4, -75.7)

        assertTrue("expected failure on 404, got: $result", result.isFailure)
    }

    // -------- Timezone handling --------

    @Test
    fun fetchCurrent_nonUtcTimezone_trendHandled() = runTest {
        // The repo uses the server-provided timezone for trend hour matching. America/Toronto
        // in July is UTC-4. The current time is 10:00 local, next-hour slot is 11:00 local.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                validBody(
                    temp = 20.0,
                    currentTime = "2026-07-03T10:00",
                    nextHourTime = "2026-07-03T11:00",
                    nextHourTemp = 22.0,
                    timezone = "America/Toronto",
                ),
            ),
        )

        val result = repo.fetchCurrent(45.4, -75.7)

        assertTrue(result.isSuccess)
        assertEquals(2.0, result.getOrNull()!!.tempTrendCelsius!!, 0.001)
    }

    // -------- Failure type sanity --------

    @Test
    fun fetchCurrent_failure_carriesException() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))

        val result = repo.fetchCurrent(45.4, -75.7)

        assertTrue(result.isFailure)
        // The failure must carry a non-null Throwable.
        try {
            result.getOrThrow()
            fail("expected getOrThrow to throw")
        } catch (e: Throwable) {
            // Sanity: an actual exception type, not null.
            assertNotNull("failure must carry a non-null exception", e)
        }
    }
}
