package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.R

/**
 * Weather radar colour legend matching RainViewer colour scheme.
 * Displayed when the weather radar layer is active.
 */

private data class LegendEntry(val color: Color, val labelRes: Int)

private val legendGroups = listOf(
    listOf(
        LegendEntry(Color(0xFF2F7A2E), R.string.legend_overcast),
        LegendEntry(Color(0xFF5BAA27), R.string.legend_drizzle),
        LegendEntry(Color(0xFFF7F713), R.string.legend_rain_light),
        LegendEntry(Color(0xFFF9A414), R.string.legend_rain_moderate),
        LegendEntry(Color(0xFFF73514), R.string.legend_rain_heavy),
    ),
    listOf(
        LegendEntry(Color(0xFFDD1E42), R.string.legend_hail_light),
        LegendEntry(Color(0xFFC01C6F), R.string.legend_hail_moderate),
        LegendEntry(Color(0xFFD41E99), R.string.legend_hail_heavy),
    ),
    listOf(
        LegendEntry(Color(0xFF91CDFD), R.string.legend_snow_light),
        LegendEntry(Color(0xFF508CFB), R.string.legend_snow_moderate),
        LegendEntry(Color(0xFF195CFC), R.string.legend_snow_heavy),
    ),
)

private val cornerRadius = 2.dp

private fun colorBlockShape(index: Int, groupSize: Int): Shape = when {
    groupSize == 1 -> RoundedCornerShape(cornerRadius)
    index == 0 -> RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
    index == groupSize - 1 -> RoundedCornerShape(bottomStart = cornerRadius, bottomEnd = cornerRadius)
    else -> RoundedCornerShape(0.dp)
}

@Composable
private fun LegendCell(entry: LegendEntry, blockShape: Shape = RoundedCornerShape(cornerRadius)) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(10.dp)
                .clip(blockShape)
                .background(entry.color),
        )
        Text(
            text = stringResource(entry.labelRes),
            fontSize = OverlayTypography.timelineLegendLabelFontSize,
            lineHeight = OverlayTypography.timelineLegendLabelLineHeight,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
    }
}

@Composable
fun WeatherLegend(
    horizontal: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (horizontal) {
        Row(
            modifier = modifier
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            legendGroups.forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    group.forEachIndexed { i, entry ->
                        Box(modifier = Modifier.width(52.dp)) {
                            LegendCell(entry, colorBlockShape(i, group.size))
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    RoundedCornerShape(8.dp),
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            legendGroups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    group.forEachIndexed { i, entry ->
                        LegendCell(entry, colorBlockShape(i, group.size))
                    }
                }
            }
        }
    }
}
