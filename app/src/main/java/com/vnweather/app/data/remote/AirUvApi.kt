package com.vnweather.app.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.Locale

/**
 * World Air Quality Index feed for the station nearest a coordinate.
 *
 * Needs a free personal token from aqicn.org/data-platform/token. The public
 * "demo" token ignores the location and always answers with Shanghai.
 * Returned as a raw body because errors arrive as HTTP 200 with a string in
 * place of the data object; [com.vnweather.app.domain.AirUvParser] reads it.
 */
interface WaqiApi {

    @GET
    suspend fun feed(@Url url: String): ResponseBody

    companion object {
        const val BASE_URL = "https://api.waqi.info/"
        const val TOKEN_URL = "https://aqicn.org/data-platform/token/"
        const val SITE_URL = "https://waqi.info/"

        /** `geo:lat;lng` is part of the path, so the URL is built by hand. */
        fun feedUrl(latitude: Double, longitude: Double, token: String): String =
            String.format(Locale.US, "%sfeed/geo:%.4f;%.4f/?token=%s", BASE_URL, latitude, longitude, token)
    }
}

/**
 * UV Index API (uvindexapi.com): no key, NOAA data refreshed once a day,
 * 1,000 requests per day per IP, CC BY-SA 4.0 with a visible link required.
 */
interface UvIndexApi {

    @GET("api/v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("timezone") timezone: String = "Auto",
        @Query("hourly") hourly: Boolean = true
    ): ResponseBody

    companion object {
        const val BASE_URL = "https://uvindexapi.com/"
        const val SITE_URL = "https://uvindexapi.com"
    }
}
