package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Toggle
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.car.nextCycle
import ca.voiditswarranty.roadtripradar.car.radarModeLabel
import ca.voiditswarranty.roadtripradar.model.WmoWeatherCodes
import ca.voiditswarranty.roadtripradar.ui.formatTemp
import ca.voiditswarranty.roadtripradar.ui.formatTrend
import ca.voiditswarranty.roadtripradar.ui.tempUnitSymbol
import ca.voiditswarranty.roadtripradar.ui.windUnitLabel
import ca.voiditswarranty.roadtripradar.ui.windValue
import kotlin.math.abs

/**
 * Weather settings: radar mode (off/on/playing), wind-arrow + legend + timeline toggles,
 * opacity preset cycle, wind + temperature unit pickers, metric toggle, and a
 * current-conditions header. All driving-ok (standard toggles/rows); "radar off" is
 * parked-only since it stops background polling.
 */
class WeatherScreen(carContext: CarContext) : BaseCarScreen(
    carContext,
    CarViewModelHolder.ensureInitialized(carContext.applicationContext),
) {

    override fun buildTemplate(): ListTemplate {
        val itemList = ItemList.Builder().apply {
            // Current conditions header row
            val snap = vm.openMeteoSnapshot
            if (snap != null) {
                val cond = carContext.getString(WmoWeatherCodes.labelRes(snap.weatherCode))
                val temp = formatTemp(snap.temperatureCelsius, vm.temperatureUnit)
                val wind = "${windValue(snap.windSpeedKmh, vm.windSpeedUnit)} " +
                    windUnitLabel(carContext, vm.windSpeedUnit)
                val trend = snap.tempTrendCelsius?.let { formatTrend(it, vm.temperatureUnit) } ?: "—"
                addItem(
                    Row.Builder()
                        .setTitle("$temp · $cond")
                        .addText(wind)
                        .addText("${carContext.getString(R.string.car_home_trend)} $trend")
                        .build()
                )
            } else {
                addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.car_weather_no_data))
                        .build()
                )
            }

            addItem(cycleRow(R.string.car_weather_radar_mode, radarModeLabel(carContext, vm.weatherMode)) { vm.cycleWeatherMode() })

            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_weather_wind_arrow))
                    .setToggle(Toggle.Builder { on -> vm.updateWindEnabled(on) }.setChecked(vm.windEnabled).build())
                    .build()
            )
            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_weather_legend))
                    .setToggle(Toggle.Builder { on -> vm.updateShowLegend(on) }.setChecked(vm.showLegend).build())
                    .build()
            )
            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_weather_timeline))
                    .setToggle(Toggle.Builder { on -> vm.updateShowTimeline(on) }.setChecked(vm.showTimeline).build())
                    .build()
            )
            addItem(
                cycleRow(R.string.car_weather_opacity, "${(vm.radarOpacity * 100).toInt()}%") {
                    vm.updateRadarOpacity(nextOpacity(vm.radarOpacity))
                    vm.saveRadarOpacity()
                }
            )
            addItem(
                cycleRow(R.string.car_weather_wind_unit, windUnitLabel(carContext, vm.windSpeedUnit)) {
                    vm.updateWindSpeedUnit(vm.windSpeedUnit.nextCycle())
                }
            )
            addItem(
                cycleRow(R.string.car_weather_temp_unit, tempUnitSymbol(vm.temperatureUnit)) {
                    vm.updateTemperatureUnit(vm.temperatureUnit.nextCycle())
                }
            )
            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_settings_metric))
                    .setToggle(Toggle.Builder { on -> vm.updateUseMetric(on) }.setChecked(vm.useMetric).build())
                    .build()
            )
            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_action_radar_off))
                    .setOnClickListener(ParkedOnlyOnClickListener.create { vm.turnOffWeather() })
                    .build()
            )
        }.build()

        return ListTemplate.Builder()
            .setSingleList(itemList)
            .setTitle(carContext.getString(R.string.car_weather_title))
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun cycleRow(titleRes: Int, value: String, onClick: () -> Unit): Row =
        Row.Builder()
            .setTitle(carContext.getString(titleRes))
            .addText(value)
            .setOnClickListener { onClick() }
            .build()

    private fun nextOpacity(current: Float): Float {
        val presets = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val idx = presets.indexOfFirst { abs(it - current) < 0.01f }
        return presets[(idx + 1) % presets.size]
    }
}