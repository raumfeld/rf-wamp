package com.raumfeld.wamp.session

import com.raumfeld.wamp.IdGenerator
import com.raumfeld.wamp.websocket.WebSocketDelegate
import io.mockk.mockk

open class BaseSessionTests {
    protected lateinit var session: WampSession
    protected lateinit var mockWebSocketDelegate: WebSocketDelegate
    protected lateinit var mockIdGenerator: IdGenerator
    protected lateinit var sessionListener: WampSession.WampSessionListener

    open fun setup() {
        mockWebSocketDelegate = mockk(relaxUnitFun = true)
        mockIdGenerator = mockk()
        sessionListener = mockk(relaxUnitFun = true)
        session = WampSession(mockWebSocketDelegate, mockIdGenerator)
    }

    protected suspend fun receiveMessage(message: String) = session.onMessage(message)
}