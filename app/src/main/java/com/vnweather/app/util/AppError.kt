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
/** The selected provider needs a key and Settings has none. */
class MissingApiKeyException : Exception("API key required for the selected provider")

object AppError {

    fun classify(error: Throwable): ErrorType = when (error) {
        is MissingApiKeyException -> ErrorType.MISSING_API_KEY
        is UnknownHostException -> ErrorType.DNS_ERROR
        is SSLException -> ErrorType.TLS_ERROR
        is SocketTimeoutException -> ErrorType.TIMEOUT
        // A keyed provider fails in specific, fixable ways. Reporting all of
        // them as "try again" hides a rejected or exhausted key.
        is HttpException -> when (error.code()) {
            401, 403 -> ErrorType.API_KEY_REJECTED
            429 -> ErrorType.RATE_LIMITED
            else -> ErrorType.API_ERROR
        }
        // A response we cannot parse is a server-side/format problem, not a
        // mystery. Reporting it as "something went wrong" hides the cause.
        is SerializationException -> ErrorType.API_ERROR
        is IOException -> ErrorType.NO_NETWORK
        else -> ErrorType.UNKNOWN
    }

    /** Raw text for the diagnostics screen and logcat, never for normal UI. */
    fun detail(error: Throwable): String {
        val head = "${error.javaClass.simpleName}: ${error.message ?: "no message"}"
        if (error !is HttpException) return head

        // The provider's own explanation lives in the error body ("Invalid
        // API key", "The rate limit ... has been exceeded"). Without it every
        // 4xx looks the same.
        val body = runCatching { error.response()?.errorBody()?.string() }
            .getOrNull()
            ?.take(500)
            ?.takeIf { it.isNotBlank() }

        return if (body == null) head else "$head\n\n$body"
    }
}
