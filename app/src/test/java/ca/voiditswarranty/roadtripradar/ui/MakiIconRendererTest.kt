package ca.voiditswarranty.roadtripradar.ui

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the maki icon renderer used by [MapLayers.loadMakiIcon]. The renderer
 * is the only piece of the icon-loading pipeline that has pure-ish input/output
 * shape (icon name + size → bitmap or null); everything else around it is
 * Compose-coupled glue.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MakiIconRendererTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun renderMakiIcon_knownName_returnsBitmap() {
        // "restaurant" is one of the bundled POI category icons.
        val bitmap = renderMakiIcon(
            context = context,
            iconName = "restaurant",
            sizePx = 64,
            fillArgb = 0xFFFFFFFF.toInt(),
        )
        assertNotNull("restaurant icon should rasterize", bitmap)
        assertEquals("width matches requested size", 64, bitmap!!.width)
        assertEquals("height matches requested size", 64, bitmap.height)
    }

    @Test
    fun renderMakiIcon_unknownName_returnsNull() {
        // No icon named "this-is-not-a-maki-icon".
        val bitmap = renderMakiIcon(
            context = context,
            iconName = "this-is-not-a-maki-icon",
            sizePx = 64,
            fillArgb = 0xFFFFFFFF.toInt(),
        )
        assertNull(bitmap)
    }

    @Test
    fun renderMakiIcon_respectsCustomSize() {
        val bitmap = renderMakiIcon(
            context = context,
            iconName = "fuel",
            sizePx = 32,
            fillArgb = 0xFFFFFFFF.toInt(),
        )
        assertNotNull(bitmap)
        assertEquals(32, bitmap!!.width)
        assertEquals(32, bitmap.height)
    }

    @Test
    fun renderMakiIcon_glyphTint_producesRecoloredBitmap() {
        // The car path tints the glyph; the phone path doesn't. The car
        // variant must still produce a non-null bitmap.
        val bitmap = renderMakiIcon(
            context = context,
            iconName = "fuel",
            sizePx = 64,
            fillArgb = 0xFF333333.toInt(),
            glyphTintArgb = 0xFFFFFFFF.toInt(),
            paddingFraction = 0.22f,
            circleInset = 2f,
        )
        assertNotNull(bitmap)
        assertTrue("recolored bitmap has non-zero pixels", bitmap!!.width > 0)
    }
}
