package ca.voiditswarranty.roadtripradar.model

import androidx.annotation.StringRes
import ca.voiditswarranty.roadtripradar.R

/**
 * Maps an Open-Meteo / WMO weather code (`weather_code`) to a localized condition
 * label string resource. Shared by the phone UI and the Android Auto car screens.
 * Reference: https://open-meteo.com/en/docs (WMO weather interpretation codes).
 */
object WmoWeatherCodes {
    @StringRes
    fun labelRes(code: Int): Int = when (code) {
        0 -> R.string.wmo_0
        1 -> R.string.wmo_1
        2 -> R.string.wmo_2
        3 -> R.string.wmo_3
        45 -> R.string.wmo_45
        48 -> R.string.wmo_48
        51 -> R.string.wmo_51
        53 -> R.string.wmo_53
        55 -> R.string.wmo_55
        56 -> R.string.wmo_56
        57 -> R.string.wmo_57
        61 -> R.string.wmo_61
        63 -> R.string.wmo_63
        65 -> R.string.wmo_65
        66 -> R.string.wmo_66
        67 -> R.string.wmo_67
        71 -> R.string.wmo_71
        73 -> R.string.wmo_73
        75 -> R.string.wmo_75
        77 -> R.string.wmo_77
        80 -> R.string.wmo_80
        81 -> R.string.wmo_81
        82 -> R.string.wmo_82
        85 -> R.string.wmo_85
        86 -> R.string.wmo_86
        95 -> R.string.wmo_95
        96 -> R.string.wmo_96
        99 -> R.string.wmo_99
        else -> R.string.wmo_unknown
    }
}