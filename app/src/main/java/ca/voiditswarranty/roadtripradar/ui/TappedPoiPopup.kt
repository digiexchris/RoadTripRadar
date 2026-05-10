package ca.voiditswarranty.roadtripradar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.data.InsertPosition
import ca.voiditswarranty.roadtripradar.model.formatDistanceLabel
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.turf.measurement.distance

@Composable
fun TappedPoiPopup(
    vm: MapViewModel,
    onCenterOnMap: (Position) -> Unit = {},
) {
    val poi = vm.tappedPoi
    val origin = vm.tappedPoiOrigin
    val isFromSearch = origin == MapViewModel.TappedPoiOrigin.Search
    val isNavigationTarget = origin == MapViewModel.TappedPoiOrigin.NavigationTarget

    BackHandler(enabled = poi != null) {
        if (isFromSearch) {
            vm.tappedPoiBackToSearch()
        } else {
            vm.dismissTappedPoi()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = poi != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { vm.dismissTappedPoi() }
            )
        }

        AnimatedVisibility(
            visible = poi != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                if (poi != null) {
                    val userPos = vm.userPositionForSearch
                    val distanceText = if (userPos != null) {
                        val dist = distance(Point(userPos), Point(poi.position))
                        formatDistanceLabel(dist, vm.useMetric)
                    } else null

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = poi.name,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                if (poi.categoryLabel.isNotEmpty()) {
                                    Text(
                                        text = poi.categoryLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (poi.subtitle.isNotEmpty()) {
                                    Text(
                                        text = poi.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (distanceText != null) {
                                    Text(
                                        text = distanceText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (!poi.openingHours.isNullOrEmpty()) {
                                    Text(
                                        text = "🕐 ${poi.openingHours}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { vm.dismissTappedPoi() }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                            }
                        }

                        when {
                            isNavigationTarget -> NavigationTargetActions(
                                onCenter = { onCenterOnMap(poi.position) },
                                onRemove = { vm.removeNavigationTarget() },
                            )
                            vm.waypoints.isEmpty() -> EmptyRouteActions(
                                isFromSearch = isFromSearch,
                                onBack = { vm.tappedPoiBackToSearch() },
                                onCenter = { onCenterOnMap(poi.position) },
                                onNavigate = { vm.addWaypointFromTapped(InsertPosition.End) },
                            )
                            else -> ExistingRouteActions(
                                vm = vm,
                                isFromSearch = isFromSearch,
                                onBack = { vm.tappedPoiBackToSearch() },
                                onCenter = { onCenterOnMap(poi.position) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationTargetActions(onCenter: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LargeFloatingActionButton(
            onClick = onCenter,
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = null)
                Text(
                    stringResource(R.string.action_center_on_map),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        LargeFloatingActionButton(
            onClick = onRemove,
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text(
                    stringResource(R.string.action_remove_waypoint),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun EmptyRouteActions(
    isFromSearch: Boolean,
    onBack: () -> Unit,
    onCenter: () -> Unit,
    onNavigate: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isFromSearch) {
            FilledTonalButton(
                onClick = onBack,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(72.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Text(stringResource(R.string.action_back), style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LargeFloatingActionButton(
                onClick = onCenter,
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = null)
                    Text(
                        stringResource(R.string.action_center_on_map),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        LargeFloatingActionButton(
            onClick = onNavigate,
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null)
                Text(
                    stringResource(R.string.action_navigate_here),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun ExistingRouteActions(
    vm: MapViewModel,
    isFromSearch: Boolean,
    onBack: () -> Unit,
    onCenter: () -> Unit,
) {
    val size = vm.waypoints.size
    val firstLabel = waypointDisplayLabel(vm.waypoints.first(), 0)
    val lastIndex = size - 1
    val lastLabel = waypointDisplayLabel(vm.waypoints.last(), lastIndex)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isFromSearch) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
            IconButton(onClick = onCenter) {
                Icon(
                    Icons.Default.CenterFocusStrong,
                    contentDescription = stringResource(R.string.action_center_on_map),
                )
            }
            Box(modifier = Modifier.weight(1f))
            OverflowMenu(vm = vm, lastLabel = lastLabel)
        }

        if (size == 1) {
            ActionRow(
                icon = Icons.Default.Add,
                label = stringResource(R.string.action_insert_before, firstLabel),
                onClick = { vm.addWaypointFromTapped(InsertPosition.Start) },
            )
            ActionRow(
                icon = Icons.Default.Add,
                label = stringResource(R.string.action_add_after, firstLabel),
                onClick = { vm.addWaypointFromTapped(InsertPosition.End) },
            )
        } else {
            ActionRow(
                icon = Icons.Default.Add,
                label = stringResource(R.string.action_add_to_start),
                secondary = firstLabel,
                onClick = { vm.addWaypointFromTapped(InsertPosition.Start) },
            )
            ActionRow(
                icon = Icons.Default.Add,
                label = stringResource(R.string.action_insert_before_final),
                secondary = lastLabel,
                onClick = { vm.addWaypointFromTapped(InsertPosition.BeforeLast) },
            )
            ActionRow(
                icon = Icons.Default.Add,
                label = stringResource(R.string.action_add_to_end),
                secondary = lastLabel,
                onClick = { vm.addWaypointFromTapped(InsertPosition.End) },
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    secondary: String? = null,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                if (secondary != null) {
                    Text(
                        secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverflowMenu(vm: MapViewModel, lastLabel: String) {
    var expanded by remember { mutableStateOf(false) }
    val size = vm.waypoints.size
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.action_more_options),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (size == 1) {
                val wp = vm.waypoints.first()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_replace_waypoint, lastLabel)) },
                    leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                    onClick = {
                        expanded = false
                        vm.addWaypointFromTapped(InsertPosition.ReplaceId(wp.id))
                    },
                )
            } else {
                val lastWp = vm.waypoints.last()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_replace_last)) },
                    leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                    onClick = {
                        expanded = false
                        vm.addWaypointFromTapped(InsertPosition.ReplaceId(lastWp.id))
                    },
                )
            }
        }
    }
}

