package ca.voiditswarranty.roadtripradar.data

/**
 * RainViewer radar-tile source configuration, shared by the phone (maplibre-compose in
 * `ui.MapLayers.WeatherRadarLayers`) and the car (native MapLibre SDK in
 * `car.CarMapContainer.updateRadar`). Keeping the tile URL template and limits in one place
 * ensures both surfaces request the same tiles — RainViewer only serves up to z7, and the
 * `2/1_1` suffix selects the color/snow options.
 */
object RainViewer {
    /** Tile size in pixels. RainViewer serves 512px tiles. */
    const val TILE_SIZE_PX = 512

    /** Maximum zoom RainViewer serves; the SDK overzoomes this tile for higher zooms. */
    const val MAX_ZOOM = 7

    /**
     * Tile URL template for a RainViewer frame [path] (e.g. `/v2/radar/123/256`), including the
     * 512px size and the `2/1_1` color/snow options.
     */
    fun tileUrl(path: String): String =
        "https://tilecache.rainviewer.com$path/512/{z}/{x}/{y}/2/1_1.png"
}