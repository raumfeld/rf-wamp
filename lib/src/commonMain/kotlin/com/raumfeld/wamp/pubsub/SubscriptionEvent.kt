package com.raumfeld.wamp.pubsub

import com.raumfeld.wamp.protocol.SubscriptionId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

sealed class SubscriptionEvent {
    data class SubscriptionEstablished(val subscriptionId: SubscriptionId) : SubscriptionEvent()
    data class Payload(val arguments: JsonArray?, val argumentsKw: JsonObject?) : SubscriptionEvent()
    object ClientUnsubscribed : SubscriptionEvent()
    class SubscriptionFailed(val errorUri: String) : SubscriptionEvent()
}
