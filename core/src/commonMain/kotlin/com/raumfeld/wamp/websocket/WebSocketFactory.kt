package com.raumfeld.wamp.websocket

/**
 * Must be implemented by clients so the library can work with WebSockets across platforms.
 */
interface WebSocketFactory {

    /**
     * This method should be implemented on each platform so that a WebSocket connection to the given URI is made
     * and all corresponding events (like received messages, errors, etc) are forwarded to the given callback.
     *
     * @param uri location the WebSocket connection should be made to
     * @param callback a set of callback methods that must be invoked on the platform when certain WebSocket-related
     * events happen
     */
    fun createWebSocket(uri: String, callback: WebSocketCallback)
}