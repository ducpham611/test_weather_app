package com.vnweather.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.vnweather.app.R
import com.vnweather.app.WeatherApp
import com.vnweather.app.domain.WeatherCodeMapper
import com.vnweather.app.ui.main.MainActivity
import com.vnweather.app.util.Formatters
import com.vnweather.app.util.LocaleHelper

/**
 * Home screen widget. It only ever reads the cache, so drawing the widget is
 * cheap and never blocks on the network; WidgetRefreshScheduler keeps that
 * cache warm in the background.
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val app = context.applicationContext as WeatherApp
        val city = app.savedCities.getSelectedCity()
        val forecast = app.repository.getCached(city)
        val views = RemoteViews(context.packageName, R.layout.widget_weather)

        if (forecast == null) {
            views.setTextViewText(R.id.widgetCity, city.name)
            views.setTextViewText(R.id.widgetTemp, "--\u00B0")
            views.setTextViewText(R.id.widgetCondition, context.getString(R.string.widget_no_data))
        } else {
            val current = forecast.current
            views.setTextViewText(R.id.widgetCity, forecast.city.name)
            views.setTextViewText(
                R.id.widgetTemp,
                Formatters.temperature(current.temperature, app.settings.temperatureUnit)
            )
            views.setTextViewText(
                R.id.widgetCondition,
                context.getString(WeatherCodeMapper.descriptionRes(current.weatherCode))
            )
            views.setImageViewResource(
                R.id.widgetIcon,
                WeatherCodeMapper.iconRes(current.weatherCode, current.isDay)
            )
            views.setTextViewText(
                R.id.widgetUpdated,
                Formatters.clockLabel(forecast.fetchedAtMillis, LocaleHelper.currentLocale())
            )
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), flags
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

        manager.updateAppWidget(widgetId, views)
    }
}
