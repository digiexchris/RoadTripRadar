package ca.voiditswarranty.roadtripradar.data

import android.content.Context
import ca.voiditswarranty.roadtripradar.model.MapStyle

fun MapStyle.resolveToConcrete(context: Context): MapStyle =
    if (this == MapStyle.AUTO) PreferencesRepository.defaultMapStyleFor(context) else this

fun MapStyle.resolvedStyleUri(context: Context): String =
    resolveToConcrete(context).styleUri

fun MapStyle.isDarkForAppTheme(context: Context): Boolean =
    resolveToConcrete(context) != MapStyle.LIBERTY
