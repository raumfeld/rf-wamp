package com.raumfeld.wamp.websocket

interface WebSocketDelegate {

    suspend fun send(message: String)

    suspend fun close(code: Int, reason: String? = null)
}