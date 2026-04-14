package ca.voiditswarranty.roadtripradar.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.BuildConfig
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

private data class DrawerAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

private enum class ActionsDrawerPage { Main, Map, Weather, System, Help }

@Composable
fun ActionsDrawer(
    vm: MapViewModel,
    mapStyle: MapStyle,
    onStyleChange: (MapStyle) -> Unit,
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

    var isSliderDragging by remember { mutableStateOf(false) }
    var sliderDisplayLabel by remember { mutableStateOf("") }
    val drawerContentAlpha by animateFloatAsState(
        targetValue = if (isSliderDragging) 0.12f else 1f,
        label = "drawerFade",
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isSliderDragging) 0f else 0.5f,
        label = "scrimFade",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = vm.showActionsDrawer,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
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
                        onClick = { vm.isNorthUp = !vm.isNorthUp },
                    ),
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
                    DrawerAction(
                        label = "Map",
                        icon = Icons.Default.Map,
                        onClick = { drawerPage = ActionsDrawerPage.Map },
                    ),
                    DrawerAction(
                        label = "Weather",
                        icon = Icons.Default.Cloud,
                        onClick = { drawerPage = ActionsDrawerPage.Weather },
                    ),
                    DrawerAction(
                        label = "System",
                        icon = Icons.Default.Settings,
                        onClick = { drawerPage = ActionsDrawerPage.System },
                    ),
                    DrawerAction(
                        label = "Help",
                        icon = Icons.AutoMirrored.Filled.Help,
                        onClick = { drawerPage = ActionsDrawerPage.Help },
                    ),
                )
                ActionsDrawerPage.Map -> emptyList()
                ActionsDrawerPage.System -> emptyList()
                ActionsDrawerPage.Weather -> emptyList()
                ActionsDrawerPage.Help -> emptyList()
            }

            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .navigationBarsPadding()
                    .alpha(drawerContentAlpha),
            ) {
                CompositionLocalProvider(
                    LocalSliderDragCallback provides { dragging, label ->
                        isSliderDragging = dragging
                        sliderDisplayLabel = label
                    },
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    if (drawerPage != ActionsDrawerPage.Main) {
                        Text(
                            text = when (drawerPage) {
                                ActionsDrawerPage.Map -> "Map"
                                ActionsDrawerPage.Weather -> "Weather"
                                ActionsDrawerPage.System -> "System"
                                ActionsDrawerPage.Help -> "Help & Info"
                                ActionsDrawerPage.Main -> ""
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    when (drawerPage) {
                        ActionsDrawerPage.Map -> {
                            val mapScroll = rememberScrollState()
                            val mapTopActions = listOf(
                                closeToMap,
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(mapScroll),
                            ) {
                                DrawerTopActionsGrid(actions = mapTopActions)
                                ThemeChooserRow(
                                    mapStyle = mapStyle,
                                    vm = vm,
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                MapDrawerSettingsContent(
                                    vm = vm,
                                    mapStyle = mapStyle,
                                    onStyleChange = onStyleChange,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                )
                            }
                        }
                        ActionsDrawerPage.Weather -> {
                            val weatherScroll = rememberScrollState()
                            val weatherTopActions = listOf(
                                closeToMap,
                                DrawerAction(
                                    label = if (vm.windEnabled) "Wind & Temp Off" else "Wind & Temp On",
                                    icon = Icons.Default.Air,
                                    onClick = { vm.updateWindEnabled(!vm.windEnabled) },
                                ),
                                DrawerAction(
                                    label = if (vm.showLegend) "Hide Legend" else "Show Legend",
                                    icon = if (vm.showLegend) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    enabled = vm.weatherActive,
                                    onClick = { vm.updateShowLegend(!vm.showLegend) },
                                ),
                                DrawerAction(
                                    label = if (vm.showTimeline) "Hide Timeline" else "Show Timeline",
                                    icon = if (vm.showTimeline) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    enabled = vm.weatherActive,
                                    onClick = { vm.updateShowTimeline(!vm.showTimeline) },
                                ),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(weatherScroll),
                            ) {
                                DrawerTopActionsGrid(actions = weatherTopActions)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                WeatherDrawerSettingsContent(
                                    vm = vm,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                )
                            }
                        }
                        ActionsDrawerPage.System -> {
                            val settingsScroll = rememberScrollState()
                            val settingsTopActions = listOf(
                                closeToMap,
                                DrawerAction(
                                    label = "Reset",
                                    icon = Icons.Default.Restore,
                                    onClick = { vm.openResetConfirm() },
                                ),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(settingsScroll),
                            ) {
                                DrawerTopActionsGrid(actions = settingsTopActions)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                SettingsDrawerContent(
                                    vm = vm,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                )
                            }
                        }
                        ActionsDrawerPage.Help -> {
                            val helpScroll = rememberScrollState()
                            val wikiUri = Uri.parse("https://github.com/digiexchris/RoadTripRadar/wiki")
                            val privacyUri =
                                Uri.parse("https://github.com/digiexchris/RoadTripRadar/wiki/Privacy-Policy")
                            val helpTopActions = listOf(closeToMap)
                            val helpLinkActions = listOf(
                                DrawerAction(
                                    label = "Terms & Conditions",
                                    icon = Icons.Default.Gavel,
                                    onClick = { vm.viewTerms() },
                                ),
                                DrawerAction(
                                    label = "Radar Legend",
                                    icon = Icons.Default.Layers,
                                    onClick = { vm.openLegendDetail() },
                                ),
                                DrawerAction(
                                    label = "Documentation",
                                    icon = Icons.AutoMirrored.Filled.LibraryBooks,
                                    onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, wikiUri),
                                        )
                                    },
                                ),
                                DrawerAction(
                                    label = "Privacy Policy",
                                    icon = Icons.Default.Policy,
                                    onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, privacyUri),
                                        )
                                    },
                                ),
                                DrawerAction(
                                    label = "Changelog",
                                    icon = Icons.Default.History,
                                    onClick = { vm.openFullChangelog() },
                                ),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(helpScroll),
                            ) {
                                DrawerTopActionsGrid(actions = helpTopActions)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                Text(
                                    text = "Full documentation and legal information are available from the buttons below.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                )
                                DrawerTopActionsGrid(actions = helpLinkActions)
                                Text(
                                    text = "Version ${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(top = 20.dp, bottom = 8.dp)
                                        .align(Alignment.CenterHorizontally),
                                )
                                if (BuildConfig.DEBUG) {
                                    TextButton(
                                        onClick = { vm.debugPreviewWhatsNewChangelog() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                    ) {
                                        Text("Preview What's New (debug)")
                                    }
                                }
                            }
                        }
                        else -> {
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
            }
        }

        AnimatedVisibility(
            visible = isSliderDragging && sliderDisplayLabel.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                tonalElevation = 4.dp,
            ) {
                Text(
                    text = sliderDisplayLabel,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                )
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
private fun DrawerTopActionsGrid(
    actions: List<DrawerAction>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        actions.chunked(2).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                rowActions.forEach { action ->
                    DrawerActionFab(
                        label = action.label,
                        icon = action.icon,
                        enabled = action.enabled,
                        onClick = action.onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowActions.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DrawerActionFab(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = modifier
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
