package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit

@Composable
fun WeatherDrawerSettingsContent(
    weatherActive: Boolean,
    radarOpacity: Float,
    onRadarOpacityChange: (Float) -> Unit,
    onRadarOpacityCommit: () -> Unit,
    windEnabled: Boolean,
    windSpeedUnit: WindSpeedUnit,
    onWindSpeedUnitChange: (WindSpeedUnit) -> Unit,
    temperatureUnit: TemperatureUnit,
    onTemperatureUnitChange: (TemperatureUnit) -> Unit,
    weatherWidgetSize: Float,
    onWeatherWidgetSizeChange: (Float) -> Unit,
    onWeatherWidgetSizeCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (weatherActive) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.settings_weather_radar_opacity), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${(radarOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                GloveFriendlySlider(
                    value = radarOpacity,
                    onValueChange = onRadarOpacityChange,
                    onValueChangeFinished = onRadarOpacityCommit,
                    valueRange = 0.1f..1.0f,
                )
            }
        }

        if (windEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_wind_speed_units), style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    WindSpeedUnit.entries.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = windSpeedUnit == unit,
                            onClick = { onWindSpeedUnitChange(unit) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index,
                                WindSpeedUnit.entries.size,
                            ),
                            modifier = Modifier.height(DrawerControlSizing.segmentedButtonHeight),
                        ) {
                            Text(
                                when (unit) {
                                    WindSpeedUnit.KMH -> stringResource(R.string.unit_kmh)
                                    WindSpeedUnit.MPH -> stringResource(R.string.unit_mph)
                                    WindSpeedUnit.KNOTS -> stringResource(R.string.unit_knots)
                                },
                                style = selectionLabelTextStyle(),
                            )
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_temperature_units), style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TemperatureUnit.entries.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = temperatureUnit == unit,
                            onClick = { onTemperatureUnitChange(unit) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index,
                                TemperatureUnit.entries.size,
                            ),
                            modifier = Modifier.height(DrawerControlSizing.segmentedButtonHeight),
                        ) {
                            Text(
                                when (unit) {
                                    TemperatureUnit.CELSIUS -> stringResource(R.string.unit_celsius)
                                    TemperatureUnit.FAHRENHEIT -> stringResource(R.string.unit_fahrenheit)
                                    TemperatureUnit.KELVIN -> stringResource(R.string.unit_kelvin)
                                },
                                style = selectionLabelTextStyle(),
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.settings_weather_widget_size),
                style = MaterialTheme.typography.titleSmall,
            )
            GloveFriendlySlider(
                value = weatherWidgetSize,
                onValueChange = onWeatherWidgetSizeChange,
                onValueChangeFinished = onWeatherWidgetSizeCommit,
                valueRange = 24f..96f,
            )
        }
    }
}
