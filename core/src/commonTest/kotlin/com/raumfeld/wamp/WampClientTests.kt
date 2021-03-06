package com.raumfeld.wamp

import com.raumfeld.wamp.session.WampSession
import com.raumfeld.wamp.websocket.WebSocketCallback
import com.raumfeld.wamp.websocket.WebSocketDelegate
import com.raumfeld.wamp.websocket.WebSocketFactory
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success
import kotlin.test.BeforeTest
import kotlin.test.Test

class WampClientTests {

    companion object {
        const val WS_URI = "ws://localhost:55555/ws"
    }

    @MockK private lateinit var mockFactory: WebSocketFactory
    @MockK(relaxed = true) private lateinit var mockSession: WampSession
    @MockK private lateinit var mockSessionFactory: (WebSocketDelegate, WampSession.WampSessionListener) -> WampSession
    @MockK private lateinit var mockResultCallback: (Result<WampSession>) -> Unit
    @MockK private lateinit var mockWebSocketDelegate: WebSocketDelegate
    @MockK private lateinit var mockSessionListener: WampSession.WampSessionListener
    private lateinit var client: WampClient
    private lateinit var capturedWebSocketCallback: WebSocketCallback

    @BeforeTest
    fun setup() {
        MockKAnnotations.init(this)
        client = WampClient(mockFactory)
        client.sessionFactory = mockSessionFactory
        every { mockSessionFactory(any(), any()) } returns mockSession
        every { mockResultCallback.invoke(any()) } just Runs
        captureWebSocketCallback()
    }

    private fun captureWebSocketCallback() {
        val slot = slot<WebSocketCallback>()
        every {
            mockFactory.createWebSocket(any(), capture(slot))
        } answers {
            capturedWebSocketCallback = slot.captured
            Unit
        }
    }

    @Test
    fun shouldCallBackWithSuccess() {
        createSession()
        verify { mockResultCallback.invoke(success(mockSession)) }
        verify { mockSessionFactory.invoke(mockWebSocketDelegate, mockSessionListener) }
    }

    @Test
    fun shouldDelegateCloseAndForgetSession() = runTest {
        createSession()
        val (code, reason) = 1001 to "No reason!"
        capturedWebSocketCallback.onClosed(mockWebSocketDelegate, code, reason)
        coVerify { mockSession.onWebSocketClosed(code, reason) }
        clearMocks(mockSession)
        capturedWebSocketCallback.onMessage(mockWebSocketDelegate, "I hope this gets ignored.")
        verify { mockSession wasNot Called }
    }

    @Test
    fun shouldDelegateFailureAndForgetSession() = runTest {
        createSession()
        val throwable = RuntimeException("Oopsie.")
        capturedWebSocketCallback.onFailure(mockWebSocketDelegate, throwable)
        coVerify { mockSession.onWebSocketFailed(throwable) }
        verify { mockResultCallback.invoke(failure(throwable)) }
        clearMocks(mockSession)
        capturedWebSocketCallback.onMessage(mockWebSocketDelegate, "I hope this gets ignored.")
        verify { mockSession wasNot Called }
    }

    @Test
    fun shouldNotCreateSessionUponFailure() = runTest {
        createSession(callOnOpen = false)
        val throwable = RuntimeException("Could not create websocket")
        capturedWebSocketCallback.onFailure(mockWebSocketDelegate, throwable)
        verify { mockSessionFactory wasNot Called }
        verify { mockResultCallback.invoke(failure(throwable)) }
    }

    @Test
    fun shouldDelegateMessage() = runTest {
        createSession()
        val message = "I am a message"
        val binaryMessage = ByteArray(0)
        capturedWebSocketCallback.onMessage(mockWebSocketDelegate, message)
        capturedWebSocketCallback.onMessage(mockWebSocketDelegate, binaryMessage)
        coVerifySequence {
            mockSession.onMessage(message)
            mockSession.onBinaryMessageReceived()
        }
    }

    private fun createSession(callOnOpen: Boolean = true) = runTest {
        client.createSession(WS_URI, mockSessionListener, mockResultCallback)
        if (callOnOpen)
            capturedWebSocketCallback.onOpen(mockWebSocketDelegate)
    }
}