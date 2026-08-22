package ca.voiditswarranty.roadtripradar.data

import ca.voiditswarranty.roadtripradar.model.PoiCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position

/**
 * Tests for the extracted Postpass helpers — [postpassCategoryTagMap], [buildPostpassQuery],
 * [findMatchedCategory], [buildSubtitle], [buildPoiFeature]. The HTTP and shared-prefs
 * sides are exercised separately in [PostpassRepositoryTest] (Phase 2) with a
 * MockWebServer.
 */
class PostpassHelpersTest {

    private val ottawaBounds = BoundingBox(
        southwest = Position(latitude = 45.0, longitude = -76.0),
        northeast = Position(latitude = 45.5, longitude = -75.5),
    )

    // -------- postpassCategoryTagMap --------

    @Test
    fun postpassCategoryTagMap_isTheExpectedMap() {
        // Pin the static mapping. The shape: every POI_CATEGORIES entry has a tag.
        val map = postpassCategoryTagMap
        for (cat in ca.voiditswarranty.roadtripradar.model.POI_CATEGORIES) {
            assertTrue(
                "category ${cat.query} must be in the tag map",
                map.containsKey(cat.query),
            )
            assertTrue(
                "tag for ${cat.query} must be a known OSM key, got ${map[cat.query]}",
                map[cat.query] in setOf("amenity", "tourism", "leisure"),
            )
        }
    }

    @Test
    fun postpassCategoryTagMap_parkMapsToLeisure() {
        // The "park" query is the only one that maps to "leisure" — pin the special case.
        assertEquals("leisure", postpassCategoryTagMap["park"])
    }

    @Test
    fun postpassCategoryTagMap_tourismCategories() {
        // camp_site, viewpoint, information are tourism-keyed.
        assertEquals("tourism", postpassCategoryTagMap["camp_site"])
        assertEquals("tourism", postpassCategoryTagMap["viewpoint"])
        assertEquals("tourism", postpassCategoryTagMap["information"])
    }

    @Test
    fun postpassCategoryTagMap_defaultIsAmenity() {
        // Everything not in the special list maps to amenity. Sanity-check a few.
        assertEquals("amenity", postpassCategoryTagMap["fuel"])
        assertEquals("amenity", postpassCategoryTagMap["restaurant"])
        assertEquals("amenity", postpassCategoryTagMap["hospital"])
    }

    // -------- buildPostpassQuery --------

    @Test
    fun buildPostpassQuery_singleCategory_returnsSql() {
        val sql = buildPostpassQuery(ottawaBounds, setOf("fuel"))
        // The query selects from postpass_pointpolygon, filters by tags->>'amenity'='fuel',
        // and is bounded by ST_MakeEnvelope with the lat/lon range.
        assertTrue("expected FROM clause, got: $sql", sql.contains("FROM postpass_pointpolygon"))
        assertTrue("expected fuel condition, got: $sql", sql.contains("tags->>'amenity' = 'fuel'"))
        assertTrue("expected bounding box, got: $sql", sql.contains("ST_MakeEnvelope(-76.0, 45.0, -75.5, 45.5, 4326)"))
        assertTrue("expected LIMIT, got: $sql", sql.contains("LIMIT 2000"))
    }

    @Test
    fun buildPostpassQuery_multipleCategories_joinsWithOr() {
        val sql = buildPostpassQuery(ottawaBounds, setOf("fuel", "restaurant"))
        // The two conditions are joined with " OR ".
        assertTrue(
            "expected both conditions, got: $sql",
            sql.contains("tags->>'amenity' = 'fuel'") &&
                sql.contains("tags->>'amenity' = 'restaurant'") &&
                sql.contains(" OR "),
        )
    }

    @Test
    fun buildPostpassQuery_emptyCategories_isHandledByCaller() {
        // The buildPostpassQuery helper doesn't filter empty categories — that's the
        // caller's job (the repo returns Success(empty) before calling the helper). But
        // the SQL still must be valid even if called with an empty set.
        val sql = buildPostpassQuery(ottawaBounds, emptySet())
        // Empty joinToString → empty condition. The WHERE clause still emits.
        assertTrue("expected WHERE clause, got: $sql", sql.contains("WHERE ()"))
    }

