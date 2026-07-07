package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.R
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
 * Compose UI tests for [WeatherWidget]. The widget is a self-contained composable
 * — the only input is a snapshot (or null) plus the unit/bearing parameters — so
 * these tests don't need a [ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeatherWidgetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun snapshot(
        temp: Double = 20.0,
        windKmh: Double = 10.0,
        gustsKmh: Double = 15.0,
        windDir: Int = 0,
        trend: Double? = 0.5,
    ) = OpenMeteoSnapshot(
        temperatureCelsius = temp,
        weatherCode = 1,
        windSpeedKmh = windKmh,
        windDirectionDeg = windDir,
        windGustsKmh = gustsKmh,
        tempTrendCelsius = trend,
    )

    @Test
    fun widget_withSnapshot_rendersTemperature() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherWidget(
                    snapshot = snapshot(temp = 21.0),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    windSpeedUnit = WindSpeedUnit.KMH,
                    weatherWidgetSize = 100f,
                    cameraBearing = 0.0,
                )
            }
        }
        // 21°C → "21°C"
        composeTestRule.onNodeWithText("21°C").assertIsDisplayed()
    }

    @Test
    fun widget_withSnapshot_rendersWindSpeedAndGusts() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherWidget(
                    snapshot = snapshot(windKmh = 10.0, gustsKmh = 15.0),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    windSpeedUnit = WindSpeedUnit.KMH,
                    weatherWidgetSize = 100f,
                    cameraBearing = 0.0,
                )
            }
        }
        // 10 km/h wind + 15 km/h gusts
        composeTestRule.onNodeWithText("10↑15").assertIsDisplayed()
    }

    @Test
    fun widget_withSnapshot_rendersTrend() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherWidget(
                    snapshot = snapshot(trend = 0.5),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    windSpeedUnit = WindSpeedUnit.KMH,
                    weatherWidgetSize = 100f,
                    cameraBearing = 0.0,
                )
            }
        }
        // trend 0.5°C → "+0.5°/h"
        composeTestRule.onNodeWithText("+0.5°/h").assertIsDisplayed()
    }

    @Test
    fun widget_withNullTrend_rendersDash() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherWidget(
                    snapshot = snapshot(trend = null),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    windSpeedUnit = WindSpeedUnit.KMH,
                    weatherWidgetSize = 100f,
                    cameraBearing = 0.0,
                )
            }
        }
        // null trend → "—/h"
        composeTestRule.onNodeWithText("—/h").assertIsDisplayed()
    }

    @Test
    fun widget_rendersWindUnitLabel() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherWidget(
                    snapshot = snapshot(),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    windSpeedUnit = WindSpeedUnit.KMH,
                    weatherWidgetSize = 100f,
                    cameraBearing = 0.0,
                )
            }
        }
        // KMH unit label is "km/h"
        composeTestRule.onNodeWithText("km/h").assertIsDisplayed()
    }

    @Test
    fun widget_withoutSnapshot_rendersUnavailableState() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherWidget(
                    snapshot = null,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    windSpeedUnit = WindSpeedUnit.KMH,
                    weatherWidgetSize = 100f,
                    cameraBearing = 0.0,
                )
            }
        }
        // CloudOff icon + "Weather\nUnavailable" text.
        composeTestRule.onNodeWithContentDescription("Weather unavailable").assertIsDisplayed()
    }

    @Test
    fun widget_windIconHasContentDescription() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherWidget(
                    snapshot = snapshot(windDir = 180),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    windSpeedUnit = WindSpeedUnit.KMH,
                    weatherWidgetSize = 100f,
                    cameraBearing = 0.0,
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                context.getString(R.string.cd_wind_direction),
            )
            .assertExists()
    }

    @Test
    fun widget_darkMode_windIconIsFindable() {
        composeTestRule.setContent {
            RoadTripRadarTheme(darkTheme = true) {
                WeatherWidget(
                    snapshot = snapshot(windDir = 180),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    windSpeedUnit = WindSpeedUnit.KMH,
                    weatherWidgetSize = 100f,
                    cameraBearing = 0.0,
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                context.getString(R.string.cd_wind_direction),
            )
            .assertExists()
    }
}
