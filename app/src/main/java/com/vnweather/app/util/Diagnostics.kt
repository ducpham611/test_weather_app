package com.vnweather.app.util

import android.content.Context
import android.os.Build
import com.vnweather.app.WeatherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A self-test the user can run on a problem device (a lab handset, a phone
 * behind a corporate proxy, a device with the wrong clock). It reports each
 * stage separately so you can see exactly where the chain breaks.
 */
object Diagnostics {

    suspend fun run(context: Context): String = withContext(Dispatchers.IO) {
        val app = context.applicationContext as WeatherApp
        val out = StringBuilder()

        out.appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        out.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        out.appendLine()

        // A wrong clock makes every certificate look invalid.
        val now = Date()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.US)
        out.appendLine("Device time: ${fmt.format(now)}")
        val year = SimpleDateFormat("yyyy", Locale.US).format(now).toInt()
        if (year < 2024 || year > 2100) {
            out.appendLine("  !! The clock looks wrong. This breaks HTTPS certificates.")
        }
        out.appendLine()

        out.appendLine("Connectivity flag: ${NetworkMonitor.isOnline(context)}")
        out.appendLine("Conscrypt (modern TLS): ${if (app.conscryptInstalled) "installed" else "NOT installed"}")

        val proxy = System.getProperty("http.proxyHost")
        out.appendLine("System proxy: ${if (proxy.isNullOrBlank()) "none" else "$proxy:${System.getProperty("http.proxyPort")}"}")
        out.appendLine()

        // Stage 1: DNS
        out.append("1. DNS lookup api.open-meteo.com ... ")
        val dnsOk = runCatching {
            InetAddress.getByName("api.open-meteo.com").hostAddress
        }
        out.appendLine(dnsOk.fold({ "OK ($it)" }, { "FAILED\n   ${AppError.detail(it)}" }))

        // Stage 2: HTTPS request
        out.append("2. HTTPS request ... ")
        val httpsResult = runCatching {
            val request = Request.Builder()
                .url("https://api.open-meteo.com/v1/forecast?latitude=21.03&longitude=105.85&models=ecmwf_ifs&current=temperature_2m")
                .build()
            NetworkModule.rawClient.newCall(request).execute().use { response ->
                "HTTP ${response.code}, ${response.body?.contentLength() ?: -1} bytes"
            }
        }
        out.appendLine(httpsResult.fold({ "OK ($it)" }, { "FAILED\n   ${AppError.detail(it)}" }))

        if (httpsResult.isFailure) {
            out.appendLine()
            out.appendLine("Hints:")
            out.appendLine("- DNS failed  -> the device has no working internet, or a proxy is required.")
            out.appendLine("- SSL error   -> check the device clock, or the network uses an intercepting proxy.")
            out.appendLine("- Timeout     -> the network is very slow or the host is blocked by a firewall.")
        }

        out.toString()
    }
}
