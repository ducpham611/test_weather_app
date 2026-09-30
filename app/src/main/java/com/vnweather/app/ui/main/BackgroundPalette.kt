package com.vnweather.app.ui.main

import org.breezyweather.ui.theme.weatherView.WeatherView

/**
 * Colours for the main-screen background, per weather kind.
 *
 * Two palettes:
 *  - BREEZY: the exact start / centre / end colours of Breezy Weather's
 *    weather_background_*.xml (ui-weather-view, LGPL-3.0), light and dark
 *    UI variants. Only the way they are blended changes (see
 *    SmoothGradientDrawable), so they look the same minus the mid-screen line.
 *  - VN: VN Weather's own two-colour gradients, picked so white text stays
 *    readable (at least 4:1 inside the blocks, about 2.3:1 at the very
 *    palest bottom edge). Follows the city's day / night, not the UI theme.
 */
object BackgroundPalette {

    /** Returns top-to-bottom colours, or null when the kind has no gradient. */
    fun breezy(kind: Int, daylight: Boolean, darkUi: Boolean): IntArray? {
        val key = keyFor(kind, daylight) ?: return null
        return (if (darkUi) breezyDark else breezyLight)[key]
    }

    fun vn(kind: Int, daylight: Boolean): IntArray? {
        val key = keyFor(kind, true)?.let { if (it == "CLEAR_DAY") "CLEAR" else it } ?: return null
        val pair = vnPalette[key] ?: return null
        return if (daylight) pair.first else pair.second
    }

    private fun keyFor(kind: Int, daylight: Boolean): String? = when (kind) {
        WeatherView.WEATHER_KIND_CLEAR -> if (daylight) "CLEAR_DAY" else "CLEAR_NIGHT"
        WeatherView.WEATHER_KIND_CLOUD -> "CLOUD"
        WeatherView.WEATHER_KIND_CLOUDY -> "CLOUDY"
        WeatherView.WEATHER_KIND_FOG -> "FOG"
        WeatherView.WEATHER_KIND_HAIL -> "HAIL"
        WeatherView.WEATHER_KIND_HAZE -> "HAZE"
        WeatherView.WEATHER_KIND_RAINY -> "RAINY"
        WeatherView.WEATHER_KIND_SLEET -> "SLEET"
        WeatherView.WEATHER_KIND_SNOW -> "SNOW"
        WeatherView.WEATHER_KIND_THUNDER, WeatherView.WEATHER_KIND_THUNDERSTORM -> "THUNDER"
        WeatherView.WEATHER_KIND_WIND -> "WIND"
        else -> null
    }

