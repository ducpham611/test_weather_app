package com.vnweather.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vnweather.app.WeatherApp
import java.util.concurrent.TimeUnit

/**
 * Periodic background refresh for the widget and the offline cache.
 *
 * Constrained to "connected" and "battery not low" so old phones with tired
 * batteries are not drained.
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as WeatherApp
        val city = app.savedCities.getSelectedCity()

        val result = app.repository.getForecast(city, forceRefresh = true)
        return if (result.isSuccess) {
            notifyWidgets()
            Result.success()
        } else {
            Result.retry()
        }
    }

    private fun notifyWidgets() {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val component = ComponentName(applicationContext, WeatherWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            setComponent(component)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        applicationContext.sendBroadcast(intent)
    }
}

object WidgetRefreshScheduler {

    private const val WORK_NAME = "weather-refresh"

    fun schedule(context: Context, everyHours: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<RefreshWorker>(
            everyHours.coerceAtLeast(1L), TimeUnit.HOURS
        ).setConstraints(constraints).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
