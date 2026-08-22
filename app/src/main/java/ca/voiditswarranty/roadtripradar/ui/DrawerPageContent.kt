package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Renders a [DrawerPage] as a vertical scrollable column of [DrawerAction]s
 * under a title. Used by `DrawerPageTest` to exercise [DrawerPage] variants
 * in isolation.
 *
 * `onDismiss` is reserved for the future [ca.voiditswarranty.roadtripradar.ui.ActionsDrawer]
 * refactor, where the composable would wrap each action's `onClick` to also
 * dismiss the drawer. It is intentionally unused in the body today.
 *
 * The current [ca.voiditswarranty.roadtripradar.ui.ActionsDrawer] does NOT
 * yet use this composable — its sub-pages have non-[DrawerAction] content
 * (theme choosers, settings sliders, version text) that doesn't fit the
 * [DrawerPage] data class. See TODO below for the future refactor.
 *
 * TODO: refactor [ca.voiditswarranty.roadtripradar.ui.ActionsDrawer] to use
 * [DrawerPageContent] for the simple list-of-actions sub-pages once a
 * richer [DrawerPage] data class (or per-page content slots) is designed.
 */
@Composable
internal fun DrawerPageContent(
    page: DrawerPage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = page.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        page.actions.forEach { action ->
            DrawerActionCell(action = action)
        }
    }
}

/**
 * Renders a single [DrawerAction] as an icon + label row. This is a minimal
 * rendering suitable for unit tests; the production
 * [ca.voiditswarranty.roadtripradar.ui.ActionsDrawer] uses
 * [DrawerActionFab] which is visually richer (LargeFloatingActionButton with
 * toggle state, tutorial anchor, etc.).
 */
@Composable
private fun DrawerActionCell(action: DrawerAction) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = action.enabled) { action.onClick() }
            .padding(vertical = 4.dp),
    ) {
        Icon(imageVector = action.icon, contentDescription = null)
        Text(text = action.label, style = MaterialTheme.typography.bodyLarge)
    }
}