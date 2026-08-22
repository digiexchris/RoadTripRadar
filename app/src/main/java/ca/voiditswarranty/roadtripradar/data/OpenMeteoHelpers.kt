package ca.voiditswarranty.roadtripradar.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Trend = hourly [temperature_2m] for the clock hour immediately after the current hour minus the
 * current temperature (local timezone from the API). Positive = forecast to warm over the next
 * hour, negative = forecast to cool. Uses hourly `time` + `forecast_hours` per Open-Meteo forecast
 * API (https://open-meteo.com/en/docs).
 *
 * Extracted from `OpenMeteoRepository` so it can be unit-tested without network IO. `null` is
 * returned when any required field is missing or unparseable.
 */
internal fun computeTrendNextHour(
    currentTempCelsius: Double,
    currentTimeIso: String?,
    zoneId: ZoneId,
    hourlyRoot: JsonObject?,
): Double? {
    if (currentTimeIso == null || hourlyRoot == null) return null
    val times = hourlyRoot["time"]?.jsonArray ?: return null
    val temps = hourlyRoot["temperature_2m"]?.jsonArray ?: return null
    if (times.isEmpty()) return null

    val nowZ = parseOpenMeteoTimeToZoned(currentTimeIso, zoneId) ?: return null
    val nextHourStart = nowZ.truncatedTo(ChronoUnit.HOURS).plusHours(1)
    val nextEpoch = nextHourStart.toInstant().epochSecond

    val n = minOf(times.size, temps.size)
    for (i in 0 until n) {
        val tStr = times[i].jsonPrimitive.content
        val z = parseOpenMeteoTimeToZoned(tStr, zoneId) ?: continue
        val slotStart = z.truncatedTo(ChronoUnit.HOURS)
        if (slotStart.toInstant().epochSecond == nextEpoch) {
            val nextTemp = temps[i].jsonPrimitive.content.toDoubleOrNull() ?: return null
            return nextTemp - currentTempCelsius
        }
    }
    return null
}

/**
 * Parses an Open-Meteo time string to a [ZonedDateTime] in [zoneId]. Tries three formats in
 * order: `OffsetDateTime` (e.g. `2026-07-03T14:00:00Z`), `LocalDateTime` (e.g.
 * `2026-07-03T14:00:00`), and `Instant` (e.g. `2026-07-03T14:00:00Z`). Returns null when none
 * of the three parse successfully.
 */
internal fun parseOpenMeteoTimeToZoned(iso: String, zoneId: ZoneId): ZonedDateTime? {
    return try {
        OffsetDateTime.parse(iso).atZoneSameInstant(zoneId)
    } catch (_: Exception) {
        try {
            val local = LocalDateTime.parse(iso.take(19))
            local.atZone(zoneId)
        } catch (_: Exception) {
            try {
                Instant.parse(iso).atZone(zoneId)
            } catch (_: Exception) {
                null
            }
        }
    }
}
