package com.raumfeld.wamp.protocol

import kotlinx.serialization.json.JsonException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.expect

class MessageTests {

    @Test
    fun shouldHandleUnknownMessageTypes() {
        val unknownTypeMessage = "[100, {}, [], {}]"
        val message = fromJsonToMessage(unknownTypeMessage)
        assertTrue(message is Message.InvalidMessage)
        assertEquals(unknownTypeMessage, message.originalMessage)
        assertTrue(message.throwable is IllegalArgumentException)
    }

    @Test
    fun shouldInvalidJson() {
        val invalidJson = "[{where did my braces go.."
        val message = fromJsonToMessage(invalidJson)
        assertTrue(message is Message.InvalidMessage)
        assertEquals(invalidJson, message.originalMessage)
        assertTrue(message.throwable is JsonException)
    }


    @Test
    fun shouldConvertMessagesCorrectly() {
        val allMessages = ExampleMessage.values()
        allMessages.forEach {
            expect(it.message) { fromJsonToMessage(it.messageJson) }
            expect(it.messageJson) { it.message.toJson() }
        }
    }
}