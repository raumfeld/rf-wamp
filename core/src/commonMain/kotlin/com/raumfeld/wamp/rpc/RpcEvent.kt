package com.raumfeld.wamp.rpc

import com.raumfeld.wamp.protocol.RegistrationId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

sealed class CalleeEvent {
    /** A REGISTER request has been acknowledged by the dealer and the procedure can now be called by other peers */
    data class ProcedureRegistered(val registrationId: RegistrationId) : CalleeEvent()

    /** Someone is calling this procedure. Clients *MUST* invoke the [returnResult] lambda to indicate they are done */
    data class Invocation(
        val arguments: JsonArray?,
        val argumentsKw: JsonObject?,
        val returnResult: suspend (CallerEvent) -> Unit
    ) : CalleeEvent()

    /** An UNREGISTER request has been acknowledged by the dealer. The associated event channel will be closed afterwards.*/
    object ProcedureUnregistered : CalleeEvent()

    /** A REGISTER request has failed or the realm was left/session was shutdown before we could unregister.
     * The associated event channel will be closed afterwards.*/
    data class RegistrationFailed(val errorUri: String) : CalleeEvent()

    /** An UNREGISTER request has failed or the realm was left/session was shutdown before the UNREGISTER request was acknowledged.
     * The associated event channel will be closed afterwards.*/
    data class UnregistrationFailed(val errorUri: String) : CalleeEvent()
}

/** This class is used to notify CALLERs about the result of a call but also by CALLEEs to yield a result back to the dealer.*/
sealed class CallerEvent {
    /** The CALL request was successful.
     * The associated event channel will be closed afterwards.*/
    data class CallSucceeded(val arguments: JsonArray? = null, val argumentsKw: JsonObject? = null) : CallerEvent()

    /** The CALL request has failed or the realm was left/session was shutdown when the call was in-flight.
     * The associated event channel will be closed afterwards.*/
    data class CallFailed(val errorUri: String, val arguments: JsonArray? = null, val argumentsKw: JsonObject? = null) :
        CallerEvent()
}

