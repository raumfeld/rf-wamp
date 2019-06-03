package com.raumfeld.wamp

import com.raumfeld.wamp.session.WampSession
import com.raumfeld.wamp.websocket.WebSocketCallback
import com.raumfeld.wamp.websocket.WebSocketDelegate
import com.raumfeld.wamp.websocket.WebSocketFactory
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success

/**
 * Entry-point for users of this library. We require users to provide a [WebSocketFactory] because we can't
 * create them in a platform-agnostic way.
 */
class WampClient(private val socketFactory: WebSocketFactory) {

    /** Exposed for testing only */
    internal var sessionFactory: (WebSocketDelegate, WampSession.WampSessionListener) -> WampSession =
        { delegate, listener -> WampSession(delegate, listener) }

    /**
     * Establishes a WebSocket connection to the given URI and creates a [WampSession] on success.
     * Since that is an asynchronous operation and the WebSocket can fail at any time in its lifetime
     * the result is provided via a callback instead of a direct return value.
     */
    fun createSession(
        uri: String,
        sessionListener: WampSession.WampSessionListener,
        callback: (Result<WampSession>) -> Unit
    ) {
        socketFactory.createWebSocket(uri, object : WebSocketCallback {

            private var session: WampSession? = null

            override suspend fun onOpen(webSocketDelegate: WebSocketDelegate) {
                session = sessionFactory(webSocketDelegate, sessionListener).apply {
                    callback(success(this))
                }
            }

            override suspend fun onMessage(webSocketDelegate: WebSocketDelegate, text: String) {
                session?.onMessage(text)
            }

            override suspend fun onMessage(webSocketDelegate: WebSocketDelegate, bytes: ByteArray) {
                session?.onBinaryMessageReceived()
            }

            override suspend fun onClosing(webSocketDelegate: WebSocketDelegate, code: Int, reason: String) = Unit

            override suspend fun onClosed(webSocketDelegate: WebSocketDelegate, code: Int, reason: String) {
                session?.onWebSocketClosed(code, reason)
                session = null
            }

            override suspend fun onFailure(webSocketDelegate: WebSocketDelegate, t: Throwable) {
                session?.onWebSocketFailed(t)
                session = null
                callback(failure(t))
            }
        })
    }
}