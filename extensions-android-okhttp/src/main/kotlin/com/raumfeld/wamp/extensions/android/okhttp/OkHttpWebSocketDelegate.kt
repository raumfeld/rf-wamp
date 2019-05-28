package com.raumfeld.wamp.extensions.android.okhttp

import com.raumfeld.wamp.websocket.WebSocketDelegate
import okhttp3.*

class OkHttpWebSocketDelegate(private val webSocket: WebSocket) : WebSocketDelegate {
    override suspend fun close(code: Int, reason: String?) {
        webSocket.close(code, reason)
    }

    override suspend fun send(message: String) {
        webSocket.send(message)
    }
}

