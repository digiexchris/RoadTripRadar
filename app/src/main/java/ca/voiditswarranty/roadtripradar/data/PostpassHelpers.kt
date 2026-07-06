package ca.voiditswarranty.roadtripradar.data

import ca.voiditswarranty.roadtripradar.model.POI_CATEGORIES
import ca.voiditswarranty.roadtripradar.model.PoiCategory
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

/**
 * Pure helpers for the Postpass POI pipeline — extracted from `PostpassRepository` so they
 * can be unit-tested without a [android.content.Context]. The `parsePoi` instance method
 * is split into two halves: [findMatchedCategory] (pure) and [buildPoiFeature] (pure, but
 * takes a `categoryLabelFor` lambda to resolve the localized label). The repo passes a
 * lambda that delegates to `appContext.getString(...)`; tests pass a stub.
 */

/**
 * Maps each POI category's `query` string to the OSM tag key that Postpass uses to filter
 * on it (e.g. `amenity`, `tourism`, `leisure`). Pinned here so a future add / remove is a
 * deliberate test diff.
 */
internal val postpassCategoryTagMap: Map<String, String> by lazy {
    val map = mutableMapOf<String, String>()
    for (cat in POI_CATEGORIES) {
        val tag = when (cat.query) {
            "camp_site", "viewpoint", "information" -> "tourism"
            "parking", "rest_area" -> "amenity"
            "park" -> "leisure"
            else -> "amenity"
        }
        map[cat.query] = tag
    }
    map
}

/**
 * Builds the Postpass SQL query for a single tile's worth of POIs. The query is a
 * `SELECT ... FROM postpass_pointpolygon WHERE (tags->>'...' = '...') AND geom &&
 * ST_MakeEnvelope(...)` with a 2000-row LIMIT. The `categoryTagMap` is parameterized so
 * the helper is testable without the production lazy.
 */
internal fun buildPostpassQuery(
    bounds: BoundingBox,
    categories: Set<String>,
    categoryTagMap: Map<String, String> = postpassCategoryTagMap,
): String {
    val w = bounds.southwest.longitude
    val s = bounds.southwest.latitude
    val e = bounds.northeast.longitude
    val n = bounds.northeast.latitude
    val tagConditions = categories.joinToString(" OR ") { cat ->
        val tag = categoryTagMap[cat] ?: "amenity"
        "tags->>'$tag' = '$cat'"
    }
    return buildString {
        append("SELECT osm_id, osm_type, tags, ST_Centroid(geom) AS geom\n")
        append("FROM postpass_pointpolygon\n")
        append("WHERE ($tagConditions)\n")
        append("  AND geom && ST_MakeEnvelope($w, $s, $e, $n, 4326)\n")
        append("LIMIT 2000")
    }
}

/**
 * Returns the matched category `query` string for [tags] against the [enabledCategories]
 * set, or null when no match. Pure.
 */
internal fun findMatchedCategory(
    tags: JsonObject,
    enabledCategories: Set<String>,
    categoryTagMap: Map<String, String> = postpassCategoryTagMap,
): String? = enabledCategories.firstOrNull { cat ->
    val tag = categoryTagMap[cat] ?: "amenity"
    tags[tag]?.jsonPrimitive?.content == cat
}

/**
 * Builds the POI subtitle from the OSM tag set. Priority: `addr:street` + `addr:housenumber`
 * (joined) → either alone → `addr:city`. Returns empty string when none of the keys are
 * present.
 */
internal fun buildSubtitle(tags: JsonObject): String {
    val houseNumber = tags["addr:housenumber"]?.jsonPrimitive?.content
    val street = tags["addr:street"]?.jsonPrimitive?.content
    val city = tags["addr:city"]?.jsonPrimitive?.content
    val streetPart = if (houseNumber != null && street != null) "$houseNumber $street"
        else street ?: houseNumber
    return listOfNotNull(streetPart, city).joinToString(", ")
}

/**
 * Builds the [Feature] for a parsed POI from a (lat, lon, tags) triple + the resolved
 * category. The [categoryLabelFor] lambda resolves a [PoiCategory] to its localized
 * label string (production passes `appContext::getString`; tests pass a stub). Pure
 * given the lambda.
 */
internal fun buildPoiFeature(
    lat: Double,
    lon: Double,
    tags: JsonObject,
    matchedCategory: String,
    categoryLabelFor: (PoiCategory) -> String,
): Feature<Point, JsonObject>? {
    val poiCat = POI_CATEGORIES.firstOrNull { it.query == matchedCategory } ?: return null
    val categoryLabel = categoryLabelFor(poiCat)
    val name = tags["name"]?.jsonPrimitive?.content?.ifEmpty { null }
        ?: tags["brand"]?.jsonPrimitive?.content
        ?: tags["operator"]?.jsonPrimitive?.content
        ?: categoryLabel
    return Feature(
        geometry = Point(Position(latitude = lat, longitude = lon)),
        properties = buildJsonObject {
            put("name", name)
            put("subtitle", buildSubtitle(tags))
            put("categoryQuery", matchedCategory)
            put("categoryLabel", categoryLabel)
            put("iconName", poiCat.iconName)
            tags["opening_hours"]?.jsonPrimitive?.content?.let { put("openingHours", it) }
        },
    )
}
