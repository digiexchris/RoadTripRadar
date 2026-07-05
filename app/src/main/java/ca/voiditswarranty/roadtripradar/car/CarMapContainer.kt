package ca.voiditswarranty.roadtripradar.car

import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.car.app.CarContext
import ca.voiditswarranty.roadtripradar.data.RainViewer
import ca.voiditswarranty.roadtripradar.data.activeRouteLeg
import ca.voiditswarranty.roadtripradar.data.inactiveRouteLegs
import ca.voiditswarranty.roadtripradar.data.isDarkForAppTheme
import ca.voiditswarranty.roadtripradar.data.resolveToConcrete
import ca.voiditswarranty.roadtripradar.data.resolvedStyleUri
import ca.voiditswarranty.roadtripradar.model.RadarRingsData
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import ca.voiditswarranty.roadtripradar.model.buildRadarRingsData
import ca.voiditswarranty.roadtripradar.model.ringDistancesForZoom
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.spatialk.geojson.Position
import kotlin.math.abs
import kotlin.math.ln

/**
 * Owns the native MapLibre [MapView] rendered onto the Android Auto surface, plus the
 * overlays that mirror the phone map: the RainViewer radar raster tiles, the waypoint route
 * line, and the user's location puck.
 *
 * The car surface path is the *native* MapLibre Android SDK (`org.maplibre.android`), not
 * `maplibre-compose`: the Car App Library hands us a [android.view.Surface] via a
 * `SurfaceCallback`, and we draw onto it by showing a [MapView] on a
 * [android.app.Presentation] backed by a virtual display bound to that surface (see
 * [CarMapRenderer]).
 *
 * Overlays are updated from the shared [MapViewModel]: a refresh-bus listener hops to the main
 * thread (MapLibre is main-thread-only) and re-syncs radar opacities / route geometry / puck
 * whenever the VM mutates. The user's position is **read from the VM** — the phone already
 * computes a correct [Position] (via maplibre-compose's location state) and exposes it as
 * [MapViewModel.userPositionForSearch] / [MapViewModel.lastKnownPosition]; the car does NOT run
 * its own LocationManager or reconstruct positions (an earlier attempt did, and got the
 * `Position(longitude, latitude)` argument order wrong, crashing on any longitude > 90°). A light
 * poll re-pulls that position so the puck tracks, and derives "driving" from position movement for
 * the SA-1 radar freeze. The style is resolved against the car's current day/night mode
 * ([CarContext.isDarkMode]) and reloaded on config change (MR-1).
 */
