package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.graphics.Color
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

/**
 * Pure helpers extracted from [ThemeSelectorPanel] so they can be unit-tested
 * without Compose. The panel still owns the `swatchColorForStyle` resolution
 * (which depends on [MapViewModel]); the pure pieces are the hex parser, the
 * background-color extractor, and the built-in-style swatch table.
 */

/**
 * Parses a CSS hex color string (`#RGB`, `#RRGGBB`, or `#RRGGBBAA`) into a
 * Compose [Color], or `null` on failure. The leading `#` is optional; whitespace
 * is trimmed. Returns `null` for any input that can't be parsed (non-hex digits,
 * wrong length, etc.).
 */
internal fun parseHexColor(hex: String): Color? {
    val cleaned = hex.trim().trimStart('#')
    return try {
        when (cleaned.length) {
            3 -> {
                val r = cleaned[0].toString().repeat(2).toInt(16)
                val g = cleaned[1].toString().repeat(2).toInt(16)
                val b = cleaned[2].toString().repeat(2).toInt(16)
                Color(r, g, b)
            }
            6 -> {
                val r = cleaned.substring(0, 2).toInt(16)
                val g = cleaned.substring(2, 4).toInt(16)
                val b = cleaned.substring(4, 6).toInt(16)
                Color(r, g, b)
            }
            8 -> {
                val r = cleaned.substring(0, 2).toInt(16)
                val g = cleaned.substring(2, 4).toInt(16)
                val b = cleaned.substring(4, 6).toInt(16)
                val a = cleaned.substring(6, 8).toInt(16)
                Color(r, g, b, a)
            }
            else -> null
        }
    } catch (_: NumberFormatException) {
        null
    }
}

/**
 * Extracts the `background-color` from a MapLibre style JSON string and parses it
 * into a [Color]. Returns [fallback] if the JSON has no background layer, no
 * `background-color`, or the color is malformed.
 */
internal fun extractBackgroundColor(json: String, fallback: Color): Color {
    // Simple regex: find the background layer and its background-color value.
    val bgLayerPattern = Regex(
        """"id"\s*:\s*"background"[\s\S]{0,500}?"background-color"\s*:\s*"(#[0-9a-fA-F]{3,8})"""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    val match = bgLayerPattern.find(json) ?: return fallback
    return parseHexColor(match.groupValues[1]) ?: fallback
}

/** Approximate luminance for [Color], used for swatch text-contrast decisions. */
internal fun Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/**
 * Returns the swatch color for a built-in style. Custom styles have their
 * swatch resolved at the panel level (via [MapViewModel]'s `customThemeRepo`);
 * this helper only knows the fixed built-in palette.
 */
internal fun swatchColorForStyle(style: MapStyle): Color = when (style) {
    MapStyle.LIBERTY -> Color(0xFFF5F1EC)
    MapStyle.DARK -> Color(0xFF1A1A2E)
    MapStyle.COLOR_DARK -> Color(0xFF1A1A2E)
    MapStyle.CUSTOM_LIGHT -> Color(0xFFEEEEEE)
    MapStyle.CUSTOM_DARK -> Color(0xFF2A2A2A)
    MapStyle.AUTO -> Color.Unspecified
}
