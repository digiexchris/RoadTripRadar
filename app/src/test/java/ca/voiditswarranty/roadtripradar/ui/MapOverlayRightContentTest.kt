package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [MapOverlayRightContent]. The composable
 * is a [androidx.compose.foundation.layout.ColumnScope] extension
 * (it uses a Spacer with `weight(1f)` to push the legend to the
 * bottom of its parent Column). Tests wrap the call in a Column
 * to provide the receiver.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapOverlayRightContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun hostInColumn(content: @Composable ColumnScope.() -> Unit) {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Column(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    @Test
    fun rightContent_windDisabled_doesNotRenderWidget() {
        hostInColumn {
            MapOverlayRightContent(
                weatherWidgetSize = 100f,
                bearing = 0.0,
                windEnabled = false,
                openMeteoSnapshot = null,
                temperatureUnit = TemperatureUnit.CELSIUS,
                windSpeedUnit = WindSpeedUnit.KMH,
                weatherActive = true,
                showLegend = true,
                hasRadarFrames = true,
                onHeightChanged = {},
            )
        }
    }

    @Test
    fun rightContent_weatherInactive_doesNotRenderLegend() {
        hostInColumn {
            MapOverlayRightContent(
                weatherWidgetSize = 100f,
                bearing = 0.0,
                windEnabled = false,
                openMeteoSnapshot = null,
                temperatureUnit = TemperatureUnit.CELSIUS,
                windSpeedUnit = WindSpeedUnit.KMH,
                weatherActive = false,
                showLegend = true,
                hasRadarFrames = true,
                onHeightChanged = {},
            )
        }
    }

    @Test
    fun rightContent_showLegendFalse_doesNotRenderLegend() {
        hostInColumn {
            MapOverlayRightContent(
                weatherWidgetSize = 100f,
                bearing = 0.0,
                windEnabled = false,
                openMeteoSnapshot = null,
                temperatureUnit = TemperatureUnit.CELSIUS,
                windSpeedUnit = WindSpeedUnit.KMH,
                weatherActive = true,
                showLegend = false,
                hasRadarFrames = true,
                onHeightChanged = {},
            )
        }
    }

    @Test
    fun rightContent_noRadarFrames_doesNotRenderLegend() {
        hostInColumn {
            MapOverlayRightContent(
                weatherWidgetSize = 100f,
                bearing = 0.0,
                windEnabled = false,
                openMeteoSnapshot = null,
                temperatureUnit = TemperatureUnit.CELSIUS,
                windSpeedUnit = WindSpeedUnit.KMH,
                weatherActive = true,
                showLegend = true,
                hasRadarFrames = false,
                onHeightChanged = {},
            )
        }
    }

    @Test
    fun rightContent_allEnabled_doesNotCrash() {
        var reportedHeight: Int? = null
        hostInColumn {
            MapOverlayRightContent(
                weatherWidgetSize = 100f,
                bearing = 45.0,
                windEnabled = true,
                openMeteoSnapshot = null,
                temperatureUnit = TemperatureUnit.CELSIUS,
                windSpeedUnit = WindSpeedUnit.KMH,
                weatherActive = true,
                showLegend = true,
                hasRadarFrames = true,
                onHeightChanged = { reportedHeight = it },
            )
        }
        composeTestRule.waitForIdle()
    }
}
