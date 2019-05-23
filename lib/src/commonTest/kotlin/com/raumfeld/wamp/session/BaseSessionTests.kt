package com.raumfeld.wamp.session

import com.raumfeld.wamp.IdGenerator
import com.raumfeld.wamp.protocol.ExampleMessage
import com.raumfeld.wamp.websocket.WebSocketDelegate
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.mockk

internal open class BaseSessionTests {
    companion object {
        val ALL_MESSAGES = ExampleMessage.values().toList()
    }
    protected lateinit var session: WampSession
    protected lateinit var mockWebSocketDelegate: WebSocketDelegate
    protected lateinit var mockIdGenerator: IdGenerator
    protected lateinit var sessionListener: WampSession.WampSessionListener

    open fun setup() {
        mockWebSocketDelegate = mockk(relaxUnitFun = true)
        mockIdGenerator = mockk(relaxed = true)
        sessionListener = mockk(relaxUnitFun = true)
        createSession()
    }

    protected fun createSession() {
        session = WampSession(mockWebSocketDelegate, mockIdGenerator)
        clearMocks(mockWebSocketDelegate)
        clearMocks(sessionListener)
        clearMocks(mockIdGenerator)
    }

    protected fun verifyMessagesSent(vararg messages: String) {
        require(messages.isNotEmpty())
        messages.forEach {
            coVerify { mockWebSocketDelegate.send(it) }
        }
    }

    protected fun verifyMessagesSent(vararg messages: ExampleMessage) =
        verifyMessagesSent(*messages.toList().asJson().toTypedArray())

    protected suspend fun receiveMessages(vararg messages: String) = messages.forEach { session.onMessage(it) }
    protected suspend fun receiveMessages(vararg messages: ExampleMessage) = receiveMessages(messages.toList())
    protected suspend inline fun receiveMessages(messages: List<ExampleMessage>) = receiveMessages(*messages.asJson().toTypedArray())

    protected fun List<ExampleMessage>.asJson() = map { it.messageJson }
}