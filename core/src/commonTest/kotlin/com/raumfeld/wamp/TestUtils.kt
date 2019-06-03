package com.raumfeld.wamp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.test.fail

expect fun runTest(block: suspend (scope: CoroutineScope) -> Unit)

suspend fun assertChannelClosed(channel: ReceiveChannel<*>, timeout: Long = 1000L) =
    withTimeout(timeout) { assertEquals(expected = null, actual = channel.receiveOrNull()) }

fun assertNoEvent(channel: ReceiveChannel<*>) =
    assertEquals(expected = null, actual = channel.getEventOrNull())

private fun ReceiveChannel<*>.getEventOrNull() = poll()

suspend inline fun <reified T> ReceiveChannel<*>.getEvent(timeout: Long = 1000L): T {
    var actualEvent: Any? = null
    try {
        return withTimeout(timeout) { receive().also { actualEvent = it } } as T
    } catch (e: TimeoutCancellationException) {
        fail("Timeout while waiting for event: ${T::class}")
    } catch (e: ClassCastException) {
        fail("Did not receive expected event. Expected ${T::class} but was: $actualEvent")
    }
}