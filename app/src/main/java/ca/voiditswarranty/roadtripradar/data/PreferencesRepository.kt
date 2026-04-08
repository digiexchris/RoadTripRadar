package ca.voiditswarranty.roadtripradar.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.PrefsDefaults
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import org.maplibre.spatialk.geojson.Position

class PreferencesRepository(context: Context) {

    private val appContext: Context = context.applicationContext

    val prefs: SharedPreferences =
        context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)

    fun getLastSeenChangelogVersionCode(): Int? =
        if (prefs.contains("last_seen_changelog_version_code")) {
            prefs.getInt("last_seen_changelog_version_code", 0)
        } else {
            null
        }

    fun setLastSeenChangelogVersionCode(code: Int) {
        prefs.edit().putInt("last_seen_changelog_version_code", code).apply()
    }

    companion object {
        /** Liberty when not in night mode, [MapStyle.COLOR_DARK] when UI night mode is on. */
        fun defaultMapStyleFor(context: Context): MapStyle {
            val app = context.applicationContext
            val night =
                app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return if (night == Configuration.UI_MODE_NIGHT_YES) {
                MapStyle.COLOR_DARK
            } else {
                MapStyle.LIBERTY
            }
        }
    }

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
            prefs.edit().putInt("prefs_version", 1).apply()
        }
        if (prefs.getInt("prefs_version", 0) < 2) {
            if (!prefs.contains("nav_widget_size") && prefs.contains("speed_size")) {
                prefs.edit()
                    .putFloat("nav_widget_size", prefs.getFloat("speed_size", PrefsDefaults.NAV_WIDGET_SIZE))
                    .apply()
            }
            if (!prefs.contains("hud_widget_size") && prefs.contains("speed_size")) {
                prefs.edit()
                    .putFloat("hud_widget_size", prefs.getFloat("speed_size", PrefsDefaults.SPEED_SIZE))
                    .apply()
            }
            prefs.edit().putInt("prefs_version", 2).apply()
        }
        if (prefs.getInt("prefs_version", 0) < 3) {
            if (!prefs.contains("weather_widget_size")) {
                prefs.edit()
                    .putFloat(
                        "weather_widget_size",
                        prefs.getFloat("nav_widget_size", PrefsDefaults.WEATHER_WIDGET_SIZE),
                    )
                    .apply()
            }
            prefs.edit().putInt("prefs_version", 3).apply()
        }
        if (prefs.getInt("prefs_version", 0) < 4) {
            if (!prefs.contains("compass_widget_size")) {
                val base = prefs.getFloat(
                    "hud_widget_size",
                    prefs.getFloat("speed_size", PrefsDefaults.SPEED_SIZE),
                )
                prefs.edit()
                    .putFloat("compass_widget_size", base * 1.5f)
                    .apply()
            }
            prefs.edit().putInt("prefs_version", 4).apply()
        }
        if (prefs.getInt("prefs_version", 0) < 5) {
            val mergedSpeed = prefs.getFloat(
                "hud_widget_size",
                prefs.getFloat("speed_size", PrefsDefaults.SPEED_SIZE),
            )
            prefs.edit()
                .putFloat("speed_size", mergedSpeed)
                .remove("hud_widget_size")
                .putInt("prefs_version", 5)
                .apply()
        }
        if (prefs.getInt("prefs_version", 0) < 6) {
            prefs.edit()
                .remove("enabled_poi_categories")
                .remove("show_pois_on_map")
                .putInt("prefs_version", 6)
                .apply()
        }
        if (prefs.getInt("prefs_version", 0) < 7) {
            prefs.edit()
                .remove("poi_display_mode")
                .putInt("prefs_version", 7)
                .apply()
        }
        if (prefs.getInt("prefs_version", 0) < 8) {
            val ed = prefs.edit()
            if (prefs.getString("map_style", null) == "LIBERTY_DARK") {
                ed.putString("map_style", MapStyle.COLOR_DARK.name)
            }
            ed.putInt("prefs_version", PrefsDefaults.PREFS_VERSION).apply()
        }
    }

    private fun parseMapStyle(name: String): MapStyle = when (name) {
        "LIBERTY_DARK" -> MapStyle.COLOR_DARK
        else -> MapStyle.valueOf(name)
    }

    var mapStyle: MapStyle
        get() {
            val saved = prefs.getString("map_style", null)
            return try {
                if (saved == null) defaultMapStyleFor(appContext) else parseMapStyle(saved)
            } catch (_: IllegalArgumentException) {
                defaultMapStyleFor(appContext)
            }
        }
        set(value) = prefs.edit().putString("map_style", value.name).apply()

    var zoomLevel: Float
        get() = prefs.getFloat("zoom_level", PrefsDefaults.ZOOM_LEVEL)
        set(value) = prefs.edit().putFloat("zoom_level", value).apply()

    var mapCenterOffsetPortraitFraction: Float
        get() = if (prefs.contains("map_center_offset_portrait_fraction")) {
            prefs.getFloat(
                "map_center_offset_portrait_fraction",
                PrefsDefaults.MAP_CENTER_OFFSET_PORTRAIT_FRACTION,
            )
        } else {
            // Backward compatibility with the original single offset key.
            prefs.getFloat("map_center_offset_fraction", PrefsDefaults.MAP_CENTER_OFFSET_PORTRAIT_FRACTION)
        }
        set(value) = prefs.edit().putFloat("map_center_offset_portrait_fraction", value).apply()

    var mapCenterOffsetLandscapeFraction: Float
        get() = if (prefs.contains("map_center_offset_landscape_fraction")) {
            prefs.getFloat(
                "map_center_offset_landscape_fraction",
                PrefsDefaults.MAP_CENTER_OFFSET_LANDSCAPE_FRACTION,
            )
        } else {
            // Backward compatibility with the original single offset key.
            prefs.getFloat("map_center_offset_fraction", PrefsDefaults.MAP_CENTER_OFFSET_LANDSCAPE_FRACTION)
        }
        set(value) = prefs.edit().putFloat("map_center_offset_landscape_fraction", value).apply()

    var weatherMode: WeatherMode
        get() {
            val saved = prefs.getString("weather_mode", null)
            return try {
                saved?.let { WeatherMode.valueOf(it) }
                    ?: WeatherMode.valueOf(PrefsDefaults.WEATHER_MODE)
            } catch (_: IllegalArgumentException) {
                WeatherMode.valueOf(PrefsDefaults.WEATHER_MODE)
            }
        }
        set(value) = prefs.edit().putString("weather_mode", value.name).apply()

    var isWeatherPlaying: Boolean
        get() = prefs.getBoolean("weather_playing", PrefsDefaults.WEATHER_PLAYING)
        set(value) = prefs.edit().putBoolean("weather_playing", value).apply()

    var showLegend: Boolean
        get() = prefs.getBoolean("show_legend", PrefsDefaults.SHOW_LEGEND)
        set(value) = prefs.edit().putBoolean("show_legend", value).apply()

    var showTimeline: Boolean
        get() = prefs.getBoolean("show_timeline", PrefsDefaults.SHOW_TIMELINE)
        set(value) = prefs.edit().putBoolean("show_timeline", value).apply()

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

    var weatherWidgetSize: Float
        get() = prefs.getFloat("weather_widget_size", PrefsDefaults.WEATHER_WIDGET_SIZE)
        set(value) = prefs.edit().putFloat("weather_widget_size", value).apply()

    var compassWidgetSize: Float
        get() = prefs.getFloat("compass_widget_size", PrefsDefaults.COMPASS_WIDGET_SIZE)
        set(value) = prefs.edit().putFloat("compass_widget_size", value).apply()

    var windEnabled: Boolean
        get() = prefs.getBoolean("wind_enabled", PrefsDefaults.WIND_ENABLED)
        set(value) = prefs.edit().putBoolean("wind_enabled", value).apply()

    var windSpeedUnit: WindSpeedUnit
        get() {
            val saved = prefs.getString("wind_speed_unit", null)
            return try {
                saved?.let { WindSpeedUnit.valueOf(it) } ?: WindSpeedUnit.valueOf(PrefsDefaults.WIND_SPEED_UNIT)
            } catch (_: IllegalArgumentException) {
                WindSpeedUnit.valueOf(PrefsDefaults.WIND_SPEED_UNIT)
            }
        }
        set(value) = prefs.edit().putString("wind_speed_unit", value.name).apply()

    var temperatureUnit: TemperatureUnit
        get() {
            val saved = prefs.getString("temperature_unit", null)
            return try {
                saved?.let { TemperatureUnit.valueOf(it) } ?: TemperatureUnit.valueOf(PrefsDefaults.TEMPERATURE_UNIT)
            } catch (_: IllegalArgumentException) {
                TemperatureUnit.valueOf(PrefsDefaults.TEMPERATURE_UNIT)
            }
        }
        set(value) = prefs.edit().putString("temperature_unit", value.name).apply()

    var poiIconOpacity: Float
        get() = prefs.getFloat("poi_icon_opacity", PrefsDefaults.POI_ICON_OPACITY)
        set(value) = prefs.edit().putFloat("poi_icon_opacity", value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", PrefsDefaults.KEEP_SCREEN_ON)
        set(value) = prefs.edit().putBoolean("keep_screen_on", value).apply()

    var autostartPoiLoadingOnLaunch: Boolean
        get() = prefs.getBoolean("autostart_poi_loading_on_launch", PrefsDefaults.AUTOSTART_POI_LOADING_ON_LAUNCH)
        set(value) = prefs.edit().putBoolean("autostart_poi_loading_on_launch", value).apply()

    var useGps: Boolean
        get() = prefs.getBoolean("use_gps", PrefsDefaults.USE_GPS)
        set(value) = prefs.edit().putBoolean("use_gps", value).apply()

    var gpsIconOpacity: Float
        get() = prefs.getFloat("gps_icon_opacity", PrefsDefaults.GPS_ICON_OPACITY)
        set(value) = prefs.edit().putFloat("gps_icon_opacity", value).apply()

    var lastKnownPosition: Position
        get() {
            val lat = prefs.getFloat("last_known_lat", PrefsDefaults.LAST_KNOWN_LAT.toFloat()).toDouble()
            val lon = prefs.getFloat("last_known_lon", PrefsDefaults.LAST_KNOWN_LON.toFloat()).toDouble()
            return Position(latitude = lat, longitude = lon)
        }
        set(value) {
            prefs.edit()
                .putFloat("last_known_lat", value.latitude.toFloat())
                .putFloat("last_known_lon", value.longitude.toFloat())
                .apply()
        }

    var acceptedTermsVersion: Int
        get() = prefs.getInt("accepted_terms_version", -1)
        set(value) = prefs.edit().putInt("accepted_terms_version", value).apply()

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


    var enabledPoiCategories: Set<String>
        get() {
            val csv = prefs.getString("enabled_poi_categories", null)
            return csv?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        }
        set(value) {
            prefs.edit().putString("enabled_poi_categories", value.joinToString(",")).apply()
        }

    fun resetToDefaults(systemDefault: MapStyle) {
        prefs.edit()
            .putString("map_style", systemDefault.name)
            .putString("weather_mode", PrefsDefaults.WEATHER_MODE)
            .putBoolean("weather_playing", PrefsDefaults.WEATHER_PLAYING)
            .putBoolean("show_legend", PrefsDefaults.SHOW_LEGEND)
            .putBoolean("show_timeline", PrefsDefaults.SHOW_TIMELINE)
            .putFloat("radar_opacity", PrefsDefaults.RADAR_OPACITY)
            .putBoolean("use_metric", PrefsDefaults.USE_METRIC)
            .putFloat("speed_size", PrefsDefaults.SPEED_SIZE)
            .putFloat("nav_widget_size", PrefsDefaults.NAV_WIDGET_SIZE)
            .putFloat("weather_widget_size", PrefsDefaults.WEATHER_WIDGET_SIZE)
            .putFloat("compass_widget_size", PrefsDefaults.COMPASS_WIDGET_SIZE)
            .putBoolean("wind_enabled", PrefsDefaults.WIND_ENABLED)
            .putString("wind_speed_unit", PrefsDefaults.WIND_SPEED_UNIT)
            .putString("temperature_unit", PrefsDefaults.TEMPERATURE_UNIT)
            .putFloat("poi_icon_opacity", PrefsDefaults.POI_ICON_OPACITY)
            .putBoolean("keep_screen_on", PrefsDefaults.KEEP_SCREEN_ON)
            .putBoolean("autostart_poi_loading_on_launch", PrefsDefaults.AUTOSTART_POI_LOADING_ON_LAUNCH)
            .putBoolean("use_gps", PrefsDefaults.USE_GPS)
            .putFloat("gps_icon_opacity", PrefsDefaults.GPS_ICON_OPACITY)
            .putFloat("zoom_level", PrefsDefaults.ZOOM_LEVEL)
            .putFloat("map_center_offset_portrait_fraction", PrefsDefaults.MAP_CENTER_OFFSET_PORTRAIT_FRACTION)
            .putFloat("map_center_offset_landscape_fraction", PrefsDefaults.MAP_CENTER_OFFSET_LANDSCAPE_FRACTION)
            .remove("enabled_poi_categories")
            .remove("show_pois_on_map")
            .remove("poi_display_mode")
            .remove("poi_lat").remove("poi_lon").remove("poi_name")
            .remove("last_known_lat").remove("last_known_lon")
            .apply()
    }
}
