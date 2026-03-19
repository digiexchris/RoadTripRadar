package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WeatherTimeline(
    frameTimes: List<Long>,
    currentFrameIndex: Int,
    horizontal: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val frameCount = frameTimes.size
    if (frameCount == 0) return

    val dateFormat = remember { java.text.SimpleDateFormat("H:mm", java.util.Locale.getDefault()) }
    val startLabel = dateFormat.format(java.util.Date(frameTimes.first() * 1000L))
    val endLabel = dateFormat.format(java.util.Date(frameTimes.last() * 1000L))

    @Composable
    fun TimelineMarker(i: Int) {
        val isActive = i == currentFrameIndex
        val isThirtyMinuteTick = ((frameCount - 1 - i) % 3) == 0
        val markerWidth = when {
            isActive && isThirtyMinuteTick -> 22.dp
            isActive -> 14.dp
            isThirtyMinuteTick -> 16.dp
            else -> 8.dp
        }
        val slotSize = 28.dp
        val slotHeight = 4.dp
        Box(
            modifier = Modifier
                .padding(2.dp)
                .width(slotSize)
                .height(slotHeight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(markerWidth)
                    .height(if (isActive) 4.dp else 2.dp)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        RoundedCornerShape(1.dp),
                    ),
            )
        }
    }

    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (horizontal) {
            val splitIndex = (frameCount + 1) / 2
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0..1) {
                    val start = if (row == 0) 0 else splitIndex
                    val end = if (row == 0) splitIndex else frameCount
                    if (start >= end) continue

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.Top,
                    ) {
                        for (i in start until end) {
                            TimelineMarker(i)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = startLabel,
                        fontSize = OverlayTypography.timelineLegendLabelFontSize,
                        lineHeight = OverlayTypography.timelineLegendLabelLineHeight,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Text(
                        text = endLabel,
                        fontSize = OverlayTypography.timelineLegendLabelFontSize,
                        lineHeight = OverlayTypography.timelineLegendLabelLineHeight,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            Text(
                text = startLabel,
                fontSize = OverlayTypography.timelineLegendLabelFontSize,
                lineHeight = OverlayTypography.timelineLegendLabelLineHeight,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 4.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for (i in 0 until frameCount) {
                    TimelineMarker(i)
                }
            }

            Text(
                text = endLabel,
                fontSize = OverlayTypography.timelineLegendLabelFontSize,
                lineHeight = OverlayTypography.timelineLegendLabelLineHeight,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
