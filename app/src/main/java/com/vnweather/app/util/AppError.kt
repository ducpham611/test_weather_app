package com.vnweather.app.util

import com.vnweather.app.domain.ErrorType
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns a raw exception into something the user can act on.
 *
 * The first version of this app reported EVERY IOException as "no internet",
 * which hid the real cause: a TLS handshake failure looks identical to an
 * offline phone unless you separate them.
 */
object AppError {

    fun classify(error: Throwable): ErrorType = when (error) {
        is UnknownHostException -> ErrorType.DNS_ERROR
        is SSLException -> ErrorType.TLS_ERROR
        is SocketTimeoutException -> ErrorType.TIMEOUT
        is HttpException -> ErrorType.API_ERROR
        // A response we cannot parse is a server-side/format problem, not a
        // mystery. Reporting it as "something went wrong" hides the cause.
        is SerializationException -> ErrorType.API_ERROR
        is IOException -> ErrorType.NO_NETWORK
        else -> ErrorType.UNKNOWN
    }

    /** Raw text for the diagnostics screen and logcat, never for normal UI. */
    fun detail(error: Throwable): String =
        "${error.javaClass.simpleName}: ${error.message ?: "no message"}"
}
