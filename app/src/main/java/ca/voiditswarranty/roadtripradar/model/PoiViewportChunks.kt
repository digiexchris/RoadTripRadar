package ca.voiditswarranty.roadtripradar.model

import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val KM_PER_DEG_LAT = 111.0
private const val CHUNK_MAX_KM = 25.0

/** ~25 km in degrees (latitude); same step used for longitude for a simple world-fixed grid. */
private const val GRID_STEP_LAT_DEG = CHUNK_MAX_KM / KM_PER_DEG_LAT
private const val GRID_STEP_LON_DEG = CHUNK_MAX_KM / KM_PER_DEG_LAT

/**
 * Maximum number of chunk cells along latitude and along longitude expected when
 * [MAX_POI_LOAD_EXTENT_KM] is applied (~150 km / ~25 km ≈ 6).
 */
const val MAX_POI_CHUNK_GRID_CELLS_PER_SIDE = 14

/**
 * One world-aligned POI load cell: integer grid indices and the geographic bbox for Postpass queries.
 * IDs are stable when the viewport jitters within the same cells.
 */
data class PoiGridCell(
    val iLat: Int,
    val iLon: Int,
    val bounds: BoundingBox,
) {
    val id: String get() = "g_${iLat}_${iLon}"
}

/** Padded viewport (1.25×) is split into ~25 km × 25 km Postpass requests on a fixed global grid. */
object PoiViewportChunks {

    const val VIEWPORT_PAD_FACTOR = 1.25

    /** Viewport padding for manual “load POIs” (picker action), before [MAX_POI_LOAD_EXTENT_KM] clamp. */
    const val POI_MANUAL_LOAD_PAD = 1.5

    /** Max north–south and east–west extent (km) for POI loading, centered on the viewport. */
    const val MAX_POI_LOAD_EXTENT_KM = 150.0

    fun approximateViewportBounds(
        lat: Double, lon: Double, zoom: Double,
        screenWidthDp: Double = 360.0, screenHeightDp: Double = 800.0,
    ): BoundingBox {
        // Empirically validated: 360/2^(zoom+1) closely matches the visible latitude half-span.
        // Scale longitude by screen aspect ratio and latitude correction.
        val halfLatDeg = 360.0 / 2.0.pow(zoom + 1)
        val aspectRatio = screenWidthDp / screenHeightDp
        val halfLonDeg = halfLatDeg * aspectRatio / cos(Math.toRadians(lat))
        return BoundingBox(
            southwest = Position(latitude = lat - halfLatDeg, longitude = lon - halfLonDeg),
            northeast = Position(latitude = lat + halfLatDeg, longitude = lon + halfLonDeg),
        )
    }

    /** Axis-aligned intersection; uses min/max of corners so SW/NE need not be ordered. */
    fun boundingBoxesIntersect(a: BoundingBox, b: BoundingBox): Boolean {
        val aSw = a.southwest
        val aNe = a.northeast
        val bSw = b.southwest
        val bNe = b.northeast
        val aMinLat = min(aSw.latitude, aNe.latitude)
        val aMaxLat = max(aSw.latitude, aNe.latitude)
        val aMinLon = min(aSw.longitude, aNe.longitude)
        val aMaxLon = max(aSw.longitude, aNe.longitude)
        val bMinLat = min(bSw.latitude, bNe.latitude)
        val bMaxLat = max(bSw.latitude, bNe.latitude)
        val bMinLon = min(bSw.longitude, bNe.longitude)
        val bMaxLon = max(bSw.longitude, bNe.longitude)
        return aMinLat <= bMaxLat && aMaxLat >= bMinLat && aMinLon <= bMaxLon && aMaxLon >= bMinLon
    }

    fun padBounds(bounds: BoundingBox, factor: Double): BoundingBox {
        val latSpan = bounds.northeast.latitude - bounds.southwest.latitude
        val lonSpan = bounds.northeast.longitude - bounds.southwest.longitude
        val latPad = latSpan * (factor - 1.0) / 2.0
        val lonPad = lonSpan * (factor - 1.0) / 2.0
        return BoundingBox(
            southwest = Position(
                latitude = bounds.southwest.latitude - latPad,
                longitude = bounds.southwest.longitude - lonPad,
            ),
            northeast = Position(
                latitude = bounds.northeast.latitude + latPad,
                longitude = bounds.northeast.longitude + lonPad,
            ),
        )
    }

    /**
     * After [padBounds], replaces the rectangle with a ground-square box.
     * Side length is max of the padded box’s two km spans (longitude span uses cos of the box’s mid-latitude), capped at [maxExtentKm].
     * Default center is the padded box midpoint (works with [queryVisibleBoundingBox] and asymmetric camera padding).
     * Optional [centerLatitude] / [centerLongitude] override placement (e.g. for tests).
     */
    fun clampBoundsToMaxCenterExtentKm(
        bounds: BoundingBox,
        maxExtentKm: Double,
        centerLatitude: Double? = null,
        centerLongitude: Double? = null,
    ): BoundingBox {
        val sw = bounds.southwest
        val ne = bounds.northeast
        val midLat = (sw.latitude + ne.latitude) / 2.0
        val midLon = (sw.longitude + ne.longitude) / 2.0
        val latSpanKm = abs(ne.latitude - sw.latitude) * KM_PER_DEG_LAT
        val lonSpanKm = abs(ne.longitude - sw.longitude) * KM_PER_DEG_LAT * cos(Math.toRadians(midLat))

        val sideKm = min(max(latSpanKm, lonSpanKm), maxExtentKm)
        val cLat = if (centerLatitude != null && centerLongitude != null) centerLatitude else midLat
        val cLon = if (centerLatitude != null && centerLongitude != null) centerLongitude else midLon
        val latDeg = sideKm / KM_PER_DEG_LAT
        val lonDeg = sideKm / (KM_PER_DEG_LAT * cos(Math.toRadians(cLat)))

        return BoundingBox(
            southwest = Position(
                latitude = cLat - latDeg / 2.0,
                longitude = cLon - lonDeg / 2.0,
            ),
            northeast = Position(
                latitude = cLat + latDeg / 2.0,
                longitude = cLon + lonDeg / 2.0,
            ),
        )
    }

