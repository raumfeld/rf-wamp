package com.raumfeld.wamp.session

import com.raumfeld.wamp.IdGenerator
import com.raumfeld.wamp.protocol.*
import com.raumfeld.wamp.pubsub.PublicationEvent
import com.raumfeld.wamp.pubsub.SubscriptionEvent
import com.raumfeld.wamp.rpc.CalleeEvent
import com.raumfeld.wamp.rpc.CallerEvent
import com.raumfeld.wamp.session.WampSession.State.*
import com.raumfeld.wamp.session.WampSession.Trigger.*
import com.raumfeld.wamp.websocket.WebSocketCloseCodes
import com.raumfeld.wamp.websocket.WebSocketDelegate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.json

class WampSession(
    private val webSocketDelegate: WebSocketDelegate,
    private val sessionListener: WampSessionListener? = null,
    private val idGenerator: IdGenerator = IdGenerator()
) {

    interface WampSessionListener {
        fun onRealmJoined(realm: String)
        fun onRealmLeft(realm: String, fromRouter: Boolean)
        fun onSessionShutdown()
        fun onSessionAborted(reason: String, throwable: Throwable?)
    }

    private enum class State {
        INITIAL,
        JOINING,
        JOINED,
        ABORTED,
        LEAVING,
        SHUTTING_DOWN,
        SHUT_DOWN
    }

    private sealed class Trigger {
        data class MessageReceived(val message: Message) : Trigger()
        object BinaryMessageReceived : Trigger()
        data class Join(val realm: String) : Trigger()
        data class Subscribe(val topic: String, val eventChannel: SendChannel<SubscriptionEvent>) : Trigger()
        data class Unsubscribe(val subscriptionId: SubscriptionId) : Trigger()
        data class Register(val procedureId: ProcedureId, val eventChannel: SendChannel<CalleeEvent>) : Trigger()
        data class Unregister(val registrationId: RegistrationId) : Trigger()
        data class Call(
            val procedureId: ProcedureId,
            val arguments: JsonArray?,
            val argumentsKw: JsonObject?,
            val eventChannel: SendChannel<CallerEvent>
        ) : Trigger()

        data class Yield(
            val requestId: RequestId,
            val arguments: JsonArray?,
            val argumentsKw: JsonObject?
        ) : Trigger()

        data class Error(val errorType: MessageType, val requestId: RequestId, val wampErrorUri: String) : Trigger()

        data class Publish(
            val topic: String,
            val arguments: JsonArray?,
            val argumentsKw: JsonObject?,
            val acknowledge: Boolean,
            val eventChannel: SendChannel<PublicationEvent>
        ) : Trigger()

        object Leave : Trigger()
        object Shutdown : Trigger()
        class WebSocketClosed(val code: Int, val reason: String) : Trigger()
        class WebSocketFailed(val throwable: Throwable) : Trigger()
    }

    private val mutex = Mutex()
    // INTERNAL STATE MUST ONLY BE MUTATED FROM INSIDE OUR CONTEXT
    private var realm: String? = null
    private var state = INITIAL
    private val pendingSubscriptions = mutableMapOf<RequestId, SendChannel<SubscriptionEvent>>()
    private val pendingUnsubscriptions = mutableMapOf<RequestId, Pair<SubscriptionId, SendChannel<SubscriptionEvent>>>()
    private val subscriptions = mutableMapOf<SubscriptionId, SendChannel<SubscriptionEvent>>()
    private val pendingRegistrations = mutableMapOf<RequestId, SendChannel<CalleeEvent>>()
    private val pendingUnregistrations = mutableMapOf<RequestId, Pair<RegistrationId, SendChannel<CalleeEvent>>>()
    private val registrations = mutableMapOf<RegistrationId, SendChannel<CalleeEvent>>()
    private val pendingCalls = mutableMapOf<RequestId, SendChannel<CallerEvent>>()
    private val pendingPublications = mutableMapOf<RequestId, SendChannel<PublicationEvent>>()

    suspend fun join(realm: String): Unit = dispatch(Join(realm))

    suspend fun leave(): Unit = dispatch(Leave)

    suspend fun shutdown(): Unit = dispatch(Shutdown)

    suspend fun subscribe(topic: String): ReceiveChannel<SubscriptionEvent> =
        Channel<SubscriptionEvent>().also {
            dispatch(Subscribe(topic, it))
        }

    suspend fun unsubscribe(subscriptionId: SubscriptionId) = dispatch(Unsubscribe(subscriptionId))

    suspend fun publish(
        topic: String,
        arguments: JsonArray? = null,
        argumentsKw: JsonObject? = null,
        acknowledge: Boolean = false
    ): ReceiveChannel<PublicationEvent> = Channel<PublicationEvent>().also {
        dispatch(Publish(topic, arguments, argumentsKw, acknowledge, it))
    }

    suspend fun register(procedureId: ProcedureId): ReceiveChannel<CalleeEvent> =
        Channel<CalleeEvent>().also {
            dispatch(Register(procedureId, it))
        }

    suspend fun unregister(registrationId: RegistrationId): Unit = dispatch(Unregister(registrationId))

    suspend fun call(
        procedureId: ProcedureId,
        arguments: JsonArray = emptyJsonArray(),
        argumentsKw: JsonObject = emptyJsonObject()
    ): ReceiveChannel<CallerEvent> =
        Channel<CallerEvent>().also {
            dispatch(Call(procedureId, arguments, argumentsKw, it))
        }

    private suspend fun evaluate(trigger: Trigger) {
        try {
            if (!ensureNonBinaryMessage(trigger)) {
                return
            }

            when (state) {
                SHUTTING_DOWN -> evaluateLeavingOrShuttingDown(trigger, mustShutdown = true)
                INITIAL       -> evaluateInitial(trigger)
                JOINING       -> evaluateJoining(trigger)
                JOINED        -> evaluateJoined(trigger)
                ABORTED       -> evaluatedAborted(trigger)
                LEAVING       -> evaluateLeavingOrShuttingDown(trigger, mustShutdown = false)
                SHUT_DOWN     -> evaluateShutdown(trigger)
            }

        } finally {
            releaseId(trigger)
        }
    }

    private suspend fun ensureNonBinaryMessage(trigger: Trigger) = if (trigger is BinaryMessageReceived) {
        onProtocolViolated("Received binary message. This is not supported by this client.")
        false
    } else true

    // we need these IDs only until we've received them back from the broker for correlation
    private fun releaseId(trigger: Trigger) {
        if (trigger is MessageReceived && trigger.message is RequestMessage) {
            idGenerator.releaseId(trigger.message.requestId)
        }
    }

    private suspend fun evaluateShutdown(trigger: Trigger) =
        when (trigger) {
            is MessageReceived -> onProtocolViolated("Session is shut down. We cannot process messages.")
            is WebSocketClosed -> Unit // we caused this ourselves
            is WebSocketFailed -> onWebSocketFailed(trigger.throwable)
            else               -> failTransition(trigger)
        }

    private suspend fun evaluateLeavingOrShuttingDown(trigger: Trigger, mustShutdown: Boolean) = when (trigger) {
        is MessageReceived -> {
            when (trigger.message) {
                is Message.Goodbye -> onGoodbyeAcknowledgedReceived(mustShutdown)
                else               -> Unit // according to the spec we are to ignore other messages
            }
        }
        is WebSocketClosed -> onWebSocketClosedPrematurely(trigger.code, trigger.reason)
        is WebSocketFailed -> doOnWebSocketFailed(trigger.throwable)
        else               -> failTransition(trigger)
    }

    private suspend fun evaluatedAborted(trigger: Trigger) =
        when (trigger) {
            is WebSocketClosed, is WebSocketFailed -> Unit // we don't even care anymore!
            is MessageReceived                     -> onProtocolViolated("Session is aborted. We cannot process messages.")
            else                                   -> failTransition(trigger)
        }

    private suspend fun evaluateJoined(trigger: Trigger) {
        when (trigger) {
            is MessageReceived -> {
                when (val message = trigger.message) {
                    is Message.Unsubscribed -> onUnsubscribedReceived(message.requestId)
                    is Message.Subscribed   -> onSubscribedReceived(message.requestId, message.subscriptionId)
                    is Message.Event        -> onEventReceived(
                        message.subscriptionId,
                        message.arguments,
                        message.argumentsKw
                    )
                    is Message.Published    -> onPublishedReceived(message.requestId, message.publicationId)
                    is Message.Registered   -> onRegisteredReceived(message.requestId, message.registrationId)
                    is Message.Unregistered -> onUnregisteredReceived(message.requestId)
                    is Message.Invocation   -> onInvocationReceived(
                        message.registrationId,
                        message.requestId,
                        message.arguments,
                        message.argumentsKw
                    )
                    is Message.Result       -> onResultReceived(
                        message.requestId,
                        message.arguments,
                        message.argumentsKw
                    )
                    is Message.Error        -> onErrorReceived(
                        message.originalType,
                        message.requestId,
                        message.wampErrorUri
                    )
                    is Message.Goodbye      ->
                        if (message.reason != WampClose.GOODBYE_AND_OUT.content)
                            onGoodbyeReceived(mustShutdown = message.reason == WampClose.SYSTEM_SHUTDOWN.content)
                        else
                            onProtocolViolated()
                    is Message.Abort        -> onAbort(reason = message.reason, throwable = null)
                    else                    -> onProtocolViolated()
                }
            }
            is Subscribe       -> setupSubscription(trigger.topic, trigger.eventChannel)
            is Unsubscribe     -> doUnsubscribe(trigger.subscriptionId)
            is Publish         -> doPublish(
                trigger.topic,
                trigger.arguments,
                trigger.argumentsKw,
                trigger.acknowledge,
                trigger.eventChannel
            )
            is Register        -> doRegister(trigger.procedureId, trigger.eventChannel)
            is Unregister      -> doUnregister(trigger.registrationId)
            is Call            -> doCall(
                trigger.procedureId,
                trigger.arguments,
                trigger.argumentsKw,
                trigger.eventChannel
            )
            is Yield           -> doYield(trigger.requestId, trigger.arguments, trigger.argumentsKw)
            is Leave           -> sendGoodbye(mustShutdown = false)
            is Shutdown        -> sendGoodbye(mustShutdown = true)
            is Error           -> sendError(trigger.errorType, trigger.requestId, trigger.wampErrorUri)
            is WebSocketClosed -> onWebSocketClosedPrematurely(trigger.code, trigger.reason)
            is WebSocketFailed -> onWebSocketFailed(trigger.throwable)
            else               -> failTransition(trigger)
        }
    }

    private suspend fun doYield(requestId: RequestId, arguments: JsonArray?, argumentsKw: JsonObject?) =
        send(Message.Yield(requestId, arguments, argumentsKw))

    private suspend fun doUnregister(registrationId: RegistrationId) {
        val channel = registrations.remove(registrationId)
        if (channel != null) {
            val requestId = idGenerator.newId()
            pendingUnregistrations[requestId] = registrationId to channel
            send(Message.Unregister(requestId, registrationId))
        }
    }

    private suspend fun doRegister(procedureId: ProcedureId, eventChannel: SendChannel<CalleeEvent>) {
        val requestId = idGenerator.newId()
        pendingRegistrations[requestId] = eventChannel
        send(Message.Register(requestId, procedureId))
    }

    private suspend fun doCall(
        procedureId: ProcedureId,
        arguments: JsonArray?,
        argumentsKw: JsonObject?,
        eventChannel: SendChannel<CallerEvent>
    ) {
        val requestId = idGenerator.newId()
        pendingCalls[requestId] = eventChannel
        send(Message.Call(requestId, procedureId, arguments, argumentsKw))
    }

    private suspend fun sendError(errorType: MessageType, requestId: RequestId, wampErrorUri: String) =
        send(Message.Error(requestId, errorType, wampErrorUri))

    private suspend fun onResultReceived(requestId: RequestId, arguments: JsonArray?, argumentsKw: JsonObject?) {
        val eventChannel = pendingCalls.remove(requestId)
        if (eventChannel == null) {
            onProtocolViolated("Received RESULT that we have no pending call for. RequestId = $requestId")
            return
        }
        eventChannel.sendAsync(CallerEvent.Result(arguments, argumentsKw))
    }

    private suspend fun onInvocationReceived(
        registrationId: RegistrationId,
        requestId: RequestId,
        arguments: JsonArray?,
        argumentsKw: JsonObject?
    ) {
        val eventChannel = registrations[registrationId]
        if (eventChannel == null) {
            val pendingUnregistrationIds = pendingUnregistrations.values.map { it.first }
            if (registrationId !in pendingUnregistrationIds)
                onProtocolViolated("Received INVOCATION that we have no registration or pending unregistration for. RequestId = $requestId RegistrationId = $registrationId")
            return
        }
        eventChannel.sendAsync(CalleeEvent.Invocation(arguments, argumentsKw) { yieldResult(requestId, it) })
    }

    private suspend fun yieldResult(requestId: RequestId, result: CallerEvent) =
        dispatch(
            when (result) {
                is CallerEvent.CallFailed -> Error(Message.Invocation.type, requestId, result.errorUri)
                is CallerEvent.Result     -> Yield(requestId, result.arguments, result.argumentsKw)
            }
        )

    private suspend fun onRegisteredReceived(requestId: RequestId, registrationId: RegistrationId) {
        val eventChannel = pendingRegistrations.remove(requestId)
        if (eventChannel == null) {
            onProtocolViolated("Received REGISTERED that we have no pending registration for. RequestId = $requestId")
            return
        }
        registrations[registrationId] = eventChannel
        eventChannel.sendAsync(CalleeEvent.ProcedureRegistered(registrationId))
    }

    private suspend fun doPublish(
        topic: String,
        arguments: JsonArray?,
        argumentsKw: JsonObject?,
        acknowledge: Boolean,
        eventChannel: SendChannel<PublicationEvent>
    ) {
        val requestId = idGenerator.newId()
        if (acknowledge)
            pendingPublications[requestId] = eventChannel

        send(
            Message.Publish(
                requestId,
                topic = topic,
                arguments = arguments,
                argumentsKw = argumentsKw,
                options = if (acknowledge) json { "acknowledge" to true } else emptyJsonObject()))

        if (!acknowledge)
            eventChannel.close()
    }

    private suspend fun doUnsubscribe(subscriptionId: SubscriptionId) {
        val channel = subscriptions.remove(subscriptionId)
        // don't send a request if we don't even have a local subscriber running
        if (channel != null) {
            val requestId = idGenerator.newId()
            pendingUnsubscriptions[requestId] = subscriptionId to channel
            send(Message.Unsubscribe(requestId, subscriptionId))
        }
    }

    private suspend fun setupSubscription(topic: String, eventChannel: SendChannel<SubscriptionEvent>) {
        val requestId = idGenerator.newId()
        pendingSubscriptions[requestId] = eventChannel
        sendSubscribe(requestId, topic)
    }

    private suspend fun sendSubscribe(requestId: RequestId, topic: String) {
        val message = Message.Subscribe(requestId, topic)
        send(message)
    }

    private suspend fun onErrorReceived(
        errorType: MessageType,
        requestId: RequestId,
        wampErrorUri: String
    ) {
        when (errorType) {
            Message.Unsubscribe.type -> failUnsubscription(requestId, wampErrorUri)
            Message.Unregister.type  -> failUnregistration(requestId, wampErrorUri)
            Message.Publish.type     -> failPublication(requestId, wampErrorUri)
            Message.Subscribe.type   -> failSubscription(requestId, wampErrorUri)
            Message.Register.type    -> failRegistration(requestId, wampErrorUri)
            Message.Call.type        -> failCall(requestId, wampErrorUri)
            else                     -> onProtocolViolated("Received invalid REQUEST. Type: $errorType")
        }
    }

    private suspend fun failPublication(requestId: RequestId, wampErrorUri: String) {
        val eventChannel = pendingPublications.remove(requestId)
        if (eventChannel == null) {
            onProtocolViolated("Received PUBLISH ERROR that we have no pending publication for. RequestId = $requestId ERROR uri = $wampErrorUri")
            return
        }
        failPublication(wampErrorUri, eventChannel)
    }

    private suspend fun failCall(requestId: RequestId, wampErrorUri: String) {
        val eventChannel = pendingCalls.remove(requestId)
        if (eventChannel == null) {
            onProtocolViolated("Received CALL ERROR that we have no pending call for. RequestId = $requestId ERROR uri = $wampErrorUri")
            return
        }
        failCall(wampErrorUri, eventChannel)
    }

    private suspend fun failRegistration(requestId: RequestId, wampErrorUri: String) {
        val eventChannel = pendingRegistrations.remove(requestId)
        if (eventChannel == null) {
            onProtocolViolated("Received REGISTER ERROR that we have no pending registration for. RequestId = $requestId ERROR uri = $wampErrorUri")
            return
        }
        failRegistration(wampErrorUri, eventChannel)
    }

    private suspend fun failUnregistration(requestId: RequestId, wampErrorUri: String) {
        val eventChannel = pendingUnregistrations.remove(requestId)?.second
        if (eventChannel == null) {
            onProtocolViolated("Received UNREGISTER ERROR that we have no pending unregistration for. RequestId = $requestId ERROR uri = $wampErrorUri")
            return
        }
        failUnregistration(wampErrorUri, eventChannel)
    }

    private suspend fun failUnsubscription(requestId: RequestId, wampErrorUri: String) {
        val eventChannel = pendingUnsubscriptions.remove(requestId)?.second
        if (eventChannel == null) {
            onProtocolViolated("Received UNSUBSCRIBE ERROR that we have no pending unsubscription for. RequestId = $requestId ERROR uri = $wampErrorUri")
            return
        }
        failUnsubscription(wampErrorUri, eventChannel)
    }

    private suspend fun failSubscription(requestId: RequestId, wampErrorUri: String) {
        val eventChannel = pendingSubscriptions.remove(requestId)
        if (eventChannel == null) {
            onProtocolViolated("Received SUBSCRIBE ERROR that we have no pending subscription for. RequestId = $requestId ERROR uri = $wampErrorUri")
            return
        }
        failSubscription(wampErrorUri, eventChannel)
    }

    private fun failUnregistration(errorUri: String, channel: SendChannel<CalleeEvent>) =
        channel.sendAsync(CalleeEvent.UnregistrationFailed(errorUri), close = true)

    private fun failUnsubscription(errorUri: String, channel: SendChannel<SubscriptionEvent>) =
        channel.sendAsync(SubscriptionEvent.UnsubscriptionFailed(errorUri), close = true)

    private fun failSubscription(errorUri: String, channel: SendChannel<SubscriptionEvent>) =
        channel.sendAsync(SubscriptionEvent.SubscriptionFailed(errorUri), close = true)

    private fun failCall(errorUri: String, channel: SendChannel<CallerEvent>) =
        channel.sendAsync(CallerEvent.CallFailed(errorUri), close = true)

    private fun failPublication(errorUri: String, channel: SendChannel<PublicationEvent>) =
        channel.sendAsync(PublicationEvent.PublicationFailed(errorUri), close = true)

    private fun failRegistration(errorUri: String, channel: SendChannel<CalleeEvent>) =
        channel.sendAsync(CalleeEvent.RegistrationFailed(errorUri), close = true)

    private suspend fun onPublishedReceived(requestId: RequestId, publicationId: PublicationId) {
        val eventChannel = pendingPublications.remove(requestId)
        if (eventChannel == null) {
            onProtocolViolated("Received PUBLISHED that we have no pending publication for. RequestId = $requestId publicationId = $publicationId")
            return
        }
        eventChannel.sendAsync(PublicationEvent.PublicationSucceeded(publicationId), close = true)
    }

    private suspend fun onEventReceived(
        subscriptionId: SubscriptionId,
        arguments: JsonArray?,
        argumentsKw: JsonObject?
    ) {
        val eventChannel = subscriptions[subscriptionId]
        if (eventChannel == null) {
            val pendingUnsubscriptionIds = pendingUnsubscriptions.values.map { it.first }
            if (subscriptionId !in pendingUnsubscriptionIds)
                onProtocolViolated("Received EVENT that we have no subscription or pending unsubscription for. SubscriptionId = $subscriptionId")
            return
        }
        eventChannel.sendAsync(SubscriptionEvent.Payload(arguments, argumentsKw))
    }

    private suspend fun onSubscribedReceived(requestId: RequestId, subscriptionId: SubscriptionId) {
        val eventChannel = pendingSubscriptions.remove(requestId)
        if (eventChannel == null) {
            onProtocolViolated("Received SUBSCRIBED that we have no pending subscription for. RequestId = $requestId subscriptionId = $subscriptionId")
            return
        }
        subscriptions[subscriptionId] = eventChannel
        eventChannel.sendAsync(SubscriptionEvent.SubscriptionEstablished(subscriptionId))
    }

    private suspend fun onUnsubscribedReceived(requestId: RequestId) {
        val channel = pendingUnsubscriptions.remove(requestId)?.second
        if (channel == null) {
            onProtocolViolated("Received UNSUBSCRIBED that we have no pending unsubscription for. RequestId = $requestId")
            return
        }

        channel.sendAsync(SubscriptionEvent.SubscriptionClosed, close = true)
    }

    private suspend fun onUnregisteredReceived(requestId: RequestId) {
        val channel = pendingUnregistrations.remove(requestId)?.second
        if (channel == null) {
            onProtocolViolated("Received UNREGISTERED that we have no pending unregistration for. RequestId = $requestId")
            return
        }

        channel.sendAsync(CalleeEvent.ProcedureUnregistered, close = true)
    }

    private suspend fun evaluateJoining(trigger: Trigger) = when (trigger) {
        is Leave, is Shutdown -> sendAbort(WampClose.SYSTEM_SHUTDOWN)
        is MessageReceived    -> {
            when (trigger.message) {
                is Message.Welcome -> onWelcomeReceived()
                is Message.Abort   -> onAbort(trigger.message.reason)
                else               -> onProtocolViolated("Illegal message received. Expected 'Welcome' or 'Abort'.")
            }
        }
        is WebSocketClosed    -> onWebSocketClosedPrematurely(trigger.code, trigger.reason)
        is WebSocketFailed    -> onWebSocketFailed(trigger.throwable)
        else                  -> failTransition(trigger)
    }

    private fun onWelcomeReceived() {
        state = JOINED
        sessionListener?.onRealmJoined(realm!!)
    }

    private suspend fun onGoodbyeAcknowledgedReceived(mustShutdown: Boolean) {
        state = if (!mustShutdown) INITIAL else SHUT_DOWN
        notifyRealmLeft(fromRouter = false)
        if (mustShutdown) {
            sessionListener?.onSessionShutdown()
            closeWebSocket()
        }
    }

    private fun notifyRealmLeft(fromRouter: Boolean) {
        sessionListener?.onRealmLeft(realm!!, fromRouter)
        clearChannels(WampClose.CLOSE_REALM.content)
    }

    private suspend fun onGoodbyeReceived(mustShutdown: Boolean) {
        state = if (!mustShutdown) INITIAL else SHUT_DOWN
        val message = Message.Goodbye(reason = WampClose.GOODBYE_AND_OUT.content)
        send(message)
        notifyRealmLeft(fromRouter = true)
        if (mustShutdown) {
            sessionListener?.onSessionShutdown()
            closeWebSocket()
        }
    }

    private suspend fun onAbort(reason: String, throwable: Throwable? = null) {
        state = ABORTED
        notifySessionAbort(reason, throwable)
        webSocketDelegate.close(WebSocketCloseCodes.GOING_AWAY, "ABORT")
    }

    private suspend fun evaluateInitial(trigger: Trigger) = when (trigger) {
        is MessageReceived -> onProtocolViolated("Not ready to receive messages yet. Session has not been established.")
        is Join            -> sendHello(trigger.realm)
        is WebSocketClosed -> onWebSocketClosedPrematurely(trigger.code, trigger.reason)
        is WebSocketFailed -> onWebSocketFailed(trigger.throwable)
        else               -> failTransition(trigger)
    }

    private suspend fun doOnWebSocketFailed(throwable: Throwable) = onAbort("WebSocket failed", throwable)

    private suspend fun onWebSocketClosedPrematurely(code: Int, reason: String) =
        onAbort("WebSocket closed prematurely ($code - $reason)")

    private suspend fun failTransition(trigger: Trigger) = onAbort("Invalid state trigger $trigger for state $state")

    private suspend fun sendGoodbye(mustShutdown: Boolean) {
        state = if (!mustShutdown) LEAVING else SHUTTING_DOWN
        val message =
            Message.Goodbye(reason = if (mustShutdown) WampClose.SYSTEM_SHUTDOWN.content else WampClose.CLOSE_REALM.content)
        send(message)
    }

    private suspend fun onProtocolViolated(message: String = "Received unexpected message.") =
        sendAbort(WampClose.PROTOCOL_VIOLATION, message)

    private suspend fun sendAbort(reason: WampClose, message: String? = null) {
        state = ABORTED
        notifySessionAbort(reason.content + (message?.let { " : $it" } ?: ""), null)
        val details = message?.let {
            json {
                "message" to it
            }
        } ?: emptyJsonObject()
        val abortMessage = Message.Abort(reason = reason.content, details = details)
        send(abortMessage)
        webSocketDelegate.close(reason.webSocketCloseCode, "Session aborted")
    }

    private fun notifySessionAbort(reason: String, throwable: Throwable?) {
        sessionListener?.onSessionAborted(reason, throwable)
        clearChannels(reason)
    }

    private fun clearChannels(reason: String) {
        pendingRegistrations.values.forEach { failRegistration(reason, it) }
        pendingRegistrations.clear()
        registrations.values.forEach { failRegistration(reason, it) }
        registrations.clear()
        pendingSubscriptions.values.forEach { failSubscription(reason, it) }
        pendingSubscriptions.clear()
        pendingUnsubscriptions.values.map { it.second }.forEach { failUnsubscription(reason, it) }
        pendingUnsubscriptions.clear()
        subscriptions.values.forEach { failSubscription(reason, it) }
        subscriptions.clear()
        pendingCalls.values.forEach { failCall(reason, it) }
        pendingCalls.clear()
        pendingPublications.values.forEach { failPublication(reason, it) }
        pendingPublications.clear()
    }

    private suspend fun closeWebSocket() = webSocketDelegate.close(WebSocketCloseCodes.GOING_AWAY, "Session closed")

    private suspend fun sendHello(realm: String) {
        state = JOINING
        this.realm = realm
        val message = Message.Hello(
            realm, json {
                "roles" to json {
                    "publisher" to emptyJsonObject()
                    "subscriber" to emptyJsonObject()
                    "caller" to emptyJsonObject()
                    "callee" to emptyJsonObject()
                }
            }
        )
        send(message)
    }

    private suspend fun send(message: Message) {
        webSocketDelegate.send(message.toJson())
    }

    private fun <T> SendChannel<T>.sendAsync(value: T, close: Boolean = false) {
        // launch a coroutine for each new value so we're not suspended by consumers not reading from a channel
        // Note: this also means that if coroutines can start piling up, but between dropping messages and piling them up, the latter
        // is probably preferable
        GlobalScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                // send will fail when someone closes the channel, we swallow that
                send(value)
                if (close) close()
            }
        }
    }

    private suspend fun dispatch(trigger: Trigger) = mutex.withLock {
        evaluate(trigger)
    }

    internal suspend fun onMessage(messageJson: String) {
        val message = fromJsonToMessage(messageJson)
        dispatch(MessageReceived(message))
    }

    internal suspend fun onBinaryMessageReceived() {
        dispatch(BinaryMessageReceived)
    }

    internal suspend fun onWebSocketClosed(code: Int, reason: String) {
        dispatch(WebSocketClosed(code, reason))
    }

    internal suspend fun onWebSocketFailed(throwable: Throwable) {
        dispatch(WebSocketFailed(throwable))
    }
}