    // Copied from ui-weather-view/src/main/res/drawable*/weather_background_*.xml
    private val breezyLight = mapOf(
        "CLEAR_DAY" to intArrayOf(0xFFFFBA5E.toInt(), 0xFFFFC67E.toInt(), 0xFFFFF0E2.toInt()),
        "CLEAR_NIGHT" to intArrayOf(0xFF171D52.toInt(), 0xFF3F4DBA.toInt(), 0xFF5E68BD.toInt()),
        "CLOUDY" to intArrayOf(0xFFAFBCC7.toInt(), 0xFFD2D6DB.toInt(), 0xFFEBEFF8.toInt()),
        "FOG" to intArrayOf(0xFFA2C3FF.toInt(), 0xFFB3AFD1.toInt(), 0xFFE7BEB0.toInt()),
        "HAIL" to intArrayOf(0xFFDDF7FF.toInt(), 0xFFE1E5E9.toInt(), 0xFFA0AFC1.toInt()),
        "HAZE" to intArrayOf(0xFFFFDDA1.toInt(), 0xFFD1C3AF.toInt(), 0xFFB5E8EF.toInt()),
        "CLOUD" to intArrayOf(0xFF64DBFF.toInt(), 0xFFD6DEE0.toInt(), 0xFFE2F0F6.toInt()),
        "RAINY" to intArrayOf(0xFF97B6D1.toInt(), 0xFFA6BBCE.toInt(), 0xFF97A8D7.toInt()),
        "SLEET" to intArrayOf(0xFFB4D3ED.toInt(), 0xFFC5D2DC.toInt(), 0xFFADC3DE.toInt()),
        "SNOW" to intArrayOf(0xFF93A2AD.toInt(), 0xFFE3E7EB.toInt(), 0xFFEDF2F6.toInt()),
        "THUNDER" to intArrayOf(0xFFC697D7.toInt(), 0xFFC1A3CE.toInt(), 0xFFAB90DB.toInt()),
        "WIND" to intArrayOf(0xFF96D0A3.toInt(), 0xFFDFFAE7.toInt(), 0xFFE8F4EE.toInt()),
    )
    private val breezyDark = mapOf(
        "CLEAR_DAY" to intArrayOf(0xFF592B19.toInt(), 0xFFCC6143.toInt(), 0xFFD18268.toInt()),
        "CLEAR_NIGHT" to intArrayOf(0xFF171D52.toInt(), 0xFF3F4DBA.toInt(), 0xFF5E68BD.toInt()),
        "CLOUDY" to intArrayOf(0xFF23262C.toInt(), 0xFF525D66.toInt(), 0xFF6B8394.toInt()),
        "FOG" to intArrayOf(0xFF131B45.toInt(), 0xFF2A5476.toInt(), 0xFF918EAF.toInt()),
        "HAIL" to intArrayOf(0xFF335A7E.toInt(), 0xFF435A6F.toInt(), 0xFF303742.toInt()),
        "HAZE" to intArrayOf(0xFF4D3314.toInt(), 0xFF755125.toInt(), 0xFFC5AF9D.toInt()),
        "CLOUD" to intArrayOf(0xFF1C2F75.toInt(), 0xFF585D6D.toInt(), 0xFF747B99.toInt()),
        "RAINY" to intArrayOf(0xFF233361.toInt(), 0xFF2F4997.toInt(), 0xFF252731.toInt()),
        "SLEET" to intArrayOf(0xFF23306B.toInt(), 0xFF3549A4.toInt(), 0xFF23262F.toInt()),
        "SNOW" to intArrayOf(0xFF353A47.toInt(), 0xFF455762.toInt(), 0xFF414F83.toInt()),
        "THUNDER" to intArrayOf(0xFF2F2B38.toInt(), 0xFF50406D.toInt(), 0xFF2C1C4D.toInt()),
        "WIND" to intArrayOf(0xFF313E3A.toInt(), 0xFF529B73.toInt(), 0xFF638170.toInt()),
    )

    private fun c(hex: Long) = hex.toInt()

    /** Day colours first, night second; each top then bottom. */
    private val vnPalette = mapOf(
        "CLEAR" to (intArrayOf(c(0xFF1E6FD9), c(0xFF4A9EEB)) to intArrayOf(c(0xFF0B1A3A), c(0xFF253C75))),
        "CLOUD" to (intArrayOf(c(0xFF3A72BF), c(0xFF7098C8)) to intArrayOf(c(0xFF14223F), c(0xFF34466B))),
        "CLOUDY" to (intArrayOf(c(0xFF56697F), c(0xFF8494A7)) to intArrayOf(c(0xFF1E2632), c(0xFF3A4656))),
        "FOG" to (intArrayOf(c(0xFF66778A), c(0xFF97A3AF)) to intArrayOf(c(0xFF262D36), c(0xFF48525E))),
        "HAZE" to (intArrayOf(c(0xFF8A7358), c(0xFFB59E80)) to intArrayOf(c(0xFF2E261C), c(0xFF554736))),
        "RAINY" to (intArrayOf(c(0xFF35517A), c(0xFF63809F)) to intArrayOf(c(0xFF141E30), c(0xFF2E3F5A))),
        "SLEET" to (intArrayOf(c(0xFF4F6E96), c(0xFF8AA2BF)) to intArrayOf(c(0xFF1D2638), c(0xFF3E4D66))),
        "HAIL" to (intArrayOf(c(0xFF4F6E96), c(0xFF8AA2BF)) to intArrayOf(c(0xFF1D2638), c(0xFF3E4D66))),
        "SNOW" to (intArrayOf(c(0xFF5E7EA5), c(0xFF98AFCB)) to intArrayOf(c(0xFF222B3F), c(0xFF45546E))),
        "THUNDER" to (intArrayOf(c(0xFF3B3563), c(0xFF655A90)) to intArrayOf(c(0xFF17132B), c(0xFF362D55))),
        "WIND" to (intArrayOf(c(0xFF3A7A74), c(0xFF6AA59C)) to intArrayOf(c(0xFF132826), c(0xFF2C4A46))),
    )
}
