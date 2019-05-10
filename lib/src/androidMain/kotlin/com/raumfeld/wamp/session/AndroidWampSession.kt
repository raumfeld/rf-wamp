package com.raumfeld.wamp.session

import kotlinx.coroutines.newSingleThreadContext
import kotlin.coroutines.CoroutineContext

actual val wampContextFactory: () -> CoroutineContext = { newSingleThreadContext("Wamp Session Context") }