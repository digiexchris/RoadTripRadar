package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WeatherTimeline(
    frameTimes: List<Long>,
    currentFrameIndex: Int,
    horizontal: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val frameCount = frameTimes.size
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val dateFormat = remember { java.text.SimpleDateFormat("H:mm", java.util.Locale.getDefault()) }
        if (horizontal) {
            val splitIndex = (frameCount + 1) / 2
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0..1) {
                    val start = if (row == 0) 0 else splitIndex
                    val end = if (row == 0) splitIndex else frameCount
                    if (start >= end) continue

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        for (i in start until end) {
                            val label = dateFormat.format(java.util.Date(frameTimes[i] * 1000L))
                            val isActive = i == currentFrameIndex
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(if (isActive) 6.dp else 3.dp)
                                        .width(16.dp)
                                        .background(
                                            if (isActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            RoundedCornerShape(1.dp),
                                        ),
                                )
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp,
                                    textAlign = TextAlign.Center,
                                    color = if (isActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.width(34.dp),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            for (i in 0 until frameCount) {
                val label = dateFormat.format(java.util.Date(frameTimes[i] * 1000L))
                val isActive = i == currentFrameIndex
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        textAlign = TextAlign.End,
                        color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.width(34.dp),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .height(if (isActive) 4.dp else 2.dp)
                            .width(if (isActive) 14.dp else 8.dp)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                RoundedCornerShape(1.dp),
                            ),
                    )
                }
            }
        }
    }
}
