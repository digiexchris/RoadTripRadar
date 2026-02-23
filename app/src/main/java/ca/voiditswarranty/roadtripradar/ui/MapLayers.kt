package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.value.TextRotationAlignment
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.LocationPuckSizes
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.RasterSource
import org.maplibre.compose.sources.TileSetOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import ca.voiditswarranty.roadtripradar.model.RadarRingsData

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
                visible = index == currentFrameIndex,
                opacity = const(radarOpacity),
            )
        }
    }
}

@Composable
fun RadarRingsLayers(
    radarData: RadarRingsData?,
    isDarkStyle: Boolean,
) {
    val ringsSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(
            radarData?.ringsFeatures ?: FeatureCollection<LineString, JsonObject>()
        )
    )
    val labelsSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(
            radarData?.labelsFeatures ?: FeatureCollection<Point, JsonObject>()
        )
    )

    val ringColor = if (isDarkStyle) Color.LightGray else Color.Black
    val haloColor = if (isDarkStyle) Color.DarkGray else Color.White

    LineLayer(
        id = "radar-rings",
        source = ringsSource,
        color = const(ringColor),
        width = const(1.5.dp),
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
