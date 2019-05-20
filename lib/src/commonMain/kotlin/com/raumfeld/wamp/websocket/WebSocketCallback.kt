package com.raumfeld.wamp.websocket


/**
 * Inspired by OkHttp's WebSocketListener
 */
interface WebSocketCallback {
    /**
     * Invoked when a web socket has been accepted by the remote peer and may begin transmitting
     * messages.
     */
    fun onOpen(webSocketDelegate: WebSocketDelegate)

    /** Invoked when a text (type `0x1`) message has been received.  */
    fun onMessage(webSocketDelegate: WebSocketDelegate, text: String)

    /** Invoked when a binary (type {@code 0x2}) message has been received. */
    fun onMessage(webSocketDelegate: WebSocketDelegate, bytes: ByteArray)

    /**
     * Invoked when the remote peer has indicated that no more incoming messages will be
     * transmitted.
     */
    fun onClosing(webSocketDelegate: WebSocketDelegate, code: Int, reason: String)

    /**
     * Invoked when both peers have indicated that no more messages will be transmitted and the
     * connection has been successfully released. No further calls to this listener will be made.
     */
    fun onClosed(webSocketDelegate: WebSocketDelegate, code: Int, reason: String)

    /**
     * Invoked when a web socket has been closed due to an error reading from or writing to the
     * network. Both outgoing and incoming messages may have been lost. No further calls to this
     * listener will be made.
     */
    fun onFailure(webSocketDelegate: WebSocketDelegate, t: Throwable)
}