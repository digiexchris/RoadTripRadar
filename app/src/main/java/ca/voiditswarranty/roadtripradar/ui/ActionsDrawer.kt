package ca.voiditswarranty.roadtripradar.ui

import android.app.Activity
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

private data class DrawerAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ActionsDrawer(
    vm: MapViewModel,
) {
    if (!vm.showActionsDrawer) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val weatherDependentEnabled = vm.weatherActive
    var showQuitConfirm by remember { mutableStateOf(false) }

    val actions = listOf(
        DrawerAction(
            label = "Close",
            icon = Icons.Default.KeyboardArrowDown,
            onClick = { vm.closeActionsDrawer() },
        ),
        DrawerAction(
            label = "Quit",
            icon = Icons.Default.PowerSettingsNew,
            onClick = { showQuitConfirm = true },
        ),
        DrawerAction(
            label = if (vm.poiPosition != null) "Clear Target Location" else "Location Search",
            icon = if (vm.poiPosition != null) Icons.Default.Close else Icons.Default.Search,
            onClick = {
                if (vm.poiPosition != null) vm.clearPoi() else vm.openPoiSearch()
                vm.closeActionsDrawer()
            },
        ),
        DrawerAction(
            label = if (vm.isNorthUp) "Track Bearing" else "Track North",
            icon = Icons.Default.Navigation,
            onClick = {
                vm.isNorthUp = !vm.isNorthUp
                vm.closeActionsDrawer()
            },
        ),
        DrawerAction(
            label = if (vm.isWeatherPlaying) "Pause Weather" else "Play Weather",
            icon = if (vm.isWeatherPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            enabled = weatherDependentEnabled,
            onClick = {
                vm.toggleWeatherPlaying()
                vm.closeActionsDrawer()
            },
        ),
        DrawerAction(
            label = if (vm.weatherActive) "Weather On" else "Weather Off",
            icon = Icons.Default.SatelliteAlt,
            onClick = {
                vm.updateWeatherMode(if (vm.weatherActive) WeatherMode.OFF else WeatherMode.ON)
                vm.closeActionsDrawer()
            },
        ),
        DrawerAction(
            label = if (vm.showLegend) "Hide Legend" else "Show Legend",
            icon = if (vm.showLegend) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            enabled = weatherDependentEnabled,
            onClick = {
                vm.updateShowLegend(!vm.showLegend)
                vm.closeActionsDrawer()
            },
        ),
        DrawerAction(
            label = if (vm.showTimeline) "Hide Timeline" else "Show Timeline",
            icon = if (vm.showTimeline) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            enabled = weatherDependentEnabled,
            onClick = {
                vm.updateShowTimeline(!vm.showTimeline)
                vm.closeActionsDrawer()
            },
        ),
        DrawerAction(
            label = "Settings",
            icon = Icons.Default.Settings,
            onClick = {
                vm.openSettings()
                vm.closeActionsDrawer()
            },
        ),
        DrawerAction(
            label = "Help",
            icon = Icons.AutoMirrored.Filled.Help,
            onClick = {
                vm.openHelp()
                vm.closeActionsDrawer()
            },
        ),
    )

    ModalBottomSheet(
        onDismissRequest = { vm.closeActionsDrawer() },
        sheetState = sheetState,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(actions) { action ->
                DrawerActionFab(
                    label = action.label,
                    icon = action.icon,
                    enabled = action.enabled,
                    onClick = action.onClick,
                )
            }
        }
    }

    if (showQuitConfirm) {
        val buttonShape = RoundedCornerShape(20.dp)
        AlertDialog(
            onDismissRequest = { showQuitConfirm = false },
            title = { Text("Quit") },
            text = { Text("Are you sure you want to quit?") },
            confirmButton = {
                LargeFloatingActionButton(
                    onClick = {
                        (context as? Activity)?.finishAffinity()
                        System.exit(0)
                    },
                    shape = buttonShape,
                    containerColor = Color(0xFFCC3333),
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth(0.45f),
                ) {
                    Text("Quit", style = MaterialTheme.typography.titleMedium)
                }
            },
            dismissButton = {
                LargeFloatingActionButton(
                    onClick = { showQuitConfirm = false },
                    shape = buttonShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(0.45f),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.titleMedium)
                }
            },
        )
    }
}

@Composable
private fun DrawerActionFab(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

    LargeFloatingActionButton(
        onClick = { if (enabled) onClick() },
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
