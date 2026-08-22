package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [WeatherDrawerSettingsContent]. Parameterized
 * over all state the VM would supply: weatherActive, radarOpacity,
 * windEnabled, windSpeedUnit, temperatureUnit, weatherWidgetSize, plus
 * the change/commit callbacks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeatherDrawerSettingsContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun content_weatherActiveTrue_rendersRadarOpacity() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherDrawerSettingsContent(
                    weatherActive = true,
                    radarOpacity = 0.5f,
                    onRadarOpacityChange = {},
                    onRadarOpacityCommit = {},
                    windEnabled = false,
                    windSpeedUnit = WindSpeedUnit.KMH,
                    onWindSpeedUnitChange = {},
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    onTemperatureUnitChange = {},
                    weatherWidgetSize = 48f,
                    onWeatherWidgetSizeChange = {},
                    onWeatherWidgetSizeCommit = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Weather Radar Opacity").assertIsDisplayed()
        composeTestRule.onNodeWithText("50%").assertIsDisplayed()
    }

    @Test
    fun content_weatherActiveFalse_hidesRadarOpacity() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherDrawerSettingsContent(
                    weatherActive = false,
                    radarOpacity = 0.5f,
                    onRadarOpacityChange = {},
                    onRadarOpacityCommit = {},
                    windEnabled = false,
                    windSpeedUnit = WindSpeedUnit.KMH,
                    onWindSpeedUnitChange = {},
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    onTemperatureUnitChange = {},
                    weatherWidgetSize = 48f,
                    onWeatherWidgetSizeChange = {},
                    onWeatherWidgetSizeCommit = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Weather Radar Opacity").assertIsNotDisplayed()
    }

    @Test
    fun content_windEnabledTrue_rendersWindAndTempRows() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherDrawerSettingsContent(
                    weatherActive = false,
                    radarOpacity = 0.5f,
                    onRadarOpacityChange = {},
                    onRadarOpacityCommit = {},
                    windEnabled = true,
                    windSpeedUnit = WindSpeedUnit.KMH,
                    onWindSpeedUnitChange = {},
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    onTemperatureUnitChange = {},
                    weatherWidgetSize = 48f,
                    onWeatherWidgetSizeChange = {},
                    onWeatherWidgetSizeCommit = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Wind Speed Units").assertIsDisplayed()
        composeTestRule.onNodeWithText("Temperature Units").assertIsDisplayed()
        composeTestRule.onNodeWithText("km/h").assertIsDisplayed()
        composeTestRule.onNodeWithText("°C").assertIsDisplayed()
    }

    @Test
    fun content_windEnabledFalse_hidesWindRows() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherDrawerSettingsContent(
                    weatherActive = false,
                    radarOpacity = 0.5f,
                    onRadarOpacityChange = {},
                    onRadarOpacityCommit = {},
                    windEnabled = false,
                    windSpeedUnit = WindSpeedUnit.KMH,
                    onWindSpeedUnitChange = {},
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    onTemperatureUnitChange = {},
                    weatherWidgetSize = 48f,
                    onWeatherWidgetSizeChange = {},
                    onWeatherWidgetSizeCommit = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Wind Speed Units").assertIsNotDisplayed()
    }

    @Test
    fun content_windSpeedUnitClick_invokesCallback() {
        var captured: WindSpeedUnit? = null
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherDrawerSettingsContent(
                    weatherActive = false,
                    radarOpacity = 0.5f,
                    onRadarOpacityChange = {},
                    onRadarOpacityCommit = {},
                    windEnabled = true,
                    windSpeedUnit = WindSpeedUnit.KMH,
                    onWindSpeedUnitChange = { captured = it },
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    onTemperatureUnitChange = {},
                    weatherWidgetSize = 48f,
                    onWeatherWidgetSizeChange = {},
                    onWeatherWidgetSizeCommit = {},
                )
            }
        }
        composeTestRule.onNodeWithText("mph").performClick()
        assertEquals(WindSpeedUnit.MPH, captured)
    }

    @Test
    fun content_temperatureUnitClick_invokesCallback() {
        var captured: TemperatureUnit? = null
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherDrawerSettingsContent(
                    weatherActive = false,
                    radarOpacity = 0.5f,
                    onRadarOpacityChange = {},
                    onRadarOpacityCommit = {},
                    windEnabled = true,
                    windSpeedUnit = WindSpeedUnit.KMH,
                    onWindSpeedUnitChange = {},
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    onTemperatureUnitChange = { captured = it },
                    weatherWidgetSize = 48f,
                    onWeatherWidgetSizeChange = {},
                    onWeatherWidgetSizeCommit = {},
                )
            }
        }
        composeTestRule.onNodeWithText("°F").performClick()
        assertEquals(TemperatureUnit.FAHRENHEIT, captured)
    }

    @Test
    fun content_rendersWeatherWidgetSizeLabel() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherDrawerSettingsContent(
                    weatherActive = false,
                    radarOpacity = 0.5f,
                    onRadarOpacityChange = {},
                    onRadarOpacityCommit = {},
                    windEnabled = false,
                    windSpeedUnit = WindSpeedUnit.KMH,
                    onWindSpeedUnitChange = {},
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    onTemperatureUnitChange = {},
                    weatherWidgetSize = 48f,
                    onWeatherWidgetSizeChange = {},
                    onWeatherWidgetSizeCommit = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Weather widget size").assertIsDisplayed()
    }
}
