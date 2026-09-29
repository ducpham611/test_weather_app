package com.vnweather.app.util

import android.os.Build
import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.vnweather.app.BuildConfig
import com.vnweather.app.data.remote.GeocodingApi
import com.vnweather.app.data.remote.OpenMeteoApi
import com.vnweather.app.data.remote.TomorrowApi
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Single shared OkHttp client plus the two Retrofit services.
 *
 * Connection settings are deliberately permissive. An earlier version pinned
 * the client to MODERN_TLS only, which silently killed every request on
 * devices where Conscrypt failed to load or where the network runs through an
 * intercepting proxy. Both are common on lab handsets and corporate Wi-Fi.
 */
object NetworkModule {

    private const val TAG = "Network"
    private const val TIMEOUT_SECONDS = 20L
    private const val MAX_ATTEMPTS = 3

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /** Exposed so the diagnostics screen can make a raw test call. */
    val rawClient: OkHttpClient by lazy { buildClient() }

    val openMeteoApi: OpenMeteoApi by lazy {
        buildRetrofit(OpenMeteoApi.BASE_URL).create(OpenMeteoApi::class.java)
    }

    val tomorrowApi: TomorrowApi by lazy {
        buildRetrofit(TomorrowApi.BASE_URL).create(TomorrowApi::class.java)
    }

    val geocodingApi: GeocodingApi by lazy {
        buildRetrofit(GeocodingApi.BASE_URL).create(GeocodingApi::class.java)
    }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // MODERN_TLS first, but keep COMPATIBLE_TLS so an older stack or a
            // proxy that only speaks TLS 1.0/1.1 can still connect.
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
            .retryOnConnectionFailure(true)
            .addInterceptor(HeaderInterceptor)
            .addInterceptor(RetryInterceptor(MAX_ATTEMPTS))

        if (BuildConfig.DEBUG) builder.addInterceptor(LogInterceptor)

        // On API 21 TLS 1.2 exists but is off by default. Turn it on when
        // Conscrypt is not doing it for us.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            enableTls12(builder)
        }

        return builder.build()
    }

    private fun enableTls12(builder: OkHttpClient.Builder) {
        runCatching {
            val trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            ).apply { init(null as KeyStore?) }

            val trustManager = trustManagerFactory.trustManagers
                .filterIsInstance<X509TrustManager>()
                .first()

            val sslContext = SSLContext.getInstance("TLSv1.2").apply { init(null, null, null) }
            builder.sslSocketFactory(Tls12SocketFactory(sslContext.socketFactory), trustManager)
            Log.i(TAG, "TLS 1.2 enabled explicitly for API ${Build.VERSION.SDK_INT}")
        }.onFailure {
            Log.w(TAG, "Could not force TLS 1.2", it)
        }
    }

    private fun buildRetrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(rawClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private object HeaderInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            // No explicit Accept-Encoding: OkHttp adds gzip itself and then
            // transparently decompresses. Setting it by hand disables that.
            val request = chain.request().newBuilder()
                .header("Accept", "application/json")
                .header("User-Agent", "VNWeather/${BuildConfig.VERSION_NAME} (Android)")
                .build()
            return chain.proceed(request)
        }
    }

    private object LogInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            Log.d(TAG, "--> ${request.method} ${request.url}")
            return try {
                val response = chain.proceed(request)
                Log.d(TAG, "<-- ${response.code} ${request.url}")
                response
            } catch (e: IOException) {
                // This is the line to look for in logcat when the UI says
                // "no internet" but the device is clearly online.
                Log.e(TAG, "<-- FAILED ${request.url}", e)
                throw e
            }
        }
    }

    /**
     * Retries transient failures with 1 s / 2 s backoff, then rethrows the
     * real exception so the UI can tell DNS, TLS and timeout errors apart.
     */
    private class RetryInterceptor(private val maxAttempts: Int) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var lastError: IOException? = null

            repeat(maxAttempts) { attempt ->
                try {
                    val response = chain.proceed(chain.request())
                    if (response.isSuccessful || response.code < 500) return response
                    response.close()
                    lastError = IOException("Server error ${response.code}")
                } catch (e: IOException) {
                    lastError = e
                }

                if (attempt < maxAttempts - 1) {
                    try {
                        Thread.sleep(1000L shl attempt)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IOException("Interrupted while retrying", ie)
                    }
                }
            }

            throw lastError ?: IOException("Request failed after $maxAttempts attempts")
        }
    }
}
