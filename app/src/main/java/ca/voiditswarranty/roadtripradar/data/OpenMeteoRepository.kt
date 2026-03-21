package ca.voiditswarranty.roadtripradar.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL

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
                    "&hourly=temperature_2m&forecast_hours=2" +
                    "&wind_speed_unit=kmh"
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
                val tempTrend = run {
                    val hourlyTemps = root["hourly"]?.jsonObject
                        ?.get("temperature_2m")?.jsonArray
                    if (hourlyTemps != null && hourlyTemps.size >= 2) {
                        val t0 = hourlyTemps[0].jsonPrimitive.content.toDoubleOrNull()
                        val t1 = hourlyTemps[1].jsonPrimitive.content.toDoubleOrNull()
                        if (t0 != null && t1 != null) t1 - t0 else null
                    } else null
                }
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
