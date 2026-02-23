package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WeatherTimeline(
    frameTimes: List<Long>,
    currentFrameIndex: Int,
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
