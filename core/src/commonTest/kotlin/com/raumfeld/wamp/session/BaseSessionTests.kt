package com.raumfeld.wamp.session

import com.raumfeld.wamp.IdGenerator
import com.raumfeld.wamp.protocol.ExampleMessage
import com.raumfeld.wamp.protocol.ExampleMessage.*
import com.raumfeld.wamp.protocol.Message
import com.raumfeld.wamp.protocol.RequestMessage
import com.raumfeld.wamp.protocol.WampClose
import com.raumfeld.wamp.websocket.WebSocketDelegate
import io.mockk.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.fail

internal open class BaseSessionTests {
    companion object {
        val ALL_MESSAGES = values().toList()
    }

    protected lateinit var session: WampSession
    protected lateinit var mockWebSocketDelegate: WebSocketDelegate
    protected lateinit var mockIdGenerator: IdGenerator
    protected lateinit var sessionListener: WampSession.WampSessionListener

    private val realm = "somerealm"

    open fun setup() {
        mockWebSocketDelegate = mockk(relaxUnitFun = true)
        mockIdGenerator = mockk(relaxed = true)
        sessionListener = mockk(relaxUnitFun = true)
        createSession()
    }

    protected fun createSession() {
        session = WampSession(mockWebSocketDelegate, sessionListener, mockIdGenerator)
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
    protected suspend inline fun receiveMessages(messages: List<ExampleMessage>) =
        receiveMessages(*messages.asJson().toTypedArray())

    protected fun List<ExampleMessage>.asJson() = map { it.messageJson }

    protected suspend fun leaveRealm() = session.leave()

    protected suspend fun shutdownSession() = session.shutdown()

    protected suspend fun joinRealm() = session.join(realm)

    protected suspend fun receiveWelcome() = receiveMessages(WELCOME)

    protected suspend fun receiveGoodbyeAndOut() = receiveMessages(GOODBYE_AND_OUT)

    protected fun verifySessionAborted(reason: String) =
        verify(exactly = 1) { sessionListener.onSessionAborted(reason, any()) }

    protected fun verifySessionAborted() = verify(exactly = 1) { sessionListener.onSessionAborted(any(), any()) }
    protected fun verifySessionNotAborted() = verify(exactly = 0) { sessionListener.onSessionAborted(any(), any()) }
    protected fun verifyRealmJoined() = verify(exactly = 1) { sessionListener.onRealmJoined(realm) }
    protected fun verifyRealmLeft(fromRouter: Boolean) =
        verify(exactly = 1) { sessionListener.onRealmLeft(realm, fromRouter) }

    protected fun verifyRealmNotLeft() = verify(exactly = 0) { sessionListener.onRealmLeft(any(), any()) }
    protected fun verifySessionShutdown() = verify(exactly = 1) { sessionListener.onSessionShutdown() }
    protected fun verifySessionNotShutdown() = verify(exactly = 0) { sessionListener.onSessionShutdown() }
    protected fun verifyNoMessageSent() = coVerify(exactly = 0) { mockWebSocketDelegate.send(any()) }
    protected fun verifyWebSocketWasClosed() = coVerify(exactly = 1) { mockWebSocketDelegate.close(any(), any()) }
    protected fun verifyWebSocketWasNotClosed() = coVerify(exactly = 0) { mockWebSocketDelegate.close(any(), any()) }
    protected fun protocolViolationMessage(message: String) =
        Message.Abort(details = buildJsonObject { put("message", message) }, reason = WampClose.PROTOCOL_VIOLATION.content)

    protected fun failOnSessionAbort(fail: Boolean = true) {
        every {
            sessionListener.onSessionAborted(
                any(),
                any()
            )
        } answers { if (fail) fail("Session was aborted prematurely: $invocation") }
    }

    protected fun mockNextRequestIdWithIdFrom(message: ExampleMessage) {
        every { mockIdGenerator.newId() } returns (message.message as RequestMessage).requestId
    }
}