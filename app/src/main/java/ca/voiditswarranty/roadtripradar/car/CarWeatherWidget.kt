package ca.voiditswarranty.roadtripradar.car

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.ui.formatTemp
import ca.voiditswarranty.roadtripradar.ui.formatTrend
import ca.voiditswarranty.roadtripradar.ui.windArrowRotationDeg
import ca.voiditswarranty.roadtripradar.ui.windUnitLabel
import ca.voiditswarranty.roadtripradar.ui.windValue
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

/**
 * The graphical weather card overlaid on the car map surface (right side), mirroring the phone
 * [ca.voiditswarranty.roadtripradar.ui.WeatherWidget]: temperature + per-hour trend, a wind arrow
 * rotated by the wind direction (relative to the map bearing) with a "Wind" label below, then wind
 * speed↑gusts + unit label. Falls back to a "Weather unavailable" state when there is no snapshot
 * yet. The whole card is hidden when the user has disabled the wind widget
 * ([MapViewModel.windEnabled]), matching the phone.
 *
 * Drawn with plain Android Views (not Compose) because the car surface lives on a
 * [android.app.Presentation] backed by a virtual display whose context is a [CarContext]-derived
 * [Context], where hosting a ComposeView would need non-Activity lifecycle/saved-state wiring.
 * Native views are deterministic here and keep the car surface self-contained.
 *
 * Colors follow the *resolved* map style's dark/light so the card always reads against the map
 * behind it; the wind arrow rotation tracks the native map's camera bearing via
 * [CarMapContainer]'s camera listeners, which call [update] on move/idle. The rotation math
 * reuses the shared [windArrowRotationDeg] so the car arrow matches the phone arrow exactly.
 *
 * The [isDark] flag is supplied by [CarMapContainer], which already computes it from the
 * resolved map style and the host [androidx.car.app.CarContext.isDarkMode] state. The widget
 * itself only needs a [Context] for resource/layout access (no `CarContext` API is touched),
 * which keeps it unit-testable under Robolectric.
 */
class CarWeatherWidget(
    private val context: Context,
    private val vm: MapViewModel,
    initialIsDark: Boolean,
) {
    val view: View

    private val root: LinearLayout
    private val contentGroup: LinearLayout
    private val unavailableGroup: LinearLayout
    private val tempText: TextView
    private val trendText: TextView
    private val arrow: ImageView
    private val windSpeedText: TextView
    private val windUnitText: TextView
    private val windLabelText: TextView
    private val offIcon: ImageView
    private val offText: TextView

    private var isDark: Boolean = initialIsDark

    init {
        view = LayoutInflater.from(context).inflate(R.layout.car_weather_widget, null)
        root = view.findViewById(R.id.car_weather_root)
        contentGroup = view.findViewById(R.id.car_weather_content)
        unavailableGroup = view.findViewById(R.id.car_weather_unavailable)
        tempText = view.findViewById(R.id.car_weather_temp)
        trendText = view.findViewById(R.id.car_weather_trend)
        arrow = view.findViewById(R.id.car_weather_arrow)
        windSpeedText = view.findViewById(R.id.car_weather_wind_speed)
        windUnitText = view.findViewById(R.id.car_weather_wind_unit)
        windLabelText = view.findViewById(R.id.car_weather_wind_label)
        offIcon = view.findViewById(R.id.car_weather_off_icon)
        offText = view.findViewById(R.id.car_weather_off_text)
    }

    /**
     * Update the theme flag. [CarMapContainer] calls this on style reload (the underlying
     * `MapViewModel.mapStyle` may have changed). No-op when the value is unchanged — callers
     * can invoke this on every style reload without paying for repeated re-themes.
     */
    fun setDark(dark: Boolean) {
        if (isDark == dark) return
        isDark = dark
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
        applyColors(isDark)

        val snap = vm.openMeteoSnapshot
        if (snap != null) {
            contentGroup.visibility = View.VISIBLE
            unavailableGroup.visibility = View.GONE
            tempText.text = formatTemp(snap.temperatureCelsius, vm.temperatureUnit)
            trendText.text = snap.tempTrendCelsius?.let { formatTrend(it, vm.temperatureUnit) } ?: "—/h"
            windSpeedText.text = "${windValue(snap.windSpeedKmh, vm.windSpeedUnit)}↑${windValue(snap.windGustsKmh, vm.windSpeedUnit)}"
            windUnitText.text = windUnitLabel(context, vm.windSpeedUnit)
            // Wind direction is where it comes FROM; the arrow points where it goes TO, corrected
            // for the map bearing so it stays oriented as the map rotates. Shared with the phone.
            arrow.rotation = windArrowRotationDeg(snap.windDirectionDeg, cameraBearingDegrees)
            windLabelText.text = context.getString(R.string.wind_label)
        } else {
            contentGroup.visibility = View.GONE
            unavailableGroup.visibility = View.VISIBLE
        }
    }

    private fun applyColors(dark: Boolean) {
        if (dark) {
            root.backgroundTintList = ColorStateList.valueOf(darkBackground)
            tempText.setTextColor(darkTextMain)
            windSpeedText.setTextColor(darkTextMain)
            trendText.setTextColor(darkTextSecondary)
            windUnitText.setTextColor(darkTextSecondary)
            windLabelText.setTextColor(darkTextSecondary)
            offText.setTextColor(darkTextSecondary)
            arrow.imageTintList = ColorStateList.valueOf(darkArrowTint)
            offIcon.imageTintList = ColorStateList.valueOf(darkTextSecondary)
        } else {
            root.backgroundTintList = ColorStateList.valueOf(lightBackground)
            tempText.setTextColor(lightTextMain)
            windSpeedText.setTextColor(lightTextMain)
            trendText.setTextColor(lightTextSecondary)
            windUnitText.setTextColor(lightTextSecondary)
            windLabelText.setTextColor(lightTextSecondary)
            offText.setTextColor(lightTextSecondary)
            arrow.imageTintList = ColorStateList.valueOf(lightArrowTint)
            offIcon.imageTintList = ColorStateList.valueOf(lightTextSecondary)
        }
    }

    companion object {
        // Dark palette — drawn on top of a dark map. Background is 70% black, text
        // is high-contrast white, arrow is a soft light blue.
        @JvmField val darkBackground: Int = 0xB3000000.toInt()
        @JvmField val darkTextMain: Int = 0xFFFFFFFF.toInt()
        @JvmField val darkTextSecondary: Int = 0xB3FFFFFF.toInt()
        @JvmField val darkArrowTint: Int = 0xFF90CAF9.toInt()

        // Light palette — drawn on top of a light map. Background is 80% white,
        // primary text is near-black, secondary is mid-grey, arrow is a saturated dark blue.
        @JvmField val lightBackground: Int = 0xCCFFFFFF.toInt()
        @JvmField val lightTextMain: Int = 0xFF212121.toInt()
        @JvmField val lightTextSecondary: Int = 0xFF616161.toInt()
        @JvmField val lightArrowTint: Int = 0xFF1565C0.toInt()
    }
}
