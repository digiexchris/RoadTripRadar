package ca.voiditswarranty.roadtripradar.data

import android.content.Context
import android.content.SharedPreferences
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.PrefsDefaults
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import org.maplibre.spatialk.geojson.Position

class PreferencesRepository(context: Context) {

    val prefs: SharedPreferences =
        context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)

    init {
        migrate()
    }

    private fun migrate() {
        val version = prefs.getInt("prefs_version", 0)
        if (version < 1) {
            when (prefs.getString("weather_mode", null)) {
                "PAUSED" -> prefs.edit()
                    .putString("weather_mode", "ON")
                    .putBoolean("weather_playing", false)
                    .apply()
                "PLAY" -> prefs.edit()
                    .putString("weather_mode", "ON")
                    .putBoolean("weather_playing", true)
                    .apply()
            }
            prefs.edit().putInt("prefs_version", PrefsDefaults.PREFS_VERSION).apply()
        }
    }

    var mapStyle: MapStyle
        get() {
            val saved = prefs.getString("map_style", null)
            return try {
                saved?.let { MapStyle.valueOf(it) } ?: MapStyle.LIBERTY
            } catch (_: IllegalArgumentException) {
                MapStyle.LIBERTY
            }
        }
        set(value) = prefs.edit().putString("map_style", value.name).apply()

    var zoomLevel: Float
        get() = prefs.getFloat("zoom_level", PrefsDefaults.ZOOM_LEVEL)
        set(value) = prefs.edit().putFloat("zoom_level", value).apply()

    var weatherMode: WeatherMode
        get() {
            val saved = prefs.getString("weather_mode", null)
            return try {
                saved?.let { WeatherMode.valueOf(it) } ?: WeatherMode.OFF
            } catch (_: IllegalArgumentException) {
                WeatherMode.OFF
            }
        }
        set(value) = prefs.edit().putString("weather_mode", value.name).apply()

    var isWeatherPlaying: Boolean
        get() = prefs.getBoolean("weather_playing", PrefsDefaults.WEATHER_PLAYING)
        set(value) = prefs.edit().putBoolean("weather_playing", value).apply()

    var radarOpacity: Float
        get() = prefs.getFloat("radar_opacity", PrefsDefaults.RADAR_OPACITY)
        set(value) = prefs.edit().putFloat("radar_opacity", value).apply()

    var useMetric: Boolean
        get() = prefs.getBoolean("use_metric", PrefsDefaults.USE_METRIC)
        set(value) = prefs.edit().putBoolean("use_metric", value).apply()

    var speedSize: Float
        get() = prefs.getFloat("speed_size", PrefsDefaults.SPEED_SIZE)
        set(value) = prefs.edit().putFloat("speed_size", value).apply()

    var navWidgetSize: Float
        get() = prefs.getFloat("nav_widget_size", PrefsDefaults.NAV_WIDGET_SIZE)
        set(value) = prefs.edit().putFloat("nav_widget_size", value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", PrefsDefaults.KEEP_SCREEN_ON)
        set(value) = prefs.edit().putBoolean("keep_screen_on", value).apply()

    var poiPosition: Position?
        get() {
            val lat = prefs.getString("poi_lat", null)?.toDoubleOrNull()
            val lon = prefs.getString("poi_lon", null)?.toDoubleOrNull()
            return if (lat != null && lon != null) Position(latitude = lat, longitude = lon) else null
        }
        set(value) {
            if (value != null) {
                prefs.edit()
                    .putString("poi_lat", value.latitude.toString())
                    .putString("poi_lon", value.longitude.toString())
                    .apply()
            } else {
                prefs.edit().remove("poi_lat").remove("poi_lon").apply()
            }
        }

    var poiName: String?
        get() = prefs.getString("poi_name", null)
        set(value) {
            if (value != null) {
                prefs.edit().putString("poi_name", value).apply()
            } else {
                prefs.edit().remove("poi_name").apply()
            }
        }

    fun resetToDefaults(systemDefault: MapStyle) {
        prefs.edit()
            .putString("map_style", systemDefault.name)
            .putString("weather_mode", PrefsDefaults.WEATHER_MODE)
            .putBoolean("weather_playing", PrefsDefaults.WEATHER_PLAYING)
            .putFloat("radar_opacity", PrefsDefaults.RADAR_OPACITY)
            .putBoolean("use_metric", PrefsDefaults.USE_METRIC)
            .putFloat("speed_size", PrefsDefaults.SPEED_SIZE)
            .putFloat("nav_widget_size", PrefsDefaults.NAV_WIDGET_SIZE)
            .putBoolean("keep_screen_on", PrefsDefaults.KEEP_SCREEN_ON)
            .putFloat("zoom_level", PrefsDefaults.ZOOM_LEVEL)
            .remove("poi_lat").remove("poi_lon").remove("poi_name")
            .apply()
    }
}