    // -------- findMatchedCategory --------

    private fun tagsOf(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject {
        for ((k, v) in pairs) {
            put(k, JsonPrimitive(v))
        }
    }

    @Test
    fun findMatchedCategory_amenityMatch_returnsQuery() {
        val tags = tagsOf("amenity" to "fuel")
        val matched = findMatchedCategory(tags, enabledCategories = setOf("fuel"))
        assertEquals("fuel", matched)
    }

    @Test
    fun findMatchedCategory_tourismMatch_returnsQuery() {
        val tags = tagsOf("tourism" to "viewpoint")
        val matched = findMatchedCategory(tags, enabledCategories = setOf("viewpoint"))
        assertEquals("viewpoint", matched)
    }

    @Test
    fun findMatchedCategory_noMatch_returnsNull() {
        val tags = tagsOf("amenity" to "fuel")
        val matched = findMatchedCategory(tags, enabledCategories = setOf("restaurant"))
        assertNull(matched)
    }

    @Test
    fun findMatchedCategory_categoryNotInMap_fallsBackToAmenity() {
        // A category not in the postpassCategoryTagMap (e.g. "unknown") falls back to
        // amenity lookup. If the tag is on the right key, it's a match.
        val tags = tagsOf("amenity" to "unknown")
        val matched = findMatchedCategory(tags, enabledCategories = setOf("unknown"))
        assertEquals("unknown", matched)
    }

    @Test
    fun findMatchedCategory_disabledCategory_neverMatches() {
        // Even if the tags say "amenity=fuel", a disabled "fuel" set doesn't match.
        val tags = tagsOf("amenity" to "fuel")
        val matched = findMatchedCategory(tags, enabledCategories = setOf("restaurant"))
        assertNull(matched)
    }

    // -------- buildSubtitle --------

    @Test
    fun buildSubtitle_houseNumberAndStreet_joined() {
        val tags = tagsOf(
            "addr:housenumber" to "123",
            "addr:street" to "Main St",
        )
        assertEquals("123 Main St", buildSubtitle(tags))
    }

    @Test
    fun buildSubtitle_streetOnly_keepsStreet() {
        val tags = tagsOf("addr:street" to "Main St")
        assertEquals("Main St", buildSubtitle(tags))
    }

    @Test
    fun buildSubtitle_houseNumberOnly_keepsHouseNumber() {
        val tags = tagsOf("addr:housenumber" to "123")
        assertEquals("123", buildSubtitle(tags))
    }

    @Test
    fun buildSubtitle_streetAndCity_joined() {
        val tags = tagsOf(
            "addr:street" to "Main St",
            "addr:city" to "Ottawa",
        )
        assertEquals("Main St, Ottawa", buildSubtitle(tags))
    }

    @Test
    fun buildSubtitle_houseNumberStreetAndCity_joined() {
        val tags = tagsOf(
            "addr:housenumber" to "123",
            "addr:street" to "Main St",
            "addr:city" to "Ottawa",
        )
        assertEquals("123 Main St, Ottawa", buildSubtitle(tags))
    }

    @Test
    fun buildSubtitle_noAddressTags_returnsEmpty() {
        val tags = tagsOf("amenity" to "fuel")
        assertEquals("", buildSubtitle(tags))
    }

    // -------- buildPoiFeature --------

    @Test
    fun buildPoiFeature_completeTags_returnsFeatureWithAllProperties() {
        val tags = tagsOf(
            "name" to "Coffee Shop",
            "amenity" to "cafe",
            "addr:street" to "Main St",
            "addr:city" to "Ottawa",
            "opening_hours" to "Mo-Fr 08:00-18:00",
        )
        val feature = buildPoiFeature(
            lat = 45.4,
            lon = -75.7,
            tags = tags,
            matchedCategory = "cafe",
            categoryLabelFor = { _ -> "Café" },
        )
        assertNotNull(feature)
        val props = feature!!.properties!!
        assertEquals("Coffee Shop", props["name"]!!.jsonPrimitive.content)
        assertEquals("Main St, Ottawa", props["subtitle"]!!.jsonPrimitive.content)
        assertEquals("cafe", props["categoryQuery"]!!.jsonPrimitive.content)
        assertEquals("Café", props["categoryLabel"]!!.jsonPrimitive.content)
        assertEquals("cafe", props["iconName"]!!.jsonPrimitive.content)
        assertEquals("Mo-Fr 08:00-18:00", props["openingHours"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildPoiFeature_unknownCategory_returnsNull() {
        val tags = tagsOf("amenity" to "fuel")
        val feature = buildPoiFeature(
            lat = 45.4,
            lon = -75.7,
            tags = tags,
            matchedCategory = "unknown-category",
            categoryLabelFor = { _ -> "X" },
        )
        assertNull(feature)
    }

    @Test
    fun buildPoiFeature_noNameFallsBackToBrandThenOperatorThenCategory() {
        // No name, no brand, no operator → label.
        val tags = tagsOf("amenity" to "fuel")
        val feature = buildPoiFeature(
            lat = 45.4,
            lon = -75.7,
            tags = tags,
            matchedCategory = "fuel",
            categoryLabelFor = { _ -> "Gas Station" },
        )
        assertNotNull(feature)
        assertEquals(
            "no name, no brand, no operator → category label",
            "Gas Station",
            feature!!.properties!!["name"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun buildPoiFeature_emptyNameFallsBackToBrand() {
        // Empty string in `name` falls back to `brand` (the helper explicitly checks
        // `ifEmpty { null }`).
        val tags = tagsOf("name" to "", "brand" to "Shell")
        val feature = buildPoiFeature(
            lat = 45.4,
            lon = -75.7,
            tags = tags,
            matchedCategory = "fuel",
            categoryLabelFor = { _ -> "Gas Station" },
        )
        assertNotNull(feature)
        assertEquals(
            "empty name should fall back to brand",
            "Shell",
            feature!!.properties!!["name"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun buildPoiFeature_geometryIsPoint() {
        val feature = buildPoiFeature(
            lat = 45.4,
            lon = -75.7,
            tags = tagsOf("amenity" to "fuel"),
            matchedCategory = "fuel",
            categoryLabelFor = { _ -> "X" },
        )
        assertNotNull(feature)
        val pt = feature!!.geometry as org.maplibre.spatialk.geojson.Point
        assertEquals(45.4, pt.coordinates.latitude, 0.0)
        assertEquals(-75.7, pt.coordinates.longitude, 0.0)
    }

    @Test
    fun buildPoiFeature_noOpeningHours_omitsField() {
        val tags = tagsOf("amenity" to "fuel", "name" to "X")
        val feature = buildPoiFeature(
            lat = 45.4,
            lon = -75.7,
            tags = tags,
            matchedCategory = "fuel",
            categoryLabelFor = { _ -> "X" },
        )
        assertNotNull(feature)
        assertEquals(
            "feature without opening_hours must NOT have the field",
            false,
            feature!!.properties!!.containsKey("openingHours"),
        )
    }

    @Test
    fun buildPoiFeature_categoryLabelLambdaIsInvokedOnce() {
        // The lambda is the only place the test can verify the production code calls it
        // correctly. If the lambda is unused (e.g. the production code calls Context
        // directly), this test will pass spuriously.
        var calls = 0
        val tags = tagsOf("amenity" to "fuel", "name" to "X")
        val feature = buildPoiFeature(
            lat = 45.4,
            lon = -75.7,
            tags = tags,
            matchedCategory = "fuel",
            categoryLabelFor = { _: PoiCategory ->
                calls++
                "Called $calls"
            },
        )
        assertNotNull(feature)
        // Exactly one call to the lambda (one feature, one category lookup).
        assertEquals(1, calls)
        assertEquals(
            "Called 1",
            feature!!.properties!!["categoryLabel"]!!.jsonPrimitive.content,
        )
    }
}
