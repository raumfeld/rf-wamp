package com.raumfeld.wamp.pubsub

import com.raumfeld.wamp.protocol.SubscriptionId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

sealed class SubscriptionData {
    data class SubscriptionEstablished(val subscriptionId: SubscriptionId) : SubscriptionData()
    data class SubscriptionEventPayload(val arguments: JsonArray, val argumentsKw: JsonObject) : SubscriptionData()
    object ClientUnsuscribed : SubscriptionData()
    class SubscriptionFailed(val errorUri: String) : SubscriptionData()
}
