package com.raumfeld.wamp.pubsub

import com.raumfeld.wamp.protocol.SubscriptionId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

sealed class SubscriptionEvent {
    /** Subscription has been acknowledged by the Broker and is now active */
    data class SubscriptionEstablished(
        /** Use this to unsubscribe */
        val subscriptionId: SubscriptionId
    ) : SubscriptionEvent()

    /** An EVENT for this subscription has been received */
    data class Payload(val arguments: JsonArray?, val argumentsKw: JsonObject?) : SubscriptionEvent()

    /** An UNSUBSCRIBE request has been acknowledged by the broker.
     * The associated event channel will be closed afterwards. */
    object SubscriptionClosed : SubscriptionEvent()

    /** A SUBSCRIBE request has failed or the realm was left/session was shutdown before we could unsubscribe.
     * The associated event channel will be closed afterwards.*/
    class SubscriptionFailed(val errorUri: String) : SubscriptionEvent()

    /** An UNSUBSCRIBE request has failed or the realm was left/session was shutdown before the UNSUBSCRIBE request was acknowledged.
     * The associated event channel will be closed afterwards.*/
    class UnsubscriptionFailed(val errorUri: String) : SubscriptionEvent()
}
