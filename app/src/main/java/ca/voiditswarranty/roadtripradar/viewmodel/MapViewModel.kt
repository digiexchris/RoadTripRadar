package ca.voiditswarranty.roadtripradar.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.voiditswarranty.roadtripradar.BuildConfig
import ca.voiditswarranty.roadtripradar.data.ChangelogRepository
import ca.voiditswarranty.roadtripradar.data.CustomThemeRepository
import ca.voiditswarranty.roadtripradar.data.InvalidStyleJsonException
import ca.voiditswarranty.roadtripradar.data.GeocodingRepository
import ca.voiditswarranty.roadtripradar.data.OpenMeteoRepository
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.data.PostpassRepository
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository
import ca.voiditswarranty.roadtripradar.data.TileFetchResult
import ca.voiditswarranty.roadtripradar.data.ViewBox
import ca.voiditswarranty.roadtripradar.data.WeatherRepository
import ca.voiditswarranty.roadtripradar.model.ChangelogRelease
import ca.voiditswarranty.roadtripradar.model.MAX_POI_CATEGORIES
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.NetworkStatus
import ca.voiditswarranty.roadtripradar.model.NetworkTransport
import ca.voiditswarranty.roadtripradar.model.PoiCategory
import ca.voiditswarranty.roadtripradar.model.PoiGridCell
import ca.voiditswarranty.roadtripradar.model.PrefsDefaults
import ca.voiditswarranty.roadtripradar.model.PoiViewportChunks
import ca.voiditswarranty.roadtripradar.model.SearchResult
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.turf.measurement.distance
import org.maplibre.spatialk.units.extensions.inMeters
import kotlin.math.max
import kotlin.math.min

class MapViewModel(
    private val appContext: Context,
    val prefsRepo: PreferencesRepository,
    val customThemeRepo: CustomThemeRepository = CustomThemeRepository(appContext),
    private val weatherRepo: WeatherRepository = WeatherRepository(),
    private val openMeteoRepo: OpenMeteoRepository = OpenMeteoRepository(),
    val postpassRepo: PostpassRepository = PostpassRepository(),
) : ViewModel() {
    private val geocodingRepo: GeocodingRepository = GeocodingRepository()

    // Weather
    var weatherMode by mutableStateOf(prefsRepo.weatherMode)
        private set
    var showLegend by mutableStateOf(prefsRepo.showLegend)
        private set
    var showTimeline by mutableStateOf(prefsRepo.showTimeline)
        private set
    var radarFramePaths by mutableStateOf(emptyList<String>())
        private set
    var radarFrameTimes by mutableStateOf(emptyList<Long>())
        private set
    var currentFrameIndex by mutableStateOf(0)
        private set
    var radarOpacity by mutableStateOf(prefsRepo.radarOpacity)
        private set

    /** Open-Meteo (temperature, WMO code, wind); updated by [setLocalWeatherAnchor] polling. */
    var openMeteoSnapshot by mutableStateOf<OpenMeteoSnapshot?>(null)
        private set

    // Settings
    var useMetric by mutableStateOf(prefsRepo.useMetric)
        private set
    var speedSize by mutableStateOf(prefsRepo.speedSize)
        private set
    var navWidgetSize by mutableStateOf(prefsRepo.navWidgetSize)
        private set
    var weatherWidgetSize by mutableStateOf(prefsRepo.weatherWidgetSize)
        private set
    var compassWidgetSize by mutableStateOf(prefsRepo.compassWidgetSize)
        private set
    var windEnabled by mutableStateOf(prefsRepo.windEnabled)
        private set
    var windSpeedUnit by mutableStateOf(prefsRepo.windSpeedUnit)
        private set
    var temperatureUnit by mutableStateOf(prefsRepo.temperatureUnit)
        private set
    var poiIconOpacity by mutableStateOf(prefsRepo.poiIconOpacity)
        private set
    var keepScreenOn by mutableStateOf(prefsRepo.keepScreenOn)
        private set
    var autostartPoiLoadingOnLaunch by mutableStateOf(prefsRepo.autostartPoiLoadingOnLaunch)
        private set
    var useGps by mutableStateOf(prefsRepo.useGps)
        private set
    var gpsIconOpacity by mutableStateOf(prefsRepo.gpsIconOpacity)
        private set
    var mapCenterOffsetPortraitFraction by mutableStateOf(prefsRepo.mapCenterOffsetPortraitFraction)
        private set
    var mapCenterOffsetLandscapeFraction by mutableStateOf(prefsRepo.mapCenterOffsetLandscapeFraction)
        private set

    // Network
    var networkStatus by mutableStateOf(NetworkStatus())
        private set

    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
                else -> null
            }
            networkStatus = NetworkStatus(
                transport = transport,
                validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                connected = true,
            )
        }

        override fun onLost(network: Network) {
            networkStatus = NetworkStatus()
        }
    }

    // POI
    var poiPosition by mutableStateOf(prefsRepo.poiPosition)
        private set
    var poiName by mutableStateOf(
        prefsRepo.poiName ?: if (prefsRepo.poiPosition != null) "Dropped Pin" else null
    )
        private set

    // Nearby POIs — cell-based pipeline
    data class CachedCell(
        val cellId: String,
        val features: List<Feature<Point, JsonObject>>,
        val fetchedAtMs: Long,
        val bounds: BoundingBox,
    )

    private data class PendingCell(
        val cell: PoiGridCell,
        val retryCount: Int = 0,
    )

    private val cellCache = mutableMapOf<String, CachedCell>()
    private val pendingCells = mutableListOf<PendingCell>()
    private val inFlightCells = mutableSetOf<String>()
    private val permanentlyFailedCells = mutableListOf<PoiGridCell>()
    private val workSignal = Channel<Unit>(Channel.CONFLATED)

    var enabledPoiCategories by mutableStateOf(prefsRepo.enabledPoiCategories)
        private set

    var showPoiCategoryPicker by mutableStateOf(false)
        private set
    var poiCategoriesVersion by mutableStateOf(0)
        private set
    var tappedPoi by mutableStateOf<TappedPoiInfo?>(null)
        private set

    /** True when the cell pipeline has been activated by the user (Search Visible Area / Refresh / autostart). */
    var poiPipelineActive by mutableStateOf(false)
        private set

    private var autostartPoiLoadAppliedThisSession = false

    var cellsRemaining by mutableIntStateOf(0)
        private set

    val isLoadingPois: Boolean
        get() = cellsRemaining > 0

    var failedCellBounds by mutableStateOf<List<BoundingBox>>(emptyList())
        private set

    val hasFailedCells: Boolean
        get() = failedCellBounds.isNotEmpty()

    private fun updateCellCounters() {
        cellsRemaining = synchronized(pendingCells) { pendingCells.size } +
            synchronized(inFlightCells) { inFlightCells.size }
        failedCellBounds = synchronized(permanentlyFailedCells) { permanentlyFailedCells.map { it.bounds } }
    }

    /** Merged features from all cached cells. */
    var nearbyPoiFeatures by mutableStateOf<FeatureCollection<Point, JsonObject>>(FeatureCollection(emptyList()))
        private set
    var poiFetchRegion: BoundingBox? = null
        private set

    /** Union bounding box of all cached cells (shown as dotted outline). */
    var poiLoadBounds: BoundingBox? by mutableStateOf(null)
        private set

    val hasNearbyPoiFeatures: Boolean
        get() = nearbyPoiFeatures.features.isNotEmpty()

    private companion object {
        const val WORKER_COUNT = 4
        const val MAX_CELL_RETRIES = 3
        const val MAX_INTER_CELL_DELAY_MS = 2_000L
        const val RADAR_POLL_MS = 60_000L
        const val LOCAL_WEATHER_SUCCESS_MS = 600_000L
        const val LOCAL_WEATHER_RETRY_MS = 60_000L
    }

    data class TappedPoiInfo(
        val name: String,
        val subtitle: String,
        val categoryLabel: String,
        val iconName: String,
        val position: Position,
        val openingHours: String? = null,
    )

    // UI state
    var isTrackingCamera by mutableStateOf(true)
    var isNorthUp by mutableStateOf(!prefsRepo.useGps)
    var showActionsDrawer by mutableStateOf(false)
        private set
    var showResetConfirm by mutableStateOf(false)
        private set
    var showPoiSearch by mutableStateOf(false)
        private set
    var showLegendDetail by mutableStateOf(false)
        private set
    var showWhatsNewChangelog by mutableStateOf(false)
        private set
    var whatsNewChangelogReleases by mutableStateOf<List<ChangelogRelease>>(emptyList())
        private set
    var showFullChangelog by mutableStateOf(false)
        private set
    var fullChangelogReleases by mutableStateOf<List<ChangelogRelease>>(emptyList())
        private set
    var showTerms by mutableStateOf(false)
        private set
    var termsNeedAcceptance by mutableStateOf(false)
        private set

    // Theme selector
    var showThemeSelector by mutableStateOf(false)
        private set

    /** Increments whenever a custom theme is written/deleted, forcing a map style reload. */
    var customThemeVersion by mutableIntStateOf(0)
        private set

    var customLightAutoEnabled by mutableStateOf(prefsRepo.customLightAutoEnabled)
        private set
    var customDarkAutoEnabled by mutableStateOf(prefsRepo.customDarkAutoEnabled)
        private set

    /** Tracks whether custom theme files exist; re-read after import/delete. */
    var hasCustomLight by mutableStateOf(customThemeRepo.hasCustomLight())
        private set
    var hasCustomDark by mutableStateOf(customThemeRepo.hasCustomDark())
        private set

    /** Non-null when a theme import failed; cleared by [dismissCustomThemeImportError]. */
    var customThemeImportError: String? by mutableStateOf(null)
        private set

    // Search state
    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf(emptyList<SearchResult>())
        private set
    var isSearching by mutableStateOf(false)
        private set

    val weatherActive get() = weatherMode != WeatherMode.OFF
    val isWeatherPlaying get() = weatherMode == WeatherMode.PLAYING

    private var lastGenerated = 0L
    private var localWeatherAnchor: Position? = null
    private var weatherPollingJob: Job? = null
    private var localWeatherPollJob: Job? = null
    private var weatherAnimationJob: Job? = null
    private var searchJob: Job? = null
    private var cellWorkerJob: Job? = null

    init {
        if (prefsRepo.acceptedTermsVersion != PrefsDefaults.TERMS_VERSION) {
            showTerms = true
            termsNeedAcceptance = true
        }
        startWeatherPollingIfActive()
        startWeatherAnimationIfPlaying()
        startLocalWeatherPolling()
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    fun setLocalWeatherAnchor(position: Position?) {
        localWeatherAnchor = position
    }

    override fun onCleared() {
        super.onCleared()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    // --- Weather ---

    fun updateWeatherMode(mode: WeatherMode) {
        weatherMode = mode
        prefsRepo.weatherMode = mode
        if (mode != WeatherMode.PLAYING) {
            currentFrameIndex = radarFramePaths.lastIndex.coerceAtLeast(0)
        }
        startWeatherPollingIfActive()
        startWeatherAnimationIfPlaying()
    }

    fun cycleWeatherMode() {
        val next = when (weatherMode) {
            WeatherMode.OFF -> WeatherMode.PLAYING
            WeatherMode.PLAYING -> WeatherMode.ON
            WeatherMode.ON -> WeatherMode.OFF
        }
        updateWeatherMode(next)
    }

    fun toggleWeatherPlayPause() {
        val next = when (weatherMode) {
            WeatherMode.OFF -> WeatherMode.PLAYING
            WeatherMode.PLAYING -> WeatherMode.ON
            WeatherMode.ON -> WeatherMode.PLAYING
        }
        updateWeatherMode(next)
    }

    fun turnOffWeather() {
        updateWeatherMode(WeatherMode.OFF)
    }

    fun toggleWeatherOnOff() {
        if (weatherActive) {
            updateWeatherMode(WeatherMode.OFF)
        } else {
            updateWeatherMode(WeatherMode.PLAYING)
        }
    }

    fun updateShowLegend(show: Boolean) {
        showLegend = show
        prefsRepo.showLegend = show
    }

    fun updateShowTimeline(show: Boolean) {
        showTimeline = show
        prefsRepo.showTimeline = show
    }

    fun updateRadarOpacity(opacity: Float) {
        radarOpacity = opacity
    }

    fun saveRadarOpacity() {
        prefsRepo.radarOpacity = radarOpacity
    }

    private fun startWeatherPollingIfActive() {
        weatherPollingJob?.cancel()
        if (!weatherActive) return
        weatherPollingJob = viewModelScope.launch {
            while (true) {
                val data = weatherRepo.fetchFrames(lastGenerated)
                if (data != null) {
                    lastGenerated = data.generated
                    radarFramePaths = data.paths
                    radarFrameTimes = data.times
                    currentFrameIndex = radarFramePaths.lastIndex.coerceAtLeast(0)
                }
                delay(RADAR_POLL_MS)
            }
        }
    }

    private fun startLocalWeatherPolling() {
        localWeatherPollJob?.cancel()
        localWeatherPollJob = viewModelScope.launch {
            while (true) {
                val pos = localWeatherAnchor
                if (pos == null) {
                    delay(2_000L)
                    continue
                }
                openMeteoRepo.fetchCurrent(pos.latitude, pos.longitude)
                    .onSuccess { snap ->
                        openMeteoSnapshot = snap
                        delay(LOCAL_WEATHER_SUCCESS_MS)
                    }
                    .onFailure {
                        delay(LOCAL_WEATHER_RETRY_MS)
                    }
            }
        }
    }

    private fun startWeatherAnimationIfPlaying() {
        weatherAnimationJob?.cancel()
        if (weatherMode != WeatherMode.PLAYING) return
        weatherAnimationJob = viewModelScope.launch {
            while (true) {
                delay(500)
                if (radarFramePaths.isNotEmpty()) {
                    currentFrameIndex = (currentFrameIndex + 1) % radarFramePaths.size
                }
            }
        }
    }

    // --- Settings ---

    fun updateUseMetric(metric: Boolean) {
        useMetric = metric
        prefsRepo.useMetric = metric
    }

    fun updateSpeedSize(size: Float) {
        speedSize = size
    }

    fun saveSpeedSize() {
        prefsRepo.speedSize = speedSize
    }

    fun updateNavWidgetSize(size: Float) {
        navWidgetSize = size
    }

    fun saveNavWidgetSize() {
        prefsRepo.navWidgetSize = navWidgetSize
    }

    fun updateWeatherWidgetSize(size: Float) {
        weatherWidgetSize = size
    }

    fun saveWeatherWidgetSize() {
        prefsRepo.weatherWidgetSize = weatherWidgetSize
    }

    fun updateCompassWidgetSize(size: Float) {
        compassWidgetSize = size
    }

    fun saveCompassWidgetSize() {
        prefsRepo.compassWidgetSize = compassWidgetSize
    }

    fun updateWindEnabled(on: Boolean) {
        windEnabled = on
        prefsRepo.windEnabled = on
    }

    fun updateWindSpeedUnit(unit: WindSpeedUnit) {
        windSpeedUnit = unit
        prefsRepo.windSpeedUnit = unit
    }

    fun updateTemperatureUnit(unit: TemperatureUnit) {
        temperatureUnit = unit
        prefsRepo.temperatureUnit = unit
    }

    fun updatePoiIconOpacity(opacity: Float) {
        poiIconOpacity = opacity
    }

    fun savePoiIconOpacity() {
        prefsRepo.poiIconOpacity = poiIconOpacity
    }

    fun updateKeepScreenOn(on: Boolean) {
        keepScreenOn = on
        prefsRepo.keepScreenOn = on
    }

    fun updateAutostartPoiLoadingOnLaunch(on: Boolean) {
        autostartPoiLoadingOnLaunch = on
        prefsRepo.autostartPoiLoadingOnLaunch = on
    }

    /**
     * Runs once per app process when the map has a camera position and the user has POI categories selected.
     * Called from [MapScreen] after [pendingCameraInfo] is first available.
     */
    fun tryAutostartPoiPipelineIfNeeded() {
        if (autostartPoiLoadAppliedThisSession) return
        if (!autostartPoiLoadingOnLaunch) return
        if (enabledPoiCategories.isEmpty()) return
        if (pendingCameraInfo == null) return
        autostartPoiLoadAppliedThisSession = true
        searchVisibleArea()
    }

    fun updateUseGps(on: Boolean) {
        useGps = on
        prefsRepo.useGps = on
        if (!on) {
            isTrackingCamera = false
        } else {
            isTrackingCamera = true
        }
    }

    fun updateGpsIconOpacity(opacity: Float) {
        gpsIconOpacity = opacity
    }

    fun saveGpsIconOpacity() {
        prefsRepo.gpsIconOpacity = gpsIconOpacity
    }

    fun updateMapCenterOffsetPortraitFraction(value: Float) {
        mapCenterOffsetPortraitFraction = value
    }

    fun saveMapCenterOffsetPortraitFraction() {
        prefsRepo.mapCenterOffsetPortraitFraction = mapCenterOffsetPortraitFraction
    }

    fun updateMapCenterOffsetLandscapeFraction(value: Float) {
        mapCenterOffsetLandscapeFraction = value
    }

    fun saveMapCenterOffsetLandscapeFraction() {
        prefsRepo.mapCenterOffsetLandscapeFraction = mapCenterOffsetLandscapeFraction
    }

    fun saveLastKnownPosition(pos: Position) {
        prefsRepo.lastKnownPosition = pos
    }

    fun openActionsDrawer() { showActionsDrawer = true }
    fun closeActionsDrawer() { showActionsDrawer = false }
    fun openResetConfirm() { showResetConfirm = true }
    fun closeResetConfirm() { showResetConfirm = false }
    fun openPoiSearch() { showPoiSearch = true }
    fun closePoiSearch() { showPoiSearch = false }
    fun openLegendDetail() {
        closeActionsDrawer()
        showLegendDetail = true
    }
    fun closeLegendDetail() { showLegendDetail = false }

    fun evaluateWhatsNewChangelog() {
        val bundle = ChangelogRepository.loadBundled(appContext) ?: return
        val last = prefsRepo.getLastSeenChangelogVersionCode()
        val current = BuildConfig.VERSION_CODE
        if (last == null) {
            prefsRepo.setLastSeenChangelogVersionCode(current)
            return
        }
        if (current <= last) return
        val newReleases = ChangelogRepository.releasesNewSince(bundle, last, current)
        if (newReleases.isEmpty()) {
            prefsRepo.setLastSeenChangelogVersionCode(current)
            return
        }
        whatsNewChangelogReleases = newReleases
        showWhatsNewChangelog = true
    }

    fun dismissWhatsNewChangelog() {
        prefsRepo.setLastSeenChangelogVersionCode(BuildConfig.VERSION_CODE)
        showWhatsNewChangelog = false
        whatsNewChangelogReleases = emptyList()
    }

    /**
     * Debug only: sets last-seen changelog code to [versionCode] - 1 and runs [evaluateWhatsNewChangelog]
     * so the What's New sheet appears without a new release tag. Requires a bundled release entry
     * with versionCode in (lastSeen, current].
     */
    fun debugPreviewWhatsNewChangelog() {
        if (!BuildConfig.DEBUG) return
        closeActionsDrawer()
        val current = BuildConfig.VERSION_CODE
        prefsRepo.setLastSeenChangelogVersionCode((current - 1).coerceAtLeast(0))
        evaluateWhatsNewChangelog()
    }

    fun openFullChangelog() {
        val bundle = ChangelogRepository.loadBundled(appContext)
        fullChangelogReleases =
            bundle?.let { ChangelogRepository.allReleasesSortedNewestFirst(it) } ?: emptyList()
        closeActionsDrawer()
        showFullChangelog = true
    }

    fun closeFullChangelog() {
        showFullChangelog = false
        fullChangelogReleases = emptyList()
    }

    fun viewTerms() {
        closeActionsDrawer()
        showTerms = true
    }

    fun acceptTerms() {
        prefsRepo.acceptedTermsVersion = PrefsDefaults.TERMS_VERSION
        termsNeedAcceptance = false
        showTerms = false
    }

    fun dismissTerms() {
        showTerms = false
    }

    // --- Theme selector ---

    fun openThemeSelector() {
        closeActionsDrawer()
        showThemeSelector = true
    }

    fun closeThemeSelector() {
        showThemeSelector = false
    }

    fun updateCustomLightAutoEnabled(enabled: Boolean) {
        customLightAutoEnabled = enabled
        prefsRepo.customLightAutoEnabled = enabled
    }

    fun updateCustomDarkAutoEnabled(enabled: Boolean) {
        customDarkAutoEnabled = enabled
        prefsRepo.customDarkAutoEnabled = enabled
    }

    fun deleteCustomTheme(style: MapStyle, currentStyle: MapStyle, onStyleChange: (MapStyle) -> Unit) {
        customThemeRepo.deleteTheme(style)
        refreshCustomThemeState()
        // If the deleted theme was active, fall back to built-in equivalent
        if (currentStyle == style) {
            val fallback = if (style == MapStyle.CUSTOM_LIGHT) MapStyle.LIBERTY else MapStyle.COLOR_DARK
            onStyleChange(fallback)
        }
    }

    fun importCustomTheme(uri: android.net.Uri, target: MapStyle, onStyleChange: (MapStyle) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                customThemeRepo.importTheme(uri, target)
                withContext(Dispatchers.Main) {
                    refreshCustomThemeState()
                    customThemeVersion++
                    onStyleChange(target)
                }
            } catch (e: InvalidStyleJsonException) {
                withContext(Dispatchers.Main) {
                    customThemeImportError = e.message
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    customThemeImportError = "Import failed: ${e.message}"
                }
            }
        }
    }

    fun dismissCustomThemeImportError() {
        customThemeImportError = null
    }

    /** Creates a custom theme by copying a bundled [source] asset into the [target] custom slot. */
    fun initCustomThemeFromAsset(source: MapStyle, target: MapStyle, onStyleChange: (MapStyle) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            customThemeRepo.initFromAsset(source, target, appContext.assets)
            withContext(Dispatchers.Main) {
                refreshCustomThemeState()
                customThemeVersion++
                onStyleChange(target)
            }
        }
    }

    /** Downloads a built-in remote style JSON and saves it to a custom slot. */
    fun initCustomThemeFromUrl(url: String, target: MapStyle, onStyleChange: (MapStyle) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val body = java.net.URL(url).readText()
                customThemeRepo.writeThemeJson(target, body)
                withContext(Dispatchers.Main) {
                    refreshCustomThemeState()
                    customThemeVersion++
                    onStyleChange(target)
                }
            } catch (_: Exception) {
                // Silently fail — theme stays as-is
            }
        }
    }

    fun notifyCustomThemeUpdated() {
        customThemeVersion++
    }

    private fun refreshCustomThemeState() {
        hasCustomLight = customThemeRepo.hasCustomLight()
        hasCustomDark = customThemeRepo.hasCustomDark()
    }

    // --- POI ---

    fun setPoiFromLongPress(position: Position) {
        poiPosition = position
        poiName = "Dropped Pin"
        persistPoi()
    }

    fun setPoiFromSearch(position: Position, name: String) {
        poiPosition = position
        poiName = name
        persistPoi()
        showPoiSearch = false
    }

    fun clearPoi() {
        poiPosition = null
        poiName = null
        persistPoi()
    }

    private fun persistPoi() {
        prefsRepo.poiPosition = poiPosition
        prefsRepo.poiName = poiName
    }

    // --- Nearby POIs (cell-based pipeline) ---

    fun togglePoiCategory(category: PoiCategory) {
        val current = enabledPoiCategories.toMutableSet()
        if (current.contains(category.query)) {
            current.remove(category.query)
        } else {
            if (current.size >= MAX_POI_CATEGORIES) return
            current.add(category.query)
        }
        enabledPoiCategories = current
        prefsRepo.enabledPoiCategories = current
        poiCategoriesVersion++
        if (poiPipelineActive) {
            if (enabledPoiCategories.isEmpty()) {
                clearNearbyPois()
            } else {
                cellCache.clear()
                synchronized(pendingCells) { pendingCells.clear() }
                synchronized(permanentlyFailedCells) { permanentlyFailedCells.clear() }
                nearbyPoiFeatures = FeatureCollection(emptyList())
                enqueueCellsForCurrentViewport()
                updateCellCounters()
            }
        }
    }

    fun searchVisibleArea() {
        if (enabledPoiCategories.isEmpty()) return
        val cam = pendingCameraInfo ?: return
        val viewBounds = viewportBoundsForPoi(cam.lat, cam.lon, cam.zoom)
        val cameraTarget = Position(latitude = cam.lat, longitude = cam.lon)
        val (loadBounds, cells) = PoiViewportChunks.gridCellsForManualLoad(
            viewBounds, cameraTarget, poiBoxBottomFraction, cam.bearing,
        )
        val centerLat = (viewBounds.southwest.latitude + viewBounds.northeast.latitude) / 2.0
        val viewHeightKm = (viewBounds.northeast.latitude - viewBounds.southwest.latitude) * 111.0
        val viewWidthKm = (viewBounds.northeast.longitude - viewBounds.southwest.longitude) * 111.0 * kotlin.math.cos(Math.toRadians(centerLat))
        val loadHeightKm = (loadBounds.northeast.latitude - loadBounds.southwest.latitude) * 111.0
        val loadWidthKm = (loadBounds.northeast.longitude - loadBounds.southwest.longitude) * 111.0 * kotlin.math.cos(Math.toRadians(centerLat))
        android.util.Log.d("POI_DEBUG", "searchVisibleArea: zoom=${cam.zoom}, screen=${screenWidthDp.toInt()}x${screenHeightDp.toInt()}dp")
        android.util.Log.d("POI_DEBUG", "viewport: ${viewHeightKm.toInt()}km × ${viewWidthKm.toInt()}km, padded load: ${loadHeightKm.toInt()}km × ${loadWidthKm.toInt()}km, cells=${cells.size}")
        android.util.Log.d("POI_DEBUG", "loadBounds=$loadBounds")
        cellCache.clear()
        synchronized(pendingCells) { pendingCells.clear() }
        synchronized(permanentlyFailedCells) { permanentlyFailedCells.clear() }
        nearbyPoiFeatures = FeatureCollection(emptyList())
        poiFetchRegion = null
        poiLoadBounds = loadBounds
        poiPipelineActive = true
        val missingCells = PoiViewportChunks.worldGridCellsIntersecting(loadBounds)
        enqueueCells(missingCells, cameraTarget)
        startCellWorker()
        poiCategoriesVersion++
    }

    fun clearNearbyPois() {
        cellWorkerJob?.cancel()
        cellWorkerJob = null
        cellCache.clear()
        synchronized(pendingCells) { pendingCells.clear() }
        synchronized(inFlightCells) { inFlightCells.clear() }
        synchronized(permanentlyFailedCells) { permanentlyFailedCells.clear() }
        nearbyPoiFeatures = FeatureCollection(emptyList())
        poiFetchRegion = null
        poiLoadBounds = null
        poiPipelineActive = false
        poiCategoriesVersion++
        updateCellCounters()
    }

    fun openPoiCategoryPicker() { showPoiCategoryPicker = true }
    fun closePoiCategoryPicker() { showPoiCategoryPicker = false }

    fun showTappedPoi(info: TappedPoiInfo) { tappedPoi = info }
    fun dismissTappedPoi() { tappedPoi = null }

    fun navigateToTappedPoi() {
        val poi = tappedPoi ?: return
        setPoiFromSearch(poi.position, poi.name)
        tappedPoi = null
    }

    // --- Cell pipeline helpers ---

    private fun rebuildMergedFeatures() {
        val allFeatures = cellCache.values.flatMap { it.features }
        nearbyPoiFeatures = FeatureCollection(allFeatures)
        if (cellCache.isEmpty()) {
            poiFetchRegion = null
        } else {
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE
            for (cell in cellCache.values) {
                val sw = cell.bounds.southwest
                val ne = cell.bounds.northeast
                minLat = min(minLat, sw.latitude)
                maxLat = max(maxLat, ne.latitude)
                minLon = min(minLon, sw.longitude)
                maxLon = max(maxLon, ne.longitude)
            }
            poiFetchRegion = BoundingBox(
                southwest = Position(latitude = minLat, longitude = minLon),
                northeast = Position(latitude = maxLat, longitude = maxLon),
            )
        }
    }

    /** Drops cached cells that do not intersect the current POI load plate (padded viewport, max 350 km side). */
    private fun evictCachedCellsOutsideLoadPlate(loadPlate: BoundingBox) {
        val toRemove = synchronized(cellCache) {
            cellCache.entries
                .filter { (_, cached) -> !PoiViewportChunks.boundingBoxesIntersect(cached.bounds, loadPlate) }
                .map { it.key }
        }
        if (toRemove.isNotEmpty()) {
            synchronized(cellCache) { for (key in toRemove) cellCache.remove(key) }
            rebuildMergedFeatures()
        }
        synchronized(pendingCells) {
            pendingCells.removeAll { !PoiViewportChunks.boundingBoxesIntersect(it.cell.bounds, loadPlate) }
        }
        synchronized(permanentlyFailedCells) {
            permanentlyFailedCells.removeAll { !PoiViewportChunks.boundingBoxesIntersect(it.bounds, loadPlate) }
        }
        updateCellCounters()
    }

    private fun enqueueCells(cells: List<PoiGridCell>, cameraCenter: Position) {
        synchronized(pendingCells) {
            for (cell in cells) {
                if (cellCache.containsKey(cell.id)) continue
                if (synchronized(inFlightCells) { inFlightCells.contains(cell.id) }) continue
                if (pendingCells.any { it.cell.id == cell.id }) continue
                if (synchronized(permanentlyFailedCells) { permanentlyFailedCells.any { it.id == cell.id } }) continue
                pendingCells.add(PendingCell(cell))
            }
        }
        reprioritizePendingCells(cameraCenter)
        updateCellCounters()
        workSignal.trySend(Unit)
    }

    private fun reprioritizePendingCells(cameraCenter: Position) {
        synchronized(pendingCells) {
            pendingCells.sortBy { pending ->
                val c = pending.cell.bounds
                val cellCenter = Position(
                    latitude = (c.southwest.latitude + c.northeast.latitude) / 2.0,
                    longitude = (c.southwest.longitude + c.northeast.longitude) / 2.0,
                )
                distance(cameraCenter, cellCenter).inMeters
            }
        }
    }

    private fun startCellWorker() {
        if (cellWorkerJob?.isActive == true) return
        cellWorkerJob = viewModelScope.launch {
            for (signal in workSignal) {
                cellWorkerLoop()
            }
        }
    }

    private suspend fun cellWorkerLoop() {
        coroutineScope {
            repeat(WORKER_COUNT) { launch { singleWorkerLoop() } }
        }
    }

    private suspend fun singleWorkerLoop() {
        while (true) {
            if (!networkStatus.connected) {
                delay(1000)
                continue
            }
            val pending = synchronized(pendingCells) {
                pendingCells.removeFirstOrNull()
            } ?: break

            if (cellCache.containsKey(pending.cell.id)) continue

            try {
                synchronized(inFlightCells) { inFlightCells.add(pending.cell.id) }
                updateCellCounters()
                fetchCell(pending)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("POI_DEBUG", "Cell ${pending.cell.id} unexpected error: ${e.message}")
                reEnqueueIfRetriable(pending)
            } finally {
                synchronized(inFlightCells) { inFlightCells.remove(pending.cell.id) }
                rebuildMergedFeatures()
                updateCellCounters()
                delay(75)
            }
        }
    }

    private suspend fun fetchCell(pending: PendingCell) {
        when (val result = postpassRepo.fetchPoisForTile(pending.cell.bounds, enabledPoiCategories)) {
            is TileFetchResult.Success -> {
                android.util.Log.d("POI_DEBUG", "Cell ${pending.cell.id}: ${result.features.size} features")
                synchronized(cellCache) {
                    cellCache[pending.cell.id] = CachedCell(
                        cellId = pending.cell.id,
                        features = result.features.features,
                        fetchedAtMs = System.currentTimeMillis(),
                        bounds = pending.cell.bounds,
                    )
                }
            }
            is TileFetchResult.ServerError -> {
                android.util.Log.e("POI_DEBUG", "Cell ${pending.cell.id} server error: ${result.message}")
                delay(min(1000L * (1 shl pending.retryCount), MAX_INTER_CELL_DELAY_MS))
                reEnqueueIfRetriable(pending)
            }
            is TileFetchResult.Failed -> {
                android.util.Log.e("POI_DEBUG", "Cell ${pending.cell.id} fetch failed: ${result.message}")
                delay(500L)
                reEnqueueIfRetriable(pending)
            }
        }
    }

    private fun reEnqueueIfRetriable(pending: PendingCell) {
        if (pending.retryCount >= MAX_CELL_RETRIES) {
            synchronized(permanentlyFailedCells) { permanentlyFailedCells.add(pending.cell) }
            updateCellCounters()
            return
        }
        synchronized(pendingCells) {
            pendingCells.add(pending.copy(retryCount = pending.retryCount + 1))
        }
        updateCellCounters()
        workSignal.trySend(Unit)
    }

    fun retryFailedCells() {
        val cells = synchronized(permanentlyFailedCells) {
            val copy = permanentlyFailedCells.toList()
            permanentlyFailedCells.clear()
            copy
        }
        if (cells.isEmpty()) return
        val cam = pendingCameraInfo ?: return
        enqueueCells(cells, Position(latitude = cam.lat, longitude = cam.lon))
    }

    /**
     * Called periodically to extend POI coverage as the user pans or drives.
     * Evicts stale data outside the current load plate, then enqueues any missing cells.
     */
    fun onCameraSettled(lat: Double, lon: Double, zoom: Double, bearing: Double = 0.0) {
        if (!poiPipelineActive) return

        val viewBounds = viewportBoundsForPoi(lat, lon, zoom)
        val cameraTarget = Position(latitude = lat, longitude = lon)
        val loadPlate = PoiViewportChunks.poiLoadPlateForVisibleBounds(
            viewBounds, cameraTarget, poiBoxBottomFraction, bearing,
        )

        evictCachedCellsOutsideLoadPlate(loadPlate)
        poiLoadBounds = loadPlate

        reprioritizePendingCells(cameraTarget)

        if (!networkStatus.connected) return

        val missingCells = PoiViewportChunks.worldGridCellsIntersecting(loadPlate).filter { cell ->
            !cellCache.containsKey(cell.id)
                && synchronized(inFlightCells) { !inFlightCells.contains(cell.id) }
                && synchronized(pendingCells) { pendingCells.none { it.cell.id == cell.id } }
                && synchronized(permanentlyFailedCells) { permanentlyFailedCells.none { it.id == cell.id } }
        }
        if (missingCells.isNotEmpty()) {
            android.util.Log.d("POI_DEBUG", "onCameraSettled: ${missingCells.size} new cells to enqueue")
            enqueueCells(missingCells, cameraTarget)
            startCellWorker()
        }
    }

    private fun enqueueCellsForCurrentViewport() {
        val cam = pendingCameraInfo ?: return
        val viewBounds = viewportBoundsForPoi(cam.lat, cam.lon, cam.zoom)
        val cameraTarget = Position(latitude = cam.lat, longitude = cam.lon)
        val loadPlate = PoiViewportChunks.poiLoadPlateForVisibleBounds(
            viewBounds, cameraTarget, poiBoxBottomFraction, cam.bearing,
        )
        evictCachedCellsOutsideLoadPlate(loadPlate)
        poiLoadBounds = loadPlate
        val missingCells = PoiViewportChunks.worldGridCellsIntersecting(loadPlate)
        enqueueCells(missingCells, cameraTarget)
        startCellWorker()
    }

    // --- Search ---

    fun updateSearchQuery(query: String) {
        searchQuery = query
        triggerNameSearch()
    }

    // Stored externally since ViewModel doesn't own location
    var userPositionForSearch: Position? = null

    private fun triggerNameSearch() {
        searchJob?.cancel()
        if (searchQuery.length < 2) {
            searchResults = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            isSearching = true
            delay(300L)
            val cam = pendingCameraInfo ?: return@launch
            val viewbox = computeViewBox(cam.lat, cam.lon, cam.zoom)
            searchResults = geocodingRepo.searchByName(
                query = searchQuery,
                centerLat = cam.lat,
                centerLon = cam.lon,
                bbox = viewbox,
                userPosition = userPositionForSearch,
            )
            isSearching = false
        }
    }

    data class CameraInfo(val lat: Double, val lon: Double, val zoom: Double, val bearing: Double = 0.0)
    var pendingCameraInfo: CameraInfo? = null

    /**
     * Last visible geographic bounds from [org.maplibre.compose.camera.CameraProjection.queryVisibleBoundingBox];
     * updated from composition when the map is ready. When null, POI loading falls back to [PoiViewportChunks.approximateViewportBounds].
     */
    var poiMapVisibleBounds: BoundingBox? by mutableStateOf(null)
        private set

    /** Called from [MapScreen] [androidx.compose.runtime.SideEffect] when projection is available. */
    fun updatePoiMapVisibleBounds(bounds: BoundingBox?) {
        poiMapVisibleBounds = bounds
    }

    /** Screen dimensions in dp, set from composition. */
    var screenWidthDp: Double = 360.0
    var screenHeightDp: Double = 800.0

    private val poiBoxBottomFraction: Double
        get() = (if (screenWidthDp > screenHeightDp) mapCenterOffsetLandscapeFraction
                 else mapCenterOffsetPortraitFraction).toDouble()

    private fun viewportBoundsForPoi(lat: Double, lon: Double, zoom: Double): BoundingBox =
        poiMapVisibleBounds
            ?: PoiViewportChunks.approximateViewportBounds(lat, lon, zoom, screenWidthDp, screenHeightDp)

    private fun computeViewBox(lat: Double, lon: Double, zoom: Double): ViewBox {
        val latDelta = 360.0 / Math.pow(2.0, zoom) * 0.5
        val lonDelta = latDelta / Math.cos(Math.toRadians(lat))
        return ViewBox(
            west = lon - lonDelta,
            south = lat - latDelta,
            east = lon + lonDelta,
            north = lat + latDelta,
        )
    }

    // --- Reset ---

    fun resetToDefaults(systemDefault: MapStyle, onStyleChange: (MapStyle) -> Unit) {
        onStyleChange(systemDefault)
        weatherMode = WeatherMode.valueOf(PrefsDefaults.WEATHER_MODE)
        showLegend = PrefsDefaults.SHOW_LEGEND
        showTimeline = PrefsDefaults.SHOW_TIMELINE
        radarOpacity = PrefsDefaults.RADAR_OPACITY
        useMetric = PrefsDefaults.USE_METRIC
        speedSize = PrefsDefaults.SPEED_SIZE
        navWidgetSize = PrefsDefaults.NAV_WIDGET_SIZE
        weatherWidgetSize = PrefsDefaults.WEATHER_WIDGET_SIZE
        compassWidgetSize = PrefsDefaults.COMPASS_WIDGET_SIZE
        windEnabled = PrefsDefaults.WIND_ENABLED
        windSpeedUnit = WindSpeedUnit.valueOf(PrefsDefaults.WIND_SPEED_UNIT)
        temperatureUnit = TemperatureUnit.valueOf(PrefsDefaults.TEMPERATURE_UNIT)
        poiIconOpacity = PrefsDefaults.POI_ICON_OPACITY
        keepScreenOn = PrefsDefaults.KEEP_SCREEN_ON
        autostartPoiLoadingOnLaunch = PrefsDefaults.AUTOSTART_POI_LOADING_ON_LAUNCH
        useGps = PrefsDefaults.USE_GPS
        gpsIconOpacity = PrefsDefaults.GPS_ICON_OPACITY
        mapCenterOffsetPortraitFraction = PrefsDefaults.MAP_CENTER_OFFSET_PORTRAIT_FRACTION
        mapCenterOffsetLandscapeFraction = PrefsDefaults.MAP_CENTER_OFFSET_LANDSCAPE_FRACTION
        isTrackingCamera = true
        isNorthUp = false
        poiPosition = null
        poiName = null
        enabledPoiCategories = emptySet()
        cellWorkerJob?.cancel()
        cellWorkerJob = null
        cellCache.clear()
        synchronized(pendingCells) { pendingCells.clear() }
        synchronized(inFlightCells) { inFlightCells.clear() }
        synchronized(permanentlyFailedCells) { permanentlyFailedCells.clear() }
        updateCellCounters()
        poiPipelineActive = false
        nearbyPoiFeatures = FeatureCollection(emptyList())
        poiFetchRegion = null
        poiLoadBounds = null
        poiCategoriesVersion++
        customLightAutoEnabled = PrefsDefaults.CUSTOM_LIGHT_AUTO_ENABLED
        customDarkAutoEnabled = PrefsDefaults.CUSTOM_DARK_AUTO_ENABLED
        prefsRepo.resetToDefaults(systemDefault)
        showResetConfirm = false
        showActionsDrawer = false
        startWeatherPollingIfActive()
        startWeatherAnimationIfPlaying()
    }

    // --- Zoom ---

    fun onZoomChanged(zoom: Float) {
        prefsRepo.zoomLevel = zoom
    }
}
