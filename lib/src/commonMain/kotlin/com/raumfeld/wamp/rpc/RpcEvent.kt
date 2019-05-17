package com.raumfeld.wamp.rpc

import com.raumfeld.wamp.protocol.RegistrationId
import com.raumfeld.wamp.protocol.emptyJsonArray
import com.raumfeld.wamp.protocol.emptyJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

sealed class CalleeEvent {
    data class ProcedureRegistered(val registrationId: RegistrationId) : CalleeEvent()
    data class Invocation(
        val arguments: JsonArray,
        val argumentsKw: JsonObject,
        val returnResult: (CallerEvent) -> Unit
    ) : CalleeEvent()

    data class RegistrationFailed(val errorUri: String) : CalleeEvent()
}

sealed class CallerEvent {
    data class Result(val arguments: JsonArray = emptyJsonArray(), val argumentsKw: JsonObject = emptyJsonObject()) : CallerEvent()
    data class CallFailed(val errorUri: String) : CallerEvent()
}

