package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.model.ChangelogRelease
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewChangelogSheet(vm: MapViewModel) {
    if (!vm.showWhatsNewChangelog) return
    val releases = vm.whatsNewChangelogReleases
    ModalBottomSheet(onDismissRequest = { vm.dismissWhatsNewChangelog() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "What's new",
                style = MaterialTheme.typography.headlineSmall,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                releases.forEach { release ->
                    ChangelogReleaseBlock(release)
                }
            }
            Button(
                onClick = { vm.dismissWhatsNewChangelog() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Got it")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullChangelogSheet(vm: MapViewModel) {
    if (!vm.showFullChangelog) return
    val releases = vm.fullChangelogReleases
    ModalBottomSheet(onDismissRequest = { vm.closeFullChangelog() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Changelog",
                style = MaterialTheme.typography.headlineSmall,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (releases.isEmpty()) {
                    Text(
                        text = "No changelog entries.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    releases.forEach { release ->
                        ChangelogReleaseBlock(release)
                    }
                }
            }
            TextButton(
                onClick = { vm.closeFullChangelog() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun ChangelogReleaseBlock(release: ChangelogRelease) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${release.versionName} (${release.versionCode})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        release.items.forEach { line ->
            Text(
                text = "• $line",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
