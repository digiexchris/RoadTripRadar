package ca.voiditswarranty.roadtripradar.ui

import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.data.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the [waypointDisplayLabel] helper. The label is shared between
 * the phone [NavWidget] and the Android Auto car surface; the car surface
 * calls the non-Composable overload that takes a [android.content.Context].
 *
 * The composable overload just delegates to the Context-based one with
 * [androidx.compose.ui.platform.LocalContext.current], so testing the
 * Context-based form is sufficient.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WaypointLabelsTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun label_withName_usesNumberedTemplate() {
        val wp = Waypoint(
            id = "wp1",
            lat = 43.65,
            lon = -79.38,
            name = "Tim Hortons",
        )
        // "#1 Tim Hortons"
        assertEquals("#1 Tim Hortons", waypointDisplayLabel(context, wp, 0))
    }

    @Test
    fun label_withoutName_usesUnnamedTemplate() {
        val wp = Waypoint(
            id = "wp1",
            lat = 43.65,
            lon = -79.38,
            name = null,
        )
        // "Waypoint 1"
        assertEquals("Waypoint 1", waypointDisplayLabel(context, wp, 0))
    }

    @Test
    fun label_withBlankName_usesUnnamedTemplate() {
        val wp = Waypoint(
            id = "wp1",
            lat = 43.65,
            lon = -79.38,
            name = "   ",
        )
        // Blank name falls back to the unnamed template.
        assertEquals("Waypoint 1", waypointDisplayLabel(context, wp, 0))
    }

    @Test
    fun label_indexIsOneBased() {
        val wp = Waypoint(
            id = "wp1",
            lat = 43.65,
            lon = -79.38,
            name = "Coffee Shop",
        )
        // index 4 → "#5 Coffee Shop"
        assertEquals("#5 Coffee Shop", waypointDisplayLabel(context, wp, 4))
    }
}
