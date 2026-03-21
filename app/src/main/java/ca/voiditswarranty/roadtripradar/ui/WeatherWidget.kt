package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit

private fun wmoLabel(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly Clear"
    2 -> "Partly Cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51 -> "Light Drizzle"
    53 -> "Drizzle"
    55 -> "Heavy Drizzle"
    56, 57 -> "Freezing Drizzle"
    61 -> "Light Rain"
    63 -> "Rain"
    65 -> "Heavy Rain"
    66, 67 -> "Freezing Rain"
    71 -> "Light Snow"
    73 -> "Snow"
    75 -> "Heavy Snow"
    77 -> "Snow Grains"
    80 -> "Light Showers"
    81 -> "Showers"
    82 -> "Heavy Showers"
    85 -> "Snow Showers"
    86 -> "Heavy Snow Showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm + Hail"
    else -> "—"
}

private fun formatTemp(celsius: Double, unit: TemperatureUnit): String {
    val value = when (unit) {
        TemperatureUnit.CELSIUS -> celsius
        TemperatureUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32
        TemperatureUnit.KELVIN -> celsius + 273.15
    }
    val rounded = kotlin.math.round(value).toInt()
    return when (unit) {
        TemperatureUnit.CELSIUS -> "$rounded°C"
        TemperatureUnit.FAHRENHEIT -> "$rounded°F"
        TemperatureUnit.KELVIN -> "$rounded K"
    }
}

private fun formatTrend(deltaCelsius: Double, unit: TemperatureUnit): String {
    val converted = when (unit) {
        TemperatureUnit.CELSIUS -> deltaCelsius
        TemperatureUnit.FAHRENHEIT -> deltaCelsius * 9.0 / 5.0
        TemperatureUnit.KELVIN -> deltaCelsius
    }
    val rounded = converted.toInt()
    val sign = if (rounded > 0) "+" else ""
    val suffix = when (unit) {
        TemperatureUnit.CELSIUS -> "°"
        TemperatureUnit.FAHRENHEIT -> "°"
        TemperatureUnit.KELVIN -> ""
    }
    return "$sign$rounded$suffix/h"
}

private fun formatWind(kmh: Double, unit: WindSpeedUnit): String = when (unit) {
    WindSpeedUnit.KMH -> "${kmh.toInt()} km/h"
    WindSpeedUnit.MPH -> "${(kmh * 0.621371).toInt()} mph"
    WindSpeedUnit.KNOTS -> "${(kmh * 0.539957).toInt()} kn"
}

@Composable
fun WeatherWidget(
    snapshot: OpenMeteoSnapshot?,
    temperatureUnit: TemperatureUnit,
    windSpeedUnit: WindSpeedUnit,
    widgetSize: Float,
    modifier: Modifier = Modifier,
) {
    val labelSize = (widgetSize / 3).coerceAtLeast(10f)
    val conditionsSize = (widgetSize / 4).coerceAtLeast(9f)
    val arrowSize = widgetSize.dp
    val windIconSize = (widgetSize * 0.4f).dp
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (snapshot != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatTemp(snapshot.temperatureCelsius, temperatureUnit),
                    fontSize = labelSize.sp,
                    lineHeight = labelSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (snapshot.tempTrendCelsius != null && snapshot.tempTrendCelsius.toInt() != 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatTrend(snapshot.tempTrendCelsius, temperatureUnit),
                        fontSize = conditionsSize.sp,
                        lineHeight = conditionsSize.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            // Wind direction arrow with wind icon in the center
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(arrowSize),
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Wind direction",
                    modifier = Modifier
                        .size(arrowSize)
                        .rotate(snapshot.windDirectionDeg.toFloat()),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Default.Air,
                    contentDescription = null,
                    modifier = Modifier.size(windIconSize),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = formatWind(snapshot.windSpeedKmh, windSpeedUnit),
                fontSize = conditionsSize.sp,
                lineHeight = conditionsSize.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "↑${formatWind(snapshot.windGustsKmh, windSpeedUnit)}",
                fontSize = conditionsSize.sp,
                lineHeight = conditionsSize.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = "—",
                fontSize = labelSize.sp,
                lineHeight = labelSize.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
