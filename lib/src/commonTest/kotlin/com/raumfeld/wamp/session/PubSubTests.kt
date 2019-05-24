package com.raumfeld.wamp.session

import com.raumfeld.wamp.protocol.ExampleMessage
import com.raumfeld.wamp.protocol.ExampleMessage.*
import com.raumfeld.wamp.protocol.Message
import com.raumfeld.wamp.protocol.RequestMessage
import com.raumfeld.wamp.protocol.SubscriptionId
import com.raumfeld.wamp.pubsub.SubscriptionEvent
import com.raumfeld.wamp.pubsub.SubscriptionEvent.*
import com.raumfeld.wamp.runTest
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

internal class PubSubTests : BaseSessionTests() {

    private lateinit var eventChannel: ReceiveChannel<SubscriptionEvent>

    @BeforeTest
    override fun setup() = super.setup()

    @Test
    fun shouldAbortWhenSubscribeBeforeJoin() = runTest {
        subscribe()
        verifySessionAborted()
        verifyWebSocketWasClosed()
    }

    @Test
    fun shouldFailSubscription() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(SUBSCRIBE_ERROR)
        subscribe()
        assertNoEvent() // haven't gotten SUBSCRIBED back yet
        receiveMessages(SUBSCRIBE_ERROR)
        assertSubscriptionFailed()
    }

    @Test
    fun shouldSubscribe() = runTest {
        fastForwardToSubscribed()
    }

    @Test
    fun shouldUnsubscribe() = runTest {
        val subscriptionId = fastForwardToSubscribed()
        mockNextRequestIdWithIdFrom(UNSUBSCRIBE)
        unsubscribe(subscriptionId)
        assertNoEvent() // haven't gotten UNSUBSCRIBED back yet
        receiveMessages(UNSUBSCRIBED)
        getEvent<SubscriptionClosed>()
        assertChannelClosed()
    }

    @Test
    fun shouldFailUnsubscription() = runTest {
        val subscriptionId = fastForwardToSubscribed()
        mockNextRequestIdWithIdFrom(UNSUBSCRIBE_ERROR)
        unsubscribe(subscriptionId)
        assertNoEvent() // haven't gotten UNSUBSCRIBED back yet
        receiveMessages(UNSUBSCRIBE_ERROR)
        assertUnsubscriptionFailed()
    }

    @Test
    fun shouldFailUnsubscriptionOnAbort() = runTest {
        val subscriptionId = fastForwardToSubscribed()
        unsubscribe(subscriptionId)
        failOnSessionAbort(false)
        receiveMessages(HELLO)
        assertUnsubscriptionFailed()
    }

    @Test
    fun shouldFailPendingSubscriptionOnAbort() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        subscribe()
        failOnSessionAbort(false)
        receiveMessages(HELLO)
        assertSubscriptionFailed()
    }

    @Test
    fun shouldFailPendingSubscriptionOnRealmLeft() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        subscribe()
        failOnSessionAbort(false)
        leaveRealm()
        receiveGoodbyeAndOut()
        assertSubscriptionFailed()
    }

    @Test
    fun shouldFailPendingSubscriptionShutdown() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        subscribe()
        failOnSessionAbort(false)
        shutdownSession()
        receiveGoodbyeAndOut()
        assertSubscriptionFailed()
    }

    @Test
    fun shouldFailSubscriptionOnAbort() = runTest {
        fastForwardToSubscribed()
        failOnSessionAbort(false)
        receiveMessages(HELLO)
        assertSubscriptionFailed()
    }

    @Test
    fun shouldFailSubscriptionOnExternalRealmLeft() = runTest {
        fastForwardToSubscribed()
        receiveMessages(GOODBYE_CLOSE_REALM)
        assertSubscriptionFailed()
    }

    @Test
    fun shouldFailSubscriptionOnRealmLeft() = runTest {
        fastForwardToSubscribed()
        failOnSessionAbort(false)
        leaveRealm()
        receiveGoodbyeAndOut()
        assertSubscriptionFailed()
    }

    @Test
    fun shouldFailSubscriptionOnShutdown() = runTest {
        fastForwardToSubscribed()
        failOnSessionAbort(false)
        shutdownSession()
        receiveGoodbyeAndOut()
        assertSubscriptionFailed()
    }

    @Test
    fun shouldFailUnsubscriptionOnShutdown() = runTest {
        val subscriptionId = fastForwardToSubscribed()
        unsubscribe(subscriptionId)
        failOnSessionAbort(false)
        shutdownSession()
        receiveGoodbyeAndOut()
        assertUnsubscriptionFailed()
    }

    @Test
    fun shouldNotFailSubscriptionOnAbortAfterUnsubscribed() = runTest {
        val subscriptionId = fastForwardToSubscribed()
        failOnSessionAbort(false)
        mockNextRequestIdWithIdFrom(UNSUBSCRIBE)
        unsubscribe(subscriptionId)
        receiveMessages(UNSUBSCRIBED)
        getEvent<SubscriptionClosed>()
        receiveMessages(HELLO)
        assertNoEvent()
        assertChannelClosed()
    }

    @Test
    fun shouldIgnoreUnsubscribeOnPendingSubscriptions() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        subscribe()
        unsubscribe(42)
        assertNoEvent()
    }

    @Test
    fun shouldIgnoreUnsubscribeWhenNotSubscribed() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        unsubscribe(42)
    }

    @Test
    fun shouldUseSeparateChannels() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()

        mockNextRequestIdWithIdFrom(SUBSCRIBE)
        val channel1 = session.subscribe((SUBSCRIBE.message as Message.Subscribe).topic)
        mockNextRequestIdWithIdFrom(SUBSCRIBE2)
        val channel2 = session.subscribe((SUBSCRIBE2.message as Message.Subscribe).topic)

        receiveMessages(SUBSCRIBED2)
        receiveMessages(SUBSCRIBED)

        receiveMessages(EVENT_FULL_ARGS)
        val subscriptionId1 = getEvent<SubscriptionEstablished>(channel1).subscriptionId
        val subscriptionId2 = getEvent<SubscriptionEstablished>(channel2).subscriptionId
        getEvent<Payload>(channel1).let {
            assertEquals(expected = (EVENT_FULL_ARGS.message as Message.Event).subscriptionId, actual = subscriptionId1)
            assertEquals(expected = EVENT_FULL_ARGS.message.arguments, actual = it.arguments)
            assertEquals(expected = EVENT_FULL_ARGS.message.argumentsKw, actual = it.argumentsKw)
        }

        mockNextRequestIdWithIdFrom(UNSUBSCRIBE)
        unsubscribe(subscriptionId1)

        receiveMessages(EVENT_NO_ARG2) // should not affect channel1 in any way
        assertNoEvent(channel1)

        receiveMessages(UNSUBSCRIBED)
        getEvent<SubscriptionClosed>(channel1)
        assertChannelClosed(channel1)

        receiveMessages(EVENT_NO_ARG2) // should not affect channel1 in any way

        mockNextRequestIdWithIdFrom(UNSUBSCRIBE2)
        unsubscribe(subscriptionId2)

        receiveMessages(EVENT_NO_ARG2) // unsubscription pending, this will get ignored

        receiveMessages(UNSUBSCRIBED2)
        fun assertEvent(event: Payload) {
            assertEquals(expected = (EVENT_NO_ARG2.message as Message.Event).subscriptionId, actual = subscriptionId2)
            assertEquals(expected = EVENT_NO_ARG2.message.arguments, actual = event.arguments)
            assertEquals(expected = EVENT_NO_ARG2.message.argumentsKw, actual = event.argumentsKw)
        }
        // we have unsubscribed, but two events are still pending on channel2 before it gets closed (third one came too late and got ignored)
        getEvent<Payload>(channel2).let(::assertEvent)
        getEvent<Payload>(channel2).let(::assertEvent)
        getEvent<SubscriptionClosed>(channel2)
        assertChannelClosed(channel2)

        failOnSessionAbort(false)
        receiveMessages(EVENT_NO_ARG2) // we have unsubscribed already, this is a protocol violation
        verifySessionAborted()
        verifyWebSocketWasClosed()
    }

    @Test
    fun shouldReleaseIds() = runTest {
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(SUBSCRIBE)
        subscribe()
        verify { mockIdGenerator.newId() }
        receiveMessages(SUBSCRIBED)
        verify { mockIdGenerator.releaseId((SUBSCRIBE.message as Message.Subscribe).requestId) }
    }

    private suspend fun fastForwardToSubscribed(): SubscriptionId {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(SUBSCRIBE)
        subscribe()
        verifyMessagesSent(SUBSCRIBE)
        receiveMessages(SUBSCRIBED)
        val subscriptionId = getEvent<SubscriptionEstablished>().subscriptionId
        assertEquals(subscriptionId, (SUBSCRIBED.message as Message.Subscribed).subscriptionId)
        return subscriptionId
    }

    private fun failOnSessionAbort(fail: Boolean = true) {
        every {
            sessionListener.onSessionAborted(
                any(),
                any()
            )
        } answers { if (fail) fail("Session was aborted prematurely: $invocation") }
    }

    private suspend inline fun <reified T : SubscriptionEvent> getEvent(channel: ReceiveChannel<*> = eventChannel) =
        withTimeout(1000) { channel.receive() } as T

    private fun getEventOrNull(channel: ReceiveChannel<*> = eventChannel) = channel.poll()

    private fun mockNextRequestIdWithIdFrom(message: ExampleMessage) {
        every { mockIdGenerator.newId() } returns (message.message as RequestMessage).requestId
    }

    private suspend fun subscribe(topic: String = (SUBSCRIBE.message as Message.Subscribe).topic) {
        eventChannel = session.subscribe(topic)
    }

    private suspend fun unsubscribe(subscriptionId: SubscriptionId) {
        session.unsubscribe(subscriptionId)
    }

    private fun assertNoEvent(channel: ReceiveChannel<*> = eventChannel) = assertEquals(expected = null, actual = getEventOrNull(channel))

    private suspend fun assertSubscriptionFailed() {
        getEvent<SubscriptionFailed>()
        assertChannelClosed()
    }

    private suspend fun assertUnsubscriptionFailed() {
        getEvent<UnsubscriptionFailed>()
        assertChannelClosed()
    }

    private suspend fun assertChannelClosed(channel: ReceiveChannel<*> = eventChannel) {
        withTimeout(1000) { assertEquals(expected = null, actual = channel.receiveOrNull()) }
    }
}