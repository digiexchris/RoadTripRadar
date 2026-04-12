package ca.voiditswarranty.roadtripradar.data

import android.content.Context
import android.content.res.Configuration
import ca.voiditswarranty.roadtripradar.model.MapStyle

fun MapStyle.resolveToConcrete(context: Context): MapStyle {
    if (this != MapStyle.AUTO) return this
    val prefsRepo = PreferencesRepository(context)
    val customRepo = CustomThemeRepository(context)
    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return if (night == Configuration.UI_MODE_NIGHT_YES) {
        if (prefsRepo.customDarkAutoEnabled && customRepo.hasCustomDark()) MapStyle.CUSTOM_DARK
        else MapStyle.COLOR_DARK
    } else {
        if (prefsRepo.customLightAutoEnabled && customRepo.hasCustomLight()) MapStyle.CUSTOM_LIGHT
        else MapStyle.LIBERTY
    }
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
