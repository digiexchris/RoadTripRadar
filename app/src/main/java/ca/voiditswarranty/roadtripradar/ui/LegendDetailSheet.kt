package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

private data class LegendDetailEntry(
    val category: String,
    val label: String,
    val dbz: String,
    val mmPerHour: String,
    val color: Color,
)

private val legendDetailEntries = listOf(
    // Rain
    LegendDetailEntry("Rain", "Overcast", "<10", "0", Color(0xFF2F7A2E)),
    LegendDetailEntry("Rain", "Drizzle", "10", "<1", Color(0xFF5BAA27)),
    LegendDetailEntry("Rain", "Light Rain", "20", "1", Color(0xFFF7F713)),
    LegendDetailEntry("Rain", "Moderate Rain", "30", "3", Color(0xFFF9A414)),
    LegendDetailEntry("Rain", "Showers", "40", "12", Color(0xFFF73514)),
    // Hail
    LegendDetailEntry("Hail", "Small Hail or Freezing Rain Possible", "50", "48", Color(0xFFDD1E42)),
    LegendDetailEntry("Hail", "Hail Possible", "55", "100", Color(0xFFC01C6F)),
    LegendDetailEntry("Hail", "Hail Likely", ">60", ">205", Color(0xFFD41E99)),
    // Snow
    LegendDetailEntry("Snow", "Light Snow", "—", "—", Color(0xFF91CDFD)),
    LegendDetailEntry("Snow", "Moderate Snow", "—", "—", Color(0xFF508CFB)),
    LegendDetailEntry("Snow", "Heavy Snow", "—", "—", Color(0xFF195CFC)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegendDetailSheet(vm: MapViewModel) {
    if (!vm.showLegendDetail) return

    ModalBottomSheet(onDismissRequest = { vm.closeLegendDetail() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Radar Legend",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "",
                    modifier = Modifier.width(20.dp),
                )
                Text(
                    text = "Label",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                Text(
                    text = "dBZ",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
                Text(
                    text = "mm/h",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp),
                )
            }

            HorizontalDivider()

            var lastCategory = ""
            legendDetailEntries.forEach { entry ->
                if (entry.category != lastCategory) {
                    if (lastCategory.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    }
                    lastCategory = entry.category
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(entry.color),
                    )
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).padding(start = 6.dp),
                    )
                    Text(
                        text = entry.dbz,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(40.dp),
                    )
                    Text(
                        text = entry.mmPerHour,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(48.dp),
                    )
                }
            }
        }
    }
}
