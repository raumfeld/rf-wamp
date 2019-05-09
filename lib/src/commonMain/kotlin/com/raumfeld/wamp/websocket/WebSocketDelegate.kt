package com.raumfeld.wamp.websocket

interface WebSocketDelegate {

    fun send(message: String)

    fun close(code: Int, reason: String? = null)
}