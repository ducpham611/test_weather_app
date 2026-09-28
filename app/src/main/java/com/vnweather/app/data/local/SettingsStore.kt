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

    var nightMode: Int
        get() = prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_NIGHT_MODE, value).apply()

    companion object {
        const val UNIT_CELSIUS = "celsius"
        const val UNIT_FAHRENHEIT = "fahrenheit"
        const val UNIT_KMH = "kmh"
        const val UNIT_MS = "ms"

        /** 3 days by default, matching the main screen's short forecast. */
        const val DEFAULT_DAILY_DAYS = 3
        const val EXTENDED_DAILY_DAYS = 7

        private const val KEY_TEMP_UNIT = "temperature_unit"
        private const val KEY_WIND_UNIT = "wind_unit"
        private const val KEY_REFRESH_MIN = "refresh_minutes"
        private const val KEY_BG_REFRESH_H = "background_refresh_hours"
        private const val KEY_DAILY_DAYS = "daily_days_shown"
        private const val KEY_NIGHT_MODE = "night_mode"
    }
}
