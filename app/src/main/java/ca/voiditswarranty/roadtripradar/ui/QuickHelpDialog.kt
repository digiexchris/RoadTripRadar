package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

@Composable
fun QuickHelpDialog(vm: MapViewModel) {
    if (!vm.showQuickHelp) return

    AlertDialog(
        onDismissRequest = { vm.closeQuickHelp() },
        title = { Text("Quick Help") },
        text = {
            Text(
                "Long-press the map to drop or move a target location.\n" +
                    "Tap the map once to open Quick Actions.\n" +
                    "Documentation is available from Quick Actions -> Help."
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.closeQuickHelp() }) {
                Text("Got it")
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.disableStartupQuickHelp() }) {
                Text("Never show again")
            }
        },
    )
}
