package com.vnweather.app.util

import android.content.Context
import android.os.Build
import com.vnweather.app.WeatherApp
import com.vnweather.app.data.local.SettingsStore
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

    /** Never print a key in full; length and ends are enough to spot a typo. */
    private fun describeKey(key: String): String = when {
        key.isBlank() -> "not set"
        key.length < 8 -> "set, ${key.length} chars (looks too short)"
        else -> "set, ${key.length} chars (${key.take(4)}...${key.takeLast(4)})"
    }

    private fun redact(url: String): String =
        url.replace(Regex("apikey=[^&]*"), "apikey=***")
            .replace(Regex("token=[^&]*"), "token=***")

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

        // Stage 3: the provider actually selected, key included. This is the
        // stage that explains a keyed provider failing: the response body
        // carries the real reason ("Invalid API key", rate limit, bad params).
        val model = app.settings.weatherModel
        val needsKey = model == SettingsStore.MODEL_TOMORROW
        val key = app.settings.tomorrowApiKey

        out.appendLine()
        out.appendLine("3. Selected provider: $model")
        if (needsKey) {
            out.appendLine("   API key: ${describeKey(key)}")
        }

        val providerUrl = if (needsKey) {
            "https://api.tomorrow.io/v4/weather/forecast" +
                "?location=21.03,105.85&units=metric&apikey=$key"
        } else {
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=21.03&longitude=105.85&models=$model&current=temperature_2m"
        }
        out.appendLine("   GET ${redact(providerUrl)}")

        if (needsKey && key.isBlank()) {
            out.appendLine("   SKIPPED - no API key entered in Settings.")
        } else {
            val providerResult = runCatching {
                val request = Request.Builder().url(providerUrl).build()
                NetworkModule.rawClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty().take(400)
                    "HTTP ${response.code}\n   ${body.ifBlank { "(empty body)" }}"
                }
            }
            out.appendLine(
                "   " + providerResult.fold({ it }, { "FAILED\n   ${AppError.detail(it)}" })
            )
        }

        if (httpsResult.isFailure) {
            out.appendLine()
            out.appendLine("Hints:")
            out.appendLine("- DNS failed  -> the device has no working internet, or a proxy is required.")
            out.appendLine("- SSL error   -> check the device clock, or the network uses an intercepting proxy.")
            out.appendLine("- Timeout     -> the network is very slow or the host is blocked by a firewall.")
        }

        if (needsKey) {
            out.appendLine()
            out.appendLine("Provider hints (stage 3):")
            out.appendLine("- HTTP 401/403 -> the key is wrong, or was copied with extra characters.")
            out.appendLine("- HTTP 429     -> free-tier rate limit reached; wait and retry.")
            out.appendLine("- HTTP 400     -> the request was rejected; the body says which parameter.")
        }

        // Stages 4-5: the AQI and UV blocks, which fail independently.
        val waqiToken = app.settings.waqiToken
        out.appendLine()
        out.appendLine("4. Air quality (WAQI)")
        out.appendLine("   Token: ${describeKey(waqiToken)}")
        if (waqiToken.isBlank()) {
            out.appendLine("   SKIPPED - no WAQI token entered in Settings.")
        } else {
            val url = com.vnweather.app.data.remote.WaqiApi.feedUrl(21.03, 105.85, waqiToken)
            out.appendLine("   GET ${redact(url)}")
            out.appendLine("   " + probe(url))
        }

        out.appendLine()
        out.appendLine("5. UV index (uvindexapi.com)")
        val uvUrl = com.vnweather.app.data.remote.UvIndexApi.BASE_URL +
            "api/v1/forecast?latitude=21.03&longitude=105.85&timezone=Auto"
        out.appendLine("   GET $uvUrl")
        out.appendLine("   " + probe(uvUrl))
        com.vnweather.app.data.AirUvRepository.lastError?.let {
            out.appendLine()
            out.appendLine("Last AQI/UV error: $it")
        }

        out.toString()
    }

    private fun probe(url: String): String = runCatching {
        val request = Request.Builder().url(url).build()
        NetworkModule.rawClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty().take(300)
            "HTTP ${response.code}\n   ${body.ifBlank { "(empty body)" }}"
        }
    }.fold({ it }, { "FAILED\n   ${AppError.detail(it)}" })
}