class CarMapContainer(
    private val carContext: CarContext,
    private val vm: MapViewModel,
) {
    var mapViewInstance: MapView? = null
        private set

    var mapLibreMapInstance: MapLibreMap? = null
        private set

    private var style: Style? = null
    private var lastStyleUri: String? = null

    private val uiHandler = Handler(Looper.getMainLooper())

    // Radar sources/layers, rebuilt when the RainViewer frame set changes.
    private val radarSourceIds = mutableListOf<String>()
    private val radarLayerIds = mutableListOf<String>()
    private var builtRadarKey: List<String> = emptyList()

    /**
     * The car surface runs its *own* radar animation (independent of the VM playhead) so it
     * can be frozen the moment the vehicle is in motion — SA-1 forbids animated content while
     * driving. The VM's animation loop only mutates `currentFrameIndex` for the phone (via
     * Compose snapshot) and is intentionally not on the refresh bus, so reusing it would either
     * never animate the car or spam template invalidates. Instead the container cycles this
     * local index at the same 500 ms cadence, but only while parked and the user has the
     * radar playing; while driving (or when paused) it pins to the latest frame (`lastIndex`),
     * i.e. exactly what the phone shows when the play/pause button is in the pause state.
     */
    private var carFrameIndex = 0
    private val animHandler = Handler(Looper.getMainLooper())
    private val animRunnable = Runnable { advanceCarFrame() }
    private var animRunning = false

    // The user's position is read from the VM (phone-computed). A light poll re-pulls it so the
    // puck tracks, and "driving" is derived from position movement for the SA-1 radar freeze.
    private val posRunnable = Runnable { pollPosition() }
    private var posPolling = false
    private var lastPollPos: Position? = null
    private var driving = false

    private val refreshListener: (MapViewModel) -> Unit = {
        uiHandler.post { refreshFromVm() }
    }

    private var routeWidget: CarRouteWidget? = null
    private var weatherWidget: CarWeatherWidget? = null
    private var rangeRingLabels: CarRangeRingLabels? = null

    /**
     * Current range-ring geometry + label positions, set by [updateRangeRings] on bracket
     * change. Cached so that pure camera-move ticks (pan/rotate) can re-project the labels
     * without rebuilding the rings, and so the camera-idle handler can detect when the
     * underlying data has actually changed vs. just shifted on screen.
     */
    private var rangeRingsData: RadarRingsData? = null
    private var rangeRingsCenter: Position? = null
    private var lastZoomForRangeRings: Double? = null

    /**
     * The FrameLayout holding the MapView + overlay cards, sized/positioned to the host's current
     * visible-area rect (see [setVisibleArea]). Null until [setupMap] runs.
     */
    private var mapHost: FrameLayout? = null
    private var latestVisibleArea: Rect? = null

    init {
        vm.addRefreshListener(refreshListener)
    }

    /** Build the map view tree to attach to the surface's virtual display. Main thread only. */
    fun setupMap(): View {
        MapLibre.getInstance(carContext)

        val mapView = createMapView().apply {
            onStart()
            getMapAsync { map ->
                mapLibreMapInstance = map
                val uri = carStyleUri()
                lastStyleUri = uri
                map.setStyle(Style.Builder().fromUri(uri)) { loaded ->
                    style = loaded
                    Log.i(LOG_TAG, "style loaded: $uri")
                    setupOverlays(loaded)
                    startPositionPolling()
                    // Keep the route arrow and the wind arrow oriented to the map bearing as the
                    // user pans/zooms (both rotate relative to the camera). Also re-project the
                    // range-ring label TextViews — the label points on the ring don't change,
                    // but their screen positions do as the map pans/rotates.
                    map.addOnCameraMoveListener {
                        val bearing = map.cameraPosition.bearing
                        routeWidget?.update(bearing)
                        weatherWidget?.update(bearing)
                        updateRangeRingLabels()
                    }
                    map.addOnCameraIdleListener {
                        val bearing = map.cameraPosition.bearing
                        routeWidget?.update(bearing)
                        weatherWidget?.update(bearing)
                        updateRangeRingLabels()
                        // Persist the car zoom so it survives app exit/restart, mirroring the
                        // phone (vm.onZoomChanged -> prefsRepo.zoomLevel) but on the car's own
                        // key so the two surfaces keep independent zoom levels.
                        persistCarZoom(map.cameraPosition.zoom)
                    }
                    refreshFromVm()
                    // Center on the current position immediately so the map opens on the user,
                    // not a world view (the camera otherwise stays at the default).
                    centerOnCurrentPosition()
                }
            }
        }
        mapViewInstance = mapView

        // The map and its two overlay cards (route center-left, weather right) live inside a host
        // FrameLayout sized/positioned to the current visible-area rect. The host composites our
        // full 800x400 surface at full-screen scale and overlays the media panel / host chrome on
        // the parts OUTSIDE the visible-area rect — so by rendering the map only into that rect,
        // the whole map shrinks to fit the visible region in split (instead of being cropped by
        // the media panel). The non-rendered area is, by definition, occluded by host chrome / the
        // media panel, so there's no visible blank strip. See [setVisibleArea].
        val isDarkInitial = vm.mapStyle.isDarkForAppTheme(carContext, carContext.isDarkMode())
        val route = CarRouteWidget(carContext, vm, initialIsDark = isDarkInitial)
        routeWidget = route
        val weather = CarWeatherWidget(carContext, vm, initialIsDark = isDarkInitial)
        weatherWidget = weather
        // Initial label colors match the resolved style so the first frame is already readable
        // (no flash of black-on-white on a dark map). `reloadStyleIfNeeded` calls setDark() on
        // subsequent style changes; the constructor default would only be visible in the brief
        // pre-style-load window.
        val labels = CarRangeRingLabels(carContext, vm, isDark = isDarkInitial)
        rangeRingLabels = labels
        val host = FrameLayout(carContext).apply {
            addView(
                mapView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                route.view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL or Gravity.START,
                ),
            )
            addView(
                weather.view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL or Gravity.END,
                ),
            )
            // Range-ring labels: positioned via x/y in updateRangeRingLabels() on every
            // camera move. The widget itself is anchored at (0, 0) in the host and spans
            // the full host size so the label TextViews can use host-pixel coordinates
            // directly. Sits on top of the MapView and the route/weather cards so the
            // labels are never obscured.
            addView(
                labels,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        mapHost = host
        return FrameLayout(carContext).apply {
            addView(host, mapHostLayoutParams())
        }
    }

    /**
     * Layout params for the map host: the current visible-area rect (width × height at its
     * top-left offset), or MATCH_PARENT when no visible area has been reported yet (so the map
     * fills the surface until the first [onVisibleAreaChanged]).
     */
    private fun mapHostLayoutParams(): FrameLayout.LayoutParams {
        val rect = latestVisibleArea
        return if (rect != null && rect.width() > 0 && rect.height() > 0) {
            FrameLayout.LayoutParams(rect.width(), rect.height(), Gravity.TOP or Gravity.START)
                .apply { leftMargin = rect.left; topMargin = rect.top }
        } else {
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    /**
     * Size/position the map (and its overlay cards) to the host's visible-area rect, received via
     * [CarMapRenderer.onVisibleAreaChanged]. No-op if the rect is unchanged. The MapView re-lays
     * out and MapLibre re-renders at the new size, keeping the camera centered so the puck stays
     * centered in the visible region. Also re-projects the range-ring labels against the new
     * pixel coordinate system (the host's pixel space has shifted).
     */
    fun setVisibleArea(rect: Rect) {
        if (rect == latestVisibleArea) return
        latestVisibleArea = rect
        val host = mapHost ?: return
        host.layoutParams = mapHostLayoutParams()
        host.requestLayout()
        // The MapView's pixel coordinate system has shifted with the relayout — re-project the
        // label points to their new screen positions. The ring geometry itself is unchanged.
        updateRangeRingLabels()
    }

    /** Pan gesture forwarded from the host's [androidx.car.app.SurfaceCallback]. */
    fun scrollBy(x: Float, y: Float) {
        mapLibreMapInstance?.scrollBy(-x, -y, 0L)
    }

    /** Pinch-zoom gesture forwarded from the host. */
    fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        val map = mapLibreMapInstance ?: return
        val zoomAdditional = ln(scaleFactor.toDouble()) / ln(2.0)
        map.setZoom(map.cameraPosition.zoom + zoomAdditional, PointF(focusX, focusY), 0L)
    }

    /**
     * Recenter on the current position, keeping the user's zoom. Uses [MapLibreMap.moveCamera]
     * (instant), not `animateCamera`: the MapLibre Android Auto sample deliberately avoids camera
     * *animation* on the car surface, and `animateCamera` here crashed the host process when the
     * recenter action was tapped. Instant moves are the proven-safe path on the virtual display.
     */
    fun recenter() {
        val pos = currentPosition() ?: return
        val map = mapLibreMapInstance ?: return
        val zoom = map.cameraPosition.zoom.takeIf { it > 0 } ?: savedCarZoom()
        centerOn(pos, zoom)
    }

    /**
     * Instantly center the map on [pos] at [zoom], applying the user-configurable
     * [PrefsDefaults.MAP_CENTER_OFFSET_CAR_FRACTION] so the user appears at that fraction from
     * the bottom of the visible area — mirroring the phone's portrait/landscape offset slider.
     * Padding is in pixels (CameraPosition padding is in map-view pixels); at fraction ≤ 0.5 we
     * add top padding to push the puck down, at > 0.5 we add bottom padding to push it up. No
     * padding at exactly 0.5 (the natural center).
     */
    private fun centerOn(pos: Position, zoom: Double) {
        val map = mapLibreMapInstance ?: return
        val builder = CameraPosition.Builder()
            .target(LatLng(pos.latitude, pos.longitude))
            .zoom(zoom)
        carOffsetPadding()?.let { (top, bottom) -> builder.padding(0.0, top, 0.0, bottom) }
        map.moveCamera(CameraUpdateFactory.newCameraPosition(builder.build()))
    }

    /**
     * Top + bottom padding (in map-view pixels) implied by the current
     * [ca.voiditswarranty.roadtripradar.model.PrefsDefaults.MAP_CENTER_OFFSET_CAR_FRACTION] and
     * the map host's current height. Returns null when no padding should be applied: the host
     * hasn't been laid out yet (height = 0) OR the fraction is exactly 0.5 (natural center).
     * Mirrors the phone's [ca.voiditswarranty.roadtripradar.ui.MapScreen] formula so the same
     * fraction produces the same visual offset on both surfaces.
     *
     * The math itself is the pure helper [carOffsetPadding]; this wrapper supplies the
     * instance-bound inputs (map host height + the user's pref).
     */
    private fun carOffsetPadding(): Pair<Double, Double>? {
        val height = mapHost?.height ?: 0
        return carOffsetPadding(
            mapHostHeight = height,
            fraction = vm.prefsRepo.mapCenterOffsetCarFraction,
        )
    }

    /**
     * Re-apply the current car-offset padding to the live camera without changing target/zoom/
     * bearing/tilt. Called from [refreshFromVm] so a phone-side slider change takes effect
     * live on the car surface (the slider's `onValueChangeFinished` saves the pref, which
     * triggers a refresh-bus notification). No-op when the host isn't laid out, the fraction
     * is 0.5, or the new padding is identical to the camera's current padding.
     */
    private fun applyCarOffsetPadding() {
        val map = mapLibreMapInstance ?: return
        val (top, bottom) = carOffsetPadding() ?: return
        val current = map.cameraPosition
        val currentPadding = current.padding
        // CameraPosition padding is [left, top, right, bottom]; compare indices 1 and 3.
        if (currentPadding != null &&
            currentPadding.getOrNull(1) == top &&
            currentPadding.getOrNull(3) == bottom
        ) return
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(current)
                    .padding(0.0, top, 0.0, bottom)
                    .build(),
            ),
        )
    }

    /**
     * Center on the best available position once the style has loaded, so the user sees their
     * location immediately instead of a world view (the camera otherwise stays at the default).
     */
    private fun centerOnCurrentPosition() {
        val pos = currentPosition() ?: return
        val map = mapLibreMapInstance ?: return
        // Fall back to the last persisted car zoom (not a hard-coded constant) so the map reopens
        // at the zoom the user left it, independent of the phone's saved zoom.
        val zoom = map.cameraPosition.zoom.takeIf { it > 0 } ?: savedCarZoom()
        Log.i(LOG_TAG, "centerOnCurrentPosition: ${pos.latitude},${pos.longitude} zoom=$zoom")
        centerOn(pos, zoom)
    }

    /** The persisted car-surface zoom (defaults to [PrefsDefaults.CAR_ZOOM_LEVEL] when never set). */
    private fun savedCarZoom(): Double = vm.prefsRepo.carZoomLevel.toDouble()

    /** Persist the current car zoom so it survives app exit/restart (separate from the phone). */
    private fun persistCarZoom(zoom: Double) {
        if (zoom > 0) vm.prefsRepo.carZoomLevel = zoom.toFloat()
    }

    /** The phone-computed position from the VM (live when the phone surface is active, else persisted). */
    private fun currentPosition(): Position? = vm.userPositionForSearch ?: vm.lastKnownPosition

    /**
     * Re-resolve the style against the car's day/night mode and reload it if it changed (e.g.
     * the car switched to night). Invoked from the session's car-configuration-changed callback.
     */
    fun reloadStyleIfNeeded() {
        val map = mapLibreMapInstance ?: return
        val uri = carStyleUri()
        if (uri == lastStyleUri) return
        lastStyleUri = uri
        // The new style replaces the old; all custom sources/layers are gone with it, so reset
        // the radar bookkeeping and re-add the overlays once the new style loads. Nulling `style`
        // also guards overlay updates during the async load window: without it, a refresh firing
        // mid-reload would try to re-add radar sources to the *old* Style (whose ids we just
        // cleared from the bookkeeping but which still holds them), throwing "source already
        // exists". Same reasoning applies to the range rings — reset their cached geometry
        // (bracket, center, data) so the next refresh rebuilds them against the new style.
        style = null
        radarSourceIds.clear()
        radarLayerIds.clear()
        builtRadarKey = emptyList()
        rangeRingsData = null
        rangeRingsCenter = null
        lastZoomForRangeRings = null
        map.setStyle(Style.Builder().fromUri(uri)) { loaded ->
            style = loaded
            setupOverlays(loaded)
            // setupOverlays resolves the new theme for the ring line color. The label TextView
            // widget survives the style swap (it's a regular View, not a MapLibre layer), so it
            // needs an explicit re-theme call here. `setDark` short-circuits if the value
            // didn't change, so this is cheap on every reload.
            val darkNow = vm.mapStyle.isDarkForAppTheme(carContext, carContext.isDarkMode())
            rangeRingLabels?.setDark(darkNow)
            routeWidget?.setDark(darkNow)
            weatherWidget?.setDark(darkNow)
            refreshFromVm()
        }
    }

    private fun setupOverlays(style: Style) {
        // Range rings, added first so we can place subsequent layers ABOVE them with addLayerAbove.
        // The rings themselves sit BELOW the route so the route line is never obscured by a ring.
        // Mirrors the phone's "rings below route" intent (ui/MapLayers.kt:RadarRingsLayers).
        // Ring color is theme-aware (light gray on dark, black on light) so the rings stay
        // visible on the current base map. The exact ARGB values come from carRingColor() and
        // match the phone's Compose `Color.LightGray`/`Color.Black` constants.
        val isDark = vm.mapStyle.isDarkForAppTheme(carContext, carContext.isDarkMode())
        style.addSource(GeoJsonSource(RANGE_RINGS_SOURCE_ID, EMPTY_FEATURE_COLLECTION))
        style.addLayer(
            LineLayer(RANGE_RINGS_LAYER_ID, RANGE_RINGS_SOURCE_ID).withProperties(
                lineColor(carRingColor(isDark)),
                lineWidth(RANGE_RING_WIDTH),
                lineOpacity(RANGE_RING_OPACITY),
                lineCap("round"),
                lineJoin("round"),
                lineDasharray(RANGE_RING_DASH),
            )
        )
        // Route, drawn as two layers mirroring the phone (ui.MapLayers.WaypointRouteLineLayer):
        // the dashed planned legs between consecutive waypoints, then the solid live approach
        // leg from the user's position to the active target on top. Both share the same green;
        // geometry comes from the shared `data.RouteGeometry` builders.
        style.addSource(GeoJsonSource(ROUTE_INACTIVE_SOURCE_ID, EMPTY_FEATURE_COLLECTION))
        style.addLayer(
            LineLayer(ROUTE_INACTIVE_LAYER_ID, ROUTE_INACTIVE_SOURCE_ID).withProperties(
                lineColor(ROUTE_COLOR),
                lineWidth(ROUTE_WIDTH),
                lineOpacity(INACTIVE_OPACITY),
                lineCap("round"),
                lineJoin("round"),
                lineDasharray(INACTIVE_DASH),
            )
        )
        style.addSource(GeoJsonSource(ROUTE_ACTIVE_SOURCE_ID, EMPTY_FEATURE_COLLECTION))
        style.addLayer(
            LineLayer(ROUTE_ACTIVE_LAYER_ID, ROUTE_ACTIVE_SOURCE_ID).withProperties(
                lineColor(ROUTE_COLOR),
                lineWidth(ROUTE_WIDTH),
                lineOpacity(ACTIVE_OPACITY),
                lineCap("round"),
                lineJoin("round"),
            )
        )
        // User location puck (on top of everything).
        style.addSource(GeoJsonSource(PUCK_SOURCE_ID, EMPTY_FEATURE_COLLECTION))
        style.addLayer(
            CircleLayer(PUCK_LAYER_ID, PUCK_SOURCE_ID).withProperties(
                circleColor(0xFF1565C0.toInt()),
                circleRadius(9f),
                circleStrokeColor(0xFFFFFFFF.toInt()),
                circleStrokeWidth(3f),
            )
        )
    }

    /** Re-sync all overlays from the VM. Must run on the main thread. */
    private fun refreshFromVm() {
        // A theme change on the phone (vm.updateMapStyle -> notifyRefresh) reaches us here; pick
        // up a new style URI (or an AUTO resolution flip from a day/night change) before refreshing
        // overlays. Cheap no-op when the URI is unchanged.
        reloadStyleIfNeeded()
        // Re-apply the user-configurable car center-offset padding. The VM fires a refresh whenever
        // the phone's slider saves a new value, so this propagates slider changes live to the car
        // surface. Runs before the style-early-return because the padding update only needs the
        // camera + map host — not the loaded style — and we want a slider change in the brief
        // pre-style-load window to take effect too.
        applyCarOffsetPadding()
        val style = this.style ?: return
        val bearing = mapLibreMapInstance?.cameraPosition?.bearing ?: 0.0
        reconcileAnimation()
        updateRadar(style)
        updateRangeRings(style)
        updateRoute(style)
        updatePuck(style)
        routeWidget?.update(bearing)
        weatherWidget?.update(bearing)
        updateRangeRingLabels()
    }

    private fun updateRadar(style: Style) {
        val paths = vm.radarFramePaths
        if (paths != builtRadarKey) {
            // Frame set changed (new RainViewer poll): tear down old radar and build fresh.
            radarLayerIds.forEach { style.removeLayer(it) }
            radarSourceIds.forEach { style.removeSource(it) }
            radarLayerIds.clear()
            radarSourceIds.clear()
            paths.forEachIndexed { i, path ->
                val sourceId = "car-radar-$i"
                val layerId = "car-radar-layer-$i"
                // maxZoom mirrors the phone (shared RainViewer.MAX_ZOOM): RainViewer only serves
                // up to z7, so cap the tile set and let MapLibre overzoom the z7 tile instead of
                // requesting z>7 (which RainViewer rejects with "zoom level not supported").
                val tileSet = TileSet("rainviewer-$i", RainViewer.tileUrl(path))
                    .apply { setMaxZoom(RainViewer.MAX_ZOOM.toFloat()) }
                style.addSource(RasterSource(sourceId, tileSet, RainViewer.TILE_SIZE_PX))
                // Below the route line so the route/puck stay visible above the radar.
                style.addLayerBelow(
                    RasterLayer(layerId, sourceId).withProperties(rasterOpacity(0f)),
                    ROUTE_INACTIVE_LAYER_ID,
                )
                radarSourceIds.add(sourceId)
                radarLayerIds.add(layerId)
            }
            builtRadarKey = paths
            if (carFrameIndex > paths.lastIndex.coerceAtLeast(0)) carFrameIndex = paths.lastIndex.coerceAtLeast(0)
        }
        val show = vm.weatherMode != WeatherMode.OFF && paths.isNotEmpty()
        // While driving (or when the radar is paused), pin to the latest frame — SA-1 forbids
        // animated content in a moving vehicle, and "paused" on the phone shows lastIndex.
        // While parked with the radar playing, follow the car-local animation playhead.
        // The display-index selection is the pure helper [carRadarDisplayIndex] (extracted
        // for testability); the for-loop below still owns the per-layer opacity update
        // because that's the MapLibre-coupled part.
        val displayIndex = carRadarDisplayIndex(
            pathCount = paths.size,
            driving = driving,
            mode = vm.weatherMode,
            carFrameIndex = carFrameIndex,
        )
        paths.forEachIndexed { i, _ ->
            val layer = style.getLayer("car-radar-layer-$i") as? RasterLayer ?: return@forEachIndexed
            val opacity = if (show && i == displayIndex) vm.radarOpacity else 0f
            layer.setProperties(rasterOpacity(opacity))
        }
    }

    /**
     * Start/stop the car-local radar animation. It runs only while parked (not driving) AND the
     * user has the radar playing ([WeatherMode.PLAYING]) with ≥2 frames to cycle. Otherwise the
     * radar is static (pinned to the latest frame by [updateRadar]).
     */
    private fun reconcileAnimation() {
        val shouldAnimate = !driving &&
            vm.weatherMode == WeatherMode.PLAYING &&
            vm.radarFramePaths.size > 1
        when {
            shouldAnimate && !animRunning -> {
                animRunning = true
                animHandler.postDelayed(animRunnable, ANIM_INTERVAL_MS)
            }
            !shouldAnimate && animRunning -> {
                animRunning = false
                animHandler.removeCallbacks(animRunnable)
            }
        }
    }

    private fun advanceCarFrame() {
        val paths = vm.radarFramePaths
        if (paths.size > 1) carFrameIndex = (carFrameIndex + 1) % paths.size
        style?.let { updateRadar(it) }
        // Re-post only if still allowed; reconcileAnimation() will have stopped us otherwise,
        // but this guards against a tick landing before the refresh-bus update is processed.
        if (!driving && vm.weatherMode == WeatherMode.PLAYING && vm.radarFramePaths.size > 1) {
            animHandler.postDelayed(animRunnable, ANIM_INTERVAL_MS)
        } else {
            animRunning = false
        }
    }

    private fun updateRoute(style: Style) {
        val waypoints = vm.waypoints
        val userPos = currentPosition()
        // Geometry is shared with the phone via `data.RouteGeometry`: dashed planned legs between
        // consecutive waypoints, plus the solid live approach leg from the user to the active
        // target (previously the car drew only a single line through the waypoints and dropped
        // the approach leg — a regression of the phone's active-route behavior).
        val inactive = inactiveRouteLegs(waypoints)
        val active = userPos?.let { activeRouteLeg(waypoints, vm.activeIndex, it) }
        (style.getSource(ROUTE_INACTIVE_SOURCE_ID) as? GeoJsonSource)
            ?.setGeoJson(lineStringsGeoJson(inactive))
        (style.getSource(ROUTE_ACTIVE_SOURCE_ID) as? GeoJsonSource)
            ?.setGeoJson(lineStringGeoJson(active))
    }

    private fun updatePuck(style: Style) {
        val pos = currentPosition() ?: return
        (style.getSource(PUCK_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(pointGeoJson(pos))
    }

    /**
     * Rebuild the range-ring geometry if the user has moved to a new center OR the zoom has
     * crossed a ring-distance boundary. A no-op when both are still the same as the cached
     * data — the labels still need re-projection (that's [updateRangeRingLabels]) but the
     * ring geometry itself hasn't changed.
     */
    private fun updateRangeRings(style: Style) {
        val map = mapLibreMapInstance ?: return
        val pos = currentPosition() ?: return
        val zoom = map.cameraPosition.zoom
        if (!shouldRebuildRings(currentCenter = pos, lastCenter = rangeRingsCenter,
                currentZoom = zoom, lastZoom = lastZoomForRangeRings)) return
        val distances = ringDistancesForZoom(zoom)
        val bearing = map.cameraPosition.bearing
        val data = buildRadarRingsData(pos, distances, bearing, vm.useMetric)
        rangeRingsData = data
        rangeRingsCenter = pos
        lastZoomForRangeRings = zoom
        (style.getSource(RANGE_RINGS_SOURCE_ID) as? GeoJsonSource)
            ?.setGeoJson(serializeRingsFeatures(data.ringsFeatures))
    }

    /**
     * Re-project each ring's 12-o'clock label point to screen pixels and hand the list to the
     * TextView overlay. Called on every camera move (the label points on the ring don't change
     * when the user pans, but their screen positions do), and on visible-area changes (the
     * MapView's pixel coordinate system shifts on layout). Reads from the cached
     * [rangeRingsData] — no work to do if [updateRangeRings] hasn't run yet.
     */
    private fun updateRangeRingLabels() {
        val data = rangeRingsData ?: return
        val map = mapLibreMapInstance ?: return
        val widget = rangeRingLabels ?: return
        val projection = map.projection
        val items = data.labelsFeatures.features.map { feature ->
            val pos = feature.geometry.coordinates
            // GeoJSON uses [lon, lat] order; native Projection.toScreenLocation takes
            // LatLng(lat, lon) — flipped. (Same conversion as `centerOn`.)
            val screen = projection.toScreenLocation(LatLng(pos.latitude, pos.longitude))
            CarRangeRingLabels.LabelItem(
                screenPoint = screen,
                text = feature.properties?.get("label")?.jsonPrimitive?.content ?: "",
            )
        }
        widget.updateLabels(items)
    }

    /**
     * Start a light poll that re-pulls the phone-computed position from the VM so the puck tracks,
     * and derives "driving" from position movement for the SA-1 radar freeze. The phone surface
     * updates [MapViewModel.userPositionForSearch] live (via maplibre-compose); this just observes
     * it on a cadence the car can render, without running its own location provider.
     */
    private fun startPositionPolling() {
        posPolling = true
        uiHandler.postDelayed(posRunnable, POSITION_POLL_MS)
    }

    private fun stopPositionPolling() {
        posPolling = false
        uiHandler.removeCallbacks(posRunnable)
    }

    private fun pollPosition() {
        val pos = currentPosition()
        if (pos != null) {
            val last = lastPollPos
            if (last != null) {
                // Rough movement check (~0.0001° ≈ 11 m). Above this over one poll interval is
                // driving-paced; below it (parked, or GPS jitter) follows the play/pause button.
                val moved = abs(pos.latitude - last.latitude) + abs(pos.longitude - last.longitude)
                driving = isDriving(moved)
            }
            lastPollPos = pos
        }
        refreshFromVm()
        if (posPolling) uiHandler.postDelayed(posRunnable, POSITION_POLL_MS)
    }

    fun cleanUpMap() {
        animRunning = false
        animHandler.removeCallbacks(animRunnable)
        stopPositionPolling()
        routeWidget = null
        weatherWidget = null
        rangeRingLabels?.dispose()
        rangeRingLabels = null
        rangeRingsData = null
        rangeRingsCenter = null
        lastZoomForRangeRings = null
        mapLibreMapInstance = null
        style = null
        mapViewInstance?.run {
            onStop()
            onDestroy()
        }
        mapViewInstance = null
    }

    /** Drop the VM refresh listener. Called when the owning screen is destroyed. */
    fun dispose() {
        animRunning = false
        animHandler.removeCallbacks(animRunnable)
        stopPositionPolling()
        vm.removeRefreshListener(refreshListener)
    }

    /**
     * Resolve the map style URI against the car's current day/night mode (MR-1). AUTO is
     * resolved with [CarContext.isDarkMode] as the night signal (the system configuration may not
     * reflect the car's actual day/night state) and honors the user's custom-AUTO theme prefs,
     * mirroring the phone's [resolveToConcrete] AUTO branch.
     */
    private fun carStyleUri(): String {
        val concrete = vm.mapStyle.resolveToConcrete(carContext, carContext.isDarkMode())
        return concrete.resolvedStyleUri(carContext)
    }

    private fun createMapView(): MapView {
        // Match the MapLibre Android Auto sample: a default (GLSurfaceView-backed) MapView with a
        // hardware layer type. The route/weather widget overlays are FrameLayout siblings added
        // after the MapView, so they draw on top of the GLSurfaceView's punched hole — the sample
        // layers a TextView the same way and it composites fine.
        val options = runCatching { MapLibreMapOptions.createFromAttributes(carContext) }
            .getOrNull() ?: @Suppress("DEPRECATION") MapLibreMapOptions()
        return MapView(carContext, options).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, Paint())
        }
    }

    companion object {
        private const val LOG_TAG = "CarMapContainer"
        private const val POSITION_POLL_MS = 2000L
        // MOVEMENT_DEG_THRESHOLD (~11 m per poll) lives in CarMapContainerLogic so the pure
        // isDriving() helper can default to it; both surfaces read the same constant.
        private const val ANIM_INTERVAL_MS = 500L
        private const val ROUTE_COLOR = 0xFF2E7D32.toInt()
        private const val ROUTE_WIDTH = 6f
        private const val ACTIVE_OPACITY = 0.95f
        private const val INACTIVE_OPACITY = 0.6f
        private val INACTIVE_DASH = arrayOf(2f, 3f)
        private const val ROUTE_INACTIVE_SOURCE_ID = "car-route-inactive"
        private const val ROUTE_INACTIVE_LAYER_ID = "car-route-inactive-layer"
        private const val ROUTE_ACTIVE_SOURCE_ID = "car-route-active"
        private const val ROUTE_ACTIVE_LAYER_ID = "car-route-active-layer"
        private const val PUCK_SOURCE_ID = "car-puck"
        private const val PUCK_LAYER_ID = "car-puck-layer"
        // Range rings (mirrors the phone's ui/MapLayers.RadarRingsLayers). Line width/opacity/
        // dash are fixed; color is theme-aware — set per-load by setupOverlays via
        // carRingColor(isDark), re-derived on each style change in reloadStyleIfNeeded.
        private const val RANGE_RINGS_SOURCE_ID = "car-range-rings"
        private const val RANGE_RINGS_LAYER_ID = "car-range-rings-layer"
        private const val RANGE_RING_WIDTH = 3.5f
        private const val RANGE_RING_OPACITY = 0.7f
        private val RANGE_RING_DASH = arrayOf(4f, 3f)
        private const val EMPTY_FEATURE_COLLECTION = """{"type":"FeatureCollection","features":[]}"""
    }
}