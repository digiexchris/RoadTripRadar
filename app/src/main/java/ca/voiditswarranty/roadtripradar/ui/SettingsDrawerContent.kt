package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

@Composable
fun SettingsDrawerContent(
    vm: MapViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Display", style = MaterialTheme.typography.titleMedium)

        if (vm.useGps) {
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
                GloveFriendlySlider(
                    value = vm.gpsIconOpacity,
                    onValueChange = { vm.updateGpsIconOpacity(it) },
                    onValueChangeFinished = { vm.saveGpsIconOpacity() },
                    valueRange = 0f..1f,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Speedometer size", style = MaterialTheme.typography.titleSmall)
            GloveFriendlySlider(
                value = vm.speedSize,
                onValueChange = { vm.updateSpeedSize(it) },
                onValueChangeFinished = { vm.saveSpeedSize() },
                valueRange = 24f..96f,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Compass size", style = MaterialTheme.typography.titleSmall)
            GloveFriendlySlider(
                value = vm.compassWidgetSize,
                onValueChange = { vm.updateCompassWidgetSize(it) },
                onValueChangeFinished = { vm.saveCompassWidgetSize() },
                valueRange = 24f..96f,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Nav widget size", style = MaterialTheme.typography.titleSmall)
            GloveFriendlySlider(
                value = vm.navWidgetSize,
                onValueChange = { vm.updateNavWidgetSize(it) },
                onValueChangeFinished = { vm.saveNavWidgetSize() },
                valueRange = 24f..96f,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
    }
}
