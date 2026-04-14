package ca.voiditswarranty.roadtripradar.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import ca.voiditswarranty.roadtripradar.R
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
        Text(stringResource(R.string.settings_display), style = MaterialTheme.typography.titleMedium)

        if (vm.useGps) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.settings_status_icon_opacity), style = MaterialTheme.typography.titleSmall)
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
            Text(stringResource(R.string.settings_speedometer_size), style = MaterialTheme.typography.titleSmall)
            GloveFriendlySlider(
                value = vm.speedSize,
                onValueChange = { vm.updateSpeedSize(it) },
                onValueChangeFinished = { vm.saveSpeedSize() },
                valueRange = 24f..96f,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settings_compass_size), style = MaterialTheme.typography.titleSmall)
            GloveFriendlySlider(
                value = vm.compassWidgetSize,
                onValueChange = { vm.updateCompassWidgetSize(it) },
                onValueChangeFinished = { vm.saveCompassWidgetSize() },
                valueRange = 24f..96f,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settings_nav_widget_size), style = MaterialTheme.typography.titleSmall)
            GloveFriendlySlider(
                value = vm.navWidgetSize,
                onValueChange = { vm.updateNavWidgetSize(it) },
                onValueChangeFinished = { vm.saveNavWidgetSize() },
                valueRange = 24f..96f,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text(stringResource(R.string.settings_misc), style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_keep_screen_on), style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = vm.keepScreenOn,
                onCheckedChange = { vm.updateKeepScreenOn(it) },
            )
        }

        LanguagePicker()
    }
}

private data class LanguageOption(val tag: String?, val label: String)

@Composable
private fun LanguagePicker() {
    val options = listOf(
        LanguageOption(null, stringResource(R.string.language_system_default)),
        LanguageOption("en", "English"),
        LanguageOption("fr", "Français"),
        LanguageOption("de", "Deutsch"),
        LanguageOption("es", "Español"),
    )

    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocales.isEmpty) null else currentLocales.toLanguageTags()
    val currentLabel = options.firstOrNull { it.tag == currentTag }?.label
        ?: options.first().label

    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { showDialog = true }) {
            Text(currentLabel)
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    options.forEach { option ->
                        TextButton(
                            onClick = {
                                val locales = if (option.tag == null) {
                                    LocaleListCompat.getEmptyLocaleList()
                                } else {
                                    LocaleListCompat.forLanguageTags(option.tag)
                                }
                                AppCompatDelegate.setApplicationLocales(locales)
                                showDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                option.label,
                                color = if (option.tag == currentTag) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
