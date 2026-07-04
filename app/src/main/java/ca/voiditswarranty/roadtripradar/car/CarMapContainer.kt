package ca.voiditswarranty.roadtripradar.car

import android.graphics.Paint
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.car.app.CarContext
import ca.voiditswarranty.roadtripradar.data.RainViewer
import ca.voiditswarranty.roadtripradar.data.activeRouteLeg
import ca.voiditswarranty.roadtripradar.data.inactiveRouteLegs
import ca.voiditswarranty.roadtripradar.data.resolveToConcrete
import ca.voiditswarranty.roadtripradar.data.resolvedStyleUri
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import org.maplibre.android.MapLibre
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
import org.maplibre.spatialk.geojson.LineString
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
                    setupOverlays(loaded)
                    startPositionPolling()
                    // Keep the route arrow oriented to the map bearing as the user pans/zooms.
                    map.addOnCameraMoveListener { routeWidget?.update(map.cameraPosition.bearing) }
                    map.addOnCameraIdleListener {
                        routeWidget?.update(map.cameraPosition.bearing)
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

        // The map fills the surface; the route card floats at the center-end (mirroring the
        // phone NavWidget, which sits at the top of the map overlay). Weather lives in the
        // MapWithContentTemplate content pane (see CarMapScreen), not on the surface.
        val widget = CarRouteWidget(carContext, vm)
        routeWidget = widget
        return FrameLayout(carContext).apply {
            addView(
                mapView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                widget.view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL or Gravity.END,
                ),
            )
        }
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

    /** Instantly center the map on [pos] at [zoom]. */
    private fun centerOn(pos: Position, zoom: Double) {
        val map = mapLibreMapInstance ?: return
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pos.latitude, pos.longitude), zoom))
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
        // exists".
        style = null
        radarSourceIds.clear()
        radarLayerIds.clear()
        builtRadarKey = emptyList()
        map.setStyle(Style.Builder().fromUri(uri)) { loaded ->
            style = loaded
            setupOverlays(loaded)
            refreshFromVm()
        }
    }

    private fun setupOverlays(style: Style) {
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
        val style = this.style ?: return
        val bearing = mapLibreMapInstance?.cameraPosition?.bearing ?: 0.0
        reconcileAnimation()
        updateRadar(style)
        updateRoute(style)
        updatePuck(style)
        routeWidget?.update(bearing)
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
        val displayIndex = if (paths.isEmpty()) 0
            else if (driving || vm.weatherMode == WeatherMode.ON) paths.lastIndex
            else carFrameIndex
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
                driving = moved > MOVEMENT_DEG_THRESHOLD
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
        // textureMode(true) makes the MapView render via a TextureView instead of a GLSurfaceView.
        // This is the MapLibre Android Auto sample's approach and is needed on the car's virtual
        // display: a SurfaceView's surface composites unreliably relative to sibling views there,
        // which would hide the weather widget overlay. TextureView renders in the normal view
        // hierarchy so the widget composites on top. (Not vector-vs-raster — it's the render
        // surface type; the documented perf penalty is acceptable for a weather/radar map.)
        val options = runCatching { MapLibreMapOptions.createFromAttributes(carContext) }
            .getOrNull() ?: @Suppress("DEPRECATION") MapLibreMapOptions()
        return MapView(carContext, options.textureMode(true)).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, Paint())
        }
    }

    private fun pointGeoJson(pos: Position): String =
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${pos.longitude},${pos.latitude}]}}"""

    /** Serialize a list of route legs (the dashed planned legs) to a GeoJSON FeatureCollection. */
    private fun lineStringsGeoJson(legs: List<LineString>): String {
        if (legs.isEmpty()) return EMPTY_FEATURE_COLLECTION
        val features = legs.joinToString(",") { leg ->
            val coords = leg.coordinates.joinToString(",") { "[${it.longitude},${it.latitude}]" }
            """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]}}"""
        }
        return """{"type":"FeatureCollection","features":[$features]}"""
    }

    /** Serialize a single route leg (the solid approach leg) to a GeoJSON FeatureCollection. */
    private fun lineStringGeoJson(leg: LineString?): String {
        if (leg == null) return EMPTY_FEATURE_COLLECTION
        val coords = leg.coordinates.joinToString(",") { "[${it.longitude},${it.latitude}]" }
        return """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]}}]}"""
    }

    companion object {
        private const val POSITION_POLL_MS = 2000L
        private const val MOVEMENT_DEG_THRESHOLD = 0.0001 // ~11 m per poll; above = driving-paced
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
        private const val EMPTY_FEATURE_COLLECTION = """{"type":"FeatureCollection","features":[]}"""
    }
}