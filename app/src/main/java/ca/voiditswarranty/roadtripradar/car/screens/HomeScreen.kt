package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.car.radarModeLabel
import ca.voiditswarranty.roadtripradar.model.WmoWeatherCodes
import ca.voiditswarranty.roadtripradar.model.formatDistanceLabel
import ca.voiditswarranty.roadtripradar.ui.formatTemp
import ca.voiditswarranty.roadtripradar.ui.formatTrend
import ca.voiditswarranty.roadtripradar.ui.windUnitLabel
import ca.voiditswarranty.roadtripradar.ui.windValue
import org.maplibre.spatialk.turf.measurement.bearingTo
import org.maplibre.spatialk.turf.measurement.distance
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.inDegrees
import kotlin.math.roundToInt

/**
 * Root car screen: a hub of rows for the four feature groups (weather, route, POIs, settings)
 * plus a radar status row and the POI-pipeline utilities (Search area / Clear / Retry failed).
 * Pushed from the map's Menu action; the BACK header action pops back to the map.
 *
 * Implemented as a [ListTemplate] rather than a PaneTemplate: PaneTemplate's action strip
 * (ACTIONS_CONSTRAINTS_SIMPLE) allows only one custom-titled action, and its pane action list
 * only two — too few for a 4-section hub. ListTemplate holds many tap-to-navigate rows (rows
 * are not title-constrained) and rows may also carry action listeners (the POI-pipeline rows
 * perform an action rather than navigating). The radar play/pause control lives on the map
 * screen's toolbar, not here. Waypoint advance/regress controls live on [RouteScreen], where
 * they belong.
 */
class HomeScreen(carContext: CarContext) : BaseCarScreen(
    carContext,
    CarViewModelHolder.ensureInitialized(carContext.applicationContext),
) {

    override fun buildTemplate(): ListTemplate {
        val itemList = ItemList.Builder().apply {
            // Weather row
            val snap = vm.openMeteoSnapshot
            if (snap != null) {
                val temp = formatTemp(snap.temperatureCelsius, vm.temperatureUnit)
                val condition = carContext.getString(WmoWeatherCodes.labelRes(snap.weatherCode))
                val wind = "${windValue(snap.windSpeedKmh, vm.windSpeedUnit)} " +
                    windUnitLabel(carContext, vm.windSpeedUnit) +
                    " · ${snap.windDirectionDeg}°"
                val trend = snap.tempTrendCelsius?.let { formatTrend(it, vm.temperatureUnit) } ?: "—"
                addItem(
                    Row.Builder()
                        .setTitle("$temp · $condition")
                        .addText(wind)
                        .addText("${carContext.getString(R.string.car_home_trend)} $trend")
                        .setOnClickListener { push(WeatherScreen(carContext)) }
                        .build()
                )
            } else {
                addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.car_weather_no_data))
                        .setOnClickListener { push(WeatherScreen(carContext)) }
                        .build()
                )
            }

            // Active waypoint row
            val active = vm.activeWaypoint
            if (active != null) {
                val name = active.name ?: carContext.getString(R.string.dropped_pin_title)
                val userPos = vm.userPositionForSearch
                val distText = userPos?.let {
                    val dist = distance(it, active.position)
                    val bearing = (it.bearingTo(active.position) - Bearing.North).inDegrees.roundToInt()
                    "${formatDistanceLabel(dist, vm.useMetric)} · ${bearing}°"
                } ?: active.subtitle ?: ""
                addItem(
                    Row.Builder()
                        .setTitle(name)
                        .addText(distText)
                        .setOnClickListener { push(RouteScreen(carContext)) }
                        .build()
                )
            } else {
                addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.car_route_empty))
                        .setOnClickListener { push(RouteScreen(carContext)) }
                        .build()
                )
            }

            // Radar status row
            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_home_radar))
                    .addText(radarModeLabel(carContext, vm.weatherMode))
                    .setOnClickListener { push(WeatherScreen(carContext)) }
                    .build()
            )

            // POIs row
            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_poi_title))
                    .setOnClickListener { push(PoiScreen(carContext)) }
                    .build()
            )

            // POI-pipeline utilities. These live on the menu (not the POI grid's action strip)
            // because GridTemplate's action strip is constrained to one custom-titled action —
            // see PoiScreen. Search area + Clear are parked-only (they re-run/erase the pipeline
            // against the phone's current map camera); Retry failed only appears when something
            // actually failed.
            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_poi_search))
                    .setOnClickListener(ParkedOnlyOnClickListener.create { vm.searchVisibleArea() })
                    .setEnabled(vm.pendingCameraInfo != null)
                    .build()
            )
            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_poi_clear))
                    .setOnClickListener(ParkedOnlyOnClickListener.create { vm.clearNearbyPois() })
                    .build()
            )
            if (vm.hasFailedCells) {
                addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.car_poi_retry))
                        .setOnClickListener { vm.retryFailedCells() }
                        .build()
                )
            }

            // Settings row
            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_settings_title))
                    .setOnClickListener { push(SettingsScreen(carContext)) }
                    .build()
            )
        }.build()

        return ListTemplate.Builder()
            .setSingleList(itemList)
            .setTitle(carContext.getString(R.string.car_home_title))
            .setHeaderAction(Action.BACK)
            .build()
    }
}