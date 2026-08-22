package ca.voiditswarranty.roadtripradar.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.PrefsDefaults
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [PreferencesRepository] — the SharedPreferences-backed repository that
 * owns the single `map_prefs` file, the 11-step migration chain, the typed property
 * accessors, and `resetToDefaults`.
 *
 * Migration tests use the production `migrateVnToVn+1(prefs: SharedPreferences)` helpers
 * (extracted from the original monolithic `migrate()` in the Phase 2 refactor) so each
 * step can be exercised in isolation with a fixture `SharedPreferences`. The other
 * tests instantiate the repo normally and round-trip values through the typed vars.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PreferencesRepositoryTest {

    private lateinit var prefs: SharedPreferences
    private val repo: PreferencesRepository by lazy {
        PreferencesRepository(ApplicationProvider.getApplicationContext(), prefsFileName = "test_map_prefs")
    }

    private fun freshRepo(prefsFileName: String = "test_map_prefs"): PreferencesRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE).edit().clear().apply()
        return PreferencesRepository(context, prefsFileName = prefsFileName)
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Use a unique prefs file per test so we don't leak state between tests.
        // Don't construct the repo here — constructing it runs migrate() and
        // stamps prefs_version=11, which would make the per-migration tests no-op.
        prefs = context.getSharedPreferences("test_map_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        // Also clear the production `map_prefs` so the freshInstall test starts clean.
        context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().also(block).apply()
    }

    // -------- Fresh install: migrate stamps the current version --------

    @Test
    fun migrate_freshInstall_stampsCurrentVersion() {
        // A fresh `map_prefs` file (no `prefs_version` key) is at v0. The constructor
        // runs `migrate()`, which walks all 11 steps and stamps `PrefsDefaults.PREFS_VERSION`.
        val r = freshRepo()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val mapPrefs = context.getSharedPreferences("test_map_prefs", Context.MODE_PRIVATE)
        assertEquals(
            "fresh install must stamp the current PREFS_VERSION",
            PrefsDefaults.PREFS_VERSION,
            mapPrefs.getInt("prefs_version", -1),
        )
        assertNotNull(r)
    }

    @Test
    fun migrate_idempotent_secondRunIsNoOp() {
        val r1 = freshRepo()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mapPrefs = context.getSharedPreferences("test_map_prefs", Context.MODE_PRIVATE)
        val firstVersion = mapPrefs.getInt("prefs_version", -1)
        val afterFirst = mapPrefs.all.toMap()

        // Constructing a second time should not change anything.
        val r2 = freshRepo()
        val afterSecond = mapPrefs.all.toMap()

        assertEquals(firstVersion, mapPrefs.getInt("prefs_version", -1))
        assertEquals("a second migrate() run must not change any keys", afterFirst, afterSecond)
        assertNotNull(r2)
    }

    // -------- Version bump: orchestrator walks all steps up to the target --------

    @Test
    fun migrate_orchestrator_fromV0_walksAllStepsToCurrent() {
        // Fresh install: prefs_version is 0. The orchestrator must walk
        // every step from V0→V1 through V10→V11, stamping the final
        // version. This is the "happy path" version-bump test: it's
        // exactly what every real install or upgrade does, but tests
        // the *orchestrator completeness* (all 11 steps wired in),
        // not just each step in isolation.
        val r = freshRepo()
        r.migrate()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val mapPrefs = context.getSharedPreferences("test_map_prefs", Context.MODE_PRIVATE)
        assertEquals(
            "orchestrator must walk all steps up to PREFS_VERSION",
            PrefsDefaults.PREFS_VERSION,
            mapPrefs.getInt("prefs_version", -1),
        )
    }

    @Test
    fun migrate_orchestrator_partialUpgrade_preservesExistingData() {
        // Simulate a user upgrading from v8 to current. v8→v11 is what
        // the orchestrator must run. The v4→v5 step removes
        // `hud_widget_size`; the v8→v9 step is a no-op; v9→v10 collapses
        // weather_mode + weather_playing; v10→v11 migrates the single
        // POI scalars into a waypoints list.
        //
        // We stamp prefs_version = 8 manually, set a known weather_mode
        // and a known widget size, then call migrate() and verify the
        // post-v8 steps ran (weather_mode collapsed, v10→v11 ran by
        // setting a single POI scalar and checking it became a
        // waypoint), while the pre-v8 key (e.g., use_metric set in
        // the fixture) was preserved unchanged.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mapPrefs = context.getSharedPreferences("test_map_prefs", Context.MODE_PRIVATE)
        mapPrefs.edit().clear().apply()
        mapPrefs.edit()
            .putInt("prefs_version", 8)
            .putString("weather_mode", "ON")
            .putBoolean("weather_playing", true)
            .putBoolean("use_metric", false)
            .apply()

        // Constructing the repo runs migrate() via init, which walks
        // V8→V9 (no-op), V9→V10 (collapses weather_mode), V10→V11.
        PreferencesRepository(context, prefsFileName = "test_map_prefs")

        // V9→V10 collapsed weather_mode + weather_playing into a
        // single tri-state. "ON" + true should map to "PLAYING".
        assertEquals("PLAYING", mapPrefs.getString("weather_mode", null))
        // weather_playing should have been removed by the v9→v10 step.
        assertFalse(mapPrefs.contains("weather_playing"))
        // Unrelated key was preserved.
        assertEquals(false, mapPrefs.getBoolean("use_metric", true))
        // Final version stamped.
        assertEquals(PrefsDefaults.PREFS_VERSION, mapPrefs.getInt("prefs_version", -1))
    }

    @Test
    fun migrate_orchestrator_alreadyAtCurrentVersion_isNoOp() {
        // If prefs_version is already at PREFS_VERSION, calling
        // migrate() must be a no-op (every per-step helper is gated on
        // "< targetVersion"). This guards against regressions where a
        // future step forgets its version guard and would re-run on
        // every startup.
        val r1 = freshRepo()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mapPrefs = context.getSharedPreferences("test_map_prefs", Context.MODE_PRIVATE)
        val afterFirst = mapPrefs.all.toMap()

        r1.migrate()

        val afterSecond = mapPrefs.all.toMap()
        assertEquals(
            "calling migrate() at current version must not change any keys",
            afterFirst,
            afterSecond,
        )
    }

    @Test
    fun migrate_orchestrator_simulatedFullUpgrade_v8ToCurrent_preservesWaypoints() {
        // End-to-end version-bump test: stamp v8, set the pre-v10
        // POI scalars (poi_lat/poi_lon/poi_name), then construct the
        // repo (which runs migrate). The v10→v11 step must migrate
        // the scalars into a one-element waypoints list.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mapPrefs = context.getSharedPreferences("test_map_prefs", Context.MODE_PRIVATE)
        mapPrefs.edit().clear().apply()
        mapPrefs.edit()
            .putInt("prefs_version", 8)
            .putString("poi_lat", "43.65")
            .putString("poi_lon", "-79.38")
            .putString("poi_name", "Coffee Shop")
            .apply()

        PreferencesRepository(context, prefsFileName = "test_map_prefs")

        // POI scalars removed.
        assertFalse(mapPrefs.contains("poi_lat"))
        assertFalse(mapPrefs.contains("poi_lon"))
        assertFalse(mapPrefs.contains("poi_name"))
        // Waypoints list has one entry with the expected fields.
        val waypointsJson = mapPrefs.getString("waypoints", "[]")
        assertNotNull(waypointsJson)
        assertTrue(
            "waypoints JSON should contain 'Coffee Shop': $waypointsJson",
            waypointsJson!!.contains("Coffee Shop"),
        )
        // active_waypoint_id was set by the migration.
        assertTrue(mapPrefs.contains("active_waypoint_id"))
        // Final version stamped.
        assertEquals(PrefsDefaults.PREFS_VERSION, mapPrefs.getInt("prefs_version", -1))
    }

    // -------- Migration step 0 → 1: collapse weather mode --------

    @Test
    fun migrateV0ToV1_legacyPaused_mapsToOn() {
        edit {
            putString("weather_mode", "PAUSED")
            putBoolean("weather_playing", true) // pre-v1 had this; v1 drops it
        }
        migrateV0ToV1(prefs)

        assertEquals("ON", prefs.getString("weather_mode", null))
        assertEquals(false, prefs.getBoolean("weather_playing", true))
        assertEquals(1, prefs.getInt("prefs_version", 0))
    }

    @Test
    fun migrateV0ToV1_legacyPlay_mapsToOnAndPlaying() {
        edit {
            putString("weather_mode", "PLAY")
        }
        migrateV0ToV1(prefs)

        assertEquals("ON", prefs.getString("weather_mode", null))
        assertEquals(true, prefs.getBoolean("weather_playing", false))
    }

    @Test
    fun migrateV0ToV1_noWeatherMode_justStampsVersion() {
        // No `weather_mode` key at all — the v1 step still stamps the version, just
        // doesn't transform any keys.
        migrateV0ToV1(prefs)

        assertEquals(1, prefs.getInt("prefs_version", 0))
    }

    // -------- Migration step 1 → 2: copy speed_size into nav/hud sizes --------

    @Test
    fun migrateV1ToV2_speedSizePresent_copiesToNavAndHud() {
        edit {
            putFloat("speed_size", 1.5f)
        }
        migrateV1ToV2(prefs)

        assertEquals(1.5f, prefs.getFloat("nav_widget_size", 0f), 0.0f)
        assertEquals(1.5f, prefs.getFloat("hud_widget_size", 0f), 0.0f)
        assertEquals(2, prefs.getInt("prefs_version", 0))
    }

    @Test
    fun migrateV1ToV2_existingNavSize_doesNotOverwrite() {
        // If `nav_widget_size` is already set, the migration must not clobber it.
        edit {
            putFloat("speed_size", 1.5f)
            putFloat("nav_widget_size", 2.5f) // already there
        }
        migrateV1ToV2(prefs)

        assertEquals(2.5f, prefs.getFloat("nav_widget_size", 0f), 0.0f)
        assertEquals(1.5f, prefs.getFloat("hud_widget_size", 0f), 0.0f) // hud still gets it
    }

    @Test
    fun migrateV1ToV2_noSpeedSize_stampsVersionOnly() {
        migrateV1ToV2(prefs)

        assertEquals(2, prefs.getInt("prefs_version", 0))
    }

    // -------- Migration step 2 → 3: copy nav_widget_size into weather_widget_size --------

    @Test
    fun migrateV2ToV3_navSizePresent_copiesToWeatherSize() {
        edit {
            putFloat("nav_widget_size", 1.7f)
        }
        migrateV2ToV3(prefs)

        assertEquals(1.7f, prefs.getFloat("weather_widget_size", 0f), 0.0f)
        assertEquals(3, prefs.getInt("prefs_version", 0))
    }

    @Test
    fun migrateV2ToV3_noNavSize_stampsVersionOnly() {
        migrateV2ToV3(prefs)

        assertEquals(3, prefs.getInt("prefs_version", 0))
    }

    // -------- Migration step 3 → 4: derive compass_widget_size --------

    @Test
    fun migrateV3ToV4_hudPresent_derivesCompassFromHud() {
        // compass_widget_size = hud * 1.5
        edit {
            putFloat("hud_widget_size", 2.0f)
        }
        migrateV3ToV4(prefs)

        assertEquals(3.0f, prefs.getFloat("compass_widget_size", 0f), 0.0f)
    }

    @Test
    fun migrateV3ToV4_noHud_fallsBackToSpeed() {
        edit {
            putFloat("speed_size", 1.4f)
        }
        migrateV3ToV4(prefs)

        assertEquals(2.1f, prefs.getFloat("compass_widget_size", 0f), 0.01f)
    }

    // -------- Migration step 4 → 5: merge hud into speed_size --------

    @Test
    fun migrateV4ToV5_hudPresent_mergesIntoSpeed() {
        edit {
            putFloat("hud_widget_size", 1.6f)
            putFloat("speed_size", 0.8f)
        }
        migrateV4ToV5(prefs)

        // speed_size takes the hud value (the comment in production says "merged speed").
        assertEquals(1.6f, prefs.getFloat("speed_size", 0f), 0.0f)
        assertFalse("hud_widget_size must be removed", prefs.contains("hud_widget_size"))
    }

    @Test
    fun migrateV4ToV5_noHud_keepsSpeed() {
        edit {
            putFloat("speed_size", 1.2f)
        }
        migrateV4ToV5(prefs)

        assertEquals(1.2f, prefs.getFloat("speed_size", 0f), 0.0f)
    }

    // -------- Migration step 5 → 6: drop POI keys --------

    @Test
    fun migrateV5ToV6_removesLegacyPoiKeys() {
        edit {
            putString("enabled_poi_categories", "fuel,restaurant")
            putBoolean("show_pois_on_map", true)
        }
        migrateV5ToV6(prefs)

        assertFalse(prefs.contains("enabled_poi_categories"))
        assertFalse(prefs.contains("show_pois_on_map"))
        assertEquals(6, prefs.getInt("prefs_version", 0))
    }

    // -------- Migration step 6 → 7: drop poi_display_mode --------

    @Test
    fun migrateV6ToV7_removesPoiDisplayMode() {
        edit {
            putString("poi_display_mode", "cluster")
        }
        migrateV6ToV7(prefs)

        assertFalse(prefs.contains("poi_display_mode"))
        assertEquals(7, prefs.getInt("prefs_version", 0))
    }

    // -------- Migration step 7 → 8: rename LIBERTY_DARK to COLOR_DARK --------

    @Test
    fun migrateV7ToV8_libertyDark_renamedToColorDark() {
        edit {
            putString("map_style", "LIBERTY_DARK")
        }
        migrateV7ToV8(prefs)

        assertEquals("COLOR_DARK", prefs.getString("map_style", null))
    }

    @Test
    fun migrateV7ToV8_otherMapStyle_unchanged() {
        edit {
            putString("map_style", "LIBERTY")
        }
        migrateV7ToV8(prefs)

        assertEquals("LIBERTY", prefs.getString("map_style", null))
    }

    @Test
    fun migrateV7ToV8_noMapStyle_justStampsVersion() {
        migrateV7ToV8(prefs)

        assertEquals(8, prefs.getInt("prefs_version", 0))
    }

    // -------- Migration step 8 → 9: no-op --------

    @Test
    fun migrateV8ToV9_stampsVersion() {
        edit {
            putString("map_style", "LIBERTY")
        }
        migrateV8ToV9(prefs)

        assertEquals(9, prefs.getInt("prefs_version", 0))
        // No key changes.
        assertEquals("LIBERTY", prefs.getString("map_style", null))
    }

    // -------- Migration step 9 → 10: collapse weather_mode into tri-state --------

    @Test
    fun migrateV9ToV10_legacyOnAndPlaying_collapsesToPlaying() {
        edit {
            putString("weather_mode", "ON")
            putBoolean("weather_playing", true)
        }
        migrateV9ToV10(prefs)

        assertEquals("PLAYING", prefs.getString("weather_mode", null))
        assertFalse(prefs.contains("weather_playing"))
    }

    @Test
    fun migrateV9ToV10_legacyOnNotPlaying_collapsesToOn() {
        edit {
            putString("weather_mode", "ON")
            putBoolean("weather_playing", false)
        }
        migrateV9ToV10(prefs)

        assertEquals("ON", prefs.getString("weather_mode", null))
    }

    @Test
    fun migrateV9ToV10_legacyOff_collapsesToOff() {
        edit {
            putString("weather_mode", "OFF")
        }
        migrateV9ToV10(prefs)

        assertEquals("OFF", prefs.getString("weather_mode", null))
    }

    @Test
    fun migrateV9ToV10_noWeatherMode_removesPlayingAndStamps() {
        edit {
            putBoolean("weather_playing", true)
        }
        migrateV9ToV10(prefs)

        assertFalse(prefs.contains("weather_playing"))
    }

    // -------- Migration step 10 → 11: collapse single POI scalar into waypoint --------

    @Test
    fun migrateV10ToV11_legacyPoiScalars_promoteToWaypoint() {
        edit {
            putString("poi_lat", "45.4")
            putString("poi_lon", "-75.7")
            putString("poi_name", "Coffee Shop")
            putString("poi_subtitle", "Main St")
        }
        migrateV10ToV11(prefs)

        // Scalars are removed.
        assertFalse(prefs.contains("poi_lat"))
        assertFalse(prefs.contains("poi_lon"))
        assertFalse(prefs.contains("poi_name"))
        assertFalse(prefs.contains("poi_subtitle"))

        // A waypoint was added, and the active_waypoint_id points at it. Read
        // back through a fresh repo against the same prefs file.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val r = PreferencesRepository(context, prefsFileName = "test_map_prefs")
        val waypoints = r.waypoints
        assertEquals(1, waypoints.size)
        assertEquals("Coffee Shop", waypoints[0].name)
        assertEquals("Main St", waypoints[0].subtitle)
        assertEquals(45.4, waypoints[0].lat, 0.0)
        assertEquals(-75.7, waypoints[0].lon, 0.0)

        val activeId = prefs.getString("active_waypoint_id", null)
        assertEquals("active_waypoint_id must point at the new waypoint", waypoints[0].id, activeId)
    }

    @Test
    fun migrateV10ToV11_noLegacyScalars_justStampsVersion() {
        migrateV10ToV11(prefs)

        assertNull(prefs.getString("waypoints", null))
        assertEquals(PrefsDefaults.PREFS_VERSION, prefs.getInt("prefs_version", 0))
    }

    // -------- parseMapStyle (promoted to internal) --------

    @Test
    fun parseMapStyle_liberty_returnsLiberty() {
        assertEquals(MapStyle.LIBERTY, parseMapStyle("LIBERTY"))
    }

    @Test
    fun parseMapStyle_colorDark_returnsColorDark() {
        assertEquals(MapStyle.COLOR_DARK, parseMapStyle("COLOR_DARK"))
    }

    @Test
    fun parseMapStyle_legacyLibertyDark_renamedToColorDark() {
        // The pre-v8 string is mapped to COLOR_DARK for backward compatibility.
        assertEquals(MapStyle.COLOR_DARK, parseMapStyle("LIBERTY_DARK"))
    }

    @Test
    fun parseMapStyle_invalidName_throws() {
        // parseMapStyle is strict — callers (the `mapStyle` getter) catch the
        // IllegalArgumentException and fall back to the default.
        try {
            parseMapStyle("NOPE")
            assertTrue("expected throw on invalid name", false)
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    // -------- mapStyle getter: round-trip + fallback --------

    @Test
    fun mapStyle_getReturnsDefaultWhenUnset() {
        // Robolectric defaults to light mode → AUTO maps to LIBERTY.
        assertEquals(MapStyle.AUTO, repo.mapStyle)
    }

    @Test
    fun mapStyle_setThenGet_returnsSet() {
        repo.mapStyle = MapStyle.COLOR_DARK
        assertEquals(MapStyle.COLOR_DARK, repo.mapStyle)
    }

    @Test
    fun mapStyle_invalidStoredValue_fallsBackToDefault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("test_map_prefs", Context.MODE_PRIVATE).edit()
            .putString("map_style", "NOPE")
            .apply()
        val r = PreferencesRepository(context, prefsFileName = "test_map_prefs")
        assertEquals(MapStyle.AUTO, r.mapStyle)
    }

    // -------- Typed property defaults --------

    @Test
    fun zoomLevel_defaultMatchesPrefsDefault() {
        assertEquals(PrefsDefaults.ZOOM_LEVEL, repo.zoomLevel, 0.0f)
    }

    @Test
    fun radarOpacity_defaultMatchesPrefsDefault() {
        assertEquals(PrefsDefaults.RADAR_OPACITY, repo.radarOpacity, 0.0f)
    }

    @Test
    fun useMetric_defaultMatchesPrefsDefault() {
        assertEquals(PrefsDefaults.USE_METRIC, repo.useMetric)
    }

    @Test
    fun weatherMode_defaultMatchesPrefsDefault() {
        assertEquals(WeatherMode.valueOf(PrefsDefaults.WEATHER_MODE), repo.weatherMode)
    }

    @Test
    fun windSpeedUnit_defaultMatchesPrefsDefault() {
        assertEquals(WindSpeedUnit.valueOf(PrefsDefaults.WIND_SPEED_UNIT), repo.windSpeedUnit)
    }

    @Test
    fun temperatureUnit_defaultMatchesPrefsDefault() {
        assertEquals(TemperatureUnit.valueOf(PrefsDefaults.TEMPERATURE_UNIT), repo.temperatureUnit)
    }

    // -------- Round-trips --------

    @Test
    fun showLegend_setThenGet_returnsSet() {
        repo.showLegend = false
        assertEquals(false, repo.showLegend)
    }

    @Test
    fun useMetric_setThenGet_returnsSet() {
        repo.useMetric = false
        assertEquals(false, repo.useMetric)
    }

    @Test
    fun autoAdvanceThresholdMeters_setThenGet_returnsSet() {
        repo.autoAdvanceThresholdMeters = 200
        assertEquals(200, repo.autoAdvanceThresholdMeters)
    }

    @Test
    fun completedTutorialGroups_setThenGet_returnsSet() {
        repo.completedTutorialGroups = setOf("intro", "weather")
        assertEquals(setOf("intro", "weather"), repo.completedTutorialGroups)
    }

    @Test
    fun enabledPoiCategories_setThenGet_returnsSet() {
        repo.enabledPoiCategories = setOf("fuel", "restaurant")
        assertEquals(setOf("fuel", "restaurant"), repo.enabledPoiCategories)
    }

    @Test
    fun waypoints_setThenGet_returnsSet() {
        val wp = Waypoint(
            id = "wp-1",
            lat = 45.4,
            lon = -75.7,
            name = "Test",
            source = WaypointSource.DROPPED_PIN,
        )
        repo.waypoints = listOf(wp)
        assertEquals(1, repo.waypoints.size)
        assertEquals("wp-1", repo.waypoints[0].id)
        assertEquals(45.4, repo.waypoints[0].lat, 0.0)
    }

    @Test
    fun activeWaypointId_setThenGet_returnsSet() {
        repo.activeWaypointId = "wp-1"
        assertEquals("wp-1", repo.activeWaypointId)
    }

    @Test
    fun activeWaypointId_setToNull_removesKey() {
        repo.activeWaypointId = "wp-1"
        repo.activeWaypointId = null
        assertNull(repo.activeWaypointId)
    }

    // -------- resetToDefaults --------

    @Test
    fun resetToDefaults_clearsAllKnownKeys() {
        // Set every override-able key to a non-default value.
        repo.mapStyle = MapStyle.COLOR_DARK
        repo.zoomLevel = 99.0f
        repo.weatherMode = WeatherMode.PLAYING
        repo.showLegend = false
        repo.showTimeline = false
        repo.radarOpacity = 0.1f
        repo.useMetric = false
        repo.speedSize = 9.0f
        repo.navWidgetSize = 9.0f
        repo.weatherWidgetSize = 9.0f
        repo.compassWidgetSize = 9.0f
        repo.windEnabled = false
        repo.windSpeedUnit = WindSpeedUnit.MPH
        repo.temperatureUnit = TemperatureUnit.FAHRENHEIT
        repo.poiIconOpacity = 0.1f
        repo.keepScreenOn = false
        repo.autostartPoiLoadingOnLaunch = false
        repo.useGps = false
        repo.gpsIconOpacity = 0.1f
        repo.waypoints = listOf(
            Waypoint(id = "wp-1", lat = 0.0, lon = 0.0, name = "x", source = WaypointSource.DROPPED_PIN),
        )
        repo.activeWaypointId = "wp-1"

        repo.resetToDefaults(MapStyle.LIBERTY)

        // The key-deleted ones should be gone; the value-set ones should be back to defaults.
        assertEquals(MapStyle.LIBERTY, repo.mapStyle)
        assertEquals(PrefsDefaults.ZOOM_LEVEL, repo.zoomLevel, 0.0f)
        assertEquals(WeatherMode.valueOf(PrefsDefaults.WEATHER_MODE), repo.weatherMode)
        assertEquals(PrefsDefaults.SHOW_LEGEND, repo.showLegend)
        assertEquals(PrefsDefaults.SHOW_TIMELINE, repo.showTimeline)
        assertEquals(PrefsDefaults.RADAR_OPACITY, repo.radarOpacity, 0.0f)
        assertEquals(PrefsDefaults.USE_METRIC, repo.useMetric)
        assertEquals(PrefsDefaults.SPEED_SIZE, repo.speedSize, 0.0f)
        assertEquals(PrefsDefaults.NAV_WIDGET_SIZE, repo.navWidgetSize, 0.0f)
        assertEquals(PrefsDefaults.WEATHER_WIDGET_SIZE, repo.weatherWidgetSize, 0.0f)
        assertEquals(PrefsDefaults.COMPASS_WIDGET_SIZE, repo.compassWidgetSize, 0.0f)
        assertEquals(PrefsDefaults.WIND_ENABLED, repo.windEnabled)
        assertEquals(WindSpeedUnit.valueOf(PrefsDefaults.WIND_SPEED_UNIT), repo.windSpeedUnit)
        assertEquals(TemperatureUnit.valueOf(PrefsDefaults.TEMPERATURE_UNIT), repo.temperatureUnit)
        assertEquals(PrefsDefaults.POI_ICON_OPACITY, repo.poiIconOpacity, 0.0f)
        assertEquals(PrefsDefaults.KEEP_SCREEN_ON, repo.keepScreenOn)
        assertEquals(PrefsDefaults.AUTOSTART_POI_LOADING_ON_LAUNCH, repo.autostartPoiLoadingOnLaunch)
        assertEquals(PrefsDefaults.USE_GPS, repo.useGps)
        assertEquals(PrefsDefaults.GPS_ICON_OPACITY, repo.gpsIconOpacity, 0.0f)
        assertTrue("waypoints must be cleared", repo.waypoints.isEmpty())
        assertNull("active_waypoint_id must be cleared", repo.activeWaypointId)
    }

    // -------- defaultMapStyleFor (companion) --------

    @Test
    fun defaultMapStyleFor_returnsAuto() {
        // The companion returns AUTO unconditionally; the resolver (MapStyleResolution)
        // collapses it to LIBERTY/COLOR_DARK/CUSTOM_* at read time.
        assertEquals(MapStyle.AUTO, PreferencesRepository.defaultMapStyleFor(ApplicationProvider.getApplicationContext()))
    }

    // -------- acceptedTermsVersion --------

    @Test
    fun acceptedTermsVersion_defaultIsMinusOne() {
        assertEquals(-1, repo.acceptedTermsVersion)
    }

    @Test
    fun acceptedTermsVersion_setThenGet_returnsSet() {
        repo.acceptedTermsVersion = 3
        assertEquals(3, repo.acceptedTermsVersion)
    }

    // -------- lastKnownPosition --------

    @Test
    fun lastKnownPosition_setThenGet_returnsSet() {
        val p = org.maplibre.spatialk.geojson.Position(latitude = 45.4, longitude = -75.7)
        repo.lastKnownPosition = p
        assertEquals(45.4, repo.lastKnownPosition.latitude, 0.0001)
        assertEquals(-75.7, repo.lastKnownPosition.longitude, 0.0001)
    }
}
