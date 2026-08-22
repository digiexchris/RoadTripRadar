package ca.voiditswarranty.roadtripradar.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Tests for the extracted Open-Meteo helpers — `computeTrendNextHour` and
 * `parseOpenMeteoTimeToZoned`. Both were originally `private` file-level functions on
 * `OpenMeteoRepository.kt`; they're promoted to `internal` in the new
 * `OpenMeteoHelpers.kt` so this test can drive them directly without network IO.
 *
 * Uses plain JUnit 4. The Open-Meteo HTTP integration is exercised separately in
 * `OpenMeteoRepositoryTest` (Phase 2) with a MockWebServer.
 */
class OpenMeteoHelpersTest {

    private fun hourlyRoot(json: String): JsonObject =
        Json.parseToJsonElement(json).let { it as JsonObject }

    // -------- parseOpenMeteoTimeToZoned --------

    @Test
    fun parseOpenMeteoTimeToZoned_offsetIso_returnsZonedInTargetZone() {
        // `2026-07-03T14:00:00Z` is 14:00 UTC. In America/Toronto (UTC-4 in July), it's 10:00.
        val z = parseOpenMeteoTimeToZoned("2026-07-03T14:00:00Z", ZoneId.of("America/Toronto"))
        assertNotNull(z)
        assertEquals(10, z!!.hour)
        assertEquals(ZoneId.of("America/Toronto"), z.zone)
    }

    @Test
    fun parseOpenMeteoTimeToZoned_localIso_treatedAsLocalInZone() {
        // `2026-07-03T14:00:00` (no offset) is parsed as LocalDateTime, then placed in the
        // target zone — so 14:00 stays 14:00 in America/Toronto.
        val z = parseOpenMeteoTimeToZoned("2026-07-03T14:00:00", ZoneId.of("America/Toronto"))
        assertNotNull(z)
        assertEquals(14, z!!.hour)
        assertEquals(ZoneId.of("America/Toronto"), z.zone)
    }

    @Test
    fun parseOpenMeteoTimeToZoned_invalidString_returnsNull() {
        assertNull(parseOpenMeteoTimeToZoned("not a date", ZoneOffset.UTC))
        assertNull(parseOpenMeteoTimeToZoned("", ZoneOffset.UTC))
    }

    @Test
    fun parseOpenMeteoTimeToZoned_instantWithZ_succeeds() {
        // The third fallback (Instant.parse) handles `2026-07-03T14:00:00Z`.
        val z = parseOpenMeteoTimeToZoned("2026-07-03T14:00:00Z", ZoneOffset.UTC)
        assertNotNull(z)
        assertEquals(14, z!!.hour)
    }

    // -------- computeTrendNextHour --------

    @Test
    fun computeTrendNextHour_noHourly_returnsNull() {
        val result = computeTrendNextHour(
            currentTempCelsius = 20.0,
            currentTimeIso = "2026-07-03T14:00:00",
            zoneId = ZoneId.of("UTC"),
            hourlyRoot = null,
        )
        assertNull(result)
    }

    @Test
    fun computeTrendNextHour_noCurrentTime_returnsNull() {
        val root = hourlyRoot("""{ "time": ["2026-07-03T15:00:00"], "temperature_2m": [22.0] }""")
        val result = computeTrendNextHour(
            currentTempCelsius = 20.0,
            currentTimeIso = null,
            zoneId = ZoneId.of("UTC"),
            hourlyRoot = root,
        )
        assertNull(result)
    }

    @Test
    fun computeTrendNextHour_zeroSlope_returnsZero() {
        // Current 14:00 is 20C, the next-hour slot (15:00) is also 20C → trend = 0.
        val root = hourlyRoot("""{ "time": ["2026-07-03T15:00:00"], "temperature_2m": [20.0] }""")
        val result = computeTrendNextHour(
            currentTempCelsius = 20.0,
            currentTimeIso = "2026-07-03T14:00:00",
            zoneId = ZoneId.of("UTC"),
            hourlyRoot = root,
        )
        assertNotNull(result)
        assertEquals(0.0, result!!, 0.001)
    }

    @Test
    fun computeTrendNextHour_warmingTrend_returnsPositive() {
        // 14:00 = 20C, 15:00 = 22C → trend = +2.
        val root = hourlyRoot("""
            {
              "time": ["2026-07-03T13:00:00", "2026-07-03T15:00:00", "2026-07-03T16:00:00"],
              "temperature_2m": [18.0, 22.0, 24.0]
            }
        """.trimIndent())
        val result = computeTrendNextHour(
            currentTempCelsius = 20.0,
            currentTimeIso = "2026-07-03T14:00:00",
            zoneId = ZoneId.of("UTC"),
            hourlyRoot = root,
        )
        assertNotNull(result)
        assertEquals(2.0, result!!, 0.001)
    }

    @Test
    fun computeTrendNextHour_coolingTrend_returnsNegative() {
        // 14:00 = 20C, 15:00 = 18C → trend = -2.
        val root = hourlyRoot("""{ "time": ["2026-07-03T15:00:00"], "temperature_2m": [18.0] }""")
        val result = computeTrendNextHour(
            currentTempCelsius = 20.0,
            currentTimeIso = "2026-07-03T14:00:00",
            zoneId = ZoneId.of("UTC"),
            hourlyRoot = root,
        )
        assertNotNull(result)
        assertEquals(-2.0, result!!, 0.001)
    }

    @Test
    fun computeTrendNextHour_noMatchingSlot_returnsNull() {
        // Hourly list has 13:00 and 16:00 but not 15:00 (the next hour after the 14:00
        // current). No match → null.
        val root = hourlyRoot("""
            {
              "time": ["2026-07-03T13:00:00", "2026-07-03T16:00:00"],
              "temperature_2m": [18.0, 24.0]
            }
        """.trimIndent())
        val result = computeTrendNextHour(
            currentTempCelsius = 20.0,
            currentTimeIso = "2026-07-03T14:00:00",
            zoneId = ZoneId.of("UTC"),
            hourlyRoot = root,
        )
        assertNull(result)
    }

    @Test
    fun computeTrendNextHour_handlesNonUtcTimezone() {
        // Current 14:00 local in America/Toronto. The hourly list has 15:00 local. Trend
        // = 22 - 20 = 2.
        val root = hourlyRoot("""{ "time": ["2026-07-03T15:00:00"], "temperature_2m": [22.0] }""")
        val result = computeTrendNextHour(
            currentTempCelsius = 20.0,
            currentTimeIso = "2026-07-03T14:00:00",
            zoneId = ZoneId.of("America/Toronto"),
            hourlyRoot = root,
        )
        assertNotNull(result)
        assertEquals(2.0, result!!, 0.001)
    }
}
