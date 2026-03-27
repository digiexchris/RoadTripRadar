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
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

@Composable
fun WeatherDrawerSettingsContent(
    vm: MapViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (vm.weatherActive) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Weather Radar Opacity", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${(vm.radarOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                GloveFriendlySlider(
                    value = vm.radarOpacity,
                    onValueChange = { vm.updateRadarOpacity(it) },
                    onValueChangeFinished = { vm.saveRadarOpacity() },
                    valueRange = 0.1f..1.0f,
                )
            }
        }

        if (vm.windEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Wind Speed Units", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    WindSpeedUnit.entries.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = vm.windSpeedUnit == unit,
                            onClick = { vm.updateWindSpeedUnit(unit) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index,
                                WindSpeedUnit.entries.size,
                            ),
                            modifier = Modifier.height(DrawerControlSizing.segmentedButtonHeight),
                        ) {
                            Text(
                                when (unit) {
                                    WindSpeedUnit.KMH -> "km/h"
                                    WindSpeedUnit.MPH -> "mph"
                                    WindSpeedUnit.KNOTS -> "kn"
                                },
                                style = selectionLabelTextStyle(),
                            )
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Temperature Units", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TemperatureUnit.entries.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = vm.temperatureUnit == unit,
                            onClick = { vm.updateTemperatureUnit(unit) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index,
                                TemperatureUnit.entries.size,
                            ),
                            modifier = Modifier.height(DrawerControlSizing.segmentedButtonHeight),
                        ) {
                            Text(
                                when (unit) {
                                    TemperatureUnit.CELSIUS -> "°C"
                                    TemperatureUnit.FAHRENHEIT -> "°F"
                                    TemperatureUnit.KELVIN -> "K"
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
                "Weather widget size",
                style = MaterialTheme.typography.titleSmall,
            )
            GloveFriendlySlider(
                value = vm.weatherWidgetSize,
                onValueChange = { vm.updateWeatherWidgetSize(it) },
                onValueChangeFinished = { vm.saveWeatherWidgetSize() },
                valueRange = 24f..96f,
            )
        }
    }
}
