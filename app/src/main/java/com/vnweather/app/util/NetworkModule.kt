package com.vnweather.app.util

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.vnweather.app.data.remote.GeocodingApi
import com.vnweather.app.data.remote.OpenMeteoApi
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.TlsVersion
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Single shared OkHttp client + the two Retrofit services.
 *
 * Old phones are usually on slow 3G, so timeouts are generous and a small
 * backoff retry is built in. TLS 1.2 is required explicitly because some
 * Android 5 builds still negotiate TLS 1.0 by default.
 */
object NetworkModule {

    private const val TIMEOUT_SECONDS = 15L
    private const val MAX_RETRIES = 3

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val modernTlsSpec: ConnectionSpec =
        ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
            .build()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Keep CLEARTEXT out; Open-Meteo is HTTPS only.
            .connectionSpecs(listOf(modernTlsSpec))
            .retryOnConnectionFailure(true)
            .addInterceptor(GzipInterceptor)
            .addInterceptor(RetryInterceptor(MAX_RETRIES))
            .build()
    }

    val openMeteoApi: OpenMeteoApi by lazy {
        buildRetrofit(OpenMeteoApi.BASE_URL).create(OpenMeteoApi::class.java)
    }

    val geocodingApi: GeocodingApi by lazy {
        buildRetrofit(GeocodingApi.BASE_URL).create(GeocodingApi::class.java)
    }

    private fun buildRetrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    /** Ask for gzip explicitly so the payload stays tiny on metered 3G. */
    private object GzipInterceptor : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            val request: Request = chain.request().newBuilder()
                .header("Accept-Encoding", "gzip")
                .header("Accept", "application/json")
                .build()
            return chain.proceed(request)
        }
    }

    /** Exponential backoff: 1 s, 2 s, 4 s. */
    private class RetryInterceptor(private val maxRetries: Int) : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            var lastError: IOException? = null
            for (attempt in 0 until maxRetries) {
                try {
                    val response = chain.proceed(chain.request())
                    if (response.isSuccessful || response.code < 500) return response
                    response.close()
                } catch (e: IOException) {
                    lastError = e
                }
                try {
                    Thread.sleep(1000L shl attempt)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Interrupted while retrying", ie)
                }
            }
            return chain.proceed(chain.request()).also { if (lastError != null) Unit }
        }
    }
}
