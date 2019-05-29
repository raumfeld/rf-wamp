package com.raumfeld.wamp.session

import com.raumfeld.wamp.protocol.ExampleMessage
import com.raumfeld.wamp.protocol.ExampleMessage.*
import com.raumfeld.wamp.protocol.Message
import com.raumfeld.wamp.runTest
import io.mockk.Called
import io.mockk.clearMocks
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test

internal class WampSessionTests : BaseSessionTests() {

    @BeforeTest
    override fun setup() = super.setup()

    @Test
    fun shouldSendHelloOnJoinRealm() = runTest {
        joinRealm()
        verifyMessagesSent(HELLO)
        verify { sessionListener wasNot Called } // we haven't received a WELCOME yet
    }

    @Test
    fun shouldAbortJoinRealm() = runTest {
        joinRealm()
        clearMocks(mockWebSocketDelegate)
        receiveMessages(ABORT_REALM_DOES_NOT_EXIST)
        verifyNoMessageSent()
        verifyWebSocketWasClosed()
        verifySessionAborted("wamp.error.no_such_realm")
    }

    @Test
    fun shouldAbortJoinRealmOnAnyOtherMessages() = runTest {
        val otherMessages = ALL_MESSAGES - WELCOME - listOf(
            ABORT_SHUTDOWN,
            ABORT_REALM_DOES_NOT_EXIST,
            ABORT_PROTOCOL_VIOLATION_NO_DETAILS,
            ABORT_PROTOCOL_VIOLATION_AFTER_HELLO,
            ABORT_PROTOCOL_VIOLATION_UNEXPECTED_MESSAGE
        )
        otherMessages.forEach {
            joinRealm()
            receiveMessages(it)
            verifyMessagesSent(HELLO, ABORT_PROTOCOL_VIOLATION_AFTER_HELLO)
            verifyWebSocketWasClosed()
            verifySessionAborted()
            createSession()
        }
    }

    @Test
    fun shouldAbortOnInvalidMessage() = runTest {
        val invalidMessages =
            listOf("[100, {}, [], {}]", "[{where did my braces go..", "[]", "\uD83D\uDE00\t\uD83D\uDD2C")
        invalidMessages.forEach {
            joinRealm()
            receiveMessages(it)
            verifyMessagesSent(HELLO, ABORT_PROTOCOL_VIOLATION_AFTER_HELLO)
            verifyWebSocketWasClosed()
            verifySessionAborted()
            createSession()
        }
    }

    @Test
    fun shouldCallSessionListenerOnRealmJoined() = runTest {
        joinRealm()
        receiveMessages(WELCOME)
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
        verifyMessagesSent(HELLO, ABORT_SHUTDOWN)
        verifyRealmNotLeft() // we haven't joined, so we can't leave
        verifyWebSocketWasClosed()
        verifySessionAborted()
    }

    @Test
    fun shouldAbortWhenShuttingDownBeforeJoined() = runTest {
        joinRealm()
        shutdownSession()
        verifyMessagesSent(HELLO, ABORT_SHUTDOWN)
        verifyRealmNotLeft() // we haven't joined, so we can't leave
        verifyWebSocketWasClosed()
        verifySessionAborted()
    }

    @Test
    fun shouldAbortWhenReceivingUnexpectedMessagesAfterJoined() = runTest {
        val genericUnexpectedMessages = ALL_MESSAGES - listOf(
            // these are all allowed
            ABORT_SHUTDOWN,
            ABORT_REALM_DOES_NOT_EXIST,
            ABORT_PROTOCOL_VIOLATION_NO_DETAILS,
            ABORT_PROTOCOL_VIOLATION_AFTER_HELLO,
            ABORT_PROTOCOL_VIOLATION_UNEXPECTED_MESSAGE,
            GOODBYE_SHUTDOWN,
            GOODBYE_SHUTDOWN_WITH_MESSAGE,
            GOODBYE_CLOSE_REALM,
            // these are just ignored
            SUBSCRIBED2,
            PUBLISHED2,
            REGISTERED2,
            UNREGISTERED2,
            UNSUBSCRIBED2,
            EVENT_NO_ARG2,
            INVOCATION_NO_ARG2,
            RESULT_NO_ARG2,
            // for these we send unique protocol violations
            SUBSCRIBED,
            SUBSCRIBE_ERROR,
            REGISTERED,
            EVENT_FULL_ARGS,
            EVENT_ONLY_ARRAY_ARG,
            EVENT_NO_ARG,
            REGISTER_ERROR,
            CALL_ERROR_FULL_ARGS,
            CALL_ERROR_ONLY_ARRAY_ARG,
            CALL_ERROR_NO_ARG,
            INVOCATION_FULL_ARGS,
            INVOCATION_ONLY_ARRAY_ARG,
            INVOCATION_NO_ARG,
            INVOCATION_ERROR_FULL_ARGS,
            INVOCATION_ERROR_ONLY_ARRAY_ARG,
            INVOCATION_ERROR_NO_ARG,
            RESULT_FULL_ARGS,
            RESULT_ONLY_ARRAY_ARG,
            RESULT_NO_ARG
        )
        val receivedToSentMessages: MutableMap<ExampleMessage, Message> =
            genericUnexpectedMessages.associate { it to ABORT_PROTOCOL_VIOLATION_UNEXPECTED_MESSAGE.message }
                .toMutableMap()

        receivedToSentMessages[SUBSCRIBED] =
            protocolViolationMessage("Received SUBSCRIBED that we have no pending subscription for. RequestId = ${(SUBSCRIBED.message as Message.Subscribed).requestId} subscriptionId = ${SUBSCRIBED.message.subscriptionId}")
        receivedToSentMessages[PUBLISHED] =
            protocolViolationMessage("Received PUBLISHED that we have no pending publication for. RequestId = ${(PUBLISHED.message as Message.Published).requestId} publicationId = ${PUBLISHED.message.publicationId}")
        receivedToSentMessages[UNSUBSCRIBED] =
            protocolViolationMessage("Received UNSUBSCRIBED that we have no pending unsubscription for. RequestId = ${(UNSUBSCRIBED.message as Message.Unsubscribed).requestId}")
        receivedToSentMessages[REGISTERED] =
            protocolViolationMessage("Received REGISTERED that we have no pending registration for. RequestId = ${(REGISTERED.message as Message.Registered).requestId}")
        receivedToSentMessages[UNREGISTERED] =
            protocolViolationMessage("Received UNREGISTERED that we have no pending unregistration for. RequestId = ${(UNREGISTERED.message as Message.Unregistered).requestId}")
        receivedToSentMessages[INVOCATION_FULL_ARGS] =
            protocolViolationMessage("Received INVOCATION that we have no registration or pending unregistration for. RequestId = ${(INVOCATION_FULL_ARGS.message as Message.Invocation).requestId} RegistrationId = ${INVOCATION_FULL_ARGS.message.registrationId}")
        receivedToSentMessages[RESULT_FULL_ARGS] =
            protocolViolationMessage("Received RESULT that we have no pending call for. RequestId = ${(RESULT_FULL_ARGS.message as Message.Result).requestId}")
        receivedToSentMessages[EVENT_FULL_ARGS] =
            protocolViolationMessage("Received EVENT that we have no subscription or pending unsubscription for. SubscriptionId = ${(EVENT_FULL_ARGS.message as Message.Event).subscriptionId}")
        receivedToSentMessages[SUBSCRIBE_ERROR] =
            protocolViolationMessage("Received SUBSCRIBE ERROR that we have no pending subscription for. RequestId = ${(SUBSCRIBE_ERROR.message as Message.Error).requestId} ERROR uri = ${SUBSCRIBE_ERROR.message.wampErrorUri}")
        receivedToSentMessages[PUBLISH_ERROR] =
            protocolViolationMessage("Received PUBLISH ERROR that we have no pending publication for. RequestId = ${(PUBLISH_ERROR.message as Message.Error).requestId} ERROR uri = ${PUBLISH_ERROR.message.wampErrorUri}")
        receivedToSentMessages[UNSUBSCRIBE_ERROR] =
            protocolViolationMessage("Received UNSUBSCRIBE ERROR that we have no pending unsubscription for. RequestId = ${(UNSUBSCRIBE_ERROR.message as Message.Error).requestId} ERROR uri = ${UNSUBSCRIBE_ERROR.message.wampErrorUri}")
        receivedToSentMessages[REGISTER_ERROR] =
            protocolViolationMessage("Received REGISTER ERROR that we have no pending registration for. RequestId = ${(REGISTER_ERROR.message as Message.Error).requestId} ERROR uri = ${REGISTER_ERROR.message.wampErrorUri}")
        receivedToSentMessages[UNREGISTER_ERROR] =
            protocolViolationMessage("Received UNREGISTER ERROR that we have no pending unregistration for. RequestId = ${(UNREGISTER_ERROR.message as Message.Error).requestId} ERROR uri = ${UNREGISTER_ERROR.message.wampErrorUri}")
        receivedToSentMessages[INVOCATION_ERROR_FULL_ARGS] =
            protocolViolationMessage("Received invalid REQUEST. Type: ${(INVOCATION_ERROR_FULL_ARGS.message as Message.Error).originalType}")
        receivedToSentMessages[CALL_ERROR_FULL_ARGS] =
            protocolViolationMessage("Received CALL ERROR that we have no pending call for. RequestId = ${(CALL_ERROR_FULL_ARGS.message as Message.Error).requestId} ERROR uri = ${CALL_ERROR_FULL_ARGS.message.wampErrorUri}")

        receivedToSentMessages.forEach { (received, sent) ->
            joinRealm()
            clearMocks(mockWebSocketDelegate) // clear the first HELLO
            receiveMessages(WELCOME)
            receiveMessages(received)
            verifyMessagesSent(sent.toJson())
            verifyWebSocketWasClosed()
            verifySessionAborted()
            createSession()
        }
    }

    @Test
    fun shouldNotSendAbortWhenAbortReceived() = runTest {
        joinRealm()
        receiveMessages(WELCOME)
        clearMocks(mockWebSocketDelegate)
        receiveMessages(ABORT_PROTOCOL_VIOLATION_NO_DETAILS)
        verifyNoMessageSent()
        verifyWebSocketWasClosed()
        verifySessionAborted()
    }

    @Test
    fun shouldAlwaysAbortAfterLeaveBeforeJoined() = runTest {
        joinRealm()
        leaveRealm()
        clearMocks(sessionListener)
        joinRealm()
        verifySessionAborted()
    }

    @Test
    fun shouldAlwaysAbortAfterShutdownBeforeJoined() = runTest {
        joinRealm()
        shutdownSession()
        clearMocks(sessionListener)
        joinRealm()
        verifySessionAborted()
    }

    @Test
    fun shouldLeaveAndShutdown() = runTest {
        joinRealm()
        receiveMessages(WELCOME)
        leaveRealm()
        verifyMessagesSent(HELLO, GOODBYE_CLOSE_REALM)
        verifyRealmNotLeft() // haven't received GOODBYE back yet
        verifyWebSocketWasNotClosed()
        receiveMessages(GOODBYE_AND_OUT)
        verifyRealmLeft(fromRouter = false)
        verifySessionNotShutdown()
        verifyWebSocketWasNotClosed() // we didn't shutdown yet, keep the web socket open!

        clearMocks(mockWebSocketDelegate)
        clearMocks(sessionListener)

        joinRealm() // one more time, now with shutdown
        receiveMessages(WELCOME)
        shutdownSession()
        verifyMessagesSent(HELLO, GOODBYE_SHUTDOWN)
        receiveMessages(GOODBYE_AND_OUT)
        verifyRealmLeft(fromRouter = false)
        verifySessionShutdown()
        verifyWebSocketWasClosed()
    }

    @Test
    fun shouldLeaveAndShutdownFromRouter() = runTest {
        joinRealm()
        receiveMessages(WELCOME)
        receiveMessages(GOODBYE_CLOSE_REALM)
        verifyMessagesSent(HELLO, GOODBYE_AND_OUT)
        verifyRealmLeft(fromRouter = true)
        verifySessionNotShutdown()
        verifyWebSocketWasNotClosed()

        clearMocks(mockWebSocketDelegate)
        clearMocks(sessionListener)

        joinRealm() // one more time, now with shutdown
        receiveMessages(WELCOME)
        receiveMessages(GOODBYE_SHUTDOWN_WITH_MESSAGE)
        verifyMessagesSent(HELLO, GOODBYE_AND_OUT)
        verifyRealmLeft(fromRouter = true)
        verifySessionShutdown()
        verifyWebSocketWasClosed()
    }

    @Test
    fun shouldIgnoreOtherMessagesWhileLeavingAndShuttingDown() = runTest {
        joinRealm()
        receiveMessages(WELCOME)
        leaveRealm()

        val goodbyeMessages =
            listOf(GOODBYE_AND_OUT, GOODBYE_SHUTDOWN, GOODBYE_SHUTDOWN_WITH_MESSAGE, GOODBYE_CLOSE_REALM)
        val otherMessages = ALL_MESSAGES - goodbyeMessages
        receiveMessages(otherMessages)

        verifyRealmNotLeft()
        verifySessionNotShutdown()
        verifyWebSocketWasNotClosed()

        receiveMessages(GOODBYE_AND_OUT) // acknowledge leaving

        verifyRealmLeft(fromRouter = false)

        clearMocks(sessionListener)

        joinRealm() // one more time, now with shutdown
        receiveMessages(WELCOME)
        shutdownSession()

        receiveMessages(otherMessages)
        verifyRealmNotLeft()
        verifySessionNotShutdown()
        verifyWebSocketWasNotClosed()

        receiveMessages(GOODBYE_AND_OUT) // acknowledge leaving
        verifyRealmLeft(fromRouter = false)
        verifySessionShutdown()
        verifyWebSocketWasClosed()
    }

    @Test
    fun shouldIgnoreErrorMessagesWhileInitialOrShutdown() = runTest {
        joinRealm()
        receiveWelcome()
        leaveRealm()
        receiveGoodbyeAndOut()

        receiveMessages(CALL_ERROR_FULL_ARGS)

        joinRealm()
        receiveWelcome()
        shutdownSession()
        receiveGoodbyeAndOut()

        receiveMessages(CALL_ERROR_FULL_ARGS)
        verifySessionNotAborted()
    }
}