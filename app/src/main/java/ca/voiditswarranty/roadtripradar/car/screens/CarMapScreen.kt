package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.core.graphics.drawable.IconCompat
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarMapRenderer
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import ca.voiditswarranty.roadtripradar.model.WmoWeatherCodes
import ca.voiditswarranty.roadtripradar.ui.compassDirectionLabel
import ca.voiditswarranty.roadtripradar.ui.formatTemp
import ca.voiditswarranty.roadtripradar.ui.formatTrend
import ca.voiditswarranty.roadtripradar.ui.windUnitLabel
import ca.voiditswarranty.roadtripradar.ui.windValue

/**
 * Root car screen: the live MapLibre map rendered onto the Android Auto surface via
 * [MapWithContentTemplate] + a [CarMapRenderer], with the weather shown as the template's
 * content pane and a toolbar for the primary controls.
 *
 * This is the POI-category / `MAP_TEMPLATES` path (not Navigation): we render our own base map
 * on the car surface (the host gives us a `Surface`), and overlay template content. The map is
 * interactive via [Action.PAN] in the map action strip (pan + pinch-zoom gestures forwarded by
 * the host). The toolbar holds the three primary controls — open the menu (the list-based hub
 * for POIs / route / search / settings), play/pause the radar animation, and recenter on the
 * device's last known position.
 *
 * The content pane carries the **weather** (condition + temp + trend, plus a wind row whose
 * direction is a 16-point compass label — N, NNE, … — instead of a rotated arrow, so it only
 * changes when the weather is refreshed, not as the map pans). The **route** (bearing arrow,
 * distance, name, stop count — mirroring the phone `NavWidget`) is drawn on the map surface by
 * [ca.voiditswarranty.roadtripradar.car.CarRouteWidget], and is only visible when there is an
 * active waypoint, matching the phone. [MapWithContentTemplate] requires a content template, so
 * the pane is mandatory even when weather is unavailable (a "Weather unavailable" row is shown
 * in that case). POI pins are intentionally not drawn on the car surface (the symbol-layer/icon
 * pipeline is heavy to replicate on the native SDK); POIs remain reachable via Menu → POI.
 */
class CarMapScreen(carContext: CarContext) : BaseCarScreen(
    carContext,
    CarViewModelHolder.ensureInitialized(carContext.applicationContext),
) {
    private val renderer = CarMapRenderer(carContext, lifecycle)

    /**
     * Re-resolve the map style against the car's current day/night mode and reload it on the
     * surface if it changed. Invoked from the session's car-configuration-changed callback (MR-1).
     */
    fun reloadMapStyle() = renderer.reloadStyleIfNeeded()

    override fun buildTemplate(): Template {
        // The weather lives in the content pane (below); the route overlay is drawn on the map
        // surface by CarRouteWidget. MapWithContentTemplate mandates a content template, so the
        // pane is always present — when there's no weather yet, weatherRows() falls back to a
        // "Weather unavailable" row.
        val content = PaneTemplate.Builder(
            Pane.Builder().apply {
                weatherRows().forEach { addRow(it) }
            }.build()
        )
            .setHeader(Header.Builder().setStartHeaderAction(Action.APP_ICON).build())
            .build()

        return MapWithContentTemplate.Builder()
            .setMapController(
                MapController.Builder()
                    // PAN alone in the map action strip: it's required for touch pan/pinch
                    // gestures to be forwarded to our SurfaceCallback, but it isn't drawn on
                    // touchscreens. The three primary controls live in the template action strip
                    // (top-right) below.
                    .setMapActionStrip(
                        ActionStrip.Builder()
                            .addAction(Action.PAN)
                            .build()
                    )
                    .build()
            )
            .setContentTemplate(content)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setIcon(carIcon(R.drawable.ic_car_menu))
                            .setOnClickListener { push(HomeScreen(carContext)) }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setIcon(carIcon(radarPlayPauseIcon()))
                            .setOnClickListener { vm.toggleWeatherPlayPause() }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setIcon(carIcon(R.drawable.ic_car_recenter))
                            .setOnClickListener { renderer.recenter() }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    /**
     * The weather content-pane rows: a condition + temp + trend row, plus a wind row (compass
     * direction text + speed↑gusts + unit) when the wind widget is enabled. Falls back to a
     * single "Weather unavailable" row when no snapshot has been fetched yet. The wind direction
     * is a 16-point compass label derived from the snapshot (it does NOT track the camera
     * bearing), so it only changes when the weather is refreshed — unlike the route arrow on the
     * surface, which rotates with the map.
     */
    private fun weatherRows(): List<Row> {
        val snap = vm.openMeteoSnapshot
        if (snap == null) {
            return listOf(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_weather_no_data))
                    .build()
            )
        }
        val rows = mutableListOf<Row>()
        val condition = carContext.getString(WmoWeatherCodes.labelRes(snap.weatherCode))
        val temp = formatTemp(snap.temperatureCelsius, vm.temperatureUnit)
        val trend = snap.tempTrendCelsius?.let { formatTrend(it, vm.temperatureUnit) } ?: "—/h"
        rows.add(
            Row.Builder()
                .setTitle(condition)
                .addText("$temp · $trend")
                .build()
        )
        if (vm.windEnabled) {
            val dir = compassDirectionLabel(carContext, snap.windDirectionDeg)
            val speed = "${windValue(snap.windSpeedKmh, vm.windSpeedUnit)}↑" +
                "${windValue(snap.windGustsKmh, vm.windSpeedUnit)}"
            val unit = windUnitLabel(carContext, vm.windSpeedUnit)
            rows.add(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_weather_wind_dir, dir))
                    .addText("$speed $unit")
                    .build()
            )
        }
        return rows
    }

    /** Pause icon while the radar is playing, play icon otherwise — mirroring the phone FAB. */
    private fun radarPlayPauseIcon(): Int = when (vm.weatherMode) {
        WeatherMode.PLAYING -> R.drawable.ic_car_pause
        else -> R.drawable.ic_car_play
    }

    private fun carIcon(drawableRes: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, drawableRes)).build()
}