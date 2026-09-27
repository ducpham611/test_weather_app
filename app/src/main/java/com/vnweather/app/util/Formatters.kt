package com.vnweather.app.util

import com.vnweather.app.data.local.SettingsStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Date / number formatting.
 *
 * SimpleDateFormat (not java.time) is used for parsing Open-Meteo's local
 * timestamps because it is available everywhere from API 21 with no
 * desugaring cost at runtime.
 */
object Formatters {

    private const val ISO_LOCAL_MINUTE = "yyyy-MM-dd'T'HH:mm"
    private const val ISO_LOCAL_DATE = "yyyy-MM-dd"

    fun parseIsoDateTime(value: String): Date? = runCatching {
        SimpleDateFormat(ISO_LOCAL_MINUTE, Locale.US).parse(value)
    }.getOrNull()

    fun parseIsoDate(value: String): Date? = runCatching {
        SimpleDateFormat(ISO_LOCAL_DATE, Locale.US).parse(value)
    }.getOrNull()

    /** "14:00" */
    fun hourLabel(iso: String, locale: Locale): String {
        val date = parseIsoDateTime(iso) ?: return iso
        return SimpleDateFormat("HH:mm", locale).format(date)
    }

    /** "Thứ Hai, 27/09" in Vietnamese, "Mon, 27/09" in English. */
    fun dayLabel(iso: String, locale: Locale): String {
        val date = parseIsoDate(iso) ?: parseIsoDateTime(iso) ?: return iso
        return SimpleDateFormat("EEEE, dd/MM", locale).format(date)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    /** Short day label used in the hourly strip headers. */
    fun shortDayLabel(iso: String, locale: Locale): String {
        val date = parseIsoDate(iso) ?: parseIsoDateTime(iso) ?: return iso
        return SimpleDateFormat("EEE dd/MM", locale).format(date)
    }

    fun clockLabel(millis: Long, locale: Locale): String =
        SimpleDateFormat("HH:mm", locale).format(Date(millis))

    fun isSameCalendarDay(a: Date, b: Date): Boolean {
        val ca = Calendar.getInstance().apply { time = a }
        val cb = Calendar.getInstance().apply { time = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    fun temperature(value: Double, unit: String): String {
        val symbol = if (unit == SettingsStore.UNIT_FAHRENHEIT) "\u00B0F" else "\u00B0C"
        return "${value.roundToInt()}$symbol"
    }

    fun temperatureShort(value: Double): String = "${value.roundToInt()}\u00B0"

    fun wind(value: Double, unit: String): String =
        if (unit == SettingsStore.UNIT_MS) {
            String.format(Locale.US, "%.1f m/s", value)
        } else {
            "${value.roundToInt()} km/h"
        }

    fun precipitation(value: Double): String = String.format(Locale.US, "%.1f mm", value)

    /** 8-point compass, localised through string arrays. */
    fun windDirectionIndex(degrees: Int): Int =
        (((degrees % 360) + 360) % 360).let { ((it + 22.5) / 45).toInt() % 8 }
}
