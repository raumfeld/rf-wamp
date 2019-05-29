package com.raumfeld.wamp.session

import com.raumfeld.wamp.WampClient
import com.raumfeld.wamp.extensions.android.okhttp.OkHttpWebSocketFactory

actual fun getLocalWampClient() = WampClient(OkHttpWebSocketFactory())