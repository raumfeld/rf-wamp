package com.raumfeld.wamp.protocol

import kotlinx.serialization.SerializationException
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
    fun shouldHandleInvalidJson() {
        val invalidJson = "[{where did my braces go.."
        val message = fromJsonToMessage(invalidJson)
        assertTrue(message is Message.InvalidMessage)
        assertEquals(invalidJson, message.originalMessage)
        assertTrue(message.throwable is SerializationException)
    }


    @Test
    fun shouldConvertMessagesCorrectly() {
        val allMessages = ExampleMessage.values()
        allMessages.forEach {
            // both a message with empty and null arguments serializes to "arguments=[]"
            // but "arguments=[]" never deserializes into arguments=null so we exclude those here
            if (!it.hasNullArgumentsAndNonNullArgumentsKw())
                expect(it.message) { fromJsonToMessage(it.messageJson) }
            expect(it.messageJson) { it.message.toJson() }
        }
    }
}

private fun ExampleMessage.hasNullArgumentsAndNonNullArgumentsKw() =
    message.toString().contains("arguments=null") // ugly hack >:)
