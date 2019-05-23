package com.raumfeld.wamp.protocol

import com.raumfeld.wamp.websocket.WebSocketCloseCodes

enum class WampClose(val content: String, val webSocketCloseCode: Int) {
    SYSTEM_SHUTDOWN("wamp.close.system_shutdown", WebSocketCloseCodes.GOING_AWAY),
    CLOSE_REALM("wamp.close.close_realm", WebSocketCloseCodes.GOING_AWAY),
    GOODBYE_AND_OUT("wamp.close.goodbye_and_out", WebSocketCloseCodes.GOING_AWAY),
    PROTOCOL_VIOLATION("wamp.error.protocol_violation", WebSocketCloseCodes.PROTOCOL_ERROR);
}