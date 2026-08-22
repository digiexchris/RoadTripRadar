package ca.voiditswarranty.roadtripradar.data

import android.content.Context
import android.content.res.Configuration
import ca.voiditswarranty.roadtripradar.model.MapStyle

/**
 * Collapses [MapStyle.AUTO] to a concrete (non-AUTO) style based on whether the device is in
 * night mode. Non-AUTO styles are returned as-is. The night flag is read from the system
 * configuration; callers that have a more authoritative dark/light signal (e.g. the car host's
 * [androidx.car.app.CarContext.isDarkMode]) should use [resolveToConcrete] with an explicit
 * `night` argument so AUTO tracks that signal instead.
 */
fun MapStyle.resolveToConcrete(context: Context): MapStyle =
    resolveToConcrete(context, isNightMode(context))

/**
 * Collapses [MapStyle.AUTO] to a concrete style using an explicit night flag (e.g. from
 * [androidx.car.app.CarContext.isDarkMode] on the car surface, where the system configuration
 * may not reflect the car's actual day/night state). Honors the user's custom-AUTO theme prefs
 * (`customLightAutoEnabled` / `customDarkAutoEnabled`) when a custom theme of the right polarity
 * is present. Non-AUTO styles are returned as-is.
 */
fun MapStyle.resolveToConcrete(context: Context, night: Boolean): MapStyle {
    if (this != MapStyle.AUTO) return this
    val prefsRepo = PreferencesRepository(context)
    val customRepo = CustomThemeRepository(context)
    return resolveAuto(
        night = night,
        customLightAutoEnabled = prefsRepo.customLightAutoEnabled,
        customDarkAutoEnabled = prefsRepo.customDarkAutoEnabled,
        hasCustomLight = customRepo.hasCustomLight(),
        hasCustomDark = customRepo.hasCustomDark(),
    )
}

/**
 * Pure decision function for the AUTO → concrete collapse. Extracted from
 * [resolveToConcrete] so the 4-quadrant truth table can be unit-tested without standing up
 * [PreferencesRepository] and [CustomThemeRepository]. Not part of the public API.
 */
internal fun resolveAuto(
    night: Boolean,
    customLightAutoEnabled: Boolean,
    customDarkAutoEnabled: Boolean,
    hasCustomLight: Boolean,
    hasCustomDark: Boolean,
): MapStyle = if (night) {
    if (customDarkAutoEnabled && hasCustomDark) MapStyle.CUSTOM_DARK
    else MapStyle.COLOR_DARK
} else {
    if (customLightAutoEnabled && hasCustomLight) MapStyle.CUSTOM_LIGHT
    else MapStyle.LIBERTY
}

fun MapStyle.resolvedStyleUri(context: Context): String {
    val customRepo = CustomThemeRepository(context)
    return when (val concrete = resolveToConcrete(context)) {
        MapStyle.CUSTOM_LIGHT -> customRepo.themeUri(MapStyle.CUSTOM_LIGHT)
            ?: MapStyle.LIBERTY.styleUri
        MapStyle.CUSTOM_DARK -> customRepo.themeUri(MapStyle.CUSTOM_DARK)
            ?: MapStyle.COLOR_DARK.styleUri
        else -> concrete.styleUri
    }
}

fun MapStyle.isDarkForAppTheme(context: Context): Boolean =
    when (resolveToConcrete(context)) {
        MapStyle.LIBERTY, MapStyle.CUSTOM_LIGHT -> false
        else -> true
    }

/**
 * Dark/light for the app chrome using an explicit night flag — for the car surface, where the
 * system configuration may not reflect the car host's actual day/night state (use
 * [androidx.car.app.CarContext.isDarkMode]).
 */
fun MapStyle.isDarkForAppTheme(context: Context, night: Boolean): Boolean =
    when (resolveToConcrete(context, night)) {
        MapStyle.LIBERTY, MapStyle.CUSTOM_LIGHT -> false
        else -> true
    }

private fun isNightMode(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES