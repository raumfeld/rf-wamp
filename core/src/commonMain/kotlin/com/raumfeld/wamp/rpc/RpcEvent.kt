package com.raumfeld.wamp.rpc

import com.raumfeld.wamp.protocol.RegistrationId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

sealed class CalleeEvent {
    data class ProcedureRegistered(val registrationId: RegistrationId) : CalleeEvent()
    data class Invocation(
        val arguments: JsonArray?,
        val argumentsKw: JsonObject?,
        val returnResult: suspend (CallerEvent) -> Unit
    ) : CalleeEvent()
    object ProcedureUnregistered : CalleeEvent()
    data class RegistrationFailed(val errorUri: String) : CalleeEvent()
    data class UnregistrationFailed(val errorUri: String) : CalleeEvent()
}

sealed class CallerEvent {
    data class Result(val arguments: JsonArray? = null, val argumentsKw: JsonObject? = null) : CallerEvent()
    data class CallFailed(val errorUri: String, val arguments: JsonArray? = null, val argumentsKw: JsonObject? = null) : CallerEvent()
}