    /**
     * Returns every world grid cell whose rectangle intersects [loadBounds].
     * Grid lines are fixed in latitude/longitude (step [GRID_STEP_LAT_DEG] / [GRID_STEP_LON_DEG]),
     * so chunk IDs do not drift when the viewport moves slightly.
     */
    fun worldGridCellsIntersecting(loadBounds: BoundingBox): List<PoiGridCell> {
        val sw = loadBounds.southwest
        val ne = loadBounds.northeast
        val latMin = min(sw.latitude, ne.latitude)
        val latMax = max(sw.latitude, ne.latitude)
        val lonMin = min(sw.longitude, ne.longitude)
        val lonMax = max(sw.longitude, ne.longitude)

        val iLatFrom = floor(latMin / GRID_STEP_LAT_DEG).toInt()
        val iLatTo = ceil(latMax / GRID_STEP_LAT_DEG).toInt()
        val iLonFrom = floor(lonMin / GRID_STEP_LON_DEG).toInt()
        val iLonTo = ceil(lonMax / GRID_STEP_LON_DEG).toInt()

        val nLat = iLatTo - iLatFrom
        val nLon = iLonTo - iLonFrom
        if (nLat <= 0 || nLon <= 0) return emptyList()

        val result = ArrayList<PoiGridCell>(nLat * nLon)
        for (iLat in iLatFrom until iLatTo) {
            val south = iLat * GRID_STEP_LAT_DEG
            val north = (iLat + 1) * GRID_STEP_LAT_DEG
            for (iLon in iLonFrom until iLonTo) {
                val west = iLon * GRID_STEP_LON_DEG
                val east = (iLon + 1) * GRID_STEP_LON_DEG
                result.add(
                    PoiGridCell(
                        iLat = iLat,
                        iLon = iLon,
                        bounds = BoundingBox(
                            southwest = Position(latitude = south, longitude = west),
                            northeast = Position(latitude = north, longitude = east),
                        ),
                    ),
                )
            }
        }
        return result
    }

    /**
     * Geographic “load plate” for POI fetch and sliding-window eviction: visible bounds padded by
     * [POI_MANUAL_LOAD_PAD] (viewport max dimension × that factor in each direction), then replaced with a
     * square whose side is the larger of the two km spans, capped at [MAX_POI_LOAD_EXTENT_KM].
     */
    fun poiLoadPlateForVisibleBounds(visibleBounds: BoundingBox): BoundingBox {
        val padded = padBounds(visibleBounds, POI_MANUAL_LOAD_PAD)
        return clampBoundsToMaxCenterExtentKm(padded, MAX_POI_LOAD_EXTENT_KM)
    }

    /**
     * Visible map bounds (e.g. [org.maplibre.compose.camera.CameraProjection.queryVisibleBoundingBox]),
     * then × [POI_MANUAL_LOAD_PAD], square clamp in km, then grid intersection.
     */
    fun gridCellsForManualLoad(visibleBounds: BoundingBox): Pair<BoundingBox, List<PoiGridCell>> {
        val loadBounds = poiLoadPlateForVisibleBounds(visibleBounds)
        return loadBounds to worldGridCellsIntersecting(loadBounds)
    }

    /** Fallback when the map projection is not available yet: [approximateViewportBounds] + pad + clamp + grid. */
    fun gridCellsForManualLoad(
        lat: Double, lon: Double, zoom: Double,
        screenWidthDp: Double = 360.0, screenHeightDp: Double = 800.0,
    ): Pair<BoundingBox, List<PoiGridCell>> {
        val viewBounds = approximateViewportBounds(lat, lon, zoom, screenWidthDp, screenHeightDp)
        return gridCellsForManualLoad(viewBounds)
    }

    /** Closed ring for the clamped POI load rectangle (for map overlay). */
    fun boundingBoxOutlineLineString(bounds: BoundingBox): LineString {
        val sw = bounds.southwest
        val ne = bounds.northeast
        val latMin = min(sw.latitude, ne.latitude)
        val latMax = max(sw.latitude, ne.latitude)
        val lonMin = min(sw.longitude, ne.longitude)
        val lonMax = max(sw.longitude, ne.longitude)
        val p1 = Position(latitude = latMin, longitude = lonMin)
        val p2 = Position(latitude = latMin, longitude = lonMax)
        val p3 = Position(latitude = latMax, longitude = lonMax)
        val p4 = Position(latitude = latMax, longitude = lonMin)
        return LineString(listOf(p1, p2, p3, p4, p1))
    }
}
