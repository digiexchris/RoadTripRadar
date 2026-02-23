package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SpeedReadout(
    speedMps: Double,
    useMetric: Boolean,
    speedSize: Float,
    modifier: Modifier = Modifier,
) {
    val displaySpeed = if (useMetric) (speedMps * 3.6).toInt() else (speedMps * 2.23694).toInt()
    val unitLabel = if (useMetric) "km/h" else "mph"
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = displaySpeed.coerceAtLeast(0).toString(),
            fontSize = speedSize.sp,
            lineHeight = speedSize.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = unitLabel,
            fontSize = (speedSize / 3).sp,
            lineHeight = (speedSize / 3).sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
