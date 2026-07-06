package ca.voiditswarranty.roadtripradar.data

import android.content.SharedPreferences
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.PrefsDefaults
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * SharedPreferences migration steps, extracted as `internal` top-level functions
 * so each step is independently testable. The original `migrate()` method in
 * `PreferencesRepository` is a thin orchestrator that calls these in order.
 *
 * Each helper gates itself on `prefs.getInt("prefs_version", 0) < N` so calling
 * any of them on an already-migrated prefs is a no-op (idempotent). The
 * `migrate_idempotent_secondRunIsNoOp` test in `PreferencesRepositoryTest` pins
 * this property.
 */

internal fun migrateV0ToV1(prefs: SharedPreferences) {
    if (prefs.getInt("prefs_version", 0) < 1) {
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
}

internal fun migrateV1ToV2(prefs: SharedPreferences) {
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
}

internal fun migrateV2ToV3(prefs: SharedPreferences) {
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
}

internal fun migrateV3ToV4(prefs: SharedPreferences) {
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
}

internal fun migrateV4ToV5(prefs: SharedPreferences) {
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
}

internal fun migrateV5ToV6(prefs: SharedPreferences) {
    if (prefs.getInt("prefs_version", 0) < 6) {
        prefs.edit()
            .remove("enabled_poi_categories")
            .remove("show_pois_on_map")
            .putInt("prefs_version", 6)
            .apply()
    }
}

internal fun migrateV6ToV7(prefs: SharedPreferences) {
    if (prefs.getInt("prefs_version", 0) < 7) {
        prefs.edit()
            .remove("poi_display_mode")
            .putInt("prefs_version", 7)
            .apply()
    }
}

internal fun migrateV7ToV8(prefs: SharedPreferences) {
    if (prefs.getInt("prefs_version", 0) < 8) {
        val ed = prefs.edit()
        if (prefs.getString("map_style", null) == "LIBERTY_DARK") {
            ed.putString("map_style", MapStyle.COLOR_DARK.name)
        }
        ed.putInt("prefs_version", 8).apply()
    }
}

internal fun migrateV8ToV9(prefs: SharedPreferences) {
    if (prefs.getInt("prefs_version", 0) < 9) {
        // No data migration needed; just bump version.
        prefs.edit().putInt("prefs_version", 9).apply()
    }
}

internal fun migrateV9ToV10(prefs: SharedPreferences) {
    if (prefs.getInt("prefs_version", 0) < 10) {
        if (prefs.contains("weather_mode")) {
            // Collapse weather_mode + weather_playing into a single tri-state weather_mode.
            val wasOn = prefs.getString("weather_mode", "OFF") == "ON"
            val wasPlaying = prefs.getBoolean("weather_playing", true)
            val newMode = when {
                !wasOn -> "OFF"
                wasPlaying -> "PLAYING"
                else -> "ON"
            }
            prefs.edit()
                .putString("weather_mode", newMode)
                .remove("weather_playing")
                .putInt("prefs_version", 10)
                .apply()
        } else {
            prefs.edit()
                .remove("weather_playing")
                .putInt("prefs_version", 10)
                .apply()
        }
    }
}

internal fun migrateV10ToV11(prefs: SharedPreferences) {
    if (prefs.getInt("prefs_version", 0) < 11) {
        // Migrate the single-POI scalar keys (poi_lat/poi_lon/poi_name/poi_subtitle)
        // into a one-element waypoints list, plus an active_waypoint_id pointer.
        val lat = prefs.getString("poi_lat", null)?.toDoubleOrNull()
        val lon = prefs.getString("poi_lon", null)?.toDoubleOrNull()
        val ed = prefs.edit()
        if (lat != null && lon != null) {
            val wp = Waypoint(
                id = UUID.randomUUID().toString(),
                lat = lat,
                lon = lon,
                name = prefs.getString("poi_name", null),
                subtitle = prefs.getString("poi_subtitle", null),
                source = WaypointSource.DROPPED_PIN,
            )
            ed.putString("waypoints", Json.encodeToString(listOf(wp)))
            ed.putString("active_waypoint_id", wp.id)
        }
        ed.remove("poi_lat")
            .remove("poi_lon")
            .remove("poi_name")
            .remove("poi_subtitle")
            .putInt("prefs_version", PrefsDefaults.PREFS_VERSION)
            .apply()
    }
}

/**
 * Parses a stored `map_style` string into a [MapStyle]. The pre-v8 string
 * `LIBERTY_DARK` is mapped to [MapStyle.COLOR_DARK] for backward compatibility
 * (the v7→v8 migration rewrites the stored string, but defensive parsing in
 * the getter is still useful for any in-flight `map_style` value that hasn't
 * yet been migrated).
 */
internal fun parseMapStyle(name: String): MapStyle = when (name) {
    "LIBERTY_DARK" -> MapStyle.COLOR_DARK
    else -> MapStyle.valueOf(name)
}
