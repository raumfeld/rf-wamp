package com.raumfeld.wamp.extensions.android.okhttp

import com.raumfeld.wamp.websocket.WebSocketCallback
import com.raumfeld.wamp.websocket.WebSocketFactory
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okio.ByteString

class OkHttpWebSocketFactory : WebSocketFactory {
    override fun createWebSocket(uri: String, callback: WebSocketCallback) {
        val request =
            Request.Builder().url(uri).header("Sec-WebSocket-Protocol", "wamp.2.json").build()
        lateinit var delegate: OkHttpWebSocketDelegate
        delegate = OkHttpWebSocketDelegate(
            OkHttpClient().newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) =
                        runBlocking { callback.onOpen(delegate) }

                    override fun onMessage(webSocket: WebSocket, text: String) =
                        runBlocking { callback.onMessage(delegate, text) }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) =
                        runBlocking { callback.onMessage(delegate, bytes.toByteArray()) }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) =
                        runBlocking { callback.onClosing(delegate, code, reason) }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
                        runBlocking { callback.onClosed(delegate, code, reason) }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) = runBlocking { callback.onFailure(delegate, t) }
                })
        )
    }

}