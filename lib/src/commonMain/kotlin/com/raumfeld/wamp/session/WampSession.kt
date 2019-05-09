package com.raumfeld.wamp.session

import com.raumfeld.wamp.protocol.fromJsonToMessage
import com.raumfeld.wamp.websocket.WebSocketDelegate

class WampSession(private val webSocketDelegate: WebSocketDelegate) {

    enum class State {
        INITIAL,
        JOINING,
        JOINED,
        ABORTED,
        CLOSING,
        CLOSED
    }

    enum class Trigger {
        JOIN,
        LEAVE,
        ABORT
    }

    private var realm: String? = null

    fun join(realm: String) {

    }

    fun leave() {

    }

    private fun evaluate(trigger: Trigger) {

    }

    internal fun onMessage(messageJson: String) {
        val message = fromJsonToMessage(messageJson)

    }

    internal fun onClosed(code: Int, reason: String) {

    }
}