package com.raumfeld.wamp.protocol

import com.raumfeld.wamp.protocol.Message.*
import kotlinx.serialization.json.*

/*
From: https://wamp-proto.org/_static/gen/wamp_latest.html#protocol-overview
| Cod | Message        |  Pub |  Brk | Subs |  Calr | Dealr | Callee|
|-----|----------------|------|------|------|-------|-------|-------|
|  1  | `HELLO`        | Tx   | Rx   | Tx   | Tx    | Rx    | Tx    |
|  2  | `WELCOME`      | Rx   | Tx   | Rx   | Rx    | Tx    | Rx    |
|  3  | `ABORT`        | Rx   | TxRx | Rx   | Rx    | TxRx  | Rx    |
|  6  | `GOODBYE`      | TxRx | TxRx | TxRx | TxRx  | TxRx  | TxRx  |
|     |                |      |      |      |       |       |       |
|  8  | `ERROR`        | Rx   | Tx   | Rx   | Rx    | TxRx  | TxRx  |
|     |                |      |      |      |       |       |       |
| 16  | `PUBLISH`      | Tx   | Rx   |      |       |       |       |
| 17  | `PUBLISHED`    | Rx   | Tx   |      |       |       |       |
|     |                |      |      |      |       |       |       |
| 32  | `SUBSCRIBE`    |      | Rx   | Tx   |       |       |       |
| 33  | `SUBSCRIBED`   |      | Tx   | Rx   |       |       |       |
| 34  | `UNSUBSCRIBE`  |      | Rx   | Tx   |       |       |       |
| 35  | `UNSUBSCRIBED` |      | Tx   | Rx   |       |       |       |
| 36  | `EVENT`        |      | Tx   | Rx   |       |       |       |
|     |                |      |      |      |       |       |       |
| 48  | `CALL`         |      |      |      | Tx    | Rx    |       |
| 50  | `RESULT`       |      |      |      | Rx    | Tx    |       |
|     |                |      |      |      |       |       |       |
| 64  | `REGISTER`     |      |      |      |       | Rx    | Tx    |
| 65  | `REGISTERED`   |      |      |      |       | Tx    | Rx    |
| 66  | `UNREGISTER`   |      |      |      |       | Rx    | Tx    |
| 67  | `UNREGISTERED` |      |      |      |       | Tx    | Rx    |
| 68  | `INVOCATION`   |      |      |      |       | Tx    | Rx    |
| 70  | `YIELD`        |      |      |      |       | Rx    | Tx    |
 */

interface RequestMessage {
    val requestId: RequestId
}

typealias RequestId = Long
typealias SubscriptionId = Long
typealias PublicationId = Long
typealias MessageType = Int

private interface MessageFactory<out T : Message> {
    val type: MessageType
    fun create(array: JsonArray): T
}

internal sealed class Message {
    abstract fun toJsonArray(): JsonArray

    fun toJson() = Json.stringify(JsonArray.serializer(), toJsonArray())

    internal data class InvalidMessage(val originalMessage: JsonArray, val throwable: Throwable) : Message() {
        override fun toJsonArray() = error("You mustn't serialize this!")
    }

    internal data class Hello(val realm: String, val details: JsonObject) : Message() {
        companion object : MessageFactory<Hello> {
            override val type = 1

            override fun create(array: JsonArray) =
                Hello(realm = array[1].content, details = array[2].jsonObject)
        }

        override fun toJsonArray() = jsonArray {
            +(type as Number)
            +realm
            +details
        }
    }

    internal data class Welcome(val session: Long, val details: JsonObject) : Message() {
        companion object : MessageFactory<Welcome> {
            override val type = 2
            override fun create(array: JsonArray) =
                Welcome(session = array[1].long, details = array[2].jsonObject)
        }

        override fun toJsonArray() = jsonArray {
            +(type as Number)
            +session
            +details
        }
    }

    internal data class Abort(val details: JsonObject = emptyJsonObject(), val reason: String) : Message() {
        companion object : MessageFactory<Abort> {
            override val type = 3
            override fun create(array: JsonArray) =
                Abort(details = array[1].jsonObject, reason = array[2].content)
        }

        override fun toJsonArray() = jsonArray {
            +(type as Number)
            +details
            +reason
        }
    }

    internal data class Goodbye(val details: JsonObject = emptyJsonObject(), val reason: String) : Message() {
        companion object : MessageFactory<Goodbye> {
            override val type = 6
            override fun create(array: JsonArray) =
                Goodbye(details = array[1].jsonObject, reason = array[2].content)
        }

        override fun toJsonArray() = jsonArray {
            +(type as Number)
            +details
            +reason
        }
    }


    internal data class Error(
        override val requestId: RequestId,
        val originalType: MessageType,
        val wampErrorUri: String,
        val details: JsonObject = emptyJsonObject()
    ) : Message(), RequestMessage {
        companion object : MessageFactory<Error> {
            override val type = 8
            override fun create(array: JsonArray) =
                Error(
                    originalType = array[1].int,
                    requestId = array[2].long,
                    details = array[3].jsonObject,
                    wampErrorUri = array[4].content
                )
        }

        override fun toJsonArray() = jsonArray {
            +(type as Number)
            +(originalType as Number)
            +(requestId as Number)
            +details
            +wampErrorUri
        }
    }


    internal data class Publish(
        override val requestId: RequestId,
        val topic: String,
        val arguments: JsonArray,
        val argumentsKw: JsonObject,
        val options: JsonObject = emptyJsonObject()
    ) : Message(), RequestMessage {
        companion object : MessageFactory<Publish> {
            override val type = 16
            override fun create(array: JsonArray) =
                Publish(
                    requestId = array[1].content.toLong(),
                    options = array[2].jsonObject,
                    topic = array[3].content,
                    arguments = array.getOrNull(4)?.jsonArray ?: emptyJsonArray(),
                    argumentsKw = array.getOrNull(5)?.jsonObject ?: emptyJsonObject()
                )
        }

        override fun toJsonArray() = jsonArray {
            +(type as Number)
            +(requestId as Number)
            +options
            +topic
            +arguments
            +argumentsKw
        }
    }

    internal data class Published(override val requestId: RequestId, val publicationId: PublicationId) : Message(), RequestMessage {
        companion object : MessageFactory<Published> {
            override val type = 17

            override fun create(array: JsonArray) =
                Published(requestId = array[1].content.toLong(), publicationId = array[2].content.toLong())
        }

        override fun toJsonArray() = jsonArray {
            +(type as Number)
            +(requestId as Number)
            +(publicationId as Number)
        }
    }

    internal data class Subscribe(override val requestId: RequestId, val topic: String, val options: JsonObject = emptyJsonObject()) : Message(),
        RequestMessage {
        companion object : MessageFactory<Subscribe> {
            override val type = 32
            override fun create(array: JsonArray) =
                Subscribe(
                    requestId = array[1].content.toLong(),
                    options = array[2].jsonObject,
                    topic = array[3].content
                )
        }

        override fun toJsonArray() = jsonArray {
            +(type as Number)
            +(requestId as Number)
            +options
            +topic
        }
    }

    internal data class Subscribed(override val requestId: RequestId, val subscriptionId: SubscriptionId) : Message(), RequestMessage {
        companion object : MessageFactory<Subscribed> {
            override val type = 33
            override fun create(array: JsonArray) =
                Subscribed(requestId = array[1].content.toLong(), subscriptionId = array[2].content.toLong())
        }

        override fun toJsonArray() = jsonArray {
            +(type as Number)
            +(requestId as Number)
            +(subscriptionId as Number)
        }
    }

    internal data class Unsubscribe(override val requestId: RequestId, val subscriptionId: SubscriptionId) : Message(), RequestMessage {
        companion object : MessageFactory<Unsubscribe> {
            override val type = 34
            override fun create(array: JsonArray) =
                Unsubscribe(requestId = array[1].content.toLong(), subscriptionId = array[2].content.toLong())
        }

        override fun toJsonArray() = jsonArray {
            +(type as Number)
            +(requestId as Number)
            +(subscriptionId as Number)
        }
    }

    internal data class Unsubscribed(override val requestId: RequestId) : Message(), RequestMessage {
        companion object : MessageFactory<Unsubscribed> {
            override val type = 35
            override fun create(array: JsonArray) =
                Unsubscribed(requestId = array[1].content.toLong())
        }

        override fun toJsonArray() = jsonArray {
            +(type as Number)
            +(requestId as Number)
        }
    }

    internal data class Event(
        val subscriptionId: SubscriptionId,
        val publicationId: PublicationId,
        val details: JsonObject,
        val arguments: JsonArray,
        val argumentsKw: JsonObject
    ) : Message() {
        companion object : MessageFactory<Event> {
            override val type = 36
            override fun create(array: JsonArray) =
                Event(
                    subscriptionId = array[1].content.toLong(), publicationId = array[2].content.toLong(),
                    details = array.getOrNull(3)?.jsonObject ?: emptyJsonObject(),
                    arguments = array.getOrNull(4)?.jsonArray ?: emptyJsonArray(),
                    argumentsKw = array.getOrNull(5)?.jsonObject ?: emptyJsonObject()
                )
        }

        override fun toJsonArray() = jsonArray {
            +(type as Number)
            +(subscriptionId as Number)
            +(publicationId as Number)
            +details
            +arguments
            +argumentsKw
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun emptyJsonArray() = jsonArray { }

@Suppress("NOTHING_TO_INLINE")
inline fun emptyJsonObject() = json { }

internal fun fromJsonToMessage(messageJson: String): Message {
    val wampMessage = Json.parse(JsonArraySerializer, messageJson)
    return wampMessage.createMessage()
}

private val MESSAGE_FACTORIES: Map<Int, MessageFactory<*>> by lazy {
    listOf(
        Hello,
        Welcome,
        Goodbye,
        Abort,
        Publish,
        Published,
        Subscribe,
        Subscribed,
        Unsubscribed,
        Event,
        Error
    ).associateBy { it.type }
}

private fun JsonArray.createMessage(): Message = runCatching {
    val type = this[0].int
    MESSAGE_FACTORIES[type]?.create(this) ?: InvalidMessage(this, IllegalArgumentException("Unknown message type"))
}.getOrElse { InvalidMessage(this, it) }
