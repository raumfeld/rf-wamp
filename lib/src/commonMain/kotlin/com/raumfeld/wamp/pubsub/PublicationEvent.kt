package com.raumfeld.wamp.pubsub

import com.raumfeld.wamp.protocol.PublicationId

sealed class PublicationEvent {
    class PublicationSucceeded(val publicationId: PublicationId) : PublicationEvent()
    class PublicationFailed(val errorUri: String) : PublicationEvent()
}