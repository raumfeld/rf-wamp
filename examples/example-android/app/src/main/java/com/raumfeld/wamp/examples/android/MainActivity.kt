package com.raumfeld.wamp.examples.android

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.raumfeld.wamp.WampClient
import com.raumfeld.wamp.protocol.SubscriptionId
import com.raumfeld.wamp.pubsub.SubscriptionEvent.*
import com.raumfeld.wamp.session.WampSession
import com.raumfeld.wamp.websocket.WebSocketCallback
import com.raumfeld.wamp.websocket.WebSocketDelegate
import com.raumfeld.wamp.websocket.WebSocketFactory
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import okhttp3.*

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "WampDemo"
    }

    private var session: WampSession? = null
    private var subscriptionId: SubscriptionId? = null

    private fun toast(message: String) {
        Log.i(TAG, message)
        GlobalScope.launch(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        wsUrl.setText(AppPreferences.wsUrl)
        realm.setText(AppPreferences.realm)
        subscribeTopic.setText(AppPreferences.subscribeTopic)
        publishTopic.setText(AppPreferences.publishTopic)
        publishValue.setText(AppPreferences.publishValue)

        wsUrl.doAfterTextChanged { AppPreferences.wsUrl = it.toString() }
        realm.doAfterTextChanged { AppPreferences.realm = it.toString() }
        subscribeTopic.doAfterTextChanged { AppPreferences.subscribeTopic = it.toString() }
        publishTopic.doAfterTextChanged { AppPreferences.publishTopic = it.toString() }
        publishValue.doAfterTextChanged { AppPreferences.publishValue = it.toString() }

        subscribe.setOnClickListener {
            val channel = session?.subscribe(subscribeTopic.text.toString()) ?: return@setOnClickListener
            GlobalScope.launch(Dispatchers.Main) {
                channel.consumeEach {
                    when (it) {
                        is SubscriptionEstablished -> {
                            subscriptionId = it.subscriptionId
                            toast("Subscription established")
                        }
                        is Payload                 -> toast("Received event: $it")
                        is SubscriptionFailed      -> {
                            subscriptionId = null
                            toast("Subscription failed with: ${it.errorUri}")
                        }
                        ClientUnsubscribed         -> toast("We have unsubscribed")
                    }
                }
                toast("Subscription has ended")
            }
        }
        unsubscribe.setOnClickListener {
            subscriptionId?.let {
                session?.unsubscribe(it)
            }
        }
        publish.setOnClickListener {
            session?.publish(publishTopic.text.toString(), jsonArray { +publishValue.text.toString() })
        }
        leave.setOnClickListener {
            session?.leave()
        }
        join.setOnClickListener {
            val realmString = realm.text.toString()
            WampClient(OkHttpWebSocketFactory()).createSession(uri = wsUrl.text.toString()) { result ->
                result.onSuccess {
                    Log.i(TAG, "WAMP session created successfully, joining realm ...")
                    session = it
                    it.join(realmString, object : WampSession.WampSessionListener {
                        override fun onRealmAborted() {
                            session = null
                            toast("Realm was aborted")
                        }

                        override fun onRealmJoined() {
                            toast("Realm was joined")
                        }

                        override fun onRealmLeft() {
                            session = null
                            toast("Realm was left")
                        }
                    })
                }
                result.onFailure {
                    Log.e(TAG, "WebSocket failure", it)
                    toast("Could not create session")
                }
            }
        }
    }

    class OkHttpWebSocketDelegate(val webSocket: WebSocket) : WebSocketDelegate {
        override fun close(code: Int, reason: String?) {
            webSocket.close(code, reason)
        }

        override fun send(message: String) {
            webSocket.send(message)
        }
    }

    class OkHttpWebSocketFactory : WebSocketFactory {
        val WebSocket.delegate get() = OkHttpWebSocketDelegate(this)

        override fun createWebsocket(uri: String, callback: WebSocketCallback) {
            val request =
                Request.Builder().url(uri).header("Sec-WebSocket-Protocol", "wamp.2.json").build()
            OkHttpClient().newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) =
                    callback.onOpen(webSocket.delegate)

                override fun onMessage(webSocket: WebSocket, text: String) =
                    callback.onMessage(webSocket.delegate, text)

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) =
                    callback.onClosing(webSocket.delegate, code, reason)

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
                    callback.onClosed(webSocket.delegate, code, reason)

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                    callback.onFailure(webSocket.delegate, t)
            })
        }

    }
}
