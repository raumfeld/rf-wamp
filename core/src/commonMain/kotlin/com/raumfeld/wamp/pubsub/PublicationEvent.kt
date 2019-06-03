package com.raumfeld.wamp.pubsub

import com.raumfeld.wamp.protocol.PublicationId

/**
 * Note: You will only get these events if you specify that you wish PUBLISH requests to be acknowledged
 * by the broker.
 */
sealed class PublicationEvent {
    /** The PUBLISH request was successful and an EVENT was delivered to any subscribers.
     * The associated event channel will be closed afterwards.*/
    class PublicationSucceeded(val publicationId: PublicationId) : PublicationEvent()

    /** The PUBLISH request was not successful.
     * The associated event channel will be closed afterwards.*/
    class PublicationFailed(val errorUri: String) : PublicationEvent()
}