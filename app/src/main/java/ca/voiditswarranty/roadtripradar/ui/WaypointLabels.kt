package ca.voiditswarranty.roadtripradar.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.data.Waypoint

/** "#2 Tim Hortons" when [wp] has a name; "Waypoint 2" otherwise. */
@Composable
fun waypointDisplayLabel(wp: Waypoint, index: Int): String =
    waypointDisplayLabel(LocalContext.current, wp, index)

/**
 * Context-based overload so non-Composable callers (the Android Auto car surface) can reuse the
 * same label logic as the phone [NavWidget] without a `@Composable` context.
 */
fun waypointDisplayLabel(context: Context, wp: Waypoint, index: Int): String {
    val name = wp.name?.takeIf { it.isNotBlank() }
    return if (name != null) {
        context.getString(R.string.waypoint_numbered, index + 1, name)
    } else {
        context.getString(R.string.waypoint_unnamed, index + 1)
    }
}