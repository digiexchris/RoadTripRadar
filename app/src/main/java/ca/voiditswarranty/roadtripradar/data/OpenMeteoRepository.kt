package ca.voiditswarranty.roadtripradar.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class OpenMeteoSnapshot(
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val windSpeedKmh: Double,
    val windDirectionDeg: Int,
    val windGustsKmh: Double,
    val tempTrendCelsius: Double?,
)

class OpenMeteoRepository(
    private val baseUrl: String = "https://api.open-meteo.com/v1",
) {

    suspend fun fetchCurrent(latitude: Double, longitude: Double): Result<OpenMeteoSnapshot> {
        return withContext(Dispatchers.IO) {
            try {
                val q = "latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
                    "&hourly=temperature_2m&past_hours=3&forecast_hours=1" +
                    "&timezone=auto&wind_speed_unit=kmh"
                val jsonStr = URL("$baseUrl/forecast?$q").readText()
                val root = Json.parseToJsonElement(jsonStr).jsonObject
                val current = root["current"]?.jsonObject
                    ?: return@withContext Result.failure(IllegalStateException("missing current"))
                val temp = current["temperature_2m"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: return@withContext Result.failure(IllegalStateException("missing temperature"))
                val code = current["weather_code"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: return@withContext Result.failure(IllegalStateException("missing weather_code"))
                val wind = current["wind_speed_10m"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: return@withContext Result.failure(IllegalStateException("missing wind_speed"))
                val windDir = current["wind_direction_10m"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: return@withContext Result.failure(IllegalStateException("missing wind_direction"))
                val gusts = current["wind_gusts_10m"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: return@withContext Result.failure(IllegalStateException("missing wind_gusts"))
                val currentTimeStr = current["time"]?.jsonPrimitive?.content
                val zoneId = root["timezone"]?.jsonPrimitive?.content?.let { id ->
                    runCatching { ZoneId.of(id) }.getOrNull()
                } ?: ZoneOffset.UTC
                val tempTrend = computeTrendPastHour(
                    currentTempCelsius = temp,
                    currentTimeIso = currentTimeStr,
                    zoneId = zoneId,
                    hourlyRoot = root["hourly"]?.jsonObject,
                )
                Result.success(
                    OpenMeteoSnapshot(
                        temperatureCelsius = temp,
                        weatherCode = code,
                        windSpeedKmh = wind,
                        windDirectionDeg = windDir,
                        windGustsKmh = gusts,
                        tempTrendCelsius = tempTrend,
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

/**
 * Trend = current temperature minus hourly [temperature_2m] for the clock hour immediately before
 * the current hour (local timezone from the API). Uses hourly `time` + `past_hours` per Open-Meteo
 * forecast API (https://open-meteo.com/en/docs).
 */
private fun computeTrendPastHour(
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
    val prevHourStart = nowZ.truncatedTo(ChronoUnit.HOURS).minusHours(1)
    val prevEpoch = prevHourStart.toInstant().epochSecond

    val n = minOf(times.size, temps.size)
    for (i in 0 until n) {
        val tStr = times[i].jsonPrimitive.content
        val z = parseOpenMeteoTimeToZoned(tStr, zoneId) ?: continue
        val slotStart = z.truncatedTo(ChronoUnit.HOURS)
        if (slotStart.toInstant().epochSecond == prevEpoch) {
            val pastTemp = temps[i].jsonPrimitive.content.toDoubleOrNull() ?: return null
            return currentTempCelsius - pastTemp
        }
    }
    return null
}

private fun parseOpenMeteoTimeToZoned(iso: String, zoneId: ZoneId): ZonedDateTime? {
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
