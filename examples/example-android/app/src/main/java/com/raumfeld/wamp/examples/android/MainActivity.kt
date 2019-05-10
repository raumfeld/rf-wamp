package com.raumfeld.wamp.examples.android

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.raumfeld.wamp.WampClient
import com.raumfeld.wamp.session.WampSession
import com.raumfeld.wamp.websocket.WebSocketCallback
import com.raumfeld.wamp.websocket.WebSocketDelegate
import com.raumfeld.wamp.websocket.WebSocketFactory
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.*

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "WampDemo"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        wsUrl.setText(AppPreferences.wsUrl)
        realm.setText(AppPreferences.realm)

        wsUrl.doAfterTextChanged { AppPreferences.wsUrl = it.toString() }
        realm.doAfterTextChanged { AppPreferences.realm = it.toString() }

        join.setOnClickListener {
            val realmString = realm.text.toString()
            WampClient(OkHttpWebSocketFactory()).createSession(uri = wsUrl.text.toString()) {
                it.onSuccess {
                    Log.i(TAG, "WAMP session created successfully, joining realm ...")
                    it.join(realmString, object : WampSession.WampSessionListener {
                        override fun onRealmAborted() {
                            Log.e(TAG, "Realm was aborted")
                            GlobalScope.launch(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Realm aborted!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onRealmJoined() {
                            Log.i(TAG, "Realm was joined")
                            GlobalScope.launch(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Success!", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }

                        override fun onRealmLeft() {
                            Log.i(TAG, "Realm was left")
                            GlobalScope.launch(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Realm left!", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    })
                }
                it.onFailure {
                    Log.e(TAG, "Could not create WAMP session", it)
                    GlobalScope.launch(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Could not create session: ${it.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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
