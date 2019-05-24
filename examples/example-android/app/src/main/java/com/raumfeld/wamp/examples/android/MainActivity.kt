package com.raumfeld.wamp.examples.android

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.raumfeld.wamp.WampClient
import com.raumfeld.wamp.protocol.RegistrationId
import com.raumfeld.wamp.protocol.SubscriptionId
import com.raumfeld.wamp.pubsub.SubscriptionEvent.*
import com.raumfeld.wamp.rpc.CalleeEvent
import com.raumfeld.wamp.rpc.CallerEvent
import com.raumfeld.wamp.session.WampSession
import com.raumfeld.wamp.websocket.WebSocketCallback
import com.raumfeld.wamp.websocket.WebSocketDelegate
import com.raumfeld.wamp.websocket.WebSocketFactory
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import okhttp3.*
import okio.ByteString

class MainActivity : AppCompatActivity(), WampSession.WampSessionListener {
    companion object {
        const val TAG = "WampDemo"
    }

    private var session: WampSession? = null
    private var subscriptionId: SubscriptionId? = null
    private var registrationId: RegistrationId? = null

    private fun toast(message: String) {
        Log.i(TAG, message)
        GlobalScope.launch(Main) {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupTextInputs()

        setupButtons()
    }

    private fun setupButtons() {
        call.setOnClickListener {
            GlobalScope.launch(Main) {
                val channel =
                    session?.call(
                        callProcedureUri.text.toString(),
                        jsonArray { +callProcedureArgument.text.toString() })
                        ?: return@launch
                channel.consumeEach {
                    when (it) {
                        is CallerEvent.Result     -> toast("Call returned: ${it.arguments}\n${it.argumentsKw}")
                        is CallerEvent.CallFailed -> toast("Call failed: ${it.errorUri}")
                    }
                }
            }
        }
        unregister.setOnClickListener {
            GlobalScope.launch(Main) {
                registrationId?.let {
                    session?.unregister(it)
                }

            }
        }
        register.setOnClickListener {
            val result = jsonArray { +registerProcedureReturn.text.toString() }
            GlobalScope.launch(Main) {
                val channel = session?.register(registerProcedureUri.text.toString()) ?: return@launch
                channel.consumeEach {
                    when (it) {
                        is CalleeEvent.ProcedureRegistered   -> {
                            registrationId = it.registrationId
                            toast("Procedure was registered successfully")
                        }
                        is CalleeEvent.Invocation            -> {
                            toast(
                                "Received invocation: ${it.arguments}\n" +
                                        "${it.argumentsKw}\nReturning $result"
                            )
                            it.returnResult(CallerEvent.Result(result))
                        }
                        is CalleeEvent.ProcedureUnregistered -> toast("We have unregistered")
                        is CalleeEvent.RegistrationFailed    -> toast("Procedure registration failed with: ${it.errorUri}")
                        is CalleeEvent.UnregistrationFailed  -> toast("Procedure unregistration failed with: ${it.errorUri}")
                    }
                }
                toast("Registration has ended")
            }
        }
        subscribe.setOnClickListener {

            GlobalScope.launch(Main) {
                val channel = session?.subscribe(subscribeTopic.text.toString()) ?: return@launch
                channel.consumeEach {
                    when (it) {
                        is SubscriptionEstablished -> {
                            subscriptionId = it.subscriptionId
                            toast("Subscription established")
                        }
                        is Payload                 -> toast("Received event: ${it.arguments}\n${it.argumentsKw}")
                        is SubscriptionFailed      -> {
                            subscriptionId = null
                            toast("Subscription failed with: ${it.errorUri}")
                        }
                        SubscriptionClosed         -> toast("We have unsubscribed")
                        is UnsubscriptionFailed    -> toast("Unsubscription failed with: ${it.errorUri}")
                    }
                }
                toast("Subscription has ended")
            }
        }
        unsubscribe.setOnClickListener {
            subscriptionId?.let {
                GlobalScope.launch {
                    session?.unsubscribe(it)
                }
            }
        }
        publish.setOnClickListener {
            GlobalScope.launch(Main) {
                session?.publish(publishTopic.text.toString(), jsonArray { +publishValue.text.toString() })
            }
        }
        leave.setOnClickListener {
            GlobalScope.launch(Main) {
                session?.leave()
            }
        }
        shutdown.setOnClickListener {
            GlobalScope.launch(Main) {
                session?.shutdown()
            }
        }
        join.setOnClickListener {
            val realmString = realm.text.toString()
            if (session == null) {
                WampClient(OkHttpWebSocketFactory()).createSession(
                    uri = wsUrl.text.toString(),
                    sessionListener = this
                ) { result ->
                    GlobalScope.launch(Main) {
                        result.onSuccess {
                            Log.i(TAG, "WAMP session created successfully, joining realm ...")
                            session = it
                            it.join(realmString)
                        }
                        result.onFailure {
                            Log.e(TAG, "WebSocket failure", it)
                            session = null
                            toast("Could not create session")
                        }
                    }
                }
            } else {
                GlobalScope.launch(Main) {
                    Log.i(TAG, "Re-using WAMP session, joining realm ...")
                    session?.join(realmString)
                }
            }

        }
    }

    private fun setupTextInputs() {
        wsUrl.setText(AppPreferences.wsUrl)
        realm.setText(AppPreferences.realm)
        subscribeTopic.setText(AppPreferences.subscribeTopic)
        publishTopic.setText(AppPreferences.publishTopic)
        publishValue.setText(AppPreferences.publishValue)
        callProcedureUri.setText(AppPreferences.callUri)
        callProcedureArgument.setText(AppPreferences.callArgument)
        registerProcedureUri.setText(AppPreferences.registerUri)
        registerProcedureReturn.setText(AppPreferences.registerReturn)

        wsUrl.doAfterTextChanged { AppPreferences.wsUrl = it.toString() }
        realm.doAfterTextChanged { AppPreferences.realm = it.toString() }
        subscribeTopic.doAfterTextChanged { AppPreferences.subscribeTopic = it.toString() }
        publishTopic.doAfterTextChanged { AppPreferences.publishTopic = it.toString() }
        publishValue.doAfterTextChanged { AppPreferences.publishValue = it.toString() }
        callProcedureUri.doAfterTextChanged { AppPreferences.callUri = it.toString() }
        callProcedureArgument.doAfterTextChanged { AppPreferences.callArgument = it.toString() }
        registerProcedureUri.doAfterTextChanged { AppPreferences.registerUri = it.toString() }
        registerProcedureReturn.doAfterTextChanged { AppPreferences.registerReturn = it.toString() }
    }

    override fun onSessionAborted(reason: String, throwable: Throwable?) {
        session = null
        toast("Session was aborted: $reason (${throwable?.message})")
    }

    override fun onSessionShutdown() {
        toast("Session was shutdown")
    }

    override fun onRealmJoined(realm: String) {
        toast("Realm '$realm' was joined")
    }

    override fun onRealmLeft(realm: String, fromRouter: Boolean) {
        toast("Realm '$realm' was left (fromRouter: $fromRouter)")
    }

    class OkHttpWebSocketDelegate(val webSocket: WebSocket) : WebSocketDelegate {
        override suspend fun close(code: Int, reason: String?) {
            webSocket.close(code, reason)
        }

        override suspend fun send(message: String) {
            webSocket.send(message)
        }
    }

    class OkHttpWebSocketFactory : WebSocketFactory {
        val WebSocket.delegate get() = OkHttpWebSocketDelegate(this)

        override fun createWebSocket(uri: String, callback: WebSocketCallback) {
            val request =
                Request.Builder().url(uri).header("Sec-WebSocket-Protocol", "wamp.2.json").build()
            OkHttpClient().newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) =
                    runBlocking { callback.onOpen(webSocket.delegate) }

                override fun onMessage(webSocket: WebSocket, text: String) =
                    runBlocking { callback.onMessage(webSocket.delegate, text) }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) =
                    runBlocking { callback.onMessage(webSocket.delegate, bytes.toByteArray()) }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) =
                    runBlocking { callback.onClosing(webSocket.delegate, code, reason) }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
                    runBlocking { callback.onClosed(webSocket.delegate, code, reason) }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                    runBlocking { callback.onFailure(webSocket.delegate, t) }
            })
        }

    }
}
