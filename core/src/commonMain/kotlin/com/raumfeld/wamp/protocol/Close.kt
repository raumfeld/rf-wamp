package com.raumfeld.wamp.protocol

import com.raumfeld.wamp.websocket.WebSocketCloseCodes

enum class WampClose(val content: String, val webSocketCloseCode: Int) {
    SYSTEM_SHUTDOWN("wamp.close.system_shutdown", WebSocketCloseCodes.NORMAL_CLOSURE),
    CLOSE_REALM("wamp.close.close_realm", WebSocketCloseCodes.NORMAL_CLOSURE),
    GOODBYE_AND_OUT("wamp.close.goodbye_and_out", WebSocketCloseCodes.NORMAL_CLOSURE),
    PROTOCOL_VIOLATION("wamp.error.protocol_violation", WebSocketCloseCodes.PROTOCOL_ERROR);
}