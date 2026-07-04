package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import androidx.compose.ui.platform.LocalContext

// formatTemp / formatTrend / windValue / windUnitLabel live in WeatherFormat.kt
// (shared with the Android Auto car screens).

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
    val windIconSize = (weatherWidgetSize * 0.27f).dp
    val windBadgeSize = (weatherWidgetSize * 0.34f).dp
    val context = LocalContext.current
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
                windArrowRotationDeg(snapshot.windDirectionDeg, cameraBearing)
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(windBadgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = null,
                        modifier = Modifier.size(windIconSize),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                text = "${windValue(snapshot.windSpeedKmh, windSpeedUnit)}↑${windValue(snapshot.windGustsKmh, windSpeedUnit)}",
                fontSize = distFontSize,
                lineHeight = distFontSize,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = windUnitLabel(context, windSpeedUnit),
                fontSize = nameFontSize,
                lineHeight = nameFontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = stringResource(R.string.cd_weather_unavailable),
                    modifier = Modifier.size(windIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.weather_unavailable),
                    fontSize = nameFontSize,
                    lineHeight = nameFontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
