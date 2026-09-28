package com.vnweather.app

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.vnweather.app.data.WeatherRepository
import com.vnweather.app.data.local.ForecastCache
import com.vnweather.app.data.local.SavedCitiesStore
import com.vnweather.app.data.local.SettingsStore
import com.vnweather.app.ui.widget.WidgetRefreshScheduler
import com.vnweather.app.util.LocaleHelper
import com.vnweather.app.util.NetworkModule
import org.conscrypt.Conscrypt
import java.security.Security

/**
 * Application entry point.
 *
 * The most important thing that happens here is installing Conscrypt as the
 * first JSSE provider. Android 5.0 / 6.0 ship an old TLS stack that cannot
 * complete a modern handshake with api.open-meteo.com; Conscrypt gives those
 * devices TLS 1.2 / 1.3 support.
 */
class WeatherApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var savedCities: SavedCitiesStore
        private set
    lateinit var repository: WeatherRepository
        private set

    /** Surfaced in the diagnostics screen: modern TLS is the usual suspect. */
    var conscryptInstalled: Boolean = false
        private set

    override fun onCreate() {
        installConscrypt()
        super.onCreate()

        settings = SettingsStore(this)
        savedCities = SavedCitiesStore(this)
        repository = WeatherRepository(
            api = NetworkModule.openMeteoApi,
            geocodingApi = NetworkModule.geocodingApi,
            cache = ForecastCache(this),
            settings = settings
        )

        // Mirror the in-app language into SettingsStore so the widget, which
        // is drawn outside AppCompat, can read it.
        val tag = LocaleHelper.currentLanguageTag()
        if (tag.isNotBlank()) settings.languageTag = tag

        AppCompatDelegate.setDefaultNightMode(settings.nightMode)
        WidgetRefreshScheduler.schedule(this, settings.backgroundRefreshHours)
    }

    private fun installConscrypt() {
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
            conscryptInstalled = true
            Log.i(TAG, "Conscrypt installed")
        } catch (t: Throwable) {
            // Very old / unusual devices may refuse the provider. OkHttp will
            // fall back to the platform TLS stack instead of crashing.
            Log.w(TAG, "Conscrypt unavailable, falling back to platform TLS", t)
        }
    }

    companion object {
        private const val TAG = "WeatherApp"
    }
}
