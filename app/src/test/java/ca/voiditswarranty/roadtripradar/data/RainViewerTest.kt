package ca.voiditswarranty.roadtripradar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [RainViewer] — the tile URL template + shared constants used by both the phone
 * and car surfaces. The current production code is a single `tileUrl(path)` function that
 * builds a RainViewer URL with `{z}/{x}/{y}` placeholders that the MapLibre SDK substitutes.
 *
 * The existing `RainViewerRateLimitTest` exercises the rate-limiter wrapping this URL; this
 * test pins the URL shape itself.
 */
class RainViewerTest {

    @Test
    fun tileSizePx_is512() {
        // RainViewer serves 512px tiles. Pin the constant.
        assertEquals(512, RainViewer.TILE_SIZE_PX)
    }

    @Test
    fun maxZoom_is7() {
        // RainViewer's published max zoom is 7; the SDK overzooms that tile for higher zooms.
        // Pin the constant.
        assertEquals(7, RainViewer.MAX_ZOOM)
    }

    @Test
    fun tileUrl_pathPrependedToHost() {
        val url = RainViewer.tileUrl("/v2/radar/123/256")
        // Host comes first, then the path (minus any trailing slash the caller passed in),
        // then the 512/{z}/{x}/{y} placeholder segment, then the 2/1_1 color/snow options.
        assertTrue(
            "expected URL to start with the RainViewer host; got $url",
            url.startsWith("https://tilecache.rainviewer.com/v2/radar/123/256/512/"),
        )
    }

    @Test
    fun tileUrl_includesColorAndSnowOptions() {
        // The `2/1_1` suffix selects the color scheme and snow options. Pin it explicitly
        // so a future tweak to the RainViewer color scheme is a deliberate diff.
        val url = RainViewer.tileUrl("/v2/radar/123/256")
        assertTrue(
            "expected URL to end with the 2/1_1 options; got $url",
            url.endsWith("/2/1_1.png"),
        )
    }

    @Test
    fun tileUrl_containsXYZPlaceholders() {
        // MapLibre substitutes {z}/{x}/{y} at request time. The placeholders must be
        // verbatim — MapLibre won't match `{z:2}` or `%z` style formats.
        val url = RainViewer.tileUrl("/v2/radar/123/256")
        assertTrue("URL must contain {z} placeholder; got $url", url.contains("{z}"))
        assertTrue("URL must contain {x} placeholder; got $url", url.contains("{x}"))
        assertTrue("URL must contain {y} placeholder; got $url", url.contains("{y}"))
    }

    @Test
    fun tileUrl_fullUrlFormat() {
        // End-to-end shape check. If RainViewer changes its URL scheme, this is the test
        // that breaks first.
        val url = RainViewer.tileUrl("/v2/radar/123/256")
        assertEquals(
            "https://tilecache.rainviewer.com/v2/radar/123/256/512/{z}/{x}/{y}/2/1_1.png",
            url,
        )
    }
}
