package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.data.Waypoint

/** "#2 Tim Hortons" when [wp] has a name; "Waypoint 2" otherwise. */
@Composable
fun waypointDisplayLabel(wp: Waypoint, index: Int): String {
    val name = wp.name?.takeIf { it.isNotBlank() }
    return if (name != null) {
        stringResource(R.string.waypoint_numbered, index + 1, name)
    } else {
        stringResource(R.string.waypoint_unnamed, index + 1)
    }
}
