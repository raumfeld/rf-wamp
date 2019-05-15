package com.raumfeld.wamp

import java.util.*

actual fun getThreadSafeIdStorage(): MutableCollection<Long> = Collections.synchronizedList(mutableListOf())