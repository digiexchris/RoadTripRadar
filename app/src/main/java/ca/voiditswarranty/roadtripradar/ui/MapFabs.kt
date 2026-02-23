package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val fabBorderModifier: Modifier
    @Composable get() = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)

@Composable
fun PoiSearchClearFab(
    hasPoi: Boolean,
    onClearPoi: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hasPoi) {
        FloatingActionButton(
            onClick = onClearPoi,
            modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(Icons.Default.Close, contentDescription = "Clear POI")
        }
    } else {
        FloatingActionButton(
            onClick = onOpenSearch,
            modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(Icons.Default.Search, contentDescription = "Search for POI")
        }
    }
}

@Composable
fun BottomLeftFabs(
    weatherActive: Boolean,
    isWeatherPlaying: Boolean,
    onToggleWeatherPlaying: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (weatherActive) {
        LargeFloatingActionButton(
            onClick = onToggleWeatherPlaying,
            modifier = fabBorderModifier,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = if (isWeatherPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Toggle weather animation",
            )
        }
    }

    LargeFloatingActionButton(
        onClick = onOpenSettings,
        modifier = fabBorderModifier,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
        )
    }
}

@Composable
fun BottomRightFabs(
    isTrackingCamera: Boolean,
    hasLocation: Boolean,
    onRecenter: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!isTrackingCamera && hasLocation) {
            LargeFloatingActionButton(
                onClick = onRecenter,
                modifier = fabBorderModifier,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Re-center on location",
                )
            }
        }

        LargeFloatingActionButton(
            onClick = onZoomIn,
            modifier = fabBorderModifier,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Zoom in",
            )
        }

        LargeFloatingActionButton(
            onClick = onZoomOut,
            modifier = fabBorderModifier,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Zoom out",
            )
        }
    }
}
