package com.vnweather.app.data.local

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** All user preferences, backed by a single SharedPreferences file. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** "celsius" or "fahrenheit" */
    var temperatureUnit: String
        get() = prefs.getString(KEY_TEMP_UNIT, UNIT_CELSIUS) ?: UNIT_CELSIUS
        set(value) = prefs.edit().putString(KEY_TEMP_UNIT, value).apply()

    /** "kmh" or "ms" */
    var windUnit: String
        get() = prefs.getString(KEY_WIND_UNIT, UNIT_KMH) ?: UNIT_KMH
        set(value) = prefs.edit().putString(KEY_WIND_UNIT, value).apply()

    /** Foreground cache freshness, in minutes. */
    var refreshMinutes: Int
        get() = prefs.getInt(KEY_REFRESH_MIN, 45)
        set(value) = prefs.edit().putInt(KEY_REFRESH_MIN, value).apply()

    /** Background / widget refresh, in hours. */
    var backgroundRefreshHours: Long
        get() = prefs.getLong(KEY_BG_REFRESH_H, 3L)
        set(value) = prefs.edit().putLong(KEY_BG_REFRESH_H, value).apply()

    /** How many days the daily list shows before "Xem thêm". */
    var dailyDaysShown: Int
        get() = prefs.getInt(KEY_DAILY_DAYS, DEFAULT_DAILY_DAYS)
        set(value) = prefs.edit().putInt(KEY_DAILY_DAYS, value).apply()

    /** Forecast model id sent to Open-Meteo. */
    var weatherModel: String
        get() = prefs.getString(KEY_MODEL, MODEL_ECMWF) ?: MODEL_ECMWF
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    /** API key for providers that need one (currently Tomorrow.io only). */
    var tomorrowApiKey: String
        get() = prefs.getString(KEY_TOMORROW_KEY, "")?.trim() ?: ""
        set(value) = prefs.edit().putString(KEY_TOMORROW_KEY, value.trim()).apply()

    /** Language tag the widget reads; the widget has no AppCompat context. */
    var languageTag: String
        get() = prefs.getString(KEY_LANGUAGE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var nightMode: Int
        get() = prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_NIGHT_MODE, value).apply()

    companion object {
        const val UNIT_CELSIUS = "celsius"
        const val UNIT_FAHRENHEIT = "fahrenheit"
        const val UNIT_KMH = "kmh"
        const val UNIT_MS = "ms"

        /** ECMWF IFS HRES: 9 km, the default and the better model for Vietnam. */
        const val MODEL_ECMWF = "ecmwf_ifs"

        /** NOAA GFS: ~11 km, hourly out to 120 h, no gaps at the end of the run. */
        const val MODEL_GFS = "gfs_seamless"

        /** Tomorrow.io. Needs a user-supplied key; daily timeline is 5 days. */
        const val MODEL_TOMORROW = "tomorrow_io"

        /** 3 days by default, matching the main screen's short forecast. */
        const val DEFAULT_DAILY_DAYS = 3
        const val EXTENDED_DAILY_DAYS = 7

        private const val KEY_TEMP_UNIT = "temperature_unit"
        private const val KEY_WIND_UNIT = "wind_unit"
        private const val KEY_REFRESH_MIN = "refresh_minutes"
        private const val KEY_BG_REFRESH_H = "background_refresh_hours"
        private const val KEY_DAILY_DAYS = "daily_days_shown"
        private const val KEY_NIGHT_MODE = "night_mode"
        private const val KEY_MODEL = "weather_model"
        private const val KEY_LANGUAGE = "language_tag"
        private const val KEY_TOMORROW_KEY = "tomorrow_api_key"
    }
}
