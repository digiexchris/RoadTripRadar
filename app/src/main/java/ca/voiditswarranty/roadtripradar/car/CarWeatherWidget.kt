package ca.voiditswarranty.roadtripradar.car

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.car.app.CarContext
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.data.resolveToConcrete
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.ui.formatTemp
import ca.voiditswarranty.roadtripradar.ui.formatTrend
import ca.voiditswarranty.roadtripradar.ui.windUnitLabel
import ca.voiditswarranty.roadtripradar.ui.windValue
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

/**
 * The graphical weather card overlaid on the car map surface, mirroring the phone
 * [ca.voiditswarranty.roadtripradar.ui.WeatherWidget]: temperature + per-hour trend, a wind
 * arrow rotated by the wind direction (relative to the map bearing) with an "air" badge, then
 * wind speed↑gusts + unit label. Falls back to a "Weather unavailable" state when there is no
 * snapshot yet. The whole card is hidden when the user has disabled the wind widget
 * ([MapViewModel.windEnabled]), matching the phone.
 *
 * Drawn with plain Android Views (not Compose) because the car surface lives on a
 * [android.app.Presentation] backed by a virtual display whose context is a [CarContext], where
 * hosting a ComposeView would need non-Activity lifecycle/saved-state wiring. Native views are
 * deterministic here and keep the car surface self-contained.
 *
 * Colors follow the *resolved* map style's dark/light (so the card always reads against the map
 * behind it); the wind arrow rotation tracks the native map's camera bearing via
 * [CarMapContainer]'s camera listeners, which call [update] on move/idle.
 */
class CarWeatherWidget(
    private val carContext: CarContext,
    private val vm: MapViewModel,
) {
    val view: View

    private val root: LinearLayout
    private val contentGroup: LinearLayout
    private val unavailableGroup: LinearLayout
    private val tempText: TextView
    private val trendText: TextView
    private val arrow: ImageView
    private val badge: FrameLayout
    private val airIcon: ImageView
    private val windSpeedText: TextView
    private val windUnitText: TextView
    private val offIcon: ImageView
    private val offText: TextView

    init {
        view = LayoutInflater.from(carContext).inflate(R.layout.car_weather_widget, null)
        root = view.findViewById(R.id.car_weather_root)
        contentGroup = view.findViewById(R.id.car_weather_content)
        unavailableGroup = view.findViewById(R.id.car_weather_unavailable)
        tempText = view.findViewById(R.id.car_weather_temp)
        trendText = view.findViewById(R.id.car_weather_trend)
        arrow = view.findViewById(R.id.car_weather_arrow)
        badge = view.findViewById(R.id.car_weather_badge)
        airIcon = view.findViewById(R.id.car_weather_air)
        windSpeedText = view.findViewById(R.id.car_weather_wind_speed)
        windUnitText = view.findViewById(R.id.car_weather_wind_unit)
        offIcon = view.findViewById(R.id.car_weather_off_icon)
        offText = view.findViewById(R.id.car_weather_off_text)
    }

    /**
     * Re-render from the VM. [cameraBearingDegrees] is the native map's current bearing, used to
     * keep the wind arrow oriented relative to the map (the phone gets this from Compose map
     * state). Safe to call on the main thread from the refresh listener and camera listeners.
     */
    fun update(cameraBearingDegrees: Double) {
        if (!vm.windEnabled) {
            root.visibility = View.GONE
            return
        }
        root.visibility = View.VISIBLE
        val dark = isStyleDark()
        applyColors(dark)

        val snap = vm.openMeteoSnapshot
        if (snap != null) {
            contentGroup.visibility = View.VISIBLE
            unavailableGroup.visibility = View.GONE
            tempText.text = formatTemp(snap.temperatureCelsius, vm.temperatureUnit)
            trendText.text = snap.tempTrendCelsius?.let { formatTrend(it, vm.temperatureUnit) } ?: "—/h"
            windSpeedText.text = "${windValue(snap.windSpeedKmh, vm.windSpeedUnit)}↑${windValue(snap.windGustsKmh, vm.windSpeedUnit)}"
            windUnitText.text = windUnitLabel(carContext, vm.windSpeedUnit)
            // Wind direction is where it comes FROM; +180 = where it goes TO; subtract the map
            // bearing so the arrow stays correct when the map is rotated. Identical to the phone.
            arrow.rotation = (snap.windDirectionDeg + 180.0 - cameraBearingDegrees).toFloat()
        } else {
            contentGroup.visibility = View.GONE
            unavailableGroup.visibility = View.VISIBLE
        }
    }

    private fun isStyleDark(): Boolean {
        val concrete = vm.mapStyle.resolveToConcrete(carContext, carContext.isDarkMode())
        return concrete != MapStyle.LIBERTY && concrete != MapStyle.CUSTOM_LIGHT
    }

    private fun applyColors(dark: Boolean) {
        val bg: Int
        val textMain: Int
        val textSecondary: Int
        val arrowTint: Int
        val badgeBg: Int
        val airTint: Int
        if (dark) {
            bg = 0xB3000000.toInt()
            textMain = 0xFFFFFFFF.toInt()
            textSecondary = 0xB3FFFFFF.toInt()
            arrowTint = 0xFF90CAF9.toInt()
            badgeBg = 0xE6000000.toInt()
            airTint = 0xFFFFFFFF.toInt()
        } else {
            bg = 0xCCFFFFFF.toInt()
            textMain = 0xFF212121.toInt()
            textSecondary = 0xFF616161.toInt()
            arrowTint = 0xFF1565C0.toInt()
            badgeBg = 0xE6FFFFFF.toInt()
            airTint = 0xFF212121.toInt()
        }
        root.backgroundTintList = ColorStateList.valueOf(bg)
        tempText.setTextColor(textMain)
        windSpeedText.setTextColor(textMain)
        trendText.setTextColor(textSecondary)
        windUnitText.setTextColor(textSecondary)
        offText.setTextColor(textSecondary)
        arrow.imageTintList = ColorStateList.valueOf(arrowTint)
        airIcon.imageTintList = ColorStateList.valueOf(airTint)
        offIcon.imageTintList = ColorStateList.valueOf(textSecondary)
        badge.backgroundTintList = ColorStateList.valueOf(badgeBg)
    }
}