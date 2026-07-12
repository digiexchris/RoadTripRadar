package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarMapRenderer
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.model.WeatherMode

/**
 * Root map screen: a plain [NavigationTemplate] that renders our MapLibre map on the Android Auto
 * surface via [CarMapRenderer], with the menu reachable by pushing [HomeScreen] from the toolbar.
 *
 * `NavigationTemplate` **requires** a top [setActionStrip] (build throws if null), so the three
 * primary controls (Menu / Play-Pause / Recenter) live there. The map action strip holds only
 * [Action.PAN], which is required for the host to forward pan/pinch gestures to our
 * `SurfaceCallback`.
 */
class CarNavMapScreen(carContext: CarContext) : BaseCarScreen(
    carContext,
    CarViewModelHolder.ensureInitialized(carContext.applicationContext),
) {
    private val renderer = CarMapRenderer(carContext, lifecycle)

    /** Re-resolve the map style against the car's day/night and reload if it changed (MR-1). */
    fun reloadMapStyle() = renderer.reloadStyleIfNeeded()

    override fun buildTemplate(): Template =
        NavigationTemplate.Builder()
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
            .setMapActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.PAN)
                    .build()
            )
            .build()

    /** Pause icon while the radar is playing, play icon otherwise — mirroring the phone FAB. */
    private fun radarPlayPauseIcon(): Int = when (vm.weatherMode) {
        WeatherMode.PLAYING -> R.drawable.ic_car_pause
        else -> R.drawable.ic_car_play
    }

    private fun carIcon(drawableRes: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, drawableRes)).build()
}