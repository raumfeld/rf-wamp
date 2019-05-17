package com.raumfeld.wamp.protocol

import com.raumfeld.wamp.websocket.WebSocketCloseCodes

enum class WampClose(val content: String, val webSocketCloseCode: Int) {
    SYSTEM_SHUTDOWN("wamp.close.system_shutdown", WebSocketCloseCodes.GOING_AWAY),
    PROTOCOL_VIOLATION("wamp.error.protocol_violation", WebSocketCloseCodes.PROTOCOL_ERROR);
}