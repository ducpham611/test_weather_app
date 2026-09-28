package com.vnweather.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.vnweather.app.R
import com.vnweather.app.WeatherApp
import com.vnweather.app.data.local.SettingsStore
import com.vnweather.app.domain.City
import com.vnweather.app.domain.Forecast
import com.vnweather.app.domain.UiState
import com.vnweather.app.util.AppError
import com.vnweather.app.util.LocationProvider
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as WeatherApp
    private val repository = application.repository
    private val savedCities = application.savedCities
    private val settings = application.settings
    private val locationProvider = LocationProvider(app)

    private val _state = MutableLiveData<UiState>(UiState.Loading)
    val state: LiveData<UiState> = _state

    private val _showAllDays = MutableLiveData(false)
    val showAllDays: LiveData<Boolean> = _showAllDays

    /** Raw exception text from the last failure, for the diagnostics screen. */
    var lastErrorDetail: String? = null
        private set

    private var currentCity: City = savedCities.getSelectedCity()

    val city: City get() = currentCity

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val stale = repository.getCached(currentCity)
            if (_state.value !is UiState.Success) _state.value = UiState.Loading

            if (savedCities.useCurrentLocation && locationProvider.hasPermission()) {
                resolveCurrentLocation()
            }

            // Never pre-judge connectivity. The system flag reports "offline"
            // on some lab devices and captive networks even when traffic flows
            // fine, so always attempt the request and report what really broke.
            val result = repository.getForecast(currentCity, forceRefresh)
            _state.value = result.fold(
                onSuccess = {
                    lastErrorDetail = null
                    UiState.Success(it)
                },
                onFailure = { error ->
                    lastErrorDetail = AppError.detail(error)
                    UiState.Error(AppError.classify(error), stale)
                }
            )
        }
    }

    private suspend fun resolveCurrentLocation() {
        val location = locationProvider.getCurrentLocation() ?: return
        currentCity = repository.describeCoordinates(
            latitude = location.latitude,
            longitude = location.longitude,
            language = com.vnweather.app.util.LocaleHelper.geocodingLanguage(),
            fallbackLabel = application.getString(R.string.current_location)
        )
        savedCities.setSelectedCity(currentCity)
    }

    fun selectCity(city: City) {
        currentCity = city
        savedCities.useCurrentLocation = city.isCurrentLocation
        savedCities.setSelectedCity(city)
        load(forceRefresh = false)
    }

    fun useCurrentLocation() {
        savedCities.useCurrentLocation = true
        load(forceRefresh = true)
    }

    /** Read synchronously so the UI never depends on LiveData delivery order. */
    var isShowingAllDays: Boolean = false
        private set

    fun toggleShowAllDays() {
        isShowingAllDays = !isShowingAllDays
        _showAllDays.value = isShowingAllDays
    }

    /**
     * 3 days by default (the short view), the full 15-day run after the user
     * taps "Xem thêm".
     */
    fun visibleDays(forecast: Forecast): Int =
        if (isShowingAllDays) forecast.daily.size else settings.dailyDaysShown

    val isUsingGfs: Boolean
        get() = settings.weatherModel == SettingsStore.MODEL_GFS

    val temperatureUnit: String get() = settings.temperatureUnit
    val windUnit: String get() = settings.windUnit
}
