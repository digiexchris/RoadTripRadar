package ca.voiditswarranty.roadtripradar.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
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

private enum class ActionsDrawerPage { Main, Places, Weather }

@Composable
fun ActionsDrawer(
    vm: MapViewModel,
) {
    val context = LocalContext.current
    var showQuitConfirm by remember { mutableStateOf(false) }
    var drawerPage by remember { mutableStateOf(ActionsDrawerPage.Main) }

    LaunchedEffect(vm.showActionsDrawer) {
        if (vm.showActionsDrawer) {
            drawerPage = ActionsDrawerPage.Main
        } else {
            drawerPage = ActionsDrawerPage.Main
            showQuitConfirm = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = vm.showActionsDrawer,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { vm.closeActionsDrawer() }
            )
        }

        AnimatedVisibility(
            visible = vm.showActionsDrawer,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val weatherDependentEnabled = vm.weatherActive

            val closeToMap = DrawerAction(
                label = "Close",
                icon = Icons.Default.KeyboardArrowDown,
                onClick = { vm.closeActionsDrawer() },
            )

            val actions = when (drawerPage) {
                ActionsDrawerPage.Main -> listOf(
                    closeToMap,
                    DrawerAction(
                        label = "Quit",
                        icon = Icons.Default.PowerSettingsNew,
                        onClick = { showQuitConfirm = true },
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
                        label = "Places",
                        icon = Icons.Default.Explore,
                        onClick = { drawerPage = ActionsDrawerPage.Places },
                    ),
                    DrawerAction(
                        label = "Weather",
                        icon = Icons.Default.Cloud,
                        onClick = { drawerPage = ActionsDrawerPage.Weather },
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
                ActionsDrawerPage.Places -> listOf(
                    closeToMap,
                    DrawerAction(
                        label = "Nearby Places",
                        icon = Icons.Default.Place,
                        onClick = {
                            vm.openPoiCategoryPicker()
                            vm.closeActionsDrawer()
                        },
                    ),
                    DrawerAction(
                        label = "Clear Places",
                        icon = Icons.Default.Delete,
                        enabled = vm.poiLoadBounds != null,
                        onClick = {
                            vm.clearNearbyPois()
                            vm.closeActionsDrawer()
                        },
                    ),
                    DrawerAction(
                        label = "Location Search",
                        icon = Icons.Default.Search,
                        onClick = {
                            vm.openPoiSearch()
                            vm.closeActionsDrawer()
                        },
                    ),
                    DrawerAction(
                        label = "Clear Target",
                        icon = Icons.Default.Close,
                        enabled = vm.poiPosition != null,
                        onClick = {
                            vm.clearPoi()
                            vm.closeActionsDrawer()
                        },
                    ),
                )
                ActionsDrawerPage.Weather -> listOf(
                    closeToMap,
                    DrawerAction(
                        label = if (vm.isWeatherPlaying) "Pause Weather Radar" else "Play Weather Radar",
                        icon = if (vm.isWeatherPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        enabled = weatherDependentEnabled,
                        onClick = {
                            vm.toggleWeatherPlaying()
                            vm.closeActionsDrawer()
                        },
                    ),
                    DrawerAction(
                        label = if (vm.weatherActive) "Weather Radar On" else "Weather Radar Off",
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
                )
            }

            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .navigationBarsPadding(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    if (drawerPage != ActionsDrawerPage.Main) {
                        Text(
                            text = when (drawerPage) {
                                ActionsDrawerPage.Places -> "Places"
                                ActionsDrawerPage.Weather -> "Weather"
                                ActionsDrawerPage.Main -> ""
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
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
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
