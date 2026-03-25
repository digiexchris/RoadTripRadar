package ca.voiditswarranty.roadtripradar.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import ca.voiditswarranty.roadtripradar.model.POI_CATEGORIES
import ca.voiditswarranty.roadtripradar.model.PoiViewportChunks
import ca.voiditswarranty.roadtripradar.model.RadarRingsData
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import com.caverock.androidsvg.SVG
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.expressions.dsl.asString
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
                    tiles = listOf("https://tilecache.rainviewer.com$path/512/{z}/{x}/{y}/2/1_1.png"),
                    options = TileSetOptions(maxZoom = 7),
                    tileSize = 512,
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
fun PoiLayers(
    poiPosition: Position,
    userPosition: Position?,
) {
    val poiPointData = remember(poiPosition) {
        FeatureCollection(listOf(Feature(
            geometry = Point(poiPosition),
            properties = buildJsonObject {},
        )))
    }
    val poiSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(poiPointData),
    )
    CircleLayer(
        id = "poi-marker",
        source = poiSource,
        radius = const(12.dp),
        color = const(Color.Red),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
    )

    if (userPosition != null) {
        val poiLineData = remember(
            userPosition.latitude, userPosition.longitude, poiPosition,
        ) {
            FeatureCollection(listOf(Feature(
                geometry = LineString(listOf(userPosition, poiPosition)),
                properties = buildJsonObject {},
            )))
        }
        val poiLineSource = rememberGeoJsonSource(
            data = GeoJsonData.Features(poiLineData),
        )
        LineLayer(
            id = "poi-line",
            source = poiLineSource,
            color = const(Color.Blue),
            width = const(2.dp),
            opacity = const(0.8f),
        )
    }
}

private fun loadMakiIcon(context: Context, iconName: String, sizePx: Int = 64): ImageBitmap? {
    return try {
        val svg = context.assets.open("maki/$iconName.svg").use { SVG.getFromInputStream(it) }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF999999.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawCircle(center, center, center - 1f, bgPaint)
        canvas.drawCircle(center, center, center - 1f, borderPaint)
        val iconPadding = sizePx * 0.2f
        val iconSize = sizePx - 2 * iconPadding
        svg.documentWidth = iconSize
        svg.documentHeight = iconSize
        canvas.save()
        canvas.translate(iconPadding, iconPadding)
        svg.renderToCanvas(canvas)
        canvas.restore()
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

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
            opacity = const(0.85f),
            onClick = clusterClick@{ clicked ->
                val f = clicked.firstOrNull() ?: return@clusterClick ClickResult.Pass
                val pos = (f.geometry as? Point)?.coordinates ?: return@clusterClick ClickResult.Pass
                onClusterClick(pos)
                ClickResult.Consume
            },
        )

        // Cluster count labels
        SymbolLayer(
            id = "nearby-poi-cluster-count",
            source = source,
            filter = feature.has("cluster"),
            textField = format(span(feature["point_count"].asString())),
            textFont = const(listOf("Noto Sans Regular")),
            textSize = const(1.0f.em),
            textColor = const(Color.White),
            iconAllowOverlap = const(true),
            textAllowOverlap = const(true),
        )

        // Individual (unclustered) POI icons
        SymbolLayer(
            id = "nearby-poi-unclustered",
            source = source,
            filter = !feature.has("cluster"),
            iconImage = iconExpr,
            iconSize = const(1.5f),
            iconAllowOverlap = const(false),
            textField = format(span(feature["name"].asString())),
            textFont = const(listOf("Noto Sans Regular")),
            textSize = const(0.75f.em),
            textColor = const(Color.Black),
            textHaloColor = const(Color.White),
            textHaloWidth = const(1.5f.dp),
            textOffset = offset(0f.em, 1.8f.em),
            textAllowOverlap = const(false),
            textOptional = const(true),
            minZoom = 0f,
            onClick = handleClick@{ clicked ->
                val f = clicked.firstOrNull() ?: return@handleClick ClickResult.Pass
                val props = f.properties ?: return@handleClick ClickResult.Pass
                vm.showTappedPoi(MapViewModel.TappedPoiInfo(
                    name = props["name"]?.jsonPrimitive?.content ?: "",
                    subtitle = props["subtitle"]?.jsonPrimitive?.content ?: "",
                    categoryLabel = props["categoryLabel"]?.jsonPrimitive?.content ?: "",
                    iconName = props["iconName"]?.jsonPrimitive?.content ?: "",
                    position = (f.geometry as? Point)?.coordinates ?: return@handleClick ClickResult.Pass,
                    openingHours = props["openingHours"]?.jsonPrimitive?.content,
                ))
                ClickResult.Consume
            },
        )
    }
}
