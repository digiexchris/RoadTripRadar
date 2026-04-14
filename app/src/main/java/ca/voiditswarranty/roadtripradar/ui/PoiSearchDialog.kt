package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.model.formatDistanceLabel
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

@Composable
fun PoiSearchDialog(vm: MapViewModel) {
    if (!vm.showPoiSearch) return

    AlertDialog(
        onDismissRequest = { vm.closePoiSearch() },
        title = { Text(stringResource(R.string.search_location_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.search_within_visible),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = vm.searchQuery,
                    onValueChange = { vm.updateSearchQuery(it) },
                    label = { Text(stringResource(R.string.search_address_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (vm.isSearching) {
                    Text(
                        stringResource(R.string.search_searching),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn {
                    items(vm.searchResults) { result ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setPoiFromSearch(result.position, result.name)
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = result.name,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (result.distance != null) {
                                    Text(
                                        text = formatDistanceLabel(result.distance, vm.useMetric),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                            if (result.subtitle.isNotEmpty()) {
                                Text(
                                    text = result.subtitle,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { vm.closePoiSearch() }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
