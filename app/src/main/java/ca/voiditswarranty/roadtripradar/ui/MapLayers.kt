package ca.voiditswarranty.roadtripradar.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import ca.voiditswarranty.roadtripradar.data.RainViewer
import ca.voiditswarranty.roadtripradar.data.RouteStyle
import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.data.activeRouteLeg
import ca.voiditswarranty.roadtripradar.data.inactiveRouteLegs
import ca.voiditswarranty.roadtripradar.model.POI_CATEGORIES
import ca.voiditswarranty.roadtripradar.model.PoiViewportChunks
import ca.voiditswarranty.roadtripradar.model.RadarRingsData
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.dp
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.nil
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.TextRotationAlignment
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.LocationPuckSizes
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.RasterSource
import org.maplibre.compose.sources.TileSetOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

@Composable
fun WeatherRadarLayers(
    radarFramePaths: List<String>,
    currentFrameIndex: Int,
    radarOpacity: Float,
) {
    radarFramePaths.forEachIndexed { index, path ->
        key(path) {
            val pathId = path.replace("/", "-")
            val source = remember {
                RasterSource(
                    id = "rv$pathId",
                    tiles = listOf(RainViewer.tileUrl(path)),
                    options = TileSetOptions(maxZoom = RainViewer.MAX_ZOOM),
                    tileSize = RainViewer.TILE_SIZE_PX,
                )
            }
            RasterLayer(
                id = "rvl$pathId",
                source = source,
                visible = true,
                opacity = const(if (index == currentFrameIndex) radarOpacity else 0f),
            )
        }
    }
}

@Composable
fun RadarRingsLayers(
    radarData: RadarRingsData,
    isDarkStyle: Boolean,
) {
    val ringsSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(radarData.ringsFeatures)
    )
    val labelsSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(radarData.labelsFeatures)
    )

    val ringColor = if (isDarkStyle) Color.LightGray else Color.Black
    val haloColor = if (isDarkStyle) Color.DarkGray else Color.White

    LineLayer(
        id = "radar-rings",
        source = ringsSource,
        color = const(ringColor),
        width = const(3.5.dp),
        opacity = const(0.7f),
        dasharray = const(listOf(4, 3)),
    )

    SymbolLayer(
        id = "radar-labels",
        source = labelsSource,
        textField = format(span(feature["label"].asString())),
        textFont = const(listOf("Noto Sans Regular")),
        textColor = const(ringColor),
        textHaloColor = const(haloColor),
        textHaloWidth = const(3.dp),
        textSize = const(1.2f.em),
        textRotationAlignment = const(TextRotationAlignment.Viewport),
        textAllowOverlap = const(true),
        textIgnorePlacement = const(true),
    )
}

@Composable
fun PoiLoadBoundsLayer(
    bounds: BoundingBox?,
    isDarkStyle: Boolean,
    visible: Boolean,
) {
    if (!visible || bounds == null) return

    val outline = remember(bounds) { PoiViewportChunks.boundingBoxOutlineLineString(bounds) }
    val features = remember(outline) {
        FeatureCollection(
            listOf(
                Feature(
                    geometry = outline,
                    properties = buildJsonObject { },
                ),
            ),
        )
    }
    val source = rememberGeoJsonSource(data = GeoJsonData.Features(features))
    val ringColor = if (isDarkStyle) Color.LightGray else Color.Black

    LineLayer(
        id = "poi-load-bounds",
        source = source,
        color = const(ringColor),
        width = const(3.5.dp),
        opacity = const(0.7f),
        dasharray = const(listOf(4, 3)),
    )
}

