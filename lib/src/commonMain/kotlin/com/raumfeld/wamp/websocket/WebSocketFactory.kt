package com.raumfeld.wamp.websocket

interface WebSocketFactory {

    fun createWebSocket(uri: String, callback: WebSocketCallback)
}