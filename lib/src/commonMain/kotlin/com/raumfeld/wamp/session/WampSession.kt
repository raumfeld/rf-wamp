package com.raumfeld.wamp.session

import com.raumfeld.wamp.protocol.Message
import com.raumfeld.wamp.protocol.WampClose
import com.raumfeld.wamp.protocol.emptyJsonObject
import com.raumfeld.wamp.protocol.fromJsonToMessage
import com.raumfeld.wamp.session.WampSession.State.*
import com.raumfeld.wamp.session.WampSession.Trigger.*
import com.raumfeld.wamp.websocket.WebSocketDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
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
        class MessageReceived(val message: Message) : Trigger()
        class Join(val realm: String) : Trigger()
        object Leave : Trigger()
    }

    private var realm: String? = null
    private var sessionListener: WampSessionListener? = null
    private var state = INITIAL
    private val scope = CoroutineScope(context)
    private var eventChannel = Channel<Trigger>()

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

    private fun evaluate(trigger: Trigger) {
        when (state) {
            INITIAL -> evaluateInitial(trigger)
            JOINING -> evaluateJoining(trigger)
            JOINED  -> evaluateJoined(trigger)
            ABORTED -> evaluatedAborted(trigger)
            CLOSING -> evaluateClosing(trigger)
            CLOSED  -> evaluateClosed(trigger)
        }
    }

    private fun evaluateClosed(trigger: Trigger) {
        failTransition(trigger)
    }

    private fun evaluateClosing(trigger: Trigger) {
        failTransition(trigger)
    }

    private fun evaluatedAborted(trigger: Trigger) {
        failTransition(trigger)
    }

    private fun evaluateJoined(trigger: Trigger) {
        when (trigger) {
            is Leave -> sendGoodbye()
            else     -> failTransition(trigger)
        }
    }

    private fun evaluateJoining(trigger: Trigger) {
        when (trigger) {
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
    }

    private fun onWelcomeReceived() {
        state = JOINED
        sessionListener?.onRealmJoined()
    }

    private fun onAbortReceived() {
        state = ABORTED
        sessionListener?.onRealmAborted()
        abortWebsocket()
    }

    private fun evaluateInitial(trigger: Trigger) {
        when (trigger) {
            is MessageReceived -> onProtocolViolated()
            is Join            -> sendHello(trigger.realm)
            else               -> failTransition(trigger)
        }
    }

    private fun failTransition(trigger: Trigger): Nothing =
        error("Invalid state trigger $trigger for state $state")

    private fun sendGoodbye() {
        state = CLOSING
        sessionListener?.onRealmLeft()
        val message = Message.Goodbye(reason = WampClose.SYSTEM_SHUTDOWN.content)
        send(message)
        closeWebsocket()
    }

    private fun onProtocolViolated() = sendAbort(WampClose.PROTOCOL_VIOLATION)

    private fun sendAbort(reason: WampClose) {
        state = ABORTED
        sessionListener?.onRealmAborted()
        val message = Message.Abort(reason = reason.content)
        send(message)
        abortWebsocket()
    }

    private fun closeWebsocket() = webSocketDelegate.close(1001, "Session closed")

    private fun abortWebsocket() = webSocketDelegate.close(1001, "Session aborted")

    private fun sendHello(realm: String) {
        state = JOINING
        this.realm = realm
        val message = Message.Hello(
            realm, json {
                "roles" to json {
                    "publisher" to emptyJsonObject()
                    "subscriber" to emptyJsonObject()
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
        evaluate(MessageReceived(message))
    }

    internal fun onClosed(code: Int, reason: String) {
        scope.cancel()
    }
}