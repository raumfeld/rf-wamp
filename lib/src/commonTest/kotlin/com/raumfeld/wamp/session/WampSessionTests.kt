package com.raumfeld.wamp.session

import com.raumfeld.wamp.protocol.ExampleMessage.*
import com.raumfeld.wamp.runTest
import io.mockk.Called
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test

class WampSessionTests : BaseSessionTests() {

    @BeforeTest
    override fun setup() = super.setup()

    @Test
    fun shouldSendHelloOnJoinRealm() = runTest {
        joinRealm()
        verifyMessagesSent(HELLO.messageJson)
        verify { sessionListener wasNot Called } // we haven't received a WELCOME yet
    }

    @Test
    fun shouldAbortJoinRealm() = runTest {
        joinRealm()
        clearMocks(mockWebSocketDelegate)
        session.onMessage(ABORT_REALM_DOES_NOT_EXIST.messageJson)
        verifyNoMessageSent()
        verifyWebSocketWasClosed()
        verifySessionAborted("wamp.error.no_such_realm")
    }

    @Test
    fun shouldCalledSessionListenerOnRealmJoined() = runTest {
        joinRealm()
        receiveMessage(WELCOME.messageJson)
        verifyRealmJoined()
    }

    @Test
    fun shouldAbortWhenJoinedSecondTime() = runTest {
        joinRealm()
        clearMocks(mockWebSocketDelegate)
        joinRealm()
        verifyNoMessageSent()
        verifyWebSocketWasClosed()
        verifySessionAborted()
    }

    @Test
    fun shouldAbortWhenLeavingBeforeJoined() = runTest {
        joinRealm()
        leaveRealm()
        verifyMessagesSent(HELLO.messageJson, ABORT_SHUTDOWN.messageJson)
        verifyWebSocketWasClosed()
        verifySessionAborted()
    }

    @Test
    fun shouldLeaveAndShutdown() = runTest {
        joinRealm()
        receiveMessage(WELCOME.messageJson)
        leaveRealm()
        verifyMessagesSent(HELLO.messageJson, GOODBYE_CLOSE_REALM.messageJson)
        verifyRealmNotLeft() // haven't received GOODBYE back yet
        verifyWebSocketWasNotClosed()
        receiveMessage(GOODBYE_AND_OUT.messageJson)
        verifyRealmLeft(fromRouter = false)
        verifySessionNotShutdown()
        verifyWebSocketWasNotClosed() // we didn't shutdown yet, keep the web socket open!

        clearMocks(mockWebSocketDelegate)
        clearMocks(sessionListener)

        joinRealm() // one more time, now with shutdown
        receiveMessage(WELCOME.messageJson)
        shutdownSession()
        verifyMessagesSent(HELLO.messageJson, GOODBYE_SHUTDOWN.messageJson)
        receiveMessage(GOODBYE_AND_OUT.messageJson)
        verifyRealmLeft(fromRouter = false)
        verifySessionShutdown()
        verifyWebSocketWasClosed()
    }

    @Test
    fun shouldLeaveAndShutdownFromRouter() = runTest {
        joinRealm()
        receiveMessage(WELCOME.messageJson)
        receiveMessage(GOODBYE_CLOSE_REALM.messageJson)
        verifyMessagesSent(HELLO.messageJson, GOODBYE_AND_OUT.messageJson)
        verifyRealmLeft(fromRouter = true)
        verifySessionNotShutdown()
        verifyWebSocketWasNotClosed()

        clearMocks(mockWebSocketDelegate)
        clearMocks(sessionListener)

        joinRealm() // one more time, now with shutdown
        receiveMessage(WELCOME.messageJson)
        receiveMessage(GOODBYE_SHUTDOWN_WITH_MESSAGE.messageJson)
        verifyMessagesSent(HELLO.messageJson, GOODBYE_AND_OUT.messageJson)
        verifyRealmLeft(fromRouter = true)
        verifySessionShutdown()
        verifyWebSocketWasClosed()
    }

    private suspend fun leaveRealm() = session.leave()

    private suspend fun shutdownSession() = session.shutdown()

    private suspend fun joinRealm() = session.join("somerealm", sessionListener)

    private fun verifyMessagesSent(vararg message: String) =
        message.forEach {
            coVerify { mockWebSocketDelegate.send(it) }
        }


    private fun verifySessionAborted(reason: String) = verify { sessionListener.onSessionAborted(reason, any()) }
    private fun verifySessionAborted() = verify { sessionListener.onSessionAborted(any(), any()) }
    private fun verifyRealmJoined() = verify { sessionListener.onRealmJoined() }
    private fun verifyRealmLeft(fromRouter: Boolean) = verify { sessionListener.onRealmLeft(fromRouter) }
    private fun verifyRealmNotLeft() = verify(exactly = 0) { sessionListener.onRealmLeft(any()) }
    private fun verifySessionShutdown() = verify { sessionListener.onSessionShutdown() }
    private fun verifySessionNotShutdown() = verify(exactly = 0) { sessionListener.onSessionShutdown() }
    private fun verifyNoMessageSent() = coVerify(exactly = 0) { mockWebSocketDelegate.send(any()) }
    private fun verifyWebSocketWasClosed() = coVerify { mockWebSocketDelegate.close(any(), any()) }
    private fun verifyWebSocketWasNotClosed() = coVerify(exactly = 0) { mockWebSocketDelegate.close(any(), any()) }
}