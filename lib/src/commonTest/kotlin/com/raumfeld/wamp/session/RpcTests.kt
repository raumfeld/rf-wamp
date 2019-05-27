package com.raumfeld.wamp.session

import com.raumfeld.wamp.protocol.ExampleMessage
import com.raumfeld.wamp.protocol.ExampleMessage.*
import com.raumfeld.wamp.protocol.Message
import com.raumfeld.wamp.protocol.ProcedureId
import com.raumfeld.wamp.protocol.RegistrationId
import com.raumfeld.wamp.rpc.CalleeEvent
import com.raumfeld.wamp.rpc.CalleeEvent.*
import com.raumfeld.wamp.rpc.CallerEvent
import com.raumfeld.wamp.rpc.CallerEvent.CallFailed
import com.raumfeld.wamp.runTest
import io.mockk.clearMocks
import io.mockk.verify
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal class RpcTests : BaseSessionTests() {
    private lateinit var callerEventChannel: ReceiveChannel<CallerEvent>
    private lateinit var calleeEventChannel: ReceiveChannel<CalleeEvent>

    @BeforeTest
    override fun setup() = super.setup()

    @Test
    fun shouldAbortWhenRegisterBeforeJoin() = runTest {
        register()
        verifySessionAborted()
        verifyWebSocketWasClosed()
    }

    @Test
    fun shouldFailRegistration() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(REGISTER_ERROR)
        register()
        assertNoCalleeEvent() // haven't gotten REGISTERED back yet
        receiveMessages(REGISTER_ERROR)
        assertRegistrationFailed()
    }

    @Test
    fun shouldRegister() = runTest {
        fastForwardToRegistered()
    }

    @Test
    fun shouldUnregister() = runTest {
        val registrationId = fastForwardToRegistered()
        mockNextRequestIdWithIdFrom(UNREGISTER)
        unregister(registrationId)
        assertNoCalleeEvent() // haven't gotten UNREGISTERED back yet
        receiveMessages(UNREGISTERED)
        getCalleeEvent<ProcedureUnregistered>()
        assertCalleeChannelClosed()
    }

    @Test
    fun shouldFailUnregistration() = runTest {
        val registrationId = fastForwardToRegistered()
        mockNextRequestIdWithIdFrom(UNREGISTER_ERROR)
        unregister(registrationId)
        assertNoCalleeEvent() // haven't gotten UNREGISTERED back yet
        receiveMessages(UNREGISTER_ERROR)
        assertUnregistrationFailed()
    }

    @Test
    fun shouldFailUnregistrationOnAbort() = runTest {
        val registrationId = fastForwardToRegistered()
        unregister(registrationId)
        failOnSessionAbort(false)
        receiveMessages(HELLO)
        assertUnregistrationFailed()
    }

    @Test
    fun shouldFailPendingRegistrationOnAbort() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        register()
        failOnSessionAbort(false)
        receiveMessages(HELLO)
        assertRegistrationFailed()
    }

    @Test
    fun shouldFailPendingRegistrationOnRealmLeft() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        register()
        failOnSessionAbort(false)
        leaveRealm()
        receiveGoodbyeAndOut()
        assertRegistrationFailed()
    }

    @Test
    fun shouldFailPendingRegistrationOnShutdown() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        register()
        failOnSessionAbort(false)
        shutdownSession()
        receiveGoodbyeAndOut()
        assertRegistrationFailed()
    }

    @Test
    fun shouldFailRegistrationOnAbort() = runTest {
        fastForwardToRegistered()
        failOnSessionAbort(false)
        receiveMessages(HELLO)
        assertRegistrationFailed()
    }

    @Test
    fun shouldFailRegistrationOnExternalRealmLeft() = runTest {
        fastForwardToRegistered()
        receiveMessages(GOODBYE_CLOSE_REALM)
        assertRegistrationFailed()
    }

    @Test
    fun shouldFailRegistrationOnRealmLeft() = runTest {
        fastForwardToRegistered()
        failOnSessionAbort(false)
        leaveRealm()
        receiveGoodbyeAndOut()
        assertRegistrationFailed()
    }

    @Test
    fun shouldFailRegistrationOnShutdown() = runTest {
        fastForwardToRegistered()
        failOnSessionAbort(false)
        shutdownSession()
        receiveGoodbyeAndOut()
        assertRegistrationFailed()
    }

    @Test
    fun shouldFailUnregistrationOnShutdown() = runTest {
        val registrationId = fastForwardToRegistered()
        unregister(registrationId)
        failOnSessionAbort(false)
        shutdownSession()
        receiveGoodbyeAndOut()
        assertUnregistrationFailed()
    }

    @Test
    fun shouldNotFailRegistrationOnAbortAfterUnregistered() = runTest {
        val registrationId = fastForwardToRegistered()
        failOnSessionAbort(false)
        mockNextRequestIdWithIdFrom(UNREGISTER)
        unregister(registrationId)
        receiveMessages(UNREGISTERED)
        getCalleeEvent<ProcedureUnregistered>()
        receiveMessages(HELLO)
        assertNoCalleeEvent()
        assertCalleeChannelClosed()
    }

    @Test
    fun shouldIgnoreUnregisterOnPendingRegistration() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        register()
        unregister(42)
        assertNoCalleeEvent()
    }

    @Test
    fun shouldIgnoreUnregisterWhenNotRegistered() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        unregister(42)
    }

    @Test
    fun shouldReleaseIds() = runTest {
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(REGISTER)
        register()
        verify { mockIdGenerator.newId() }
        receiveMessages(REGISTERED)
        verify { mockIdGenerator.releaseId((REGISTER.message as Message.Register).requestId) }
    }

    @Test
    fun shouldAbortWhenCallIsCalledBeforeJoinAndAfterAborted() = runTest {
        failOnSessionAbort(false)
        call(
            (CALL_NO_ARG.message as Message.Call).procedureId,
            CALL_NO_ARG.message.arguments,
            CALL_NO_ARG.message.argumentsKw
        )
        verifySessionAborted()
        verifyWebSocketWasClosed()

        clearMocks(sessionListener)
        call(
            CALL_NO_ARG.message.procedureId,
            CALL_NO_ARG.message.arguments,
            CALL_NO_ARG.message.argumentsKw
        )
        verifySessionAborted()
    }

    @Test
    fun shouldCallNoArg() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(CALL_NO_ARG)
        call(
            (CALL_NO_ARG.message as Message.Call).procedureId,
            CALL_NO_ARG.message.arguments,
            CALL_NO_ARG.message.argumentsKw
        )
        verifyMessagesSent(HELLO, CALL_NO_ARG)
        receiveMessages(RESULT_NO_ARG)
        assertCallSucceeded(RESULT_NO_ARG)
    }

    @Test
    fun shouldCallArrayArg() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(CALL_ONLY_ARRAY_ARG)
        call(
            (CALL_ONLY_ARRAY_ARG.message as Message.Call).procedureId,
            CALL_ONLY_ARRAY_ARG.message.arguments,
            CALL_ONLY_ARRAY_ARG.message.argumentsKw
        )
        verifyMessagesSent(HELLO, CALL_ONLY_ARRAY_ARG)
        receiveMessages(RESULT_ONLY_ARRAY_ARG)
        assertCallSucceeded(RESULT_ONLY_ARRAY_ARG)
    }

    @Test
    fun shouldCallAllArgs() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(CALL_FULL_ARGS)
        call(
            (CALL_FULL_ARGS.message as Message.Call).procedureId,
            CALL_FULL_ARGS.message.arguments,
            CALL_FULL_ARGS.message.argumentsKw
        )
        verifyMessagesSent(HELLO, CALL_FULL_ARGS)
        receiveMessages(RESULT_FULL_ARGS)
        assertCallSucceeded(RESULT_FULL_ARGS)
    }

    @Test
    fun shouldFailCall() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(CALL_NO_ARG)
        call(
            (CALL_NO_ARG.message as Message.Call).procedureId,
            CALL_NO_ARG.message.arguments,
            CALL_NO_ARG.message.argumentsKw
        )
        receiveMessages(CALL_ERROR_NO_ARG)
        assertCallFailed()
    }

    @Test
    fun shouldFailCallOnAbort() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(CALL_NO_ARG)
        call(
            (CALL_NO_ARG.message as Message.Call).procedureId,
            CALL_NO_ARG.message.arguments,
            CALL_NO_ARG.message.argumentsKw
        )
        failOnSessionAbort(false)
        receiveMessages(HELLO)
        assertCallFailed()
    }

    @Test
    fun shouldFailCallOnLeavingOrShuttingDown() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(CALL_NO_ARG)
        call(
            (CALL_NO_ARG.message as Message.Call).procedureId,
            CALL_NO_ARG.message.arguments,
            CALL_NO_ARG.message.argumentsKw
        )
        leaveRealm()
        receiveGoodbyeAndOut()
        assertCallFailed()

        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(CALL_NO_ARG)
        call(
            CALL_NO_ARG.message.procedureId,
            CALL_NO_ARG.message.arguments,
            CALL_NO_ARG.message.argumentsKw
        )
        shutdownSession()
        receiveGoodbyeAndOut()
        assertCallFailed()
    }

    @Test
    fun shouldUseSeparateCallChannels() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()

        mockNextRequestIdWithIdFrom(CALL_NO_ARG)
        val channel1 = session.call(
            (CALL_NO_ARG.message as Message.Call).procedureId,
            CALL_NO_ARG.message.arguments,
            CALL_NO_ARG.message.argumentsKw
        )
        mockNextRequestIdWithIdFrom(CALL_NO_ARG2)
        val channel2 = session.call(
            (CALL_NO_ARG2.message as Message.Call).procedureId,
            CALL_NO_ARG2.message.arguments,
            CALL_NO_ARG2.message.argumentsKw
        )

        clearMocks(mockWebSocketDelegate)

        receiveMessages(RESULT_NO_ARG2)
        receiveMessages(RESULT_NO_ARG)

        val (arguments1, argumentsKw1) = getEvent<CallerEvent.Result>(channel1).let { it.arguments to it.argumentsKw }
        val (arguments2, argumentsKw2) = getEvent<CallerEvent.Result>(channel2).let { it.arguments to it.argumentsKw }

        assertEquals((RESULT_NO_ARG.message as Message.Result).arguments, arguments1)
        assertEquals(RESULT_NO_ARG.message.argumentsKw, argumentsKw1)
        assertEquals((RESULT_NO_ARG2.message as Message.Result).arguments, arguments2)
        assertEquals(RESULT_NO_ARG2.message.argumentsKw, argumentsKw2)

        assertChannelClosed(channel1)
        assertChannelClosed(channel2)
    }

    @Test
    fun shouldUseSeparateRegistrationChannels() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()

        mockNextRequestIdWithIdFrom(REGISTER)
        val channel1 = session.register((REGISTER.message as Message.Register).procedureId)
        mockNextRequestIdWithIdFrom(REGISTER2)
        val channel2 = session.register((REGISTER2.message as Message.Register).procedureId)

        receiveMessages(REGISTERED2)
        receiveMessages(REGISTERED)

        receiveMessages(INVOCATION_FULL_ARGS)
        val registrationId1 = getEvent<ProcedureRegistered>(channel1).registrationId
        val registrationId2 = getEvent<ProcedureRegistered>(channel2).registrationId
        getEvent<Invocation>(channel1).let {
            assertEquals(
                expected = (INVOCATION_FULL_ARGS.message as Message.Invocation).registrationId,
                actual = registrationId1
            )
            assertEquals(expected = INVOCATION_FULL_ARGS.message.arguments, actual = it.arguments)
            assertEquals(expected = INVOCATION_FULL_ARGS.message.argumentsKw, actual = it.argumentsKw)
        }

        mockNextRequestIdWithIdFrom(UNREGISTER)
        unregister(registrationId1)

        receiveMessages(INVOCATION_NO_ARG2) // should not affect channel1 in any way
        assertNoEvent(channel1)

        receiveMessages(UNREGISTERED)
        getEvent<ProcedureUnregistered>(channel1)
        assertChannelClosed(channel1)

        receiveMessages(INVOCATION_NO_ARG2) // should not affect channel1 in any way

        mockNextRequestIdWithIdFrom(UNREGISTER2)
        unregister(registrationId2)

        receiveMessages(INVOCATION_NO_ARG2) // unsubscription pending, this will get ignored

        receiveMessages(UNREGISTERED2)
        fun assertInvocation(event: Invocation) {
            assertEquals(
                expected = (INVOCATION_NO_ARG2.message as Message.Invocation).registrationId,
                actual = registrationId2
            )
            assertEquals(expected = INVOCATION_NO_ARG2.message.arguments, actual = event.arguments)
            assertEquals(expected = INVOCATION_NO_ARG2.message.argumentsKw, actual = event.argumentsKw)
        }
        // we have unregistered, but two invocations are still pending on channel2 before it gets closed (third one came too late and got ignored)
        getEvent<Invocation>(channel2).let(::assertInvocation)
        getEvent<Invocation>(channel2).let(::assertInvocation)
        getEvent<ProcedureUnregistered>(channel2)
        assertChannelClosed(channel2)

        failOnSessionAbort(false)
        receiveMessages(INVOCATION_NO_ARG2) // we have unsubscribed already, this is a protocol violation
        verifySessionAborted()
        verifyWebSocketWasClosed()
    }

    @Test
    fun shouldYieldNoArg() = runTest {
        fastForwardToRegistered()
        receiveMessages(INVOCATION_NO_ARG)
        val returnResult = getCalleeEvent<Invocation>().returnResult
        returnResult(CallerEvent.Result())
        verifyMessagesSent(YIELD_NO_ARG)
    }

    @Test
    fun shouldYieldArrayArg() = runTest {
        fastForwardToRegistered()
        receiveMessages(INVOCATION_ONLY_ARRAY_ARG)
        val returnResult = getCalleeEvent<Invocation>().returnResult
        returnResult(CallerEvent.Result((YIELD_ONLY_ARRAY_ARG.message as Message.Yield).arguments))
        verifyMessagesSent(YIELD_ONLY_ARRAY_ARG)
    }

    @Test
    fun shouldYieldAllArgs() = runTest {
        fastForwardToRegistered()
        receiveMessages(INVOCATION_FULL_ARGS)
        val returnResult = getCalleeEvent<Invocation>().returnResult
        returnResult(
            CallerEvent.Result(
                (YIELD_FULL_ARGS.message as Message.Yield).arguments,
                YIELD_FULL_ARGS.message.argumentsKw
            )
        )
        verifyMessagesSent(YIELD_FULL_ARGS)
    }

    @Test
    fun shouldAbortWhenYieldCalledAfterRealmLeft() = runTest {
        fastForwardToRegistered()
        receiveMessages(INVOCATION_NO_ARG)
        val returnResult = getCalleeEvent<Invocation>().returnResult
        failOnSessionAbort(false)
        leaveRealm()

        clearMocks(mockWebSocketDelegate)
        clearMocks(sessionListener)

        returnResult(CallerEvent.Result())
        verifyNoMessageSent()
        verifySessionAborted()
    }

    @Test
    fun shouldSendInvocationErrorNoArg() = runTest {
        fastForwardToRegistered()
        receiveMessages(INVOCATION_NO_ARG)
        val returnResult = getCalleeEvent<Invocation>().returnResult
        val errorUri = (INVOCATION_ERROR_NO_ARG.message as Message.Error).wampErrorUri
        returnResult(
            CallFailed(
                errorUri,
                INVOCATION_ERROR_NO_ARG.message.arguments,
                INVOCATION_ERROR_NO_ARG.message.argumentsKw
            )
        )
        verifyMessagesSent(INVOCATION_ERROR_NO_ARG)
    }

    @Test
    fun shouldSendInvocationErrorArrayArg() = runTest {
        fastForwardToRegistered()
        receiveMessages(INVOCATION_NO_ARG)
        val returnResult = getCalleeEvent<Invocation>().returnResult
        val errorUri = (INVOCATION_ERROR_ONLY_ARRAY_ARG.message as Message.Error).wampErrorUri
        returnResult(
            CallFailed(
                errorUri,
                INVOCATION_ERROR_ONLY_ARRAY_ARG.message.arguments,
                INVOCATION_ERROR_ONLY_ARRAY_ARG.message.argumentsKw
            )
        )
        verifyMessagesSent(INVOCATION_ERROR_ONLY_ARRAY_ARG)
    }

    @Test
    fun shouldSendInvocationErrorAllArgs() = runTest {
        fastForwardToRegistered()
        receiveMessages(INVOCATION_NO_ARG)
        val returnResult = getCalleeEvent<Invocation>().returnResult
        val errorUri = (INVOCATION_ERROR_FULL_ARGS.message as Message.Error).wampErrorUri
        returnResult(
            CallFailed(
                errorUri,
                INVOCATION_ERROR_FULL_ARGS.message.arguments,
                INVOCATION_ERROR_FULL_ARGS.message.argumentsKw
            )
        )
        verifyMessagesSent(INVOCATION_ERROR_FULL_ARGS)
    }

    private suspend fun fastForwardToRegistered(): RegistrationId {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(REGISTER)
        register()
        verifyMessagesSent(REGISTER)
        receiveMessages(REGISTERED)
        val registrationId = getCalleeEvent<ProcedureRegistered>().registrationId
        assertEquals(registrationId, (REGISTERED.message as Message.Registered).registrationId)
        return registrationId
    }

    private suspend fun register(procedureId: ProcedureId = (REGISTER.message as Message.Register).procedureId) {
        calleeEventChannel = session.register(procedureId)
    }

    private suspend fun unregister(registrationId: RegistrationId) {
        session.unregister(registrationId)
    }

    private suspend fun call(procedureId: ProcedureId, arguments: JsonArray?, argumentsKw: JsonObject?) {
        callerEventChannel = session.call(procedureId, arguments, argumentsKw)
    }

    private suspend fun assertRegistrationFailed() {
        getCalleeEvent<RegistrationFailed>()
        assertCalleeChannelClosed()
    }

    private suspend fun assertUnregistrationFailed() {
        getCalleeEvent<UnregistrationFailed>()
        assertCalleeChannelClosed()
    }

    private suspend fun assertCallFailed() {
        getCallerEvent<CallFailed>()
        assertCallerChannelClosed()
    }

    private suspend inline fun <reified T> getCalleeEvent() = getEvent<T>(calleeEventChannel)
    private suspend inline fun <reified T> getCallerEvent() = getEvent<T>(callerEventChannel)
    private fun assertNoCalleeEvent() = assertNoEvent(calleeEventChannel)
    private fun assertNoCallerEvent() = assertNoEvent(callerEventChannel)
    private suspend fun assertCalleeChannelClosed() = assertChannelClosed(calleeEventChannel)
    private suspend fun assertCallerChannelClosed() = assertChannelClosed(callerEventChannel)
    private suspend fun assertCallSucceeded(message: ExampleMessage) {
        val (arguments, argumentsKw) = getCallerEvent<CallerEvent.Result>().let { it.arguments to it.argumentsKw }
        assertEquals((message.message as Message.Result).arguments, arguments)
        assertEquals(message.message.argumentsKw, argumentsKw)
        assertCallerChannelClosed()
    }

}