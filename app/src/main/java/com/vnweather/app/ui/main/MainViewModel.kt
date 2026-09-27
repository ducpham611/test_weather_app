package com.vnweather.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.vnweather.app.R
import com.vnweather.app.WeatherApp
import com.vnweather.app.domain.City
import com.vnweather.app.domain.ErrorType
import com.vnweather.app.domain.Forecast
import com.vnweather.app.domain.UiState
import com.vnweather.app.util.LocationProvider
import com.vnweather.app.util.NetworkMonitor
import kotlinx.coroutines.launch
import java.io.IOException

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

    private var currentCity: City = savedCities.getSelectedCity()

    val city: City get() = currentCity

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val stale = repository.getCached(currentCity)
            if (_state.value !is UiState.Success) _state.value = UiState.Loading

            if (savedCities.useCurrentLocation && locationProvider.hasPermission()) {
                resolveCurrentLocation()
            }

            if (forceRefresh && !NetworkMonitor.isOnline(application)) {
                _state.value = if (stale != null) {
                    UiState.Error(ErrorType.NO_NETWORK, stale)
                } else {
                    UiState.Error(ErrorType.NO_NETWORK)
                }
                return@launch
            }

            val result = repository.getForecast(currentCity, forceRefresh)
            _state.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { error ->
                    val type = when {
                        error is IOException && !NetworkMonitor.isOnline(application) ->
                            ErrorType.NO_NETWORK
                        error is IOException -> ErrorType.NO_NETWORK
                        else -> ErrorType.API_ERROR
                    }
                    UiState.Error(type, stale)
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

    fun toggleShowAllDays() {
        _showAllDays.value = !(_showAllDays.value ?: false)
    }

    /**
     * 3 days by default (the short view), the full 15-day run after the user
     * taps "Xem thêm".
     */
    fun visibleDays(forecast: Forecast): Int =
        if (_showAllDays.value == true) forecast.daily.size else settings.dailyDaysShown

    val temperatureUnit: String get() = settings.temperatureUnit
    val windUnit: String get() = settings.windUnit
}
