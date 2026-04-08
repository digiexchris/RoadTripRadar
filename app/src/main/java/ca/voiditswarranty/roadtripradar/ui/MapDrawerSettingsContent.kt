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
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

@Composable
fun MapDrawerSettingsContent(
    vm: MapViewModel,
    mapStyle: MapStyle,
    onStyleChange: (MapStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Speed/Distance Units", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = vm.useMetric,
                    onClick = { vm.updateUseMetric(true) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    modifier = Modifier.height(DrawerControlSizing.segmentedButtonHeight),
                ) {
                    Text("Metric", style = selectionLabelTextStyle())
                }
                SegmentedButton(
                    selected = !vm.useMetric,
                    onClick = { vm.updateUseMetric(false) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.height(DrawerControlSizing.segmentedButtonHeight),
                ) {
                    Text("Imperial", style = selectionLabelTextStyle())
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Map Center Offset (Portrait)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${(vm.mapCenterOffsetPortraitFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            GloveFriendlySlider(
                value = vm.mapCenterOffsetPortraitFraction,
                onValueChange = { vm.updateMapCenterOffsetPortraitFraction(it) },
                onValueChangeFinished = { vm.saveMapCenterOffsetPortraitFraction() },
                valueRange = 0f..1f,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Map Center Offset (Landscape)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${(vm.mapCenterOffsetLandscapeFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            GloveFriendlySlider(
                value = vm.mapCenterOffsetLandscapeFraction,
                onValueChange = { vm.updateMapCenterOffsetLandscapeFraction(it) },
                onValueChangeFinished = { vm.saveMapCenterOffsetLandscapeFraction() },
                valueRange = 0f..1f,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Places Icon Opacity", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${(vm.poiIconOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            GloveFriendlySlider(
                value = vm.poiIconOpacity,
                onValueChange = { vm.updatePoiIconOpacity(it) },
                onValueChangeFinished = { vm.savePoiIconOpacity() },
                valueRange = 0f..1f,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Map Style", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                MapStyle.entries.forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = mapStyle == style,
                        onClick = { onStyleChange(style) },
                        shape = SegmentedButtonDefaults.itemShape(index, MapStyle.entries.size),
                        modifier = Modifier.height(DrawerControlSizing.segmentedButtonHeight),
                    ) {
                        Text(style.displayName, style = selectionLabelTextStyle())
                    }
                }
            }
        }
    }
}
