package ca.voiditswarranty.roadtripradar.ui

import android.app.Activity
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    vm: MapViewModel,
    mapStyle: MapStyle,
    onStyleChange: (MapStyle) -> Unit,
) {
    val context = LocalContext.current
    val systemIsDark = isSystemInDarkTheme()

    if (vm.showSettings) {
        ModalBottomSheet(onDismissRequest = { vm.closeSettings() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Help & Information
                OutlinedButton(
                    onClick = {
                        vm.closeSettings()
                        vm.openHelp()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Help & Information")
                }

                // Quit
                OutlinedButton(
                    onClick = {
                        (context as? Activity)?.finishAffinity()
                        System.exit(0)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Quit")
                }

                // Map Style
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Map Style", style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        MapStyle.entries.forEachIndexed { index, style ->
                            SegmentedButton(
                                selected = mapStyle == style,
                                onClick = { onStyleChange(style) },
                                shape = SegmentedButtonDefaults.itemShape(index, MapStyle.entries.size),
                            ) {
                                Text(style.displayName)
                            }
                        }
                    }
                }

                // Weather Radar
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
                                if (on) WeatherMode.ON
                                else WeatherMode.OFF
                            )
                        },
                    )
                }

                // Radar Opacity (visible when weather is on)
                if (vm.weatherActive) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Radar Opacity", style = MaterialTheme.typography.titleSmall)
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
                        )
                    }
                }

                // Units
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

                // Keep Screen On
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

                // Speed Size
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Speed Size", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = vm.speedSize,
                        onValueChange = { vm.updateSpeedSize(it) },
                        onValueChangeFinished = { vm.saveSpeedSize() },
                        valueRange = 24f..96f,
                    )
                }

                // Nav Widget Size
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Nav Widget Size", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = vm.navWidgetSize,
                        onValueChange = { vm.updateNavWidgetSize(it) },
                        onValueChangeFinished = { vm.saveNavWidgetSize() },
                        valueRange = 24f..96f,
                    )
                }

                // Reset to Defaults
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.openResetConfirm() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset to Defaults")
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
                    val systemDefault = if (systemIsDark) MapStyle.LIBERTY_DARK else MapStyle.LIBERTY
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
