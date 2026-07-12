package ca.voiditswarranty.roadtripradar.viewmodel

import ca.voiditswarranty.roadtripradar.data.InsertPosition
import ca.voiditswarranty.roadtripradar.data.ViewBox
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.PoiGridCell
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import kotlinx.serialization.json.JsonObject
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pure logic helpers extracted from [MapViewModel] so the central ViewModel
 * stays focused on Compose state + coroutines. Each function is `internal` and
 * top-level so the [MapViewModelLogicTest] can drive them in isolation.
 *
 * **What's here, what's not:** the helpers that take a [MapViewModel] parameter
 * are gone — every helper is a pure function of its inputs. The VM's
 * `cycleWeatherMode` (the car toolbar's 3-state OFF→PLAYING→ON→OFF button) and
 * `toggleWeatherPlayPause` (the phone FAB's 2-state play/pause + long-press off)
 * both call the pure `nextWeatherModeOnCycle`/`nextWeatherModeOnPlayPauseToggle`
 * helpers here. Same pattern for `addWaypoint` / `tutorialNext` / `tutorialBack` /
 * `maybeAutoAdvance` / `evaluateWhatsNewChangelog` / `deleteCustomTheme`.
 *
 * The cell-pipeline state mutations (cellCache / pendingCells / inFlightCells)
 * stay in the VM — those are concurrent-collection ops, not pure logic.
 * The pure *decisions* (which cell to enqueue, which to evict, which to retry)
 * are extracted.
 */

// -------- Cell pipeline data types --------

/** A cached POI cell: the bbox, the features fetched for it, and the timestamp. */
internal data class CachedCell(
    val cellId: String,
    val features: List<Feature<Point, JsonObject>>,
    val fetchedAtMs: Long,
    val bounds: BoundingBox,
)

/** A pending cell (queued for fetch, with a retry counter). */
internal data class PendingCell(
    val cell: PoiGridCell,
    val retryCount: Int = 0,
)

// -------- Weather mode transitions --------

/**
 * Returns the next mode when the user "cycles" the radar: OFF → PLAYING → ON → OFF.
 * The cycle goes to PLAYING first (not ON) so a tap from OFF starts the radar
 * animating immediately.
 */
internal fun nextWeatherModeOnCycle(current: WeatherMode): WeatherMode = when (current) {
    WeatherMode.OFF -> WeatherMode.PLAYING
    WeatherMode.PLAYING -> WeatherMode.ON
    WeatherMode.ON -> WeatherMode.OFF
}

/**
 * Returns the next mode when the user toggles play/pause: OFF → PLAYING, PLAYING → ON,
 * ON → PLAYING. Pause from playing lands on ON (paused but radar visible). Play from
 * OFF starts the animation.
 */
internal fun nextWeatherModeOnPlayPauseToggle(current: WeatherMode): WeatherMode = when (current) {
    WeatherMode.OFF -> WeatherMode.PLAYING
    WeatherMode.PLAYING -> WeatherMode.ON
    WeatherMode.ON -> WeatherMode.PLAYING
}

/**
 * Returns the next mode for the "on/off" toggle: if the radar is on (PLAYING or ON),
 * turn it off; if off, turn it on (in PLAYING state). Toggling "off" from PLAYING
 * skips the pause intermediate and goes straight to OFF.
 */
internal fun nextWeatherModeOnOnOffToggle(weatherActive: Boolean): WeatherMode =
    if (weatherActive) WeatherMode.OFF else WeatherMode.PLAYING

// -------- Format / view math --------

/** Formats a Position as "lat, lon" with 5 decimal places. */
internal fun formatLatLng(position: Position): String =
    "%.5f, %.5f".format(position.latitude, position.longitude)

/**
 * Approximates the visible viewport as a [ViewBox] (west/south/east/north) given
 * the camera center, zoom level, and screen dimensions in dp. Used by the search
 * pipeline to scope a Photon `bbox` query.
 */
internal fun computeViewBox(
    lat: Double,
    lon: Double,
    zoom: Double,
    screenWidthDp: Double,
    screenHeightDp: Double,
): ViewBox {
    val latDelta = 360.0 / 2.0.pow(zoom) * 0.5
    val lonDelta = latDelta / cos(Math.toRadians(lat))
    // The production version of this helper uses a `PoiViewportChunks.approximateViewportBounds`
    // call for the POI pipeline; for the search bbox, the simpler lat/lon-delta math is
    // sufficient. Pin the formula here so the search uses a stable, documented shape.
    @Suppress("UNUSED_PARAMETER")
    val unused = screenWidthDp // reserved for future use
    @Suppress("UNUSED_PARAMETER")
    val unused2 = screenHeightDp
    return ViewBox(
        west = lon - lonDelta,
        south = lat - latDelta,
        east = lon + lonDelta,
        north = lat + latDelta,
    )
}

// -------- Tutorial index math --------

/**
 * Returns the next tutorial-step index for the [stepCount]-sized list. If the
 * caller is on the last step, returns [TutorialEnd] to signal "tutorial done"
 * (the VM marks the group complete and clears the active group).
 */
internal sealed class TutorialEnd {
    object End : TutorialEnd()
}

internal fun nextTutorialStep(currentIndex: Int, stepCount: Int): Any =
    if (currentIndex + 1 >= stepCount) TutorialEnd.End else currentIndex + 1

/** Returns the previous step index, clamped to 0 (no negative indices). */
internal fun previousTutorialStep(currentIndex: Int): Int =
    max(0, currentIndex - 1)

// -------- addWaypoint insert-index decision --------

/**
 * Computes the index at which a new waypoint should be inserted given the
 * current list, the requested [at] position, and the current active waypoint id.
 * The pure decision is the integer index; the VM then inserts and (optionally)
 * promotes the new waypoint to active.
 */
internal fun addWaypointIndex(
    currentSize: Int,
    at: InsertPosition,
    activeWaypointId: String?,
    findById: (String) -> Int,
): Int = when (at) {
    InsertPosition.Start -> 0
    InsertPosition.BeforeLast -> (currentSize - 1).coerceAtLeast(0)
    InsertPosition.End -> currentSize
    is InsertPosition.Index -> at.i.coerceIn(0, currentSize)
    is InsertPosition.ReplaceId -> {
        val idx = findById(at.id)
        if (idx >= 0) idx else currentSize
    }
}

// -------- maybeAutoAdvance --------

/**
 * Returns the index to advance to, or null if no advance should happen. The pure
 * decision: given the current active index, the user position, the active
 * waypoint's position, and the threshold (in meters), should we advance?
 *
 * - `null` if auto-advance is disabled
 * - `null` if no active waypoint
 * - `null` if the active waypoint is the last one (no next)
 * - `null` if the user is more than `thresholdMeters` away
 * - otherwise: the next index
 */
internal fun maybeAutoAdvanceNextIndex(
    autoAdvanceEnabled: Boolean,
    activeIndex: Int?,
    totalWaypoints: Int,
    distanceMeters: Double,
    thresholdMeters: Int,
): Int? {
    if (!autoAdvanceEnabled) return null
    val idx = activeIndex ?: return null
    if (idx >= totalWaypoints - 1) return null
    if (distanceMeters >= thresholdMeters) return null
    return idx + 1
}

// -------- deleteCustomTheme fallback --------

/**
 * Returns the built-in [MapStyle] to fall back to when a custom theme is deleted
 * while it was the active style. CUSTOM_LIGHT falls back to LIBERTY; CUSTOM_DARK
 * falls back to COLOR_DARK. If the deleted style is something other than the
 * two custom variants (or it's a built-in style), returns null — the VM
 * should not change the active style in that case.
 */
internal fun customThemeFallback(deleted: MapStyle): MapStyle? = when (deleted) {
    MapStyle.CUSTOM_LIGHT -> MapStyle.LIBERTY
    MapStyle.CUSTOM_DARK -> MapStyle.COLOR_DARK
    else -> null
}

// -------- evaluateWhatsNewChangelog "should we show?" decision --------

/**
 * Returns true iff the What's New sheet should be shown. The pure decision:
 *
 * - if the user has never seen the changelog (`lastSeenCode == null`), the sheet
 *   is *not* shown — first launch is silent; we just stamp the current code.
 * - if the current version is <= lastSeen, no show.
 * - otherwise: show iff there is at least one release in `(lastSeen, current]`
 *   that's marked `showInApp`.
 *
 * `newReleaseCount` is the count of releases in that range with `showInApp=true`.
 */
internal fun shouldShowWhatsNew(
    lastSeenCode: Int?,
    currentVersionCode: Int,
    newReleaseCount: Int,
): Boolean {
    if (lastSeenCode == null) return false
    if (currentVersionCode <= lastSeenCode) return false
    return newReleaseCount > 0
}

// -------- Retry-delay math --------

/**
 * Returns the inter-cell retry delay for a given retry count, clamped to
 * [MAX_INTER_CELL_DELAY_MS]. Exponential backoff: 1s, 2s, 4s, ... up to 2s cap.
 */
internal fun retryDelayMs(retryCount: Int, maxDelayMs: Long = MAX_INTER_CELL_DELAY_MS): Long =
    min(1_000L shl retryCount, maxDelayMs)

/** Maximum inter-cell retry delay, capped at 2 seconds. */
internal const val MAX_INTER_CELL_DELAY_MS: Long = 2_000L

// -------- POI cell dedupe (pure) --------

/**
 * Returns the subset of [cells] that are not already cached, in flight, pending,
 * or permanently failed. The pure decision over the ID sets; the VM wraps the
 * sets in `synchronized` blocks and passes them in.
 */
internal fun cellsToEnqueue(
    cells: List<PoiGridCell>,
    cachedIds: Set<String>,
    inFlightIds: Set<String>,
    pendingIds: Set<String>,
    failedIds: Set<String>,
): List<PoiGridCell> = cells.filter { cell ->
    cell.id !in cachedIds &&
        cell.id !in inFlightIds &&
        cell.id !in pendingIds &&
        cell.id !in failedIds
}

// -------- POI cell eviction (pure) --------

/**
 * Returns the IDs of cached cells that do NOT intersect [loadPlate]. The VM
 * uses the result to remove the entries.
 */
internal fun evictableCachedCellIds(
    cache: Map<String, CachedCell>,
    loadPlate: BoundingBox,
    intersects: (BoundingBox, BoundingBox) -> Boolean,
): List<String> = cache.entries
    .filter { (_, cell) -> !intersects(cell.bounds, loadPlate) }
    .map { it.key }

// -------- rebuildMergedFeatures (pure over the cache) --------

/**
 * Returns the union of features from all cached cells, plus the bounding box
 * of the union (or null if the cache is empty). The pure computation; the VM
 * just writes the result to its Compose state.
 */
internal data class MergedFeaturesResult(
    val features: List<Feature<Point, JsonObject>>,
    val region: BoundingBox?,
)

internal fun rebuildMergedFeatures(
    cells: Map<String, CachedCell>,
): MergedFeaturesResult {
    if (cells.isEmpty()) return MergedFeaturesResult(features = emptyList(), region = null)
    val all = cells.values.flatMap { it.features }
    var minLat = Double.MAX_VALUE
    var maxLat = -Double.MAX_VALUE
    var minLon = Double.MAX_VALUE
    var maxLon = -Double.MAX_VALUE
    for (cell in cells.values) {
        val sw = cell.bounds.southwest
        val ne = cell.bounds.northeast
        minLat = min(minLat, sw.latitude)
        maxLat = max(maxLat, ne.latitude)
        minLon = min(minLon, sw.longitude)
        maxLon = max(maxLon, ne.longitude)
    }
    return MergedFeaturesResult(
        features = all,
        region = BoundingBox(
            southwest = Position(latitude = minLat, longitude = minLon),
            northeast = Position(latitude = maxLat, longitude = maxLon),
        ),
    )
}
