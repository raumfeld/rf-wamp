package com.raumfeld.wamp.session

import com.raumfeld.wamp.RandomIdGenerator
import com.raumfeld.wamp.protocol.*
import com.raumfeld.wamp.pubsub.SubscriptionEvent
import com.raumfeld.wamp.rpc.CalleeEvent
import com.raumfeld.wamp.rpc.CallerEvent
import com.raumfeld.wamp.session.WampSession.State.*
import com.raumfeld.wamp.session.WampSession.Trigger.*
import com.raumfeld.wamp.websocket.WebSocketDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.json
import kotlin.coroutines.CoroutineContext

expect val wampContextFactory: () -> CoroutineContext

class WampSession(
    private val webSocketDelegate: WebSocketDelegate,
    context: CoroutineContext = wampContextFactory()
) {

    interface WampSessionListener {
        fun onRealmJoined()
        fun onRealmLeft()
        fun onRealmAborted()
    }

    private enum class State {
        INITIAL,
        JOINING,
        JOINED,
        ABORTED,
        CLOSING,
        CLOSED
    }

    private sealed class Trigger {
        data class MessageReceived(val message: Message) : Trigger()
        data class Join(val realm: String) : Trigger()
        data class Subscribe(val topic: String, val eventChannel: SendChannel<SubscriptionEvent>) : Trigger()
        data class Unsubscribe(val subscriptionId: SubscriptionId) : Trigger()
        data class Register(val procedureId: ProcedureId, val eventChannel: SendChannel<CalleeEvent>) : Trigger()
        data class Unregister(val registrationId: RegistrationId) : Trigger()
        data class Call(
            val procedureId: ProcedureId,
            val arguments: JsonArray,
            val argumentsKw: JsonObject,
            val eventChannel: SendChannel<CallerEvent>
        ) : Trigger()

        data class Yield(
            val requestId: RequestId,
            val arguments: JsonArray,
            val argumentsKw: JsonObject
        ) : Trigger()

        data class Error(val errorType: MessageType, val requestId: RequestId, val wampErrorUri: String) : Trigger()

        data class Publish(val topic: String, val arguments: JsonArray, val argumentsKw: JsonObject) : Trigger()
        object Leave : Trigger()
    }

    private var eventChannel = Channel<Trigger>()

    // INTERNAL STATE MUST ONLY BE MUTATED FROM INSIDE OUR CONTEXT
    private var realm: String? = null
    private var sessionListener: WampSessionListener? = null
    private var state = INITIAL
    private val scope = CoroutineScope(context)
    private val pendingSubscriptions = mutableMapOf<RequestId, SendChannel<SubscriptionEvent>>()
    private val subscriptions = mutableMapOf<SubscriptionId, SendChannel<SubscriptionEvent>>()
    private val pendingRegistrations = mutableMapOf<RequestId, SendChannel<CalleeEvent>>()
    private val registrations = mutableMapOf<RegistrationId, SendChannel<CalleeEvent>>()
    private val pendingCalls = mutableMapOf<RequestId, SendChannel<CallerEvent>>()

    init {
        scope.launch {
            startEventProcessor()
        }
    }

    private suspend fun startEventProcessor() {
        eventChannel.consumeEach {
            evaluate(it)
        }
    }

    fun join(realm: String, listener: WampSessionListener? = null) {
        this.sessionListener = listener
        dispatch(Join(realm))
    }

    fun leave() {
        dispatch(Leave)
    }

    fun subscribe(topic: String): ReceiveChannel<SubscriptionEvent> =
        Channel<SubscriptionEvent>().also {
            dispatch(Subscribe(topic, it))
        }

    fun unsubscribe(subscriptionId: SubscriptionId) = dispatch(Unsubscribe(subscriptionId))

    fun publish(topic: String, arguments: JsonArray = emptyJsonArray(), argumentsKw: JsonObject = emptyJsonObject()) =
        dispatch(Publish(topic, arguments, argumentsKw))

    fun register(procedureId: ProcedureId): ReceiveChannel<CalleeEvent> =
        Channel<CalleeEvent>().also {
            dispatch(Register(procedureId, it))
        }

    fun unregister(registrationId: RegistrationId) = dispatch(Unregister(registrationId))

    fun call(
        procedureId: ProcedureId,
        arguments: JsonArray = emptyJsonArray(),
        argumentsKw: JsonObject = emptyJsonObject()
    ): ReceiveChannel<CallerEvent> =
        Channel<CallerEvent>().also {
            dispatch(Call(procedureId, arguments, argumentsKw, it))
        }

    private suspend fun evaluate(trigger: Trigger) {
        when (state) {
            INITIAL -> evaluateInitial(trigger)
            JOINING -> evaluateJoining(trigger)
            JOINED  -> evaluateJoined(trigger)
            ABORTED -> evaluatedAborted(trigger)
            CLOSING -> evaluateClosing(trigger)
            CLOSED  -> evaluateClosed(trigger)
        }

        releaseId(trigger)
    }

    // we need these IDs only until we've received them back from the broker for correlation
    private fun releaseId(trigger: Trigger) {
        if (trigger is MessageReceived && trigger.message is RequestMessage) {
            RandomIdGenerator.releaseId(trigger.message.requestId)
        }
    }

    private fun evaluateClosed(trigger: Trigger) {
        failTransition(trigger)
    }

    private fun evaluateClosing(trigger: Trigger) = when (trigger) {
        is MessageReceived -> {
            when (trigger.message) {
                is Message.Goodbye -> onGoodbyeReceived()
                else               -> onProtocolViolated()
            }
        }
        else               -> failTransition(trigger)
    }

    private fun evaluatedAborted(trigger: Trigger) {
        failTransition(trigger)
    }

    private suspend fun evaluateJoined(trigger: Trigger) {
        when (trigger) {
            is MessageReceived -> {
                when (val message = trigger.message) {
                    is Message.Unsubscribed -> onUnsubscribedReceived()
                    is Message.Subscribed   -> onSubscribedReceived(message.requestId, message.subscriptionId)
                    is Message.Event        -> onEventReceived(
                        message.subscriptionId,
                        message.arguments,
                        message.argumentsKw
                    )
                    is Message.Published    -> onPublishedReceived()
                    is Message.Registered   -> onRegisteredReceived(message.requestId, message.registrationId)
                    is Message.Unregistered -> onUnregisteredReceived()
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
                    else                    -> failTransition(trigger)
                }
            }
            is Subscribe       -> setupSubscription(trigger.topic, trigger.eventChannel)
            is Unsubscribe     -> doUnsubscribe(trigger.subscriptionId)
            is Publish         -> doPublish(trigger.topic, trigger.arguments, trigger.argumentsKw)
            is Register        -> doRegister(trigger.procedureId, trigger.eventChannel)
            is Unregister      -> doUnregister(trigger.registrationId)
            is Call            -> doCall(
                trigger.procedureId,
                trigger.arguments,
                trigger.argumentsKw,
                trigger.eventChannel
            )
            is Yield           -> doYield(trigger.requestId, trigger.arguments, trigger.argumentsKw)
            is Leave           -> sendGoodbye()
            is Error           -> sendError(trigger.errorType, trigger.requestId, trigger.wampErrorUri)
            else               -> failTransition(trigger)
        }
    }

    private fun doYield(requestId: RequestId, arguments: JsonArray, argumentsKw: JsonObject) =
        send(Message.Yield(requestId, arguments, argumentsKw))

    private fun doUnregister(registrationId: RegistrationId) {
        val channel = registrations[registrationId]
        if (channel != null) {
            send(Message.Unregister(RandomIdGenerator.newId(), registrationId))
        }
    }

    private fun doRegister(procedureId: ProcedureId, eventChannel: SendChannel<CalleeEvent>) {
        val requestId = RandomIdGenerator.newId()
        pendingRegistrations[requestId] = eventChannel
        send(Message.Register(requestId, procedureId))
    }

    private fun doCall(
        procedureId: ProcedureId,
        arguments: JsonArray,
        argumentsKw: JsonObject,
        eventChannel: SendChannel<CallerEvent>
    ) {
        val requestId = RandomIdGenerator.newId()
        pendingCalls[requestId] = eventChannel
        send(Message.Call(requestId, procedureId, arguments, argumentsKw))
    }

    private fun sendError(errorType: MessageType, requestId: RequestId, wampErrorUri: String) =
        send(Message.Error(requestId, errorType, wampErrorUri))

    private suspend fun onResultReceived(requestId: RequestId, arguments: JsonArray, argumentsKw: JsonObject) {
        // Regarding the early return:
        // The spec does not say anything about this case, so I guess we just ignore RESULT messages that we are not interested in
        val eventChannel = pendingCalls.remove(requestId) ?: return
        eventChannel.send(CallerEvent.Result(arguments, argumentsKw))
    }

    private suspend fun onInvocationReceived(
        registrationId: RegistrationId,
        requestId: RequestId,
        arguments: JsonArray,
        argumentsKw: JsonObject
    ) {
        // Regarding the early return:
        // The spec does not say anything about this case, so I guess we just ignore INVOCATION messages that we are not interested in
        val eventChannel = registrations[registrationId] ?: return
        eventChannel.send(CalleeEvent.Invocation(arguments, argumentsKw) { yieldResult(requestId, it) })
    }

    private fun yieldResult(requestId: RequestId, result: CallerEvent) =
        dispatch(
            when (result) {
                is CallerEvent.CallFailed -> Error(Message.Invocation.type, requestId, result.errorUri)
                is CallerEvent.Result     -> Yield(requestId, result.arguments, result.argumentsKw)
            }
        )

    private suspend fun onRegisteredReceived(requestId: RequestId, registrationId: RegistrationId) {
        // Regarding the early return:
        // The spec does not say anything about this case, so I guess we just ignore REGISTERED messages that we are not interested in
        val eventChannel = pendingRegistrations.remove(requestId) ?: return
        registrations[registrationId] = eventChannel
        eventChannel.send(CalleeEvent.ProcedureRegistered(registrationId))
    }

    private fun doPublish(topic: String, arguments: JsonArray, argumentsKw: JsonObject) {
        send(Message.Publish(RandomIdGenerator.newId(), topic, arguments, argumentsKw))
    }

    private suspend fun doUnsubscribe(subscriptionId: SubscriptionId) {
        val channel = subscriptions.remove(subscriptionId)
        // don't send a request if we don't even have a local subscriber running
        if (channel != null) {
            channel.send(SubscriptionEvent.ClientUnsubscribed)
            send(Message.Unsubscribe(RandomIdGenerator.newId(), subscriptionId))
        }
    }

    private fun setupSubscription(topic: String, eventChannel: SendChannel<SubscriptionEvent>) {
        val requestId = RandomIdGenerator.newId()
        pendingSubscriptions[requestId] = eventChannel
        sendSubscribe(requestId, topic)
    }

    private fun sendSubscribe(requestId: RequestId, topic: String) {
        val message = Message.Subscribe(requestId, topic)
        send(message)
    }

    private suspend fun onErrorReceived(
        errorType: MessageType,
        requestId: RequestId,
        wampErrorUri: String
    ) {
        when (errorType) {
            Message.Unsubscribe.type,
            Message.Unregister.type -> Unit // don't care, it's too late now, we've already cleaned up everything
            Message.Subscribe.type  -> failSubscription(requestId, wampErrorUri)
            Message.Register.type   -> failRegistration(requestId, wampErrorUri)
            Message.Call.type       -> failCall(requestId, wampErrorUri)
            else                    -> Unit // Spec doesn't say anything about this
        }
    }

    private suspend fun failCall(requestId: RequestId, wampErrorUri: String) {
        // Regarding the early return:
        // The spec does not say anything about this case, so I guess we just ignore ERROR CALL messages that we are not interested in
        val eventChannel = pendingCalls.remove(requestId) ?: return
        eventChannel.send(CallerEvent.CallFailed(wampErrorUri))
        eventChannel.close()
    }

    private suspend fun failRegistration(requestId: RequestId, wampErrorUri: String) {
        // Regarding the early return:
        // The spec does not say anything about this case, so I guess we just ignore ERROR REGISTER messages that we are not interested in
        val eventChannel = pendingRegistrations.remove(requestId) ?: return
        eventChannel.send(CalleeEvent.RegistrationFailed(wampErrorUri))
        eventChannel.close()
    }

    private suspend fun failSubscription(requestId: RequestId, wampErrorUri: String) {
        // Regarding the early return:
        // The spec does not say anything about this case, so I guess we just ignore ERROR SUBSCRIBE messages that we are not interested in
        val eventChannel = pendingSubscriptions.remove(requestId) ?: return
        eventChannel.send(SubscriptionEvent.SubscriptionFailed(wampErrorUri))
        eventChannel.close()
    }

    private fun onPublishedReceived() {
        // we don't care for now, since we don't use PUBLISH.Options.acknowledge == true
    }

    private suspend fun onEventReceived(
        subscriptionId: SubscriptionId,
        arguments: JsonArray,
        argumentsKw: JsonObject
    ) {
        // Regarding the early return:
        // The spec does not say anything about this case, so I guess we just ignore EVENT messages that we are not interested in
        val eventChannel = subscriptions[subscriptionId] ?: return
        eventChannel.send(SubscriptionEvent.Payload(arguments, argumentsKw))
    }

    private suspend fun onSubscribedReceived(requestId: RequestId, subscriptionId: SubscriptionId) {
        // Regarding the early return:
        // The spec does not say anything about this case, so I guess we just ignore SUBSCRIBED messages that we are not interested in
        val eventChannel = pendingSubscriptions.remove(requestId) ?: return
        subscriptions[subscriptionId] = eventChannel
        eventChannel.send(SubscriptionEvent.SubscriptionEstablished(subscriptionId))
    }

    private fun onUnsubscribedReceived() {
        // We don't do anything special here. We've cleaned up everything when we sent the original UNSUBSCRIBE.
    }

    private fun onUnregisteredReceived() {
        // We don't do anything special here. We've cleaned up everything when we sent the original UNREGISTER.
    }

    private fun evaluateJoining(trigger: Trigger) = when (trigger) {
        is MessageReceived -> {
            when (trigger.message) {
                is Message.Welcome -> onWelcomeReceived()
                is Message.Abort   -> onAbortReceived()
                else               -> onProtocolViolated()
            }
        }
        is Leave           -> sendAbort(WampClose.SYSTEM_SHUTDOWN)
        else               -> failTransition(trigger)
    }

    private fun onWelcomeReceived() {
        state = JOINED
        sessionListener?.onRealmJoined()
    }

    private fun onGoodbyeReceived() {
        state = CLOSED
        sessionListener?.onRealmLeft()
        closeWebSocket()
    }

    private fun onAbortReceived() {
        state = ABORTED
        sessionListener?.onRealmAborted()
        abortWebSocket()
    }

    private fun evaluateInitial(trigger: Trigger) = when (trigger) {
        is MessageReceived -> onProtocolViolated()
        is Join            -> sendHello(trigger.realm)
        else               -> failTransition(trigger)
    }

    private fun failTransition(trigger: Trigger): Nothing =
        error("Invalid state trigger $trigger for state $state")

    private fun sendGoodbye() {
        state = CLOSING
        val message = Message.Goodbye(reason = WampClose.SYSTEM_SHUTDOWN.content)
        send(message)
    }

    private fun onProtocolViolated() = sendAbort(WampClose.PROTOCOL_VIOLATION)

    private fun sendAbort(reason: WampClose) {
        state = ABORTED
        sessionListener?.onRealmAborted()
        val message = Message.Abort(reason = reason.content)
        send(message)
        abortWebSocket()
    }

    private fun closeWebSocket() = webSocketDelegate.close(1001, "Session closed")

    private fun abortWebSocket() = webSocketDelegate.close(1001, "Session aborted")

    private fun sendHello(realm: String) {
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

    private fun send(message: Message) {
        webSocketDelegate.send(message.toJson())
    }

    private fun dispatch(trigger: Trigger) {
        scope.launch {
            eventChannel.send(trigger)
        }
    }

    internal fun onMessage(messageJson: String) {
        val message = fromJsonToMessage(messageJson)
        dispatch(MessageReceived(message))
    }

    internal fun onClosed(code: Int, reason: String) {
        scope.cancel()
    }

    internal fun onFailed(t: Throwable) {
        scope.cancel()
    }
}