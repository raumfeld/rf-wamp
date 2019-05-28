package com.raumfeld.wamp

import kotlinx.coroutines.CoroutineScope

expect fun runTest(block: suspend (scope : CoroutineScope) -> Unit)