package com.vnweather.app.data.local

import android.content.Context
import com.vnweather.app.domain.City
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Saved cities / districts plus the one currently being shown.
 * SharedPreferences + JSON is enough here: the list is tiny and this avoids
 * pulling a database into the APK.
 */
class SavedCitiesStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("cities", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    fun getSavedCities(): List<City> {
        val raw = prefs.getString(KEY_SAVED, null) ?: return listOf(City.HANOI)
        return runCatching {
            json.decodeFromString(ListSerializer(City.serializer()), raw)
        }.getOrDefault(listOf(City.HANOI))
    }

    fun save(city: City) {
        if (city.isCurrentLocation) return  // never persist a GPS fix as a saved place
        val current = getSavedCities().toMutableList()
        if (current.none { it.id == city.id }) {
            current.add(city)
            writeAll(current)
        }
    }

    fun remove(city: City) {
        writeAll(getSavedCities().filterNot { it.id == city.id })
    }

    private fun writeAll(cities: List<City>) {
        prefs.edit()
            .putString(KEY_SAVED, json.encodeToString(ListSerializer(City.serializer()), cities))
            .apply()
    }

    fun getSelectedCity(): City {
        val raw = prefs.getString(KEY_SELECTED, null) ?: return City.HANOI
        return runCatching { json.decodeFromString(City.serializer(), raw) }
            .getOrDefault(City.HANOI)
    }

    fun setSelectedCity(city: City) {
        prefs.edit()
            .putString(KEY_SELECTED, json.encodeToString(City.serializer(), city))
            .apply()
    }

    /** True when the user wants the app to follow GPS instead of a fixed city. */
    var useCurrentLocation: Boolean
        get() = prefs.getBoolean(KEY_USE_GPS, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_GPS, value).apply()

    private companion object {
        const val KEY_SAVED = "saved_cities"
        const val KEY_SELECTED = "selected_city"
        const val KEY_USE_GPS = "use_current_location"
    }
}
