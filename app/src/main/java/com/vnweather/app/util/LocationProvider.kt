package com.vnweather.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Location using the platform LocationManager only.
 *
 * Deliberately no Google Play services: many old Android 5 / 6 phones in
 * Vietnam either lack it or run an outdated version.
 *
 * Order: last known fix (instant) -> network provider -> GPS, with a timeout
 * so the UI never hangs waiting for a satellite lock indoors.
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(timeoutMillis: Long = 12_000L): Location? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        lastKnown(manager)?.let { return it }

        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            else -> return null
        }

        return suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            var finished = false

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (finished) return
                    finished = true
                    manager.removeUpdates(this)
                    if (cont.isActive) cont.resume(location)
                }

                // Required on API 21-28.
                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) = Unit
                override fun onProviderEnabled(p: String) = Unit
                override fun onProviderDisabled(p: String) {
                    if (finished) return
                    finished = true
                    manager.removeUpdates(this)
                    if (cont.isActive) cont.resume(null)
                }
            }

            handler.post {
                runCatching {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }.onFailure {
                    if (!finished) {
                        finished = true
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }

            handler.postDelayed({
                if (!finished) {
                    finished = true
                    runCatching { manager.removeUpdates(listener) }
                    if (cont.isActive) cont.resume(lastKnown(manager))
                }
            }, timeoutMillis)

            cont.invokeOnCancellation {
                runCatching { manager.removeUpdates(listener) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? {
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        return providers.mapNotNull { p ->
            runCatching { manager.getLastKnownLocation(p) }.getOrNull()
        }.maxByOrNull { it.time }
            ?.takeIf { System.currentTimeMillis() - it.time < MAX_LAST_KNOWN_AGE }
    }

    private companion object {
        const val MAX_LAST_KNOWN_AGE = 30 * 60 * 1000L // 30 minutes
    }
}
