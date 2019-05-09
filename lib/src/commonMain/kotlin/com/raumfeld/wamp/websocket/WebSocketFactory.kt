package com.raumfeld.wamp.websocket

interface WebSocketFactory {

    fun createWebsocket(uri: String, callback: WebSocketCallback)
}