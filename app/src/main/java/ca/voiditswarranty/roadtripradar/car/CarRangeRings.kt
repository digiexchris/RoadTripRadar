package ca.voiditswarranty.roadtripradar.car

import kotlinx.serialization.json.JsonObject
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString

/**
 * Bracket index (0..7) for a given zoom level, mirroring the [when] ladder in
 * [ca.voiditswarranty.roadtripradar.model.ringDistancesForZoom]. Used by the car surface to
 * detect when the zoom has crossed a ring-distance boundary (and the rings need to be
 * rebuilt for the new distance set) without re-running the builder on every camera move.
 *
 * Higher zoom → smaller bracket index. Boundaries: 16 → 0, 14..15.99 → 1, 12..13.99 → 2,
 * 10..11.99 → 3, 8..9.99 → 4, 6..7.99 → 5, 4..5.99 → 6, < 4 → 7.
 */
internal fun rangeRingsZoomBracket(zoom: Double): Int = when {
    zoom >= 16 -> 0
    zoom >= 14 -> 1
    zoom >= 12 -> 2
    zoom >= 10 -> 3
    zoom >= 8 -> 4
    zoom >= 6 -> 5
    zoom >= 4 -> 6
    else -> 7
}

/**
 * Serialize a `FeatureCollection<LineString, JsonObject>` (the shape returned by
 * [ca.voiditswarranty.roadtripradar.model.buildRadarRingsData].ringsFeatures) to a GeoJSON
 * string suitable for `GeoJsonSource.setGeoJson(...)` on the native Android SDK.
 *
 * The phone's `RadarRingsLayers` consumes the FeatureCollection directly (via
 * `rememberGeoJsonSource(GeoJsonData.Features(...))`), so it doesn't need this round-trip;
 * the car renders through the native SDK which takes a JSON string.
 *
 * Empty input → empty FeatureCollection (matches `lineStringsGeoJson`'s behavior on the
 * route path so the caller can pass either an empty or non-empty collection without a null
 * check).
 */
internal fun serializeRingsFeatures(
    collection: FeatureCollection<LineString, JsonObject>,
): String {
    if (collection.features.isEmpty()) return """{"type":"FeatureCollection","features":[]}"""
    val features = collection.features.joinToString(",") { feature ->
        val coords = feature.geometry.coordinates.joinToString(",") { pos ->
            "[${pos.longitude},${pos.latitude}]"
        }
        // JsonObject.toString() emits valid JSON for the simple property shapes the rings
        // carry (currently just `distance: number` per feature). Empty properties collapse
        // the field entirely so the output stays clean.
        val propsJson = if (feature.properties.isEmpty()) "" else ""","properties":${feature.properties}"""
        """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]}$propsJson}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

/**
 * ARGB int for the ring line color on a given theme. Matches the phone's
 * [ca.voiditswarranty.roadtripradar.ui.MapLayers.RadarRingsLayers] palette exactly so the
 * two surfaces agree on a single shared color: LightGray (`0xFFC0C0C0`, i.e.
 * `android.graphics.Color.LTGRAY`) on dark styles, Black (`0xFF000000`, `Color.BLACK`) on
 * light styles. The car uses raw ARGB ints (native `LineLayer.lineColor` takes a `Int`,
 * not a Compose `Color`); the Compose constants happen to be the same int values.
 */
internal fun carRingColor(isDarkStyle: Boolean): Int =
    if (isDarkStyle) 0xFFC0C0C0.toInt() else 0xFF000000.toInt()

/**
 * ARGB int for the label text halo on a given theme. Matches the phone's palette:
 * DarkGray (`0xFF404040`, `Color.DKGRAY`) halo on dark styles (so light text reads
 * against a dark map), White (`0xFFFFFFFF`, `Color.WHITE`) halo on light styles (so
 * dark text reads against a light map).
 */
internal fun carLabelHaloColor(isDarkStyle: Boolean): Int =
    if (isDarkStyle) 0xFF404040.toInt() else 0xFFFFFFFF.toInt()

/**
 * ARGB int for the label text color on a given theme. Matches the phone: the label text
 * is the same color as the ring line itself — light on dark, dark on light.
 */
internal fun carLabelTextColor(isDarkStyle: Boolean): Int = carRingColor(isDarkStyle)
