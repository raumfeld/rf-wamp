package com.raumfeld.wamp.session

import com.raumfeld.wamp.protocol.*
import com.raumfeld.wamp.protocol.ExampleMessage
import com.raumfeld.wamp.protocol.ExampleMessage.*
import com.raumfeld.wamp.pubsub.PublicationEvent
import com.raumfeld.wamp.pubsub.SubscriptionEvent
import com.raumfeld.wamp.pubsub.SubscriptionEvent.*
import com.raumfeld.wamp.runTest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

internal class PubSubTests : BaseSessionTests() {

    private lateinit var subscriptionEventChannel: ReceiveChannel<SubscriptionEvent>
    private lateinit var publicationEventChannel: ReceiveChannel<PublicationEvent>

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
        assertNoSubscriptionEvent() // haven't gotten SUBSCRIBED back yet
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
        assertNoSubscriptionEvent() // haven't gotten UNSUBSCRIBED back yet
        receiveMessages(UNSUBSCRIBED)
        getSubscriptionEvent<SubscriptionClosed>()
        assertSubscriptionChannelClosed()
    }

    @Test
    fun shouldFailUnsubscription() = runTest {
        val subscriptionId = fastForwardToSubscribed()
        mockNextRequestIdWithIdFrom(UNSUBSCRIBE_ERROR)
        unsubscribe(subscriptionId)
        assertNoSubscriptionEvent() // haven't gotten UNSUBSCRIBED back yet
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
    fun shouldFailPendingSubscriptionOnShutdown() = runTest {
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
        getSubscriptionEvent<SubscriptionClosed>()
        receiveMessages(HELLO)
        assertNoSubscriptionEvent()
        assertSubscriptionChannelClosed()
    }

    @Test
    fun shouldIgnoreUnsubscribeOnPendingSubscriptions() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        subscribe()
        unsubscribe(42)
        assertNoSubscriptionEvent()
    }

    @Test
    fun shouldIgnoreUnsubscribeWhenNotSubscribed() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        unsubscribe(42)
    }

    @Test
    fun shouldAbortWhenPublishIsCalledBeforeJoinAndAfterAborted() = runTest {
        failOnSessionAbort(false)
        publish(
            (PUBLISH_NO_ARG.message as Message.Publish).topic,
            PUBLISH_NO_ARG.message.arguments,
            PUBLISH_NO_ARG.message.argumentsKw
        )
        verifySessionAborted()
        verifyWebSocketWasClosed()

        clearMocks(sessionListener)
        publish(
            PUBLISH_NO_ARG.message.topic,
            PUBLISH_NO_ARG.message.arguments,
            PUBLISH_NO_ARG.message.argumentsKw
        )
        verifySessionAborted()
    }

    @Test
    fun shouldPublishNoArg() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(PUBLISH_NO_ARG)
        publish(
            (PUBLISH_NO_ARG.message as Message.Publish).topic,
            PUBLISH_NO_ARG.message.arguments,
            PUBLISH_NO_ARG.message.argumentsKw
        )
        verifyMessagesSent(HELLO, PUBLISH_NO_ARG)
        receiveMessages(PUBLISHED)
        assertPublicationSucceeded(PUBLISHED)
    }

    @Test
    fun shouldPublishArrayArg() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(PUBLISH_ONLY_ARRAY_ARG)
        publish(
            (PUBLISH_ONLY_ARRAY_ARG.message as Message.Publish).topic,
            PUBLISH_ONLY_ARRAY_ARG.message.arguments,
            PUBLISH_ONLY_ARRAY_ARG.message.argumentsKw
        )
        verifyMessagesSent(HELLO, PUBLISH_ONLY_ARRAY_ARG)
        receiveMessages(PUBLISHED)
        assertPublicationSucceeded(PUBLISHED)
    }

    @Test
    fun shouldPublishAllArgs() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(PUBLISH_FULL_ARGS)
        publish(
            (PUBLISH_FULL_ARGS.message as Message.Publish).topic,
            PUBLISH_FULL_ARGS.message.arguments,
            PUBLISH_FULL_ARGS.message.argumentsKw
        )
        verifyMessagesSent(HELLO, PUBLISH_FULL_ARGS)
        receiveMessages(PUBLISHED)
        assertPublicationSucceeded(PUBLISHED)
    }

    @Test
    fun shouldFailPublication() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(PUBLISH_NO_ARG)
        publish(
            (PUBLISH_NO_ARG.message as Message.Publish).topic,
            PUBLISH_NO_ARG.message.arguments,
            PUBLISH_NO_ARG.message.argumentsKw
        )
        receiveMessages(PUBLISH_ERROR)
        assertPublicationFailed()
    }

    @Test
    fun shouldFailPublicationOnAbort() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(PUBLISH_NO_ARG)
        publish(
            (PUBLISH_NO_ARG.message as Message.Publish).topic,
            PUBLISH_NO_ARG.message.arguments,
            PUBLISH_NO_ARG.message.argumentsKw
        )
        failOnSessionAbort(false)
        receiveMessages(HELLO)
        assertPublicationFailed()
    }

    @Test
    fun shouldFailPublicationOnLeavingOrShuttingDown() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(PUBLISH_NO_ARG)
        publish(
            (PUBLISH_NO_ARG.message as Message.Publish).topic,
            PUBLISH_NO_ARG.message.arguments,
            PUBLISH_NO_ARG.message.argumentsKw
        )
        leaveRealm()
        receiveGoodbyeAndOut()
        assertPublicationFailed()

        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(PUBLISH_NO_ARG)
        publish(
            PUBLISH_NO_ARG.message.topic,
            PUBLISH_NO_ARG.message.arguments,
            PUBLISH_NO_ARG.message.argumentsKw
        )
        shutdownSession()
        receiveGoodbyeAndOut()
        assertPublicationFailed()
    }


    @Test
    fun shouldNotAcknowledgePublication() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()
        mockNextRequestIdWithIdFrom(PUBLISH_NO_ARG_NO_ACKNOWLEDGE)
        publish(
            (PUBLISH_NO_ARG_NO_ACKNOWLEDGE.message as Message.Publish).topic,
            PUBLISH_NO_ARG_NO_ACKNOWLEDGE.message.arguments,
            PUBLISH_NO_ARG_NO_ACKNOWLEDGE.message.argumentsKw,
            acknowledge = false
        )
        assertNoPublicationEvent()
        assertPublicationChannelClosed()
    }

    @Test
    fun shouldUseSeparatePublicationChannels() = runTest {
        failOnSessionAbort()
        joinRealm()
        receiveWelcome()

        mockNextRequestIdWithIdFrom(PUBLISH_NO_ARG)
        val channel1 = session.publish(
            (PUBLISH_NO_ARG.message as Message.Publish).topic,
            PUBLISH_NO_ARG.message.arguments,
            PUBLISH_NO_ARG.message.argumentsKw,
            acknowledge = true
        )
        mockNextRequestIdWithIdFrom(PUBLISH_NO_ARG_NO_ACKNOWLEDGE)
        val channel2 = session.publish(
            (PUBLISH_NO_ARG_NO_ACKNOWLEDGE.message as Message.Publish).topic,
            PUBLISH_NO_ARG_NO_ACKNOWLEDGE.message.arguments,
            PUBLISH_NO_ARG_NO_ACKNOWLEDGE.message.argumentsKw,
            acknowledge = false
        )
        mockNextRequestIdWithIdFrom(PUBLISH_NO_ARG2)
        val channel3 = session.publish(
            (PUBLISH_NO_ARG2.message as Message.Publish).topic,
            PUBLISH_NO_ARG2.message.arguments,
            PUBLISH_NO_ARG2.message.argumentsKw,
            acknowledge = true
        )

        clearMocks(mockWebSocketDelegate)

        receiveMessages(PUBLISHED2)
        receiveMessages(PUBLISHED)

        val publicationId1 = getEvent<PublicationEvent.PublicationSucceeded>(channel1).publicationId
        val publicationId2 = getEvent<PublicationEvent.PublicationSucceeded>(channel3).publicationId

        assertEquals((PUBLISHED.message as Message.Published).publicationId, publicationId1)
        assertEquals((PUBLISHED2.message as Message.Published).publicationId, publicationId2)

        assertChannelClosed(channel1)
        assertChannelClosed(channel2)
        assertChannelClosed(channel3)
    }

    @Test
    fun shouldUseSeparateSubscriptionChannels() = runTest {
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
        val subscriptionId = getSubscriptionEvent<SubscriptionEstablished>().subscriptionId
        assertEquals(subscriptionId, (SUBSCRIBED.message as Message.Subscribed).subscriptionId)
        return subscriptionId
    }

    private suspend inline fun <reified T> getSubscriptionEvent() = getEvent<T>(subscriptionEventChannel)

    private suspend inline fun <reified T> getPublicationEvent() = getEvent<T>(publicationEventChannel)

    private suspend fun subscribe(topic: String = (SUBSCRIBE.message as Message.Subscribe).topic) {
        subscriptionEventChannel = session.subscribe(topic)
    }

    private suspend fun publish(topic: String, arguments: JsonArray?, argumentsKw: JsonObject?, acknowledge: Boolean = true) {
        publicationEventChannel = session.publish(topic, arguments, argumentsKw, acknowledge)
    }

    private suspend fun unsubscribe(subscriptionId: SubscriptionId) {
        session.unsubscribe(subscriptionId)
    }

    private fun assertNoSubscriptionEvent() = assertNoEvent(subscriptionEventChannel)

    private fun assertNoPublicationEvent() = assertNoEvent(publicationEventChannel)

    private suspend fun assertSubscriptionFailed() {
        getSubscriptionEvent<SubscriptionFailed>()
        assertSubscriptionChannelClosed()
    }

    private suspend fun assertUnsubscriptionFailed() {
        getSubscriptionEvent<UnsubscriptionFailed>()
        assertSubscriptionChannelClosed()
    }

    private suspend fun assertPublicationSucceeded(message: ExampleMessage) {
        val publicationId = getPublicationEvent<PublicationEvent.PublicationSucceeded>().publicationId
        assertEquals((message.message as Message.Published).publicationId, publicationId)
        assertPublicationChannelClosed()
    }

    private suspend fun assertPublicationFailed() {
        getPublicationEvent<PublicationEvent.PublicationFailed>()
        assertPublicationChannelClosed()
    }

    private suspend fun assertSubscriptionChannelClosed() = assertChannelClosed(subscriptionEventChannel)

    private suspend fun assertPublicationChannelClosed() = assertChannelClosed(publicationEventChannel)
}