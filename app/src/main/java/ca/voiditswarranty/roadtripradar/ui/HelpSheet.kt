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
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.BuildConfig
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(vm: MapViewModel) {
    if (!vm.showHelp) return

    ModalBottomSheet(onDismissRequest = { vm.closeHelp() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(
                onClick = { vm.viewTerms() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Terms & Conditions")
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
