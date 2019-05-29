package com.raumfeld.wamp.session

import com.raumfeld.wamp.WampClient
import com.raumfeld.wamp.getEvent
import com.raumfeld.wamp.protocol.emptyJsonArray
import com.raumfeld.wamp.protocol.emptyJsonObject
import com.raumfeld.wamp.pubsub.PublicationEvent.PublicationSucceeded
import com.raumfeld.wamp.pubsub.SubscriptionEvent
import com.raumfeld.wamp.pubsub.SubscriptionEvent.Payload
import com.raumfeld.wamp.pubsub.SubscriptionEvent.SubscriptionEstablished
import com.raumfeld.wamp.rpc.CalleeEvent.*
import com.raumfeld.wamp.rpc.CallerEvent
import com.raumfeld.wamp.rpc.CallerEvent.CallFailed
import com.raumfeld.wamp.runTest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.json
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

expect fun getLocalWampClient(): WampClient

internal class ConcurrencyTests {

    companion object {
        private const val LOCAL_WAMP_ROUTER = "ws://localhost:8080/ws"
        private const val LOCAL_WAMP_REALM = "realm1"
        private const val NUMBER_OF_CLIENTS = 5
        private const val NUMBER_OF_RUNS = 5
    }

    private suspend fun createWampSession(onJoined: CompletableDeferred<Unit>): WampSession? {
        val client = getLocalWampClient()
        val sessionDeferred = CompletableDeferred<Result<WampSession>>()
        client.createSession(LOCAL_WAMP_ROUTER, FailOnAbortSessionListener(onJoined)) {
            sessionDeferred.complete(it)
        }
        return sessionDeferred.await().getOrNull()
    }

    @Test
    fun test() = runTest {
        repeat(NUMBER_OF_RUNS) {
            coroutineScope {
                val subscriptionBarrier = Barrier(NUMBER_OF_CLIENTS)
                val registrationBarrier = Barrier(NUMBER_OF_CLIENTS)
                repeat(NUMBER_OF_CLIENTS) {
                    launch {
                        client(it, subscriptionBarrier, registrationBarrier)
                    }
                }
            }
        }
    }

    private suspend fun delayRandomly() = delay((0..50L).random())

    private suspend fun client(
        clientIndex: Int,
        subscriptionBarrier: Barrier,
        registrationBarrier: Barrier
    ) {
        val onJoined = CompletableDeferred<Unit>()
        val session = createWampSession(onJoined)
        if (session == null) {
            println("Could not open WAMP session. Ignoring this test.")
            return
        }
        try {
            session.join(LOCAL_WAMP_REALM)
            onJoined.await()

            val subscription1 = session.subscribe("firstTopic")
            val subscription2 = session.subscribe("secondTopic")
            val subscriptionId1 = subscription1.getEvent<SubscriptionEstablished>().subscriptionId
            val subscriptionId2 = subscription2.getEvent<SubscriptionEstablished>().subscriptionId
            // wait until every client is subscribed
            subscriptionBarrier.await()
            delayRandomly()
            val publication1 = session.publish("firstTopic", acknowledge = true)
            publication1.getEvent<PublicationSucceeded>()
            session.publish("firstTopic", jsonArray { +"second" })
            session.publish("secondTopic", emptyJsonArray(), emptyJsonObject())
            delayRandomly()
            session.publish("firstTopic", argumentsKw = json { "third" to 3 })
            delayRandomly()
            // now wait for the events of the other clients events
            repeat(NUMBER_OF_CLIENTS - 1) {
                // -1 because we don't get our own events
                subscription1.getEvent<Payload>()
                subscription1.getEvent<Payload>()
                subscription1.getEvent<Payload>()
                subscription2.getEvent<Payload>()
            }

            delayRandomly()
            session.call("non.existing.procedure").getEvent<CallFailed>()
            val registrationChannel1 = session.register(makeProcedureName(clientIndex))
            val registrationId = registrationChannel1.getEvent<ProcedureRegistered>().registrationId
            registrationBarrier.await()
            val otherClientProcedures = ((0 until NUMBER_OF_CLIENTS) - clientIndex).map { makeProcedureName(it) }
            val arguments = jsonArray { +(clientIndex as Number) }
            val argumentsKw = json { "clientIndex" to clientIndex }
            val resultChannels = otherClientProcedures.map {
                delayRandomly()
                session.call(it, arguments, argumentsKw)
            }
            // respond to incoming calls and send the parameters back as result
            repeat(NUMBER_OF_CLIENTS - 1) {
                val invocation = registrationChannel1.getEvent<Invocation>()
                delayRandomly()
                invocation.returnResult(CallerEvent.Result(invocation.arguments, invocation.argumentsKw))
            }
            resultChannels.forEach {
                val result = it.getEvent<CallerEvent.Result>()
                delayRandomly()
                assertEquals(arguments, result.arguments)
                assertEquals(argumentsKw, result.argumentsKw)
            }
            session.unsubscribe(subscriptionId1)
            session.unsubscribe(subscriptionId2)
            session.unregister(registrationId)

            subscription1.getEvent<SubscriptionEvent.SubscriptionClosed>()
            subscription2.getEvent<SubscriptionEvent.SubscriptionClosed>()
            registrationChannel1.getEvent<ProcedureUnregistered>()
        } finally {
            session.shutdown()
        }
    }

    private fun makeProcedureName(index: Int) = "client-$index.procedure"

    class FailOnAbortSessionListener(val onJoined: CompletableDeferred<Unit>) :
        WampSession.WampSessionListener {
        override fun onRealmJoined(realm: String) {
            onJoined.complete(Unit)
        }

        override fun onRealmLeft(realm: String, fromRouter: Boolean) = Unit

        override fun onSessionShutdown() = Unit

        override fun onSessionAborted(reason: String, throwable: Throwable?) =
            fail("Must not abort. Reason: $reason")
    }

    class Barrier(private val size: Int) {

        private var count = 0
        private val barrierMutex = Mutex(locked = true)
        private val accessMutex = Mutex()

        suspend fun await() {
            accessMutex.withLock {
                count += 1
            }
            if (count == size) {
                barrierMutex.unlock()
            } else {
                barrierMutex.lock()
                barrierMutex.unlock()
            }
        }
    }
}