package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.graphics.Color
import ca.voiditswarranty.roadtripradar.model.MapStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the pure helpers extracted from [ThemeSelectorPanel] into
 * [ThemeSelectorLogic]. Each test pins one branch of one helper.
 */
class ThemeSelectorLogicTest {

    // -------- parseHexColor --------

    @Test
    fun parseHexColor_withHash_3digit_returnsExpandedColor() {
        // #f00 → red, full alpha.
        val c = parseHexColor("#f00")
        assertNotNull(c)
        // Compose Color channels are Float in [0, 1].
        assertEquals(1.0f, c!!.red, 0.001f)
        assertEquals(0.0f, c.green, 0.001f)
        assertEquals(0.0f, c.blue, 0.001f)
        assertEquals(1.0f, c.alpha, 0.001f)
    }

    @Test
    fun parseHexColor_withoutHash_6digit_returnsColor() {
        // "ff0000" → red, no alpha channel, full alpha.
        val c = parseHexColor("ff0000")
        assertNotNull(c)
        assertEquals(1.0f, c!!.red, 0.001f)
        assertEquals(0.0f, c.green, 0.001f)
        assertEquals(0.0f, c.blue, 0.001f)
    }

    @Test
    fun parseHexColor_withHash_6digit_uppercase_returnsColor() {
        assertNotNull(parseHexColor("#FF0000"))
    }

    @Test
    fun parseHexColor_8digit_returnsColorWithAlpha() {
        // #RRGGBBAA — alpha 0x80 = 128/255 ≈ 0.502
        val withAlpha = parseHexColor("#ff000080")
        assertNotNull(withAlpha)
        assertEquals(1.0f, withAlpha!!.red, 0.001f)
        assertEquals(0.0f, withAlpha.green, 0.001f)
        assertEquals(0.0f, withAlpha.blue, 0.001f)
        assertEquals(0.502f, withAlpha.alpha, 0.01f)
    }

    @Test
    fun parseHexColor_invalidLength_returnsNull() {
        assertNull(parseHexColor("#abcde"))  // 5 chars
        assertNull(parseHexColor(""))         // 0 chars
        assertNull(parseHexColor("#"))        // just hash
    }

    @Test
    fun parseHexColor_invalidChars_returnsNull() {
        // Hex digits are 0-9 a-f A-F; "z" is invalid → NumberFormatException → null.
        assertNull(parseHexColor("#zzzzzz"))
    }

    @Test
    fun parseHexColor_whitespaceTrims() {
        assertNotNull(parseHexColor("  #ff0000  "))
    }

    // -------- extractBackgroundColor --------

    @Test
    fun extractBackgroundColor_withValidHex_returnsParsedColor() {
        val json = """{"layers":[{"id":"background","paint":{"background-color":"#ff0000"}}]}"""
        val c = extractBackgroundColor(json, fallback = Color.Green)
        assertEquals(1.0f, c.red, 0.001f)
        assertEquals(0.0f, c.green, 0.001f)
    }

    @Test
    fun extractBackgroundColor_noBackgroundLayer_returnsFallback() {
        val json = """{"layers":[{"id":"water","paint":{"fill-color":"#0000ff"}}]}"""
        val fallback = Color.Magenta
        assertEquals(fallback, extractBackgroundColor(json, fallback))
    }

    @Test
    fun extractBackgroundColor_emptyJson_returnsFallback() {
        assertEquals(Color.Cyan, extractBackgroundColor("{}", fallback = Color.Cyan))
    }

    @Test
    fun extractBackgroundColor_malformedColorInJson_returnsFallback() {
        // "z" is not a hex digit → parseHexColor returns null → use fallback.
        val json = """{"layers":[{"id":"background","paint":{"background-color":"#zzzzzz"}}]}"""
        val fallback = Color.Yellow
        assertEquals(fallback, extractBackgroundColor(json, fallback))
    }

    // -------- swatchColorForStyle --------

    @Test
    fun swatchColorForStyle_liberty_returnsBeige() {
        // Pinned: 0xFFF5F1EC
        val c = swatchColorForStyle(MapStyle.LIBERTY)
        assertEquals(0xF5 / 255.0f, c.red, 0.001f)
        assertEquals(0xF1 / 255.0f, c.green, 0.001f)
        assertEquals(0xEC / 255.0f, c.blue, 0.001f)
    }

    @Test
    fun swatchColorForStyle_dark_returnsNavy() {
        // 0xFF1A1A2E
        val c = swatchColorForStyle(MapStyle.DARK)
        assertEquals(0x1A / 255.0f, c.red, 0.001f)
        assertEquals(0x1A / 255.0f, c.green, 0.001f)
        assertEquals(0x2E / 255.0f, c.blue, 0.001f)
    }

    @Test
    fun swatchColorForStyle_colorDark_returnsNavy() {
        // Same as DARK in the panel.
        assertEquals(swatchColorForStyle(MapStyle.DARK), swatchColorForStyle(MapStyle.COLOR_DARK))
    }

    @Test
    fun swatchColorForStyle_customLight_returnsLightGray() {
        // 0xFFEEEEEE
        val c = swatchColorForStyle(MapStyle.CUSTOM_LIGHT)
        assertEquals(0xEE / 255.0f, c.red, 0.001f)
        assertEquals(0xEE / 255.0f, c.green, 0.001f)
    }

    @Test
    fun swatchColorForStyle_customDark_returnsDarkGray() {
        // 0xFF2A2A2A
        val c = swatchColorForStyle(MapStyle.CUSTOM_DARK)
        assertEquals(0x2A / 255.0f, c.red, 0.001f)
    }

    @Test
    fun swatchColorForStyle_auto_returnsUnspecified() {
        // AUTO has no fixed color; the panel renders it as a gradient.
        assertEquals(Color.Unspecified, swatchColorForStyle(MapStyle.AUTO))
    }

    // -------- Color.luminance --------

    @Test
    fun luminance_pureRed_isLowValue() {
        // Pure red: 0.2126 * 1 + 0.7152 * 0 + 0.0722 * 0 = 0.2126
        val lum = Color(1.0f, 0.0f, 0.0f).luminance()
        assertEquals(0.2126f, lum, 0.0001f)
    }

    @Test
    fun luminance_pureGreen_isHigherThanRed() {
        val red = Color(1.0f, 0.0f, 0.0f).luminance()
        val green = Color(0.0f, 1.0f, 0.0f).luminance()
        assertTrue("green should be brighter than red", green > red)
    }

    @Test
    fun luminance_black_isZero() {
        assertEquals(0.0f, Color.Black.luminance(), 0.0001f)
    }

    @Test
    fun luminance_white_isOne() {
        assertEquals(1.0f, Color.White.luminance(), 0.0001f)
    }

    private fun assertTrue(msg: String, cond: Boolean) = org.junit.Assert.assertTrue(msg, cond)
}