@Composable
fun FailedCellsLayer(
    failedBounds: List<BoundingBox>,
    visible: Boolean,
) {
    if (!visible || failedBounds.isEmpty()) return

    val features = remember(failedBounds) {
        FeatureCollection(failedBounds.flatMap { bounds ->
            val sw = bounds.southwest
            val ne = bounds.northeast
            val nw = Position(latitude = ne.latitude, longitude = sw.longitude)
            val se = Position(latitude = sw.latitude, longitude = ne.longitude)
            listOf(
                Feature(geometry = LineString(listOf(sw, ne)), properties = buildJsonObject {}),
                Feature(geometry = LineString(listOf(nw, se)), properties = buildJsonObject {}),
            )
        })
    }
    val source = rememberGeoJsonSource(data = GeoJsonData.Features(features))

    LineLayer(
        id = "poi-failed-cells",
        source = source,
        color = const(Color(0xFFE57373)),
        width = const(1.5.dp),
        opacity = const(0.6f),
    )
}

@Composable
fun UserLocationPuck(
    locationState: org.maplibre.compose.location.UserLocationState,
    cameraState: org.maplibre.compose.camera.CameraState,
) {
    LocationPuck(
        idPrefix = "user-location",
        locationState = locationState,
        cameraState = cameraState,
        showBearing = true,
        sizes = LocationPuckSizes(bearingSize = 16.dp),
    )
}

@Composable
fun TappedPoiPreviewLayer(position: Position) {
    val pointData = remember(position) {
        FeatureCollection(listOf(Feature(
            geometry = Point(position),
            properties = buildJsonObject {},
        )))
    }
    val source = rememberGeoJsonSource(
        data = GeoJsonData.Features(pointData),
    )
    CircleLayer(
        id = "tapped-poi-preview-marker",
        source = source,
        radius = const(10.dp),
        color = const(Color.Red.copy(alpha = 0.6f)),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
    )
}

@Composable
fun WaypointRouteLineLayer(
    waypoints: List<Waypoint>,
    activeIndex: Int?,
    userPosition: Position,
) {
    if (waypoints.isEmpty() || activeIndex == null) return

    // Geometry is built by the shared `data.RouteGeometry` helpers so the phone and the car
    // surface draw the same route. Inactive legs are all the planned waypoint-to-waypoint
    // segments (drawn dashed); the active leg is the solid live user→target approach that
    // advances as the active waypoint advances.
    val inactiveFc = remember(waypoints.toList()) {
        FeatureCollection(
            inactiveRouteLegs(waypoints).map {
                Feature(geometry = it, properties = buildJsonObject {})
            },
        )
    }
    val activeFc = remember(waypoints.toList(), activeIndex, userPosition) {
        val leg = activeRouteLeg(waypoints, activeIndex, userPosition)
        FeatureCollection(
            if (leg != null) listOf(Feature(geometry = leg, properties = buildJsonObject {})) else emptyList(),
        )
    }

    val inactiveSource = rememberGeoJsonSource(data = GeoJsonData.Features(inactiveFc))
    val activeSource = rememberGeoJsonSource(data = GeoJsonData.Features(activeFc))

    LineLayer(
        id = "waypoint-route-inactive",
        source = inactiveSource,
        color = const(RouteStyle.COLOR),
        width = const(5.dp),
        opacity = const(RouteStyle.INACTIVE_OPACITY),
        dasharray = const(RouteStyle.INACTIVE_DASH),
    )
    LineLayer(
        id = "waypoint-route-active",
        source = activeSource,
        color = const(RouteStyle.COLOR),
        width = const(5.dp),
        opacity = const(RouteStyle.ACTIVE_OPACITY),
    )
}

@Composable
fun WaypointMarkersLayer(
    waypoints: List<Waypoint>,
    activeWaypointId: String?,
    onClick: (Waypoint) -> Unit = {},
) {
    if (waypoints.isEmpty()) return
    val context = LocalContext.current

    // --- Circle-style markers: waypoints without an iconName ---
    val circleInactiveFeatures = remember(waypoints.toList(), activeWaypointId) {
        FeatureCollection(
            waypoints.mapIndexedNotNull { idx, wp ->
                if (!wp.iconName.isNullOrBlank()) null
                else if (wp.id == activeWaypointId) null
                else Feature(
                    geometry = Point(wp.position),
                    properties = buildJsonObject {
                        put("id", JsonPrimitive(wp.id))
                        put("label", JsonPrimitive((idx + 1).toString()))
                    },
                )
            },
        )
    }
    val circleActiveFeatures = remember(waypoints.toList(), activeWaypointId) {
        FeatureCollection(
            waypoints.mapIndexedNotNull { idx, wp ->
                if (!wp.iconName.isNullOrBlank()) null
                else if (wp.id != activeWaypointId) null
                else Feature(
                    geometry = Point(wp.position),
                    properties = buildJsonObject {
                        put("id", JsonPrimitive(wp.id))
                        put("label", JsonPrimitive((idx + 1).toString()))
                    },
                )
            },
        )
    }
    val circleInactiveSource = rememberGeoJsonSource(data = GeoJsonData.Features(circleInactiveFeatures))
    val circleActiveSource = rememberGeoJsonSource(data = GeoJsonData.Features(circleActiveFeatures))

    CircleLayer(
        id = "waypoint-marker-inactive-bg",
        source = circleInactiveSource,
        radius = const(12.dp),
        color = const(Color(0xFFE53935)),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
        onClick = handleClick@{ clicked ->
            val f = clicked.firstOrNull() ?: return@handleClick ClickResult.Pass
            val id = f.properties?.get("id")?.jsonPrimitive?.content
            val wp = waypoints.firstOrNull { it.id == id } ?: return@handleClick ClickResult.Pass
            onClick(wp)
            ClickResult.Consume
        },
    )
    SymbolLayer(
        id = "waypoint-marker-inactive-label",
        source = circleInactiveSource,
        textField = format(span(feature["label"].asString())),
        textFont = const(listOf("Noto Sans Regular")),
        textColor = const(Color.White),
        textSize = const(0.9f.em),
        textAllowOverlap = const(true),
        textIgnorePlacement = const(true),
    )

    CircleLayer(
        id = "waypoint-marker-active-bg",
        source = circleActiveSource,
        radius = const(14.dp),
        color = const(Color(0xFFE53935)),
        strokeColor = const(Color(0xFF2E7D32)),
        strokeWidth = const(3.5.dp),
        onClick = handleClick@{ clicked ->
            val f = clicked.firstOrNull() ?: return@handleClick ClickResult.Pass
            val id = f.properties?.get("id")?.jsonPrimitive?.content
            val wp = waypoints.firstOrNull { it.id == id } ?: return@handleClick ClickResult.Pass
            onClick(wp)
            ClickResult.Consume
        },
    )
    SymbolLayer(
        id = "waypoint-marker-active-label",
        source = circleActiveSource,
        textField = format(span(feature["label"].asString())),
        textFont = const(listOf("Noto Sans Regular")),
        textColor = const(Color.White),
        textSize = const(1.0f.em),
        textAllowOverlap = const(true),
        textIgnorePlacement = const(true),
    )

    // --- Icon-style markers: waypoints with a Maki iconName (NEARBY_PLACE source) ---
    val iconNamesSet = remember(waypoints.toList()) {
        waypoints.mapNotNull { it.iconName?.takeIf { name -> name.isNotBlank() } }.toSet()
    }
    val iconBitmaps = remember(iconNamesSet) {
        iconNamesSet.associateWith { loadMakiIcon(context, it) }
            .filterValues { it != null }
            .mapValues { it.value!! }
    }
    if (iconBitmaps.isNotEmpty()) {
        val iconFeatures = remember(waypoints.toList(), activeWaypointId) {
            FeatureCollection(
                waypoints.mapIndexedNotNull { idx, wp ->
                    val name = wp.iconName?.takeIf { it.isNotBlank() }
                        ?: return@mapIndexedNotNull null
                    if (name !in iconBitmaps) return@mapIndexedNotNull null
                    Feature(
                        geometry = Point(wp.position),
                        properties = buildJsonObject {
                            put("id", JsonPrimitive(wp.id))
                            put("label", JsonPrimitive((idx + 1).toString()))
                            put("iconName", JsonPrimitive(name))
                            put("active", JsonPrimitive(wp.id == activeWaypointId))
                        },
                    )
                },
            )
        }
        val activeIconRingFeatures = remember(waypoints.toList(), activeWaypointId) {
            FeatureCollection(
                waypoints.mapNotNull { wp ->
                    if (wp.id != activeWaypointId) return@mapNotNull null
                    val name = wp.iconName?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    if (name !in iconBitmaps) return@mapNotNull null
                    Feature(
                        geometry = Point(wp.position),
                        properties = buildJsonObject {
                            put("id", JsonPrimitive(wp.id))
                        },
                    )
                },
            )
        }
        val iconSource = rememberGeoJsonSource(data = GeoJsonData.Features(iconFeatures))
        val activeIconRingSource = rememberGeoJsonSource(
            data = GeoJsonData.Features(activeIconRingFeatures),
        )
        val iconExpr = switch(
            input = feature["iconName"].asString(),
            *iconBitmaps.map { (n, bmp) -> case(label = n, output = image(bmp)) }.toTypedArray(),
            fallback = nil(),
        )

        CircleLayer(
            id = "waypoint-marker-icon-active-ring",
            source = activeIconRingSource,
            radius = const(22.dp),
            color = const(Color.Transparent),
            strokeColor = const(Color(0xFF2E7D32)),
            strokeWidth = const(3.5.dp),
        )

        SymbolLayer(
            id = "waypoint-marker-icons",
            source = iconSource,
            iconImage = iconExpr,
            iconSize = const(1.6f),
            iconAllowOverlap = const(true),
            iconIgnorePlacement = const(true),
            textField = format(span(feature["label"].asString())),
            textFont = const(listOf("Noto Sans Regular")),
            textColor = const(Color.White),
            textHaloColor = const(Color(0xFFE53935)),
            textHaloWidth = const(3.dp),
            textSize = const(0.85f.em),
            textOffset = offset(1.1f.em, -1.1f.em),
            textAllowOverlap = const(true),
            textIgnorePlacement = const(true),
            onClick = handleClick@{ clicked ->
                val f = clicked.firstOrNull() ?: return@handleClick ClickResult.Pass
                val id = f.properties?.get("id")?.jsonPrimitive?.content
                val wp = waypoints.firstOrNull { it.id == id } ?: return@handleClick ClickResult.Pass
                onClick(wp)
                ClickResult.Consume
            },
        )
    }
}

private fun loadMakiIcon(context: Context, iconName: String, sizePx: Int = 64): ImageBitmap? =
    renderMakiIcon(
        context = context,
        iconName = iconName,
        sizePx = sizePx,
        fillArgb = 0xFFFFFFFF.toInt(),
        borderColor = 0xFF999999.toInt(),
        borderWidth = 2f,
    )?.asImageBitmap()

@Composable
fun NearbyPoiLayers(
    vm: MapViewModel,
    enabledCategories: Set<String>,
    visible: Boolean,
    categoriesVersion: Int,
    onClusterClick: (Position) -> Unit = {},
) {
    val context = LocalContext.current
    val showLayers = enabledCategories.isNotEmpty() && visible

    android.util.Log.d("POI_DEBUG", "NearbyPoiLayers: enabledCategories=$enabledCategories, visible=$visible, showLayers=$showLayers, categoriesVersion=$categoriesVersion")

    if (!showLayers) return

    val enabledIconNames = remember(categoriesVersion) {
        POI_CATEGORIES.filter { it.query in enabledCategories }.map { it.iconName }.toSet()
    }
    val iconBitmaps = remember(enabledIconNames) {
        enabledIconNames.associateWith { name -> loadMakiIcon(context, name) }
            .filterValues { it != null }
            .mapValues { it.value!! }
    }

    android.util.Log.d("POI_DEBUG", "NearbyPoiLayers: enabledIconNames=$enabledIconNames, iconBitmaps.keys=${iconBitmaps.keys}")

    if (iconBitmaps.isEmpty()) return

    val iconExpr = switch(
        input = feature["iconName"].asString(),
        *iconBitmaps.map { (name, bmp) ->
            case(label = name, output = image(bmp))
        }.toTypedArray(),
        fallback = nil(),
    )

    val fc = vm.nearbyPoiFeatures
    android.util.Log.d("POI_DEBUG", "NearbyPoiLayers: fc.features.size=${fc.features.size}, key=($categoriesVersion)")
    key(categoriesVersion) {
        val source = rememberGeoJsonSource(
            data = GeoJsonData.Features(fc),
            options = GeoJsonOptions(
                cluster = true,
                clusterRadius = 50,
                clusterMaxZoom = 14,
            ),
        )

        // Cluster circles
        CircleLayer(
            id = "nearby-poi-clusters",
            source = source,
            filter = feature.has("cluster"),
            radius = const(18.dp),
            color = const(Color(0xFF5B8DEF)),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
            opacity = const(vm.poiIconOpacity),
            onClick = clusterClick@{ clicked ->
                val f = clicked.firstOrNull() ?: return@clusterClick ClickResult.Pass
                val pos = (f.geometry as? Point)?.coordinates ?: return@clusterClick ClickResult.Pass
                onClusterClick(pos)
                ClickResult.Consume
            },
        )

        // Individual (unclustered) POI icons
        SymbolLayer(
            id = "nearby-poi-unclustered",
            source = source,
            filter = !feature.has("cluster"),
            iconImage = iconExpr,
            iconSize = const(1.5f),
            iconOpacity = const(vm.poiIconOpacity),
            iconAllowOverlap = const(false),
            textField = format(span(feature["name"].asString())),
            textFont = const(listOf("Noto Sans Regular")),
            textSize = const(0.75f.em),
            textColor = const(Color.Black),
            textHaloColor = const(Color.White),
            textHaloWidth = const(1.5f.dp),
            textOffset = offset(0f.em, 1.8f.em),
            textOpacity = const(vm.poiIconOpacity),
            textAllowOverlap = const(false),
            textOptional = const(true),
            minZoom = 0f,
            onClick = handleClick@{ clicked ->
                val f = clicked.firstOrNull() ?: return@handleClick ClickResult.Pass
                val props = f.properties ?: return@handleClick ClickResult.Pass
                vm.showTappedPoi(
                    MapViewModel.TappedPoiInfo(
                        name = props["name"]?.jsonPrimitive?.content ?: "",
                        subtitle = props["subtitle"]?.jsonPrimitive?.content ?: "",
                        categoryLabel = props["categoryLabel"]?.jsonPrimitive?.content ?: "",
                        iconName = props["iconName"]?.jsonPrimitive?.content ?: "",
                        position = (f.geometry as? Point)?.coordinates ?: return@handleClick ClickResult.Pass,
                        openingHours = props["openingHours"]?.jsonPrimitive?.content,
                    ),
                    MapViewModel.TappedPoiOrigin.NearbyPoi,
                )
                ClickResult.Consume
            },
        )

        SymbolLayer(
            id = "nearby-poi-cluster-count",
            source = source,
            filter = feature.has("cluster"),
            textField = format(span(feature["point_count"].convertToString())),
            textFont = const(listOf("Noto Sans Regular")),
            textSize = const(1.0f.em),
            textColor = const(Color.White),
            textHaloColor = const(Color.Black),
            textHaloWidth = const(1.5f.dp),
            iconAllowOverlap = const(true),
            textAllowOverlap = const(true),
            textIgnorePlacement = const(true),
        )
    }
}
