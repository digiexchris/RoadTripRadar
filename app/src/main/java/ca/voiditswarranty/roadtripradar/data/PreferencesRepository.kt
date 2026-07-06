package ca.voiditswarranty.roadtripradar.data

import android.content.Context
import android.content.SharedPreferences
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.PrefsDefaults
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import kotlinx.serialization.json.Json
import org.maplibre.spatialk.geojson.Position
import java.util.UUID

class PreferencesRepository(
    context: Context,
    private val prefsFileName: String = "map_prefs",
) {

    private val appContext: Context = context.applicationContext

    val prefs: SharedPreferences =
        context.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)

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
        /** Auto theme, which resolves to Liberty or [MapStyle.COLOR_DARK] based on system UI night mode. */
        fun defaultMapStyleFor(context: Context): MapStyle = MapStyle.AUTO
    }

    init {
        migrate()
    }

    private fun migrate() {
        val v0 = prefs
        migrateV0ToV1(v0)
        migrateV1ToV2(v0)
        migrateV2ToV3(v0)
        migrateV3ToV4(v0)
        migrateV4ToV5(v0)
        migrateV5ToV6(v0)
        migrateV6ToV7(v0)
        migrateV7ToV8(v0)
        migrateV8ToV9(v0)
        migrateV9ToV10(v0)
        migrateV10ToV11(v0)
    }

    var customLightAutoEnabled: Boolean
        get() = prefs.getBoolean("custom_light_auto_enabled", PrefsDefaults.CUSTOM_LIGHT_AUTO_ENABLED)
        set(value) = prefs.edit().putBoolean("custom_light_auto_enabled", value).apply()

    var customDarkAutoEnabled: Boolean
        get() = prefs.getBoolean("custom_dark_auto_enabled", PrefsDefaults.CUSTOM_DARK_AUTO_ENABLED)
        set(value) = prefs.edit().putBoolean("custom_dark_auto_enabled", value).apply()

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

    // Separate zoom for the Android Auto car surface, so the phone and car keep independent zoom
    // levels across app exit/restart (the phone persists on `zoom_level` above).
    var carZoomLevel: Float
        get() = prefs.getFloat("car_zoom_level", PrefsDefaults.CAR_ZOOM_LEVEL)
        set(value) = prefs.edit().putFloat("car_zoom_level", value).apply()

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

    // The car surface keeps its own user-location center offset, independent of the phone's
    // portrait/landscape settings. No legacy key to fall back from (the car is new).
    var mapCenterOffsetCarFraction: Float
        get() = prefs.getFloat(
            "map_center_offset_car_fraction",
            PrefsDefaults.MAP_CENTER_OFFSET_CAR_FRACTION,
        )
        set(value) = prefs.edit().putFloat("map_center_offset_car_fraction", value).apply()

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

    var completedTutorialGroups: Set<String>
        get() {
            val csv = prefs.getString("completed_tutorial_groups", null)
            return csv?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        }
        set(value) {
            prefs.edit().putString("completed_tutorial_groups", value.joinToString(",")).apply()
        }

    var waypoints: List<Waypoint>
        get() {
            val json = prefs.getString("waypoints", null) ?: return emptyList()
            return try {
                Json.decodeFromString(json)
            } catch (_: Exception) {
                emptyList()
            }
        }
        set(value) {
            prefs.edit().putString("waypoints", Json.encodeToString(value)).apply()
        }

    var activeWaypointId: String?
        get() = prefs.getString("active_waypoint_id", null)
        set(value) {
            if (value != null) {
                prefs.edit().putString("active_waypoint_id", value).apply()
            } else {
                prefs.edit().remove("active_waypoint_id").apply()
            }
        }

    var autoAdvanceEnabled: Boolean
        get() = prefs.getBoolean("auto_advance_enabled", PrefsDefaults.AUTO_ADVANCE_ENABLED)
        set(value) = prefs.edit().putBoolean("auto_advance_enabled", value).apply()

    var autoAdvanceThresholdMeters: Int
        get() = prefs.getInt("auto_advance_threshold_m", PrefsDefaults.AUTO_ADVANCE_THRESHOLD_M)
        set(value) = prefs.edit().putInt("auto_advance_threshold_m", value).apply()


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
            .putFloat("car_zoom_level", PrefsDefaults.CAR_ZOOM_LEVEL)
            .putFloat("map_center_offset_portrait_fraction", PrefsDefaults.MAP_CENTER_OFFSET_PORTRAIT_FRACTION)
            .putFloat("map_center_offset_landscape_fraction", PrefsDefaults.MAP_CENTER_OFFSET_LANDSCAPE_FRACTION)
            .putFloat("map_center_offset_car_fraction", PrefsDefaults.MAP_CENTER_OFFSET_CAR_FRACTION)
            .remove("enabled_poi_categories")
            .remove("show_pois_on_map")
            .remove("poi_display_mode")
            .remove("waypoints").remove("active_waypoint_id")
            .remove("auto_advance_enabled").remove("auto_advance_threshold_m")
            .remove("last_known_lat").remove("last_known_lon")
            .putBoolean("custom_light_auto_enabled", PrefsDefaults.CUSTOM_LIGHT_AUTO_ENABLED)
            .putBoolean("custom_dark_auto_enabled", PrefsDefaults.CUSTOM_DARK_AUTO_ENABLED)
            .apply()
    }
}
