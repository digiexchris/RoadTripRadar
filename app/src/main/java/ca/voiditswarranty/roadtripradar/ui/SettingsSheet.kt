package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

private enum class ActiveSlider {
    GpsIconOpacity,
    RadarOpacity,
    WeatherWidgetSize,
    SpeedSize,
    CompassWidgetSize,
    NavWidgetSize,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    vm: MapViewModel,
    onStyleChange: (MapStyle) -> Unit,
) {
    if (!vm.showSettings && !vm.showResetConfirm) return

    val context = LocalContext.current

    val gpsIconOpacityInteraction = remember { MutableInteractionSource() }
    val isDraggingGpsIconOpacity by gpsIconOpacityInteraction.collectIsDraggedAsState()
    val radarOpacityInteraction = remember { MutableInteractionSource() }
    val isDraggingRadarOpacity by radarOpacityInteraction.collectIsDraggedAsState()
    val weatherWidgetSizeInteraction = remember { MutableInteractionSource() }
    val isDraggingWeatherWidgetSize by weatherWidgetSizeInteraction.collectIsDraggedAsState()
    val speedSizeInteraction = remember { MutableInteractionSource() }
    val isDraggingSpeedSize by speedSizeInteraction.collectIsDraggedAsState()
    val compassWidgetSizeInteraction = remember { MutableInteractionSource() }
    val isDraggingCompassSize by compassWidgetSizeInteraction.collectIsDraggedAsState()
    val navWidgetSizeInteraction = remember { MutableInteractionSource() }
    val isDraggingNavSize by navWidgetSizeInteraction.collectIsDraggedAsState()

    val dragging = isDraggingGpsIconOpacity || isDraggingRadarOpacity ||
        isDraggingWeatherWidgetSize || isDraggingSpeedSize ||
        isDraggingCompassSize || isDraggingNavSize

    val active: ActiveSlider? = when {
        isDraggingGpsIconOpacity -> ActiveSlider.GpsIconOpacity
        isDraggingRadarOpacity -> ActiveSlider.RadarOpacity
        isDraggingWeatherWidgetSize -> ActiveSlider.WeatherWidgetSize
        isDraggingSpeedSize -> ActiveSlider.SpeedSize
        isDraggingCompassSize -> ActiveSlider.CompassWidgetSize
        isDraggingNavSize -> ActiveSlider.NavWidgetSize
        else -> null
    }

    fun showSection(section: ActiveSlider) = !dragging || active == section

    val showWeatherGroup = !dragging ||
        active == ActiveSlider.RadarOpacity ||
        active == ActiveSlider.WeatherWidgetSize
    val showDisplayGroup = !dragging ||
        active == ActiveSlider.GpsIconOpacity ||
        active == ActiveSlider.SpeedSize ||
        active == ActiveSlider.CompassWidgetSize ||
        active == ActiveSlider.NavWidgetSize

    if (vm.showSettings) {
        ModalBottomSheet(
            onDismissRequest = { vm.closeSettings() },
            containerColor = if (dragging) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── Weather ──
                if (showWeatherGroup) {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!dragging) {
                                Text("Weather", style = MaterialTheme.typography.titleMedium)
                            }

                            if (!dragging) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Weather Radar", style = MaterialTheme.typography.titleSmall)
                                    Switch(
                                        checked = vm.weatherActive,
                                        onCheckedChange = { on ->
                                            vm.updateWeatherMode(
                                                if (on) WeatherMode.ON else WeatherMode.OFF
                                            )
                                        },
                                    )
                                }
                            }

                            if (vm.weatherActive && showSection(ActiveSlider.RadarOpacity)) {
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
                                    Slider(
                                        value = vm.radarOpacity,
                                        onValueChange = { vm.updateRadarOpacity(it) },
                                        onValueChangeFinished = { vm.saveRadarOpacity() },
                                        valueRange = 0.1f..1.0f,
                                        interactionSource = radarOpacityInteraction,
                                    )
                                }
                            }

                            if (!dragging) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Local Weather", style = MaterialTheme.typography.titleSmall)
                                    Switch(
                                        checked = vm.windEnabled,
                                        onCheckedChange = { vm.updateWindEnabled(it) },
                                    )
                                }
                            }

                            if (vm.windEnabled && !dragging) {
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
                                            ) {
                                                Text(
                                                    when (unit) {
                                                        WindSpeedUnit.KMH -> "km/h"
                                                        WindSpeedUnit.MPH -> "mph"
                                                        WindSpeedUnit.KNOTS -> "kn"
                                                    },
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
                                            ) {
                                                Text(
                                                    when (unit) {
                                                        TemperatureUnit.CELSIUS -> "°C"
                                                        TemperatureUnit.FAHRENHEIT -> "°F"
                                                        TemperatureUnit.KELVIN -> "K"
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (showSection(ActiveSlider.WeatherWidgetSize)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "Weather widget size",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Slider(
                                        value = vm.weatherWidgetSize,
                                        onValueChange = { vm.updateWeatherWidgetSize(it) },
                                        onValueChangeFinished = { vm.saveWeatherWidgetSize() },
                                        valueRange = 24f..96f,
                                        interactionSource = weatherWidgetSizeInteraction,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Display ──
                if (showDisplayGroup) {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!dragging) {
                                Text("Display", style = MaterialTheme.typography.titleMedium)
                            }

                            if (!dragging) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Units", style = MaterialTheme.typography.titleSmall)
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        SegmentedButton(
                                            selected = vm.useMetric,
                                            onClick = { vm.updateUseMetric(true) },
                                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                                        ) {
                                            Text("Metric")
                                        }
                                        SegmentedButton(
                                            selected = !vm.useMetric,
                                            onClick = { vm.updateUseMetric(false) },
                                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                                        ) {
                                            Text("Imperial")
                                        }
                                    }
                                }
                            }

                            if (vm.useGps && showSection(ActiveSlider.GpsIconOpacity)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Status Icon Opacity", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${(vm.gpsIconOpacity * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    Slider(
                                        value = vm.gpsIconOpacity,
                                        onValueChange = { vm.updateGpsIconOpacity(it) },
                                        onValueChangeFinished = { vm.saveGpsIconOpacity() },
                                        valueRange = 0f..1f,
                                        interactionSource = gpsIconOpacityInteraction,
                                    )
                                }
                            }

                            if (showSection(ActiveSlider.SpeedSize)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "Speedometer size",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Slider(
                                        value = vm.speedSize,
                                        onValueChange = { vm.updateSpeedSize(it) },
                                        onValueChangeFinished = { vm.saveSpeedSize() },
                                        valueRange = 24f..96f,
                                        interactionSource = speedSizeInteraction,
                                    )
                                }
                            }

                            if (showSection(ActiveSlider.CompassWidgetSize)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "Compass size",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Slider(
                                        value = vm.compassWidgetSize,
                                        onValueChange = { vm.updateCompassWidgetSize(it) },
                                        onValueChangeFinished = { vm.saveCompassWidgetSize() },
                                        valueRange = 24f..96f,
                                        interactionSource = compassWidgetSizeInteraction,
                                    )
                                }
                            }

                            if (showSection(ActiveSlider.NavWidgetSize)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Nav widget size", style = MaterialTheme.typography.titleSmall)
                                    Slider(
                                        value = vm.navWidgetSize,
                                        onValueChange = { vm.updateNavWidgetSize(it) },
                                        onValueChangeFinished = { vm.saveNavWidgetSize() },
                                        valueRange = 24f..96f,
                                        interactionSource = navWidgetSizeInteraction,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Misc ──
                if (!dragging) {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Misc", style = MaterialTheme.typography.titleMedium)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Keep Screen On", style = MaterialTheme.typography.titleSmall)
                                Switch(
                                    checked = vm.keepScreenOn,
                                    onCheckedChange = { vm.updateKeepScreenOn(it) },
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { vm.openResetConfirm() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Reset to Defaults")
                            }
                        }
                    }
                }
            }
        }
    }

    if (vm.showResetConfirm) {
        AlertDialog(
            onDismissRequest = { vm.closeResetConfirm() },
            title = { Text("Reset to Defaults") },
            text = { Text("All settings will be reset to their default values.") },
            confirmButton = {
                TextButton(onClick = {
                    val systemDefault = PreferencesRepository.defaultMapStyleFor(context)
                    vm.resetToDefaults(systemDefault, onStyleChange)
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.closeResetConfirm() }) {
                    Text("Cancel")
                }
            },
        )
    }
}
