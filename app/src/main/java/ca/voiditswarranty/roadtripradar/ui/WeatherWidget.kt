package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import java.util.Locale

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
    val rounded = kotlin.math.round(converted * 10.0) / 10.0
    val sign = when {
        rounded > 0 -> "+"
        rounded < 0 -> "-"
        else -> ""
    }
    val mag = kotlin.math.abs(rounded)
    val num = String.format(Locale.US, "%.1f", mag)
    val suffix = when (unit) {
        TemperatureUnit.CELSIUS -> "°"
        TemperatureUnit.FAHRENHEIT -> "°"
        TemperatureUnit.KELVIN -> ""
    }
    return "$sign$num$suffix/h"
}

private fun windValue(kmh: Double, unit: WindSpeedUnit): Int = when (unit) {
    WindSpeedUnit.KMH -> kmh.toInt()
    WindSpeedUnit.MPH -> (kmh * 0.621371).toInt()
    WindSpeedUnit.KNOTS -> (kmh * 0.539957).toInt()
}

private fun windUnitLabel(unit: WindSpeedUnit): String = when (unit) {
    WindSpeedUnit.KMH -> "km/h"
    WindSpeedUnit.MPH -> "mph"
    WindSpeedUnit.KNOTS -> "kn"
}

@Composable
fun WeatherWidget(
    snapshot: OpenMeteoSnapshot?,
    temperatureUnit: TemperatureUnit,
    windSpeedUnit: WindSpeedUnit,
    weatherWidgetSize: Float,
    cameraBearing: Double,
    modifier: Modifier = Modifier,
) {
    val iconSize = weatherWidgetSize.dp
    val distFontSize = (weatherWidgetSize * 0.35f).sp
    val nameFontSize = (weatherWidgetSize * 0.25f).sp
    val windIconSize = (weatherWidgetSize * 0.4f).dp
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = formatTemp(snapshot.temperatureCelsius, temperatureUnit),
                    fontSize = distFontSize,
                    lineHeight = distFontSize,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = snapshot.tempTrendCelsius?.let { formatTrend(it, temperatureUnit) } ?: "—/h",
                    fontSize = nameFontSize,
                    lineHeight = nameFontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            val windArrowRotationDeg =
                (snapshot.windDirectionDeg + 180.0 - cameraBearing).toFloat()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(iconSize),
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = stringResource(R.string.cd_wind_direction),
                    modifier = Modifier
                        .size(iconSize)
                        .rotate(windArrowRotationDeg),
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
                text = "${windValue(snapshot.windSpeedKmh, windSpeedUnit)}↑${windValue(snapshot.windGustsKmh, windSpeedUnit)}",
                fontSize = distFontSize,
                lineHeight = distFontSize,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = windUnitLabel(windSpeedUnit),
                fontSize = nameFontSize,
                lineHeight = nameFontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = "—",
                fontSize = distFontSize,
                lineHeight = distFontSize,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
