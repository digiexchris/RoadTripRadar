package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.ui.tutorial.TutorialAnchors
import ca.voiditswarranty.roadtripradar.ui.tutorial.tutorialAnchor
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

@Composable
fun ThemeChooserRow(
    mapStyle: MapStyle,
    vm: MapViewModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .tutorialAnchor(TutorialAnchors.MAP_SETTING_THEME),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            stringResource(R.string.theme_current, stringResource(mapStyle.displayNameRes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { vm.openThemeSelector() }) {
            Icon(Icons.Default.Palette, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_choose_theme))
        }
    }
}

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
        Column(
            modifier = Modifier.tutorialAnchor(TutorialAnchors.MAP_SETTING_UNITS),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.settings_speed_distance_units), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = vm.useMetric,
                    onClick = { vm.updateUseMetric(true) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    modifier = Modifier.height(DrawerControlSizing.segmentedButtonHeight),
                ) {
                    Text(stringResource(R.string.settings_metric), style = selectionLabelTextStyle())
                }
                SegmentedButton(
                    selected = !vm.useMetric,
                    onClick = { vm.updateUseMetric(false) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.height(DrawerControlSizing.segmentedButtonHeight),
                ) {
                    Text(stringResource(R.string.settings_imperial), style = selectionLabelTextStyle())
                }
            }
        }

        Column(
            modifier = Modifier.tutorialAnchor(TutorialAnchors.MAP_SETTING_CENTER_OFFSET_PORTRAIT),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.settings_map_center_offset_portrait), style = MaterialTheme.typography.titleSmall)
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

        Column(
            modifier = Modifier.tutorialAnchor(TutorialAnchors.MAP_SETTING_CENTER_OFFSET_LANDSCAPE),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.settings_map_center_offset_landscape), style = MaterialTheme.typography.titleSmall)
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

        Column(
            modifier = Modifier.tutorialAnchor(TutorialAnchors.MAP_SETTING_CENTER_OFFSET_CAR),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.settings_map_center_offset_car), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${(vm.mapCenterOffsetCarFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            GloveFriendlySlider(
                value = vm.mapCenterOffsetCarFraction,
                onValueChange = { vm.updateMapCenterOffsetCarFraction(it) },
                onValueChangeFinished = { vm.saveMapCenterOffsetCarFraction() },
                valueRange = 0f..1f,
            )
        }

        Column(
            modifier = Modifier.tutorialAnchor(TutorialAnchors.MAP_SETTING_POI_OPACITY),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.settings_places_icon_opacity), style = MaterialTheme.typography.titleSmall)
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tutorialAnchor(TutorialAnchors.MAP_SETTING_AUTO_ADVANCE_TOGGLE),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_auto_advance_toggle),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Switch(
                checked = vm.autoAdvanceEnabled,
                onCheckedChange = { vm.updateAutoAdvanceEnabled(it) },
            )
        }

        Column(
            modifier = Modifier.tutorialAnchor(TutorialAnchors.MAP_SETTING_AUTO_ADVANCE_THRESHOLD),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.settings_auto_advance_threshold),
                    style = MaterialTheme.typography.titleSmall,
                )
                val meters = vm.autoAdvanceThresholdMeters
                val display = if (vm.useMetric) {
                    stringResource(R.string.threshold_meters_format, meters)
                } else {
                    stringResource(R.string.threshold_yards_format, (meters * 1.0936).toInt())
                }
                Text(display, style = MaterialTheme.typography.bodyMedium)
            }
            GloveFriendlySlider(
                value = vm.autoAdvanceThresholdMeters.toFloat(),
                onValueChange = { vm.updateAutoAdvanceThreshold(it.toInt()) },
                onValueChangeFinished = { vm.saveAutoAdvanceThreshold() },
                valueRange = 25f..500f,
                enabled = vm.autoAdvanceEnabled,
            )
        }
    }
}
