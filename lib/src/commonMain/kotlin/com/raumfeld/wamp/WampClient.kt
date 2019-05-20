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

            override fun onOpen(webSocketDelegate: WebSocketDelegate) {
                session = sessionFactory(webSocketDelegate).apply {
                    callback(success(this))
                }
            }

            override fun onMessage(webSocketDelegate: WebSocketDelegate, text: String) {
                session?.onMessage(text)
            }

            override fun onMessage(webSocketDelegate: WebSocketDelegate, bytes: ByteArray) {
                session?.onBinaryMessageReceived()
            }

            override fun onClosing(webSocketDelegate: WebSocketDelegate, code: Int, reason: String) = Unit

            override fun onClosed(webSocketDelegate: WebSocketDelegate, code: Int, reason: String) {
                session?.onClosed(code, reason)
                session = null
            }

            override fun onFailure(webSocketDelegate: WebSocketDelegate, t: Throwable) {
                session?.onFailed(t)
                session = null
                callback(failure(t))
            }
        })
    }
}