package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.model.NetworkStatus
import ca.voiditswarranty.roadtripradar.model.NetworkTransport

private val fabBorderModifier: Modifier = Modifier

@Composable
fun RecenterFab(
    hasLocation: Boolean,
    isTrackingCamera: Boolean,
    onRecenter: () -> Unit,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    if (!isTrackingCamera && hasLocation) {
        LargeFloatingActionButton(
            onClick = onRecenter,
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Re-center on location",
            )
        }
    }
}

@Composable
fun RecenterTextButton(
    hasLocation: Boolean,
    isTrackingCamera: Boolean,
    onRecenter: () -> Unit,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    if (!isTrackingCamera && hasLocation) {
        Button(
            onClick = onRecenter,
            modifier = modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text("Recenter")
        }
    }
}

@Composable
fun LeftContent(
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = Alignment.Start,
    ) {
        content()
    }
}

@Composable
fun RightContent(
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = Alignment.End,
    ) {
        content()
    }
}

@Composable
fun GpsStatusIcon(
    hasGpsFix: Boolean,
    opacity: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "gps-blink")
    val blinkProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gps-blink-alpha",
    )
    val tint = if (hasGpsFix) Color.White else lerp(Color.White, Color.Red, blinkProgress)
    Icon(
        imageVector = Icons.Default.SatelliteAlt,
        contentDescription = if (hasGpsFix) "GPS fix acquired" else "Waiting for GPS fix",
        tint = tint,
        modifier = modifier
            .size(32.dp)
            .graphicsLayer { alpha = opacity },
    )
}

@Composable
fun NetworkStatusIcon(
    status: NetworkStatus,
    opacity: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "net-blink")
    val blinkProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "net-blink-alpha",
    )

    val icon: ImageVector
    val description: String
    val tint: Color

    if (!status.connected) {
        icon = Icons.Default.PublicOff
        description = "No network"
        tint = lerp(Color.White, Color.Red, blinkProgress)
    } else {
        icon = when (status.transport) {
            NetworkTransport.WIFI -> Icons.Default.Wifi
            NetworkTransport.CELLULAR -> Icons.Default.SignalCellular4Bar
            else -> Icons.Default.PublicOff
        }
        description = when (status.transport) {
            NetworkTransport.WIFI -> "WiFi"
            NetworkTransport.CELLULAR -> "Cellular"
            else -> "Unknown network"
        }
        tint = if (status.validated) Color.White else lerp(Color.White, Color.Red, blinkProgress)
    }

    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = modifier
            .size(32.dp)
            .graphicsLayer { alpha = opacity },
    )
}

@Composable
fun BottomContent(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    navContent: @Composable () -> Unit,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LargeFloatingActionButton(
            onClick = onZoomOut,
            modifier = fabBorderModifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Zoom out",
            )
        }

        navContent()

        LargeFloatingActionButton(
            onClick = onZoomIn,
            modifier = fabBorderModifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Zoom in",
            )
        }
    }
}

@Composable
fun TopContent(
    leftContent: @Composable () -> Unit,
    centerContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        leftContent()
        centerContent()
        rightContent()
    }
}
