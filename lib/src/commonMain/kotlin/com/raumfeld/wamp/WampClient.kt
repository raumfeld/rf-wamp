package com.raumfeld.wamp

import com.raumfeld.wamp.session.WampSession
import com.raumfeld.wamp.websocket.WebSocketCallback
import com.raumfeld.wamp.websocket.WebSocketDelegate
import com.raumfeld.wamp.websocket.WebSocketFactory
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success

class WampClient(
    private val socketFactory: WebSocketFactory,
    private val sessionFactory: (WebSocketDelegate) -> WampSession = { WampSession(it) }
) {

    fun createSession(uri: String, callback: (Result<WampSession>) -> Unit) {
        socketFactory.createWebSocket(uri, object : WebSocketCallback {

            private var session: WampSession? = null

            override suspend fun onOpen(webSocketDelegate: WebSocketDelegate) {
                session = sessionFactory(webSocketDelegate).apply {
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