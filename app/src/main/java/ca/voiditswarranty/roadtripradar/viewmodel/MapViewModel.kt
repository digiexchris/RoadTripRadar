package ca.voiditswarranty.roadtripradar.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.voiditswarranty.roadtripradar.BuildConfig
import ca.voiditswarranty.roadtripradar.R
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
import ca.voiditswarranty.roadtripradar.data.InsertPosition
import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.data.WaypointSource
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
import ca.voiditswarranty.roadtripradar.ui.tutorial.TutorialGroup
import ca.voiditswarranty.roadtripradar.ui.tutorial.stepsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicInteger
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
    val postpassRepo: PostpassRepository = PostpassRepository(appContext),
    /**
     * Optional externally-supplied [CoroutineScope]. When null (the phone Activity
     * path), polling/launch run on [viewModelScope] and are cancelled when the
     * Activity destroys the ViewModel. When supplied (the Android Auto path via
     * [ca.voiditswarranty.roadtripradar.car.CarViewModelHolder]), they run on an
     * app-scoped supervisor scope so they survive Activity destruction while the
     * car Session is alive.
     */
    private val externalScope: CoroutineScope? = null,
) : ViewModel() {
    private val vmScope: CoroutineScope = externalScope ?: viewModelScope
    private val geocodingRepo: GeocodingRepository = GeocodingRepository()

    // --- Refresh bus for Android Auto car screens ---
    // Car App Library screens are not Jetpack Compose; they rebuild their Template
    // in onGetTemplate(). Car Screens register a listener that calls invalidate() on
    // the car host's main executor, so the car UI reflects VM state changes. The
    // phone UI keeps reacting via Compose snapshot and ignores this bus. Listeners
    // are invoked on whatever thread the mutation ran on (often Dispatchers.IO), so
    // registrants must dispatch to the main executor themselves.
    private val refreshListeners = java.util.concurrent.CopyOnWriteArrayList<(MapViewModel) -> Unit>()
    fun addRefreshListener(listener: (MapViewModel) -> Unit) {
        refreshListeners.add(listener)
    }
    fun removeRefreshListener(listener: (MapViewModel) -> Unit) {
        refreshListeners.remove(listener)
    }
    private fun notifyRefresh() {
        refreshListeners.forEach { runCatching { it(this) } }
    }

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

    /**
     * Test-visible setter for [openMeteoSnapshot]. The production path sets the
     * snapshot from inside [startLocalWeatherPolling] (via the polling loop's
     * `onSuccess`); tests that need a non-null snapshot without running the
     * network poll call this directly. `internal` so it's only visible to the
     * same module (including the test sourceset).
     */
    internal fun setOpenMeteoSnapshotForTest(snap: OpenMeteoSnapshot?) {
        openMeteoSnapshot = snap
        notifyRefresh()
    }

    /**
     * Test seam for setting [failedCellBounds] without running the full POI
     * pipeline. The production code writes the field from inside the
     * `permanentlyFailedCells` synchronized block when a cell exhausts retries.
     * Tests that need to assert the menu hub's "Retry failed" row appears use
     * this to populate the list directly. `internal` so it's only visible to
     * the same module (including the test sourceset).
     */
    internal fun setFailedCellBoundsForTest(bounds: List<org.maplibre.spatialk.geojson.BoundingBox>) {
        failedCellBounds = bounds
        notifyRefresh()
    }

    /**
     * Test seam for setting [enabledPoiCategories] without going through
     * [togglePoiCategory] (which has the cap-enforcement and pipeline-rerun
     * side effects). Tests that need a particular enabled-set state — e.g.
     * for the PoiScreen grid's enabled / at-cap branches — use this to
     * populate the set directly. `internal` so it's only visible to the same
     * module (including the test sourceset).
     */
    internal fun setEnabledPoiCategoriesForTest(categories: Set<String>) {
        enabledPoiCategories = categories
        notifyRefresh()
    }

    /**
     * Test seam for setting [poiPipelineActive] without running the pipeline.
     * The PoiScreen status text branches on this (and on
     * [hasFailedCells] / [isLoadingPois]) — tests that need a particular
     * pipeline-state branch use this to flip the flag directly.
     */
    internal fun setPoiPipelineActiveForTest(active: Boolean) {
        poiPipelineActive = active
        notifyRefresh()
    }

    /**
     * Test seam for setting [cellsRemaining] (which drives [isLoadingPois]).
     * Tests that need a particular loading-state branch use this to set the
     * counter directly.
     */
    internal fun setCellsRemainingForTest(remaining: Int) {
        cellsRemaining = remaining
        notifyRefresh()
    }

    /**
     * Test seam for setting [nearbyPoiFeatures] without going through the
     * pipeline. Tests that need to assert a non-empty FeatureCollection in
     * the PoiScreen status text use this to populate it directly.
     */
    internal fun setNearbyPoiFeaturesForTest(
        features: org.maplibre.spatialk.geojson.FeatureCollection<
            org.maplibre.spatialk.geojson.Point,
            kotlinx.serialization.json.JsonObject,
        >,
    ) {
        nearbyPoiFeatures = features
        notifyRefresh()
    }

    /**
     * Test seam for setting [searchQuery] without going through the
     * debounce-then-fetch pipeline. Tests that need a particular query
     * string in the Search screen's no-results / searching state machine use
     * this to set it directly.
     */
    internal fun setSearchQueryForTest(query: String) {
        searchQuery = query
        notifyRefresh()
    }

    /**
     * Test seam for setting [searchResults] without running the geocoding
     * network call. Tests that need the Search screen to render a particular
     * set of result rows use this to populate the list directly.
     */
    internal fun setSearchResultsForTest(results: List<ca.voiditswarranty.roadtripradar.model.SearchResult>) {
        searchResults = results
        notifyRefresh()
    }

    /**
     * Test seam for setting [isSearching] without going through the
     * debounce-then-fetch pipeline. Tests that need the Search screen's
     * loading-state branch use this to flip the flag directly.
     */
    internal fun setIsSearchingForTest(searching: Boolean) {
        isSearching = searching
        notifyRefresh()
    }

    /**
     * The resolved MapLibre style (`MapStyle`); updated by [updateMapStyle]
     * (which persists the choice). Test seam for setting [mapStyle] without
     * going through [updateMapStyle]'s persistence side effect. Tests that
     * need a particular style branch — e.g. for the theme-switching Robolectric
     * tests — use this to set it directly. `internal` so it's only visible to
     * the same module (including the test sourceset).
     */
    internal fun setMapStyleForTest(style: MapStyle) {
        mapStyle = style
        notifyRefresh()
    }

    /**
     * The auto-advance radius in meters; updated by [updateAutoAdvanceThreshold]
     * (which clamps to 25..500). Test seam for setting
     * [autoAdvanceThresholdMeters] without going through the clamping /
     * persistence path. Tests that need a particular radius branch — e.g. for
     * the route operations Robolectric tests — use this to set it directly.
     * `internal` so it's only visible to the same module (including the test
     * sourceset).
     */
    internal fun setAutoAdvanceThresholdMetersForTest(meters: Int) {
        autoAdvanceThresholdMeters = meters
        notifyRefresh()
    }

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
    var mapStyle by mutableStateOf(
        try { prefsRepo.mapStyle } catch (_: IllegalArgumentException) {
            PreferencesRepository.defaultMapStyleFor(appContext)
        }
    )
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
    var mapCenterOffsetCarFraction by mutableStateOf(prefsRepo.mapCenterOffsetCarFraction)
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

    // Waypoints / route
    val waypoints = mutableStateListOf<Waypoint>().apply { addAll(prefsRepo.waypoints) }
    var activeWaypointId by mutableStateOf(prefsRepo.activeWaypointId)
        private set
    var autoAdvanceEnabled by mutableStateOf(prefsRepo.autoAdvanceEnabled)
        private set
    var autoAdvanceThresholdMeters by mutableStateOf(prefsRepo.autoAdvanceThresholdMeters)
        private set

    val activeIndex: Int?
        get() = activeWaypointId?.let { id ->
            waypoints.indexOfFirst { it.id == id }.takeIf { it >= 0 }
        }
    val activeWaypoint: Waypoint?
        get() = activeIndex?.let { waypoints[it] }

    /** Back-compat alias: the active waypoint's position. */
    val poiPosition: Position?
        get() = activeWaypoint?.position

    /** Back-compat alias: the active waypoint's display name (or "Dropped Pin" when unnamed). */
    val poiName: String?
        get() = activeWaypoint?.let { it.name ?: appContext.getString(R.string.dropped_pin_title) }

    /** Back-compat alias: the active waypoint's subtitle. */
    val poiSubtitle: String?
        get() = activeWaypoint?.subtitle

    // Nearby POIs — cell-based pipeline
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
    var tappedPoiOrigin by mutableStateOf<TappedPoiOrigin?>(null)
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
        // Aggressive retry used while no weather has been received yet. Once a
        // snapshot arrives it is kept across failures, so openMeteoSnapshot == null
        // means "never succeeded" — we retry fast until then and fall back to the
        // regular success/retry cadence above afterwards.
        const val LOCAL_WEATHER_FAST_RETRY_MS = 15_000L
    }

    data class TappedPoiInfo(
        val name: String,
        val subtitle: String,
        val categoryLabel: String,
        val iconName: String,
        val position: Position,
        val openingHours: String? = null,
    )

    enum class TappedPoiOrigin { NearbyPoi, LongPress, Search, NavigationTarget }

    // UI state
    var isTrackingCamera by mutableStateOf(true)
    var isNorthUp by mutableStateOf(!prefsRepo.useGps)
    var showActionsDrawer by mutableStateOf(false)
        private set
    var showResetConfirm by mutableStateOf(false)
        private set
    var showPoiSearch by mutableStateOf(false)
        private set
    var showRouteEditor by mutableStateOf(false)
        private set

    fun openRouteEditor() { showRouteEditor = true }
    fun closeRouteEditor() { showRouteEditor = false }
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
    var showSafety by mutableStateOf(false)
        private set

    // Tutorial
    var tutorialActiveGroup by mutableStateOf<TutorialGroup?>(null)
        private set
    var tutorialStepIndex by mutableIntStateOf(0)
        private set
    var completedTutorialGroups by mutableStateOf(prefsRepo.completedTutorialGroups)
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

    /**
     * Ref-count of "active" surfaces (the phone Activity while resumed, plus each live
     * Android Auto car [androidx.car.app.Session]). The weather/radar/animation polling
     * loops only run while at least one surface is active — see [reconcilePolling]. This
     * keeps the app-scoped singleton ViewModel from polling forever in the background
     * after the phone app is closed and no car is connected.
     */
    private val activeSurfaces = AtomicInteger(0)

    /** Called by a surface when it comes to the foreground / is created. */
    fun onSurfaceActive() {
        if (activeSurfaces.getAndIncrement() == 0) reconcilePolling()
    }

    /** Called by a surface when it goes to the background / is destroyed. */
    fun onSurfaceInactive() {
        // Clamp at 0 so a stray inactive call (e.g. onStop without a matching onResume)
        // can't drive the count negative and starve a future surface.
        while (true) {
            val cur = activeSurfaces.get()
            if (cur <= 0) return
            if (activeSurfaces.compareAndSet(cur, cur - 1)) {
                if (cur - 1 == 0) reconcilePolling()
                return
            }
        }
    }

    /**
     * Declaratively starts/stops the long-running polling jobs to match the current
     * state: a job runs iff at least one surface is active AND its condition holds
     * (radar needs [weatherActive]; animation needs [isWeatherPlaying]; local weather
     * runs whenever any surface is active). Idempotent — safe to call on every state
     * change.
     */
    private fun reconcilePolling() {
        val active = activeSurfaces.get() > 0
        // Local weather polling: run iff a surface is active.
        if (active) startLocalWeatherPolling()
        else { localWeatherPollJob?.cancel(); localWeatherPollJob = null }
        // Radar polling: run iff active and weather is on.
        if (active && weatherActive) startWeatherPollingIfActive()
        else { weatherPollingJob?.cancel(); weatherPollingJob = null }
        // Radar animation: run iff active and playing.
        if (active && isWeatherPlaying) startWeatherAnimationIfPlaying()
        else { weatherAnimationJob?.cancel(); weatherAnimationJob = null }
    }

    init {
        if (prefsRepo.acceptedTermsVersion != PrefsDefaults.TERMS_VERSION) {
            showTerms = true
            termsNeedAcceptance = true
        } else if (prefsRepo.acceptedSafetyVersion != PrefsDefaults.SAFETY_VERSION) {
            // Terms already accepted on a prior launch but the safety notice
            // hasn't been acknowledged with "Don't Show Again" yet — show it.
            showSafety = true
        }
        // Polling is started lazily by the first surface to become active
        // (see [onSurfaceActive] / [reconcilePolling]).
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
        reconcilePolling()
        notifyRefresh()
    }

    fun cycleWeatherMode() {
        updateWeatherMode(nextWeatherModeOnCycle(weatherMode))
    }

    fun toggleWeatherPlayPause() {
        updateWeatherMode(nextWeatherModeOnPlayPauseToggle(weatherMode))
    }

    fun turnOffWeather() {
        updateWeatherMode(WeatherMode.OFF)
    }

    fun toggleWeatherOnOff() {
        updateWeatherMode(nextWeatherModeOnOnOffToggle(weatherActive))
    }

    fun updateShowLegend(show: Boolean) {
        showLegend = show
        prefsRepo.showLegend = show
        notifyRefresh()
    }

    fun updateShowTimeline(show: Boolean) {
        showTimeline = show
        prefsRepo.showTimeline = show
        notifyRefresh()
    }

    fun updateRadarOpacity(opacity: Float) {
        radarOpacity = opacity
        notifyRefresh()
    }

    fun saveRadarOpacity() {
        prefsRepo.radarOpacity = radarOpacity
    }

    private fun startWeatherPollingIfActive() {
        if (!weatherActive) return
        if (weatherPollingJob?.isActive == true) return
        weatherPollingJob = vmScope.launch {
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
        if (localWeatherPollJob?.isActive == true) return
        localWeatherPollJob = vmScope.launch {
            while (true) {
                val pos = localWeatherAnchor
                if (pos == null) {
                    // No location fix yet. Wait briefly so the first fetch is
                    // responsive once a fix arrives — this is waiting on the GPS
                    // anchor, not retrying a failed fetch.
                    delay(2_000L)
                    continue
                }
                openMeteoRepo.fetchCurrent(pos.latitude, pos.longitude)
                    .onSuccess { snap ->
                        openMeteoSnapshot = snap
                        notifyRefresh()
                        delay(LOCAL_WEATHER_SUCCESS_MS)
                    }
                    .onFailure {
                        // Retry aggressively (every 15s) until we've ever received
                        // weather; after that, fall back to the regular retry cadence.
                        // openMeteoSnapshot is kept across failures, so null here
                        // means we have never succeeded.
                        delay(if (openMeteoSnapshot == null) LOCAL_WEATHER_FAST_RETRY_MS else LOCAL_WEATHER_RETRY_MS)
                    }
            }
        }
    }

    private fun startWeatherAnimationIfPlaying() {
        if (weatherMode != WeatherMode.PLAYING) return
        if (weatherAnimationJob?.isActive == true) return
        weatherAnimationJob = vmScope.launch {
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
        notifyRefresh()
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
        notifyRefresh()
    }

    fun updateWindSpeedUnit(unit: WindSpeedUnit) {
        windSpeedUnit = unit
        prefsRepo.windSpeedUnit = unit
        notifyRefresh()
    }

    fun updateTemperatureUnit(unit: TemperatureUnit) {
        temperatureUnit = unit
        prefsRepo.temperatureUnit = unit
        notifyRefresh()
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
        notifyRefresh()
    }

    fun updateMapStyle(style: MapStyle) {
        mapStyle = style
        prefsRepo.mapStyle = style
        notifyRefresh()
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

    fun updateMapCenterOffsetCarFraction(value: Float) {
        mapCenterOffsetCarFraction = value
    }

    fun saveMapCenterOffsetCarFraction() {
        prefsRepo.mapCenterOffsetCarFraction = mapCenterOffsetCarFraction
        notifyRefresh()
    }

    fun saveLastKnownPosition(pos: Position) {
        prefsRepo.lastKnownPosition = pos
    }

    /** Last known device position (persisted by the phone surface). Read by the car map. */
    val lastKnownPosition: Position?
        get() = prefsRepo.lastKnownPosition

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
        if (!shouldShowWhatsNew(lastSeenCode = last, currentVersionCode = current, newReleaseCount = newReleases.size)) {
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
        // Show the safety notice immediately after first-launch T&C acceptance.
        showSafety = true
    }

    fun dismissTerms() {
        showTerms = false
    }

    fun viewSafety() {
        closeActionsDrawer()
        showSafety = true
    }

    fun dismissSafety() {
        showSafety = false
    }

    fun dontShowSafety() {
        prefsRepo.acceptedSafetyVersion = PrefsDefaults.SAFETY_VERSION
        showSafety = false
    }

    // --- Tutorial ---

    fun isTutorialGroupCompleted(group: TutorialGroup): Boolean =
        completedTutorialGroups.contains(group.name)

    fun startTutorialIfNotCompleted(group: TutorialGroup) {
        if (tutorialActiveGroup != null) return
        if (isTutorialGroupCompleted(group)) return
        tutorialActiveGroup = group
        tutorialStepIndex = 0
    }

    fun startTutorial(group: TutorialGroup) {
        tutorialActiveGroup = group
        tutorialStepIndex = 0
    }

    fun tutorialNext() {
        val group = tutorialActiveGroup ?: return
        val steps = stepsFor(group)
        when (val next = nextTutorialStep(currentIndex = tutorialStepIndex, stepCount = steps.size)) {
            is TutorialEnd.End -> {
                markTutorialGroupCompleted(group)
                tutorialActiveGroup = null
                tutorialStepIndex = 0
            }
            is Int -> {
                tutorialStepIndex = next
            }
        }
    }

    fun tutorialBack() {
        if (tutorialActiveGroup == null) return
        tutorialStepIndex = previousTutorialStep(tutorialStepIndex)
    }

    fun skipTutorial() {
        val group = tutorialActiveGroup ?: return
        markTutorialGroupCompleted(group)
        tutorialActiveGroup = null
        tutorialStepIndex = 0
    }

    /**
     * Clears the active tutorial without persisting completion. Used when the context
     * for the active tutorial disappears (e.g. the drawer navigates to a submenu while
     * MENU_MAIN is running). The tutorial can fire again on its next trigger.
     */
    fun cancelTutorial(group: TutorialGroup) {
        if (tutorialActiveGroup == group) {
            tutorialActiveGroup = null
            tutorialStepIndex = 0
        }
    }

    private fun markTutorialGroupCompleted(group: TutorialGroup) {
        val updated = completedTutorialGroups + group.name
        completedTutorialGroups = updated
        prefsRepo.completedTutorialGroups = updated
    }

    /**
     * Clears all completed tutorial groups and starts the MAP group from the beginning.
     * If the user later opens the actions drawer, the MENU_MAIN group will fire on its
     * own trigger since it is no longer marked completed.
     */
    fun replayTutorials() {
        completedTutorialGroups = emptySet()
        prefsRepo.completedTutorialGroups = emptySet()
        closeActionsDrawer()
        startTutorial(TutorialGroup.MAP)
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
            val fallback = customThemeFallback(style)
            if (fallback != null) onStyleChange(fallback)
        }
    }

    fun importCustomTheme(uri: android.net.Uri, target: MapStyle, onStyleChange: (MapStyle) -> Unit) {
        vmScope.launch(Dispatchers.IO) {
            try {
                customThemeRepo.importTheme(uri, target)
                withContext(Dispatchers.Main) {
                    refreshCustomThemeState()
                    customThemeVersion++
                    onStyleChange(target)
                }
            } catch (e: InvalidStyleJsonException) {
                withContext(Dispatchers.Main) {
                    customThemeImportError = if (e.formatArg != null) {
                        appContext.getString(e.messageRes, e.formatArg)
                    } else {
                        appContext.getString(e.messageRes)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    customThemeImportError = appContext.getString(R.string.theme_import_error_generic)
                }
            }
        }
    }

    fun dismissCustomThemeImportError() {
        customThemeImportError = null
    }

    /** Creates a custom theme by copying a bundled [source] asset into the [target] custom slot. */
    fun initCustomThemeFromAsset(source: MapStyle, target: MapStyle, onStyleChange: (MapStyle) -> Unit) {
        vmScope.launch(Dispatchers.IO) {
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
        vmScope.launch(Dispatchers.IO) {
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

    // --- POI / waypoints ---

    fun setPoiFromLongPress(position: Position) {
        val info = TappedPoiInfo(
            name = appContext.getString(ca.voiditswarranty.roadtripradar.R.string.dropped_pin_title),
            subtitle = formatLatLng(position),
            categoryLabel = "",
            iconName = "",
            position = position,
        )
        showTappedPoi(info, TappedPoiOrigin.LongPress)
        triggerReverseGeocode(position)
    }

    fun selectSearchResult(result: SearchResult) {
        showPoiSearch = false
        showTappedPoi(
            TappedPoiInfo(
                name = result.name,
                subtitle = result.subtitle,
                categoryLabel = "",
                iconName = "",
                position = result.position,
            ),
            TappedPoiOrigin.Search,
        )
    }

    private fun triggerReverseGeocode(position: Position) {
        val loadingSuffix = appContext.getString(ca.voiditswarranty.roadtripradar.R.string.address_loading)
        if (tappedPoi?.position == position) {
            tappedPoi = tappedPoi?.copy(subtitle = "$loadingSuffix\n${formatLatLng(position)}")
        }
        vmScope.launch {
            val address = geocodingRepo.reverseGeocode(position.latitude, position.longitude)
            val newSubtitle = if (address != null) {
                "$address\n${formatLatLng(position)}"
            } else {
                formatLatLng(position)
            }
            val current = tappedPoi
            if (current != null && current.position == position) {
                tappedPoi = current.copy(subtitle = newSubtitle)
            }
            if (address != null) {
                updateActiveWaypointIf({ it.position == position }) { it.copy(subtitle = newSubtitle) }
            }
        }
    }

    fun clearRoute() {
        waypoints.clear()
        activeWaypointId = null
        persistRoute()
        notifyRefresh()
    }

    fun moveWaypoint(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        if (fromIndex !in waypoints.indices) return
        val target = toIndex.coerceIn(0, waypoints.size - 1)
        if (fromIndex == target) return
        val wp = waypoints.removeAt(fromIndex)
        waypoints.add(target, wp)
        persistRoute()
        notifyRefresh()
    }

    fun addWaypoint(
        position: Position,
        name: String?,
        subtitle: String?,
        source: WaypointSource,
        at: InsertPosition,
        iconName: String? = null,
    ): String {
        val wp = Waypoint.create(
            position = position,
            name = name,
            subtitle = subtitle,
            source = source,
            iconName = iconName,
        )
        when (at) {
            is InsertPosition.ReplaceId -> {
                val idx = waypoints.indexOfFirst { it.id == at.id }
                if (idx >= 0) {
                    val wasActive = activeWaypointId == at.id
                    waypoints[idx] = wp
                    if (wasActive) activeWaypointId = wp.id
                } else {
                    waypoints.add(wp)
                }
            }
            else -> {
                val insertIdx = addWaypointIndex(
                    currentSize = waypoints.size,
                    at = at,
                    activeWaypointId = activeWaypointId,
                    findById = { id -> waypoints.indexOfFirst { it.id == id } },
                )
                waypoints.add(insertIdx, wp)
            }
        }
        if (activeWaypointId == null) activeWaypointId = wp.id
        persistRoute()
        notifyRefresh()
        return wp.id
    }

    fun setActiveWaypoint(id: String) {
        if (waypoints.any { it.id == id }) {
            activeWaypointId = id
            persistRoute()
            notifyRefresh()
        }
    }

    fun advanceActiveWaypoint() {
        val idx = activeIndex ?: return
        val next = waypoints.getOrNull(idx + 1) ?: return
        setActiveWaypoint(next.id)
    }

    fun regressActiveWaypoint() {
        val idx = activeIndex ?: return
        val prev = waypoints.getOrNull(idx - 1) ?: return
        setActiveWaypoint(prev.id)
    }

    fun updateAutoAdvanceEnabled(enabled: Boolean) {
        autoAdvanceEnabled = enabled
        prefsRepo.autoAdvanceEnabled = enabled
        notifyRefresh()
    }

    fun updateAutoAdvanceThreshold(meters: Int) {
        autoAdvanceThresholdMeters = meters.coerceIn(25, 500)
        notifyRefresh()
    }

    fun saveAutoAdvanceThreshold() {
        prefsRepo.autoAdvanceThresholdMeters = autoAdvanceThresholdMeters
    }

    /** Called from the map on each user-location update. Advances the active waypoint when within threshold. */
    fun maybeAutoAdvance(userPos: Position) {
        val idx = activeIndex ?: return
        val active = waypoints.getOrNull(idx) ?: return
        val distMeters = distance(Point(userPos), Point(active.position)).inMeters
        val next = maybeAutoAdvanceNextIndex(
            autoAdvanceEnabled = autoAdvanceEnabled,
            activeIndex = activeIndex,
            totalWaypoints = waypoints.size,
            distanceMeters = distMeters,
            thresholdMeters = autoAdvanceThresholdMeters,
        ) ?: return
        val nextWp = waypoints.getOrNull(next) ?: return
        setActiveWaypoint(nextWp.id)
    }

    /** Add the currently-tapped POI to the route at the requested position, then dismiss the popup. */
    fun addWaypointFromTapped(at: InsertPosition) {
        val poi = tappedPoi ?: return
        val origin = tappedPoiOrigin
        val source = when (origin) {
            TappedPoiOrigin.LongPress -> WaypointSource.DROPPED_PIN
            TappedPoiOrigin.Search -> WaypointSource.SEARCH
            TappedPoiOrigin.NearbyPoi -> WaypointSource.NEARBY_PLACE
            TappedPoiOrigin.NavigationTarget, null -> WaypointSource.DROPPED_PIN
        }
        val droppedPinTitle = appContext.getString(R.string.dropped_pin_title)
        val storedName = poi.name.takeIf {
            it.isNotBlank() && (source != WaypointSource.DROPPED_PIN || it != droppedPinTitle)
        }
        val storedIconName = poi.iconName.takeIf {
            it.isNotBlank() && source == WaypointSource.NEARBY_PLACE
        }
        val newId = addWaypoint(
            position = poi.position,
            name = storedName,
            subtitle = poi.subtitle.takeIf { it.isNotBlank() },
            source = source,
            iconName = storedIconName,
            at = at,
        )
        if (at is InsertPosition.Start) {
            setActiveWaypoint(newId)
        }
        tappedPoi = null
        tappedPoiOrigin = null
        if (origin == TappedPoiOrigin.Search) {
            searchQuery = ""
            searchResults = emptyList()
        }
        notifyRefresh()
    }

    private inline fun updateActiveWaypointIf(
        predicate: (Waypoint) -> Boolean,
        transform: (Waypoint) -> Waypoint,
    ) {
        val idx = activeIndex ?: return
        val current = waypoints[idx]
        if (!predicate(current)) return
        waypoints[idx] = transform(current)
        persistRoute()
    }

    private fun persistRoute() {
        prefsRepo.waypoints = waypoints.toList()
        prefsRepo.activeWaypointId = activeWaypointId
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
        notifyRefresh()
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
        notifyRefresh()
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
        notifyRefresh()
    }

    fun openPoiCategoryPicker() { showPoiCategoryPicker = true }
    fun closePoiCategoryPicker() { showPoiCategoryPicker = false }

    /** When [tappedPoi] reflects a specific waypoint marker, this holds its id. */
    var tappedWaypointId by mutableStateOf<String?>(null)
        private set

    fun showTappedPoi(info: TappedPoiInfo, origin: TappedPoiOrigin = TappedPoiOrigin.NearbyPoi) {
        tappedPoi = info
        tappedPoiOrigin = origin
        tappedWaypointId = null
    }

    fun dismissTappedPoi() {
        tappedPoi = null
        tappedPoiOrigin = null
        tappedWaypointId = null
    }

    fun tappedPoiBackToSearch() {
        tappedPoi = null
        tappedPoiOrigin = null
        tappedWaypointId = null
        showPoiSearch = true
    }

    /** Open the navigation-target popup for the active waypoint, if any. */
    fun showNavigationTargetPopup() {
        val activeId = activeWaypointId ?: return
        showWaypointPopup(activeId)
    }

    /** Open the navigation-target popup for a specific waypoint (e.g. when its marker is tapped). */
    fun showWaypointPopup(waypointId: String) {
        val idx = waypoints.indexOfFirst { it.id == waypointId }
        if (idx < 0) return
        val wp = waypoints[idx]
        val cachedSubtitle = wp.subtitle?.takeIf { it.isNotBlank() }
        val name = wp.name?.takeIf { it.isNotBlank() }
        val displayName = if (name != null) {
            appContext.getString(R.string.waypoint_numbered, idx + 1, name)
        } else {
            appContext.getString(R.string.waypoint_unnamed, idx + 1)
        }
        tappedPoi = TappedPoiInfo(
            name = displayName,
            subtitle = cachedSubtitle ?: formatLatLng(wp.position),
            categoryLabel = "",
            iconName = wp.iconName ?: "",
            position = wp.position,
        )
        tappedPoiOrigin = TappedPoiOrigin.NavigationTarget
        tappedWaypointId = waypointId
        if (cachedSubtitle == null) {
            triggerReverseGeocode(wp.position)
        }
    }

    fun removeWaypoint(id: String) {
        val idx = waypoints.indexOfFirst { it.id == id }
        if (idx < 0) return
        waypoints.removeAt(idx)
        if (activeWaypointId == id) {
            activeWaypointId = waypoints.firstOrNull()?.id
        }
        persistRoute()
        notifyRefresh()
    }

    fun removeNavigationTarget() {
        val id = tappedWaypointId
        if (id != null) removeWaypoint(id)
        dismissTappedPoi()
        notifyRefresh()
    }

    // --- Cell pipeline helpers ---

    private fun rebuildMergedFeatures() {
        val snapshot = synchronized(cellCache) { cellCache.toMap() }
        val result = ca.voiditswarranty.roadtripradar.viewmodel.rebuildMergedFeatures(snapshot)
        nearbyPoiFeatures = FeatureCollection(result.features)
        poiFetchRegion = result.region
        notifyRefresh()
    }

    /** Drops cached cells that do not intersect the current POI load plate (padded viewport, max 350 km side). */
    private fun evictCachedCellsOutsideLoadPlate(loadPlate: BoundingBox) {
        val toRemove = synchronized(cellCache) {
            evictableCachedCellIds(
                cache = cellCache,
                loadPlate = loadPlate,
                intersects = PoiViewportChunks::boundingBoxesIntersect,
            )
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
        val cachedIds = synchronized(cellCache) { cellCache.keys.toSet() }
        val inFlightIds = synchronized(inFlightCells) { inFlightCells.toSet() }
        val pendingIds = synchronized(pendingCells) { pendingCells.map { it.cell.id }.toSet() }
        val failedIds = synchronized(permanentlyFailedCells) { permanentlyFailedCells.map { it.id }.toSet() }
        val toEnqueue = cellsToEnqueue(
            cells = cells,
            cachedIds = cachedIds,
            inFlightIds = inFlightIds,
            pendingIds = pendingIds,
            failedIds = failedIds,
        )
        if (toEnqueue.isNotEmpty()) {
            synchronized(pendingCells) {
                for (cell in toEnqueue) pendingCells.add(PendingCell(cell))
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
        cellWorkerJob = vmScope.launch {
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
                delay(retryDelayMs(pending.retryCount))
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
        notifyRefresh()
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

        val missingCells = run {
            val cachedIds = synchronized(cellCache) { cellCache.keys.toSet() }
            val inFlightIds = synchronized(inFlightCells) { inFlightCells.toSet() }
            val pendingIds = synchronized(pendingCells) { pendingCells.map { it.cell.id }.toSet() }
            val failedIds = synchronized(permanentlyFailedCells) { permanentlyFailedCells.map { it.id }.toSet() }
            cellsToEnqueue(
                cells = PoiViewportChunks.worldGridCellsIntersecting(loadPlate),
                cachedIds = cachedIds,
                inFlightIds = inFlightIds,
                pendingIds = pendingIds,
                failedIds = failedIds,
            )
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
            notifyRefresh()
            return
        }
        searchJob = vmScope.launch {
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
            notifyRefresh()
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

    private fun computeViewBox(lat: Double, lon: Double, zoom: Double): ViewBox =
        ca.voiditswarranty.roadtripradar.viewmodel.computeViewBox(
            lat = lat, lon = lon, zoom = zoom, screenWidthDp = screenWidthDp, screenHeightDp = screenHeightDp,
        )

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
        waypoints.clear()
        activeWaypointId = null
        autoAdvanceEnabled = PrefsDefaults.AUTO_ADVANCE_ENABLED
        autoAdvanceThresholdMeters = PrefsDefaults.AUTO_ADVANCE_THRESHOLD_M
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
        reconcilePolling()
        notifyRefresh()
    }

    // --- Zoom ---

    fun onZoomChanged(zoom: Float) {
        prefsRepo.zoomLevel = zoom
    }
}
