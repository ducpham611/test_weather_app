package com.vnweather.app.util

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Android 5.0 (API 21) ships TLS 1.2 but leaves it DISABLED on the socket by
 * default. Any HTTPS call to a server that requires TLS 1.2 then dies with a
 * handshake error that looks exactly like "no internet".
 *
 * This wrapper turns the protocol on for every socket it creates. It is only
 * used when Conscrypt could not be installed.
 */
class Tls12SocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket =
        enable(delegate.createSocket(s, host, port, autoClose))

    override fun createSocket(host: String?, port: Int): Socket =
        enable(delegate.createSocket(host, port))

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int
    ): Socket = enable(delegate.createSocket(host, port, localHost, localPort))

    override fun createSocket(host: InetAddress?, port: Int): Socket =
        enable(delegate.createSocket(host, port))

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int
    ): Socket = enable(delegate.createSocket(address, port, localAddress, localPort))

    private fun enable(socket: Socket): Socket {
        if (socket is SSLSocket) {
            runCatching {
                socket.enabledProtocols = arrayOf("TLSv1.2", "TLSv1.1", "TLSv1")
            }
        }
        return socket
    }
}
