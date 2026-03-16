package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import ca.voiditswarranty.roadtripradar.BuildConfig
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(vm: MapViewModel) {
    if (!vm.showHelp) return
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = { vm.closeHelp() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Quick start: long-press the map to drop a POI, or tap once to open Quick Actions.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "For full documentation: tap the map, then tap Help in Quick Actions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = { vm.viewTerms() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Terms & Conditions")
            }

            OutlinedButton(
                onClick = { vm.openLegendDetail() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Radar Legend")
            }

            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/digiexchris/RoadTripRadar/wiki"))
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Documentation")
            }

            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/digiexchris/RoadTripRadar/wiki/Privacy-Policy"))
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Privacy Policy")
            }

            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
