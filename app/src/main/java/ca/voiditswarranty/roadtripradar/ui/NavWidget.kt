package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.voiditswarranty.roadtripradar.model.formatDistanceLabel
import org.maplibre.spatialk.units.Length

@Composable
fun NavWidget(
    poiDistance: Length,
    poiBearingDeg: Double,
    cameraBearing: Double,
    navWidgetSize: Float,
    poiName: String?,
    useMetric: Boolean,
    modifier: Modifier = Modifier,
) {
    val arrowRotation = (poiBearingDeg - cameraBearing).toFloat()
    val iconSize = navWidgetSize.dp
    val distFontSize = (navWidgetSize * 0.35f).sp
    val showName = navWidgetSize >= 50f && poiName != null
    val nameFontSize = (navWidgetSize * 0.25f).sp

    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = "Direction to POI",
            modifier = Modifier
                .size(iconSize)
                .rotate(arrowRotation),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = formatDistanceLabel(poiDistance, useMetric),
            fontSize = distFontSize,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (showName) {
            Text(
                text = poiName!!,
                fontSize = nameFontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = (navWidgetSize * 2.5f).dp),
            )
        }
    }
}
