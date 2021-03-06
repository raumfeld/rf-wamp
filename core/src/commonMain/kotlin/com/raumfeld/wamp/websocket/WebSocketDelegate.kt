package com.raumfeld.wamp.websocket

/**
 * Platform-independent WebSocket abstraction
 */
interface WebSocketDelegate {

    suspend fun send(message: String)

    suspend fun close(code: Int, reason: String? = null)
}