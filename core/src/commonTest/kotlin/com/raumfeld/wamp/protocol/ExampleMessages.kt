package com.raumfeld.wamp.protocol

import kotlinx.serialization.json.*

/**
 * The examples are taken from https://wamp-proto.org/_static/gen/wamp_latest.html#messages
 */
internal enum class ExampleMessage(val messageJson: String, val message: Message) {
    /**
     * [HELLO, Realm|uri, Details|dict]
     */
    HELLO(
        """[1,"somerealm",{"roles":{"publisher":{},"subscriber":{},"caller":{},"callee":{}}}]""",
        Message.Hello("somerealm", buildJsonObject {
            put("roles", buildJsonObject {
                put("publisher", emptyJsonObject())
                put("subscriber", emptyJsonObject())
                put("caller", emptyJsonObject())
                put("callee", emptyJsonObject())
            })
        })
    ),

    /**
     * [WELCOME, Session|id, Details|dict]
     */
    WELCOME(
        """[2,9129137332,{"roles":{"broker":{}}}]""",
        Message.Welcome(9129137332, buildJsonObject {
            put(
                "roles",
                buildJsonObject { put("broker", emptyJsonObject()) })
        })
    ),

    /**
     * [ABORT, Details|dict, Reason|uri]
     */
    ABORT_REALM_DOES_NOT_EXIST(
        """[3,{"message":"The realm does not exist."},"wamp.error.no_such_realm"]""",
        Message.Abort(
            buildJsonObject { put("message", "The realm does not exist.") },
            "wamp.error.no_such_realm"
        )
    ),

    /**
     * [ABORT, Details|dict, Reason|uri]
     */
    ABORT_SHUTDOWN(
        """[3,{},"wamp.close.system_shutdown"]""",
        Message.Abort(reason = "wamp.close.system_shutdown")
    ),

    /**
     * [ABORT, Details|dict, Reason|uri]
     */
    ABORT_PROTOCOL_VIOLATION_NO_DETAILS(
        """[3,{},"wamp.error.protocol_violation"]""",
        Message.Abort(reason = "wamp.error.protocol_violation")
    ),

    /**
     * [ABORT, Details|dict, Reason|uri]
     */
    ABORT_PROTOCOL_VIOLATION_AFTER_HELLO(
        """[3,{"message":"Illegal message received. Expected 'Welcome' or 'Abort'."},"wamp.error.protocol_violation"]""",
        Message.Abort(
            details = buildJsonObject {
                put(
                    "message",
                    "Illegal message received. Expected 'Welcome' or 'Abort'."
                )
            },
            reason = "wamp.error.protocol_violation"
        )
    ),

    /**
     * [ABORT, Details|dict, Reason|uri]
     */
    ABORT_PROTOCOL_VIOLATION_UNEXPECTED_MESSAGE(
        """[3,{"message":"Received unexpected message."},"wamp.error.protocol_violation"]""",
        Message.Abort(
            details = buildJsonObject {
                put(
                    "message",
                    "Received unexpected message."
                )
            },
            reason = "wamp.error.protocol_violation"
        )
    ),

    /**
     * [GOODBYE, Details|dict, Reason|uri]
     */
    GOODBYE_SHUTDOWN_WITH_MESSAGE(
        """[6,{"message":"The host is shutting down now."},"wamp.close.system_shutdown"]""",
        Message.Goodbye(
            buildJsonObject {
                put(
                    "message",
                    "The host is shutting down now."
                )
            },
            "wamp.close.system_shutdown"
        )
    ),

    /**
     * [GOODBYE, Details|dict, Reason|uri]
     */
    GOODBYE_SHUTDOWN(
        """[6,{},"wamp.close.system_shutdown"]""",
        Message.Goodbye(reason = "wamp.close.system_shutdown")
    ),

    /**
     * [GOODBYE, Details|dict, Reason|uri]
     */
    GOODBYE_CLOSE_REALM(
        """[6,{},"wamp.close.close_realm"]""",
        Message.Goodbye(reason = "wamp.close.close_realm")
    ),

    /**
     * [GOODBYE, Details|dict, Reason|uri]
     */
    GOODBYE_AND_OUT(
        """[6,{},"wamp.close.goodbye_and_out"]""",
        Message.Goodbye(reason = "wamp.close.goodbye_and_out")
    ),

    /**
     * [SUBSCRIBE, Request|id, Options|dict, Topic|uri]
     */
    SUBSCRIBE(
        """[32,713845233,{},"com.myapp.mytopic1"]""",
        Message.Subscribe(713845233, "com.myapp.mytopic1")
    ),

    /**
     * [SUBSCRIBE, Request|id, Options|dict, Topic|uri]
     */
    SUBSCRIBE2(
        """[32,713845234,{},"com.myapp.mytopic1"]""",
        Message.Subscribe(713845234, "com.myapp.mytopic1")
    ),

    /**
     * [SUBSCRIBED, SUBSCRIBE.Request|id, Subscription|id]
     */
    SUBSCRIBED(
        """[33,713845233,5512315355]""",
        Message.Subscribed(requestId = 713845233, subscriptionId = 5512315355)
    ),

    /**
     * [SUBSCRIBED, SUBSCRIBE.Request|id, Subscription|id]
     */
    SUBSCRIBED2(
        """[33,713845234,5512315356]""",
        Message.Subscribed(requestId = 713845234, subscriptionId = 5512315356)
    ),

    /**
     * [ERROR, SUBSCRIBE, SUBSCRIBE.Request|id, Details|dict, Error|uri]
     */
    SUBSCRIBE_ERROR(
        """[8,32,713845233,{},"wamp.error.not_authorized"]""",
        Message.Error(
            requestId = 713845233,
            originalType = 32,
            wampErrorUri = "wamp.error.not_authorized"
        )
    ),

    /**
     * [UNSUBSCRIBE, Request|id, SUBSCRIBED.Subscription|id]
     */
    UNSUBSCRIBE(
        """[34,85346237,5512315355]""",
        Message.Unsubscribe(requestId = 85346237, subscriptionId = 5512315355)
    ),

    /**
     * [UNSUBSCRIBE, Request|id, SUBSCRIBED.Subscription|id]
     */
    UNSUBSCRIBE2(
        """[34,85346238,5512315356]""",
        Message.Unsubscribe(requestId = 85346238, subscriptionId = 5512315356)
    ),

    /**
     * [UNSUBSCRIBED, UNSUBSCRIBE.Request|id]
     */
    UNSUBSCRIBED(
        """[35,85346237]""",
        Message.Unsubscribed(requestId = 85346237)
    ),

    /**
     * [UNSUBSCRIBED, UNSUBSCRIBE.Request|id]
     */
    UNSUBSCRIBED2(
        """[35,85346238]""",
        Message.Unsubscribed(requestId = 85346238)
    ),

    /**
     * [UNSUBSCRIBE, Request|id, SUBSCRIBED.Subscription|id]
     */
    UNSUBSCRIBE_ERROR(
        """[8,34,85346237,{},"wamp.error.no_such_subscription"]""",
        Message.Error(
            requestId = 85346237,
            originalType = 34,
            wampErrorUri = "wamp.error.no_such_subscription"
        )
    ),

    /**
     * [PUBLISH, Request|id, Options|dict, Topic|uri]
     */
    PUBLISH_NO_ARG(
        """[16,239714735,{"acknowledge":true},"com.myapp.mytopic1"]""",
        Message.Publish(
            239714735,
            "com.myapp.mytopic1",
            null,
            null,
            buildJsonObject { put("acknowledge", true) })
    ),

    /**
     * [PUBLISH, Request|id, Options|dict, Topic|uri]
     */
    PUBLISH_NO_ARG2(
        """[16,239714736,{"acknowledge":true},"com.myapp.mytopic1"]""",
        Message.Publish(
            239714736,
            "com.myapp.mytopic1",
            null,
            null,
            buildJsonObject { put("acknowledge", true) })
    ),

    /**
     * [PUBLISH, Request|id, Options|dict, Topic|uri]
     */
    PUBLISH_NO_ARG_NO_ACKNOWLEDGE(
        """[16,239714735,{},"com.myapp.mytopic1"]""",
        Message.Publish(239714735, "com.myapp.mytopic1", null, null)
    ),

    /**
     * [PUBLISH, Request|id, Options|dict, Topic|uri, Arguments|list]
     */
    PUBLISH_ONLY_ARRAY_ARG(
        """[16,239714735,{"acknowledge":true},"com.myapp.mytopic1",["Hello, world!"]]""",
        Message.Publish(
            239714735,
            "com.myapp.mytopic1",
            buildJsonArray { add("Hello, world!") },
            null,
            buildJsonObject { put("acknowledge", true) })
    ),

    /**
     * [PUBLISH, Request|id, Options|dict, Topic|uri, Arguments|list, ArgumentsKw|dict]
     */
    PUBLISH_FULL_ARGS(
        """[16,239714735,{"acknowledge":true},"com.myapp.mytopic1",[],{"color":"orange","sizes":[23,42,7]}]""",
        Message.Publish(
            requestId = 239714735,
            topic = "com.myapp.mytopic1",
            arguments = emptyJsonArray(),
            argumentsKw = buildJsonObject {
                put("color", "orange")
                put("sizes", buildJsonArray {
                    add(23 as Number)
                    add(42 as Number)
                    add(7 as Number)
                })
            },
            options = buildJsonObject { put("acknowledge", true) })
    ),

    /**
     * [PUBLISH, Request|id, Options|dict, Topic|uri, Arguments|list, ArgumentsKw|dict]
     */
    PUBLISH_ONLY_KW(
        """[16,239714735,{"acknowledge":true},"com.myapp.mytopic1",[],{"color":"orange","sizes":[23,42,7]}]""",
        Message.Publish(
            requestId = 239714735,
            topic = "com.myapp.mytopic1",
            arguments = null,
            argumentsKw = buildJsonObject {
                put("color", "orange")
                put("sizes", buildJsonArray {
                    add(23 as Number)
                    add(42 as Number)
                    add(7 as Number)
                })
            },
            options = buildJsonObject { put("acknowledge", true) })
    ),

    /**
     * [PUBLISHED, PUBLISH.Request|id, Publication|id]
     */
    PUBLISHED(
        """[17,239714735,4429313566]""",
        Message.Published(requestId = 239714735, publicationId = 4429313566)
    ),

    /**
     * [PUBLISHED, PUBLISH.Request|id, Publication|id]
     */
    PUBLISHED2(
        """[17,239714736,4429313567]""",
        Message.Published(requestId = 239714736, publicationId = 4429313567)
    ),

    /**
     * [ERROR, PUBLISH, PUBLISH.Request|id, Details|dict, Error|uri]
     */
    PUBLISH_ERROR(
        """[8,16,239714735,{},"wamp.error.not_authorized"]""",
        Message.Error(
            requestId = 239714735,
            originalType = 16,
            wampErrorUri = "wamp.error.not_authorized"
        )
    ),

    /**
     * [EVENT, SUBSCRIBED.Subscription|id, PUBLISHED.Publication|id, Details|dict]
     */
    EVENT_NO_ARG(
        """[36,5512315355,4429313566,{}]""",
        Message.Event(
            subscriptionId = 5512315355,
            publicationId = 4429313566,
            details = emptyJsonObject(),
            arguments = null,
            argumentsKw = null
        )
    ),

    /**
     * [EVENT, SUBSCRIBED.Subscription|id, PUBLISHED.Publication|id, Details|dict]
     */
    EVENT_NO_ARG2(
        """[36,5512315356,4429313566,{}]""",
        Message.Event(
            subscriptionId = 5512315356,
            publicationId = 4429313566,
            details = emptyJsonObject(),
            arguments = null,
            argumentsKw = null
        )
    ),

    /**
     * [EVENT, SUBSCRIBED.Subscription|id, PUBLISHED.Publication|id, Details|dict, PUBLISH.Arguments|list]
     */
    EVENT_ONLY_ARRAY_ARG(
        """[36,5512315355,4429313566,{},["Hello, world!"]]""",
        Message.Event(
            subscriptionId = 5512315355,
            publicationId = 4429313566,
            details = emptyJsonObject(),
            arguments = buildJsonArray { add("Hello, world!") },
            argumentsKw = null
        )
    ),

    /**
     * [EVENT, SUBSCRIBED.Subscription|id, PUBLISHED.Publication|id, Details|dict, PUBLISH.Arguments|list, PUBLISH.ArgumentKw|dict]
     */
    EVENT_FULL_ARGS(
        """[36,5512315355,4429313566,{},[],{"color":"orange","sizes":[23,42,7]}]""",
        Message.Event(
            subscriptionId = 5512315355,
            publicationId = 4429313566,
            details = emptyJsonObject(),
            arguments = emptyJsonArray(),
            argumentsKw = buildJsonObject {
                put("color", "orange")
                put("sizes", buildJsonArray {
                    add(23 as Number)
                    add(42 as Number)
                    add(7 as Number)
                })
            }
        )
    ),

    /**
     * [REGISTER, Request|id, Options|dict, Procedure|uri]
     */
    REGISTER(
        """[64,25349185,{},"com.myapp.myprocedure1"]""",
        Message.Register(requestId = 25349185, procedureId = "com.myapp.myprocedure1")
    ),

    /**
     * [REGISTER, Request|id, Options|dict, Procedure|uri]
     */
    REGISTER2(
        """[64,25349186,{},"com.myapp.myprocedure1"]""",
        Message.Register(requestId = 25349186, procedureId = "com.myapp.myprocedure1")
    ),

    /**
     * [REGISTERED, REGISTER.Request|id, Registration|id]
     */
    REGISTERED(
        """[65,25349185,2103333224]""",
        Message.Registered(requestId = 25349185, registrationId = 2103333224)
    ),

    /**
     * [REGISTERED, REGISTER.Request|id, Registration|id]
     */
    REGISTERED2(
        """[65,25349186,2103333225]""",
        Message.Registered(requestId = 25349186, registrationId = 2103333225)
    ),

    /**
     * [ERROR, REGISTER, REGISTER.Request|id, Details|dict, Error|uri]
     */
    REGISTER_ERROR(
        """[8,64,25349185,{},"wamp.error.procedure_already_exists"]""",
        Message.Error(
            requestId = 25349185,
            originalType = 64,
            wampErrorUri = "wamp.error.procedure_already_exists"
        )
    ),

    /**
     * [UNREGISTER, Request|id, REGISTERED.Registration|id]
     */
    UNREGISTER(
        """[66,788923562,2103333224]""",
        Message.Unregister(requestId = 788923562, registrationId = 2103333224)
    ),

    /**
     * [UNREGISTER, Request|id, REGISTERED.Registration|id]
     */
    UNREGISTER2(
        """[66,788923563,2103333225]""",
        Message.Unregister(requestId = 788923563, registrationId = 2103333225)
    ),

    /**
     * [UNREGISTERED, UNREGISTER.Request|id]
     */
    UNREGISTERED(
        """[67,788923562]""",
        Message.Unregistered(requestId = 788923562)
    ),

    /**
     * [UNREGISTERED, UNREGISTER.Request|id]
     */
    UNREGISTERED2(
        """[67,788923563]""",
        Message.Unregistered(requestId = 788923563)
    ),

    /**
     * [ERROR, UNREGISTER, UNREGISTER.Request|id, Details|dict, Error|uri]
     */
    UNREGISTER_ERROR(
        """[8,66,788923562,{},"wamp.error.no_such_registration"]""",
        Message.Error(
            requestId = 788923562,
            originalType = 66,
            wampErrorUri = "wamp.error.no_such_registration"
        )
    ),

    /**
     * [CALL, Request|id, Options|dict, Procedure|uri]
     */
    CALL_NO_ARG(
        """[48,7814135,{},"com.myapp.ping"]""",
        Message.Call(
            requestId = 7814135,
            procedureId = "com.myapp.ping",
            arguments = null,
            argumentsKw = null
        )
    ),
    CALL_NO_ARG2(
        """[48,7814136,{},"com.myapp.ping"]""",
        Message.Call(
            requestId = 7814136,
            procedureId = "com.myapp.ping",
            arguments = null,
            argumentsKw = null
        )
    ),

    /**
     * [CALL, Request|id, Options|dict, Procedure|uri, Arguments|list]
     */
    CALL_ONLY_ARRAY_ARG(
        """[48,7814135,{},"com.myapp.echo",["Hello, world!"]]""",
        Message.Call(
            requestId = 7814135,
            procedureId = "com.myapp.echo",
            arguments = buildJsonArray { add("Hello, world!") },
            argumentsKw = null
        )
    ),

    /**
     * [CALL, Request|id, Options|dict, Procedure|uri, Arguments|list, ArgumentsKw|dict]
     */
    CALL_FULL_ARGS(
        """[48,7814135,{},"com.myapp.user.new",["johnny"],{"firstname":"John","surname":"Doe"}]""",
        Message.Call(
            requestId = 7814135,
            procedureId = "com.myapp.user.new",
            arguments = buildJsonArray { add("johnny") },
            argumentsKw = buildJsonObject {
                put("firstname", "John")
                put("surname", "Doe")
            }
        )
    ),

    /**
     * [CALL, Request|id, Options|dict, Procedure|uri, Arguments|list, ArgumentsKw|dict]
     */
    CALL_ONLY_KW(
        """[48,7814135,{},"com.myapp.user.new",[],{"firstname":"John","surname":"Doe"}]""",
        Message.Call(
            requestId = 7814135,
            procedureId = "com.myapp.user.new",
            arguments = null,
            argumentsKw = buildJsonObject {
                put("firstname", "John")
                put("surname", "Doe")
            }
        )
    ),

    /**
     * [INVOCATION, Request|id, REGISTERED.Registration|id, Details|dict]
     */
    INVOCATION_NO_ARG(
        """[68,6131533,2103333224,{}]""",
        Message.Invocation(
            requestId = 6131533,
            registrationId = 2103333224,
            arguments = null,
            argumentsKw = null
        )
    ),

    /**
     * [INVOCATION, Request|id, REGISTERED.Registration|id, Details|dict]
     */
    INVOCATION_NO_ARG2(
        """[68,6131534,2103333225,{}]""",
        Message.Invocation(
            requestId = 6131534,
            registrationId = 2103333225,
            arguments = null,
            argumentsKw = null
        )
    ),

    /**
     * [INVOCATION, Request|id, REGISTERED.Registration|id, Details|dict, CALL.Arguments|list]
     */
    INVOCATION_ONLY_ARRAY_ARG(
        """[68,6131533,2103333224,{},["Hello, world!"]]""",
        Message.Invocation(
            requestId = 6131533,
            registrationId = 2103333224,
            arguments = buildJsonArray { add("Hello, world!") },
            argumentsKw = null
        )
    ),

    /**
     * [INVOCATION, Request|id, REGISTERED.Registration|id, Details|dict, CALL.Arguments|list, CALL.ArgumentsKw|dict]]
     */
    INVOCATION_FULL_ARGS(
        """[68,6131533,2103333224,{},["johnny"],{"firstname":"John","surname":"Doe"}]""",
        Message.Invocation(
            requestId = 6131533,
            registrationId = 2103333224,
            arguments = buildJsonArray { add("johnny") },
            argumentsKw = buildJsonObject {
                put("firstname", "John")
                put("surname", "Doe")
            }
        )
    ),

    /**
     * [YIELD, INVOCATION.Request|id, Options|dict]
     */
    YIELD_NO_ARG(
        """[70,6131533,{}]""",
        Message.Yield(
            requestId = 6131533,
            arguments = null,
            argumentsKw = null
        )
    ),

    /**
     * [YIELD, INVOCATION.Request|id, Options|dict, Arguments|list]
     */
    YIELD_ONLY_ARRAY_ARG(
        """[70,6131533,{},["Hello, world!"]]""",
        Message.Yield(
            requestId = 6131533,
            arguments = buildJsonArray { add("Hello, world!") },
            argumentsKw = null
        )
    ),

    /**
     * [YIELD, INVOCATION.Request|id, Options|dict, Arguments|list, ArgumentsKw|dict]
     */
    YIELD_FULL_ARGS(
        """[70,6131533,{},[],{"userid":123,"karma":10}]""",
        Message.Yield(
            requestId = 6131533,
            arguments = emptyJsonArray(),
            argumentsKw = buildJsonObject {
                put("userid", 123)
                put("karma", 10)
            }
        )
    ),

    /**
     * [YIELD, INVOCATION.Request|id, Options|dict, Arguments|list, ArgumentsKw|dict]
     */
    YIELD_ONLY_KW(
        """[70,6131533,{},[],{"userid":123,"karma":10}]""",
        Message.Yield(
            requestId = 6131533,
            arguments = null,
            argumentsKw = buildJsonObject {
                put("userid", 123)
                put("karma", 10)
            }
        )
    ),

    /**
     * [RESULT, CALL.Request|id, Details|dict]
     */
    RESULT_NO_ARG(
        """[50,7814135,{}]""",
        Message.Result(
            requestId = 7814135,
            arguments = null,
            argumentsKw = null
        )
    ),

    /**
     * [RESULT, CALL.Request|id, Details|dict]
     */
    RESULT_NO_ARG2(
        """[50,7814136,{}]""",
        Message.Result(
            requestId = 7814136,
            arguments = null,
            argumentsKw = null
        )
    ),

    /**
     * [RESULT, CALL.Request|id, Details|dict, YIELD.Arguments|list]
     */
    RESULT_ONLY_ARRAY_ARG(
        """[50,7814135,{},["Hello, world!"]]""",
        Message.Result(
            requestId = 7814135,
            arguments = buildJsonArray { add("Hello, world!") },
            argumentsKw = null
        )
    ),

    /**
     * [RESULT, CALL.Request|id, Details|dict, YIELD.Arguments|list, YIELD.ArgumentsKw|dict]
     */
    RESULT_FULL_ARGS(
        """[50,7814135,{},[],{"userid":123,"karma":10}]""",
        Message.Result(
            requestId = 7814135,
            arguments = emptyJsonArray(),
            argumentsKw = buildJsonObject {
                put("userid", 123)
                put("karma", 10)
            }
        )
    ),

    /**
     * [ERROR, INVOCATION, INVOCATION.Request|id, Details|dict, Error|uri]
     */
    INVOCATION_ERROR_NO_ARG(
        """[8,68,6131533,{},"com.myapp.error.object_write_protected"]""",
        Message.Error(
            requestId = 6131533,
            originalType = 68,
            wampErrorUri = "com.myapp.error.object_write_protected",
            arguments = null,
            argumentsKw = null
        )
    ),

    /**
     * [ERROR, INVOCATION, INVOCATION.Request|id, Details|dict, Error|uri, Arguments|list]
     */
    INVOCATION_ERROR_ONLY_ARRAY_ARG(
        """[8,68,6131533,{},"com.myapp.error.object_write_protected",["Object is write protected."]]""",
        Message.Error(
            requestId = 6131533,
            originalType = 68,
            wampErrorUri = "com.myapp.error.object_write_protected",
            arguments = buildJsonArray { add("Object is write protected.") },
            argumentsKw = null
        )
    ),

    /**
     * [ERROR, INVOCATION, INVOCATION.Request|id, Details|dict, Error|uri, Arguments|list, ArgumentsKw|dict]
     */
    INVOCATION_ERROR_FULL_ARGS(
        """[8,68,6131533,{},"com.myapp.error.object_write_protected",["Object is write protected."],{"severity":3}]""",
        Message.Error(
            requestId = 6131533,
            originalType = 68,
            wampErrorUri = "com.myapp.error.object_write_protected",
            arguments = buildJsonArray { add("Object is write protected.") },
            argumentsKw = buildJsonObject { put("severity", 3) }
        )
    ),

    /**
     * [ERROR, CALL, CALL.Request|id, Details|dict, Error|uri]
     */
    CALL_ERROR_NO_ARG(
        """[8,48,7814135,{},"com.myapp.error.object_write_protected"]""",
        Message.Error(
            requestId = 7814135,
            originalType = 48,
            wampErrorUri = "com.myapp.error.object_write_protected",
            arguments = null,
            argumentsKw = null
        )
    ),

    /**
     * [ERROR, CALL, CALL.Request|id, Details|dict, Error|uri, Arguments|list]
     */
    CALL_ERROR_ONLY_ARRAY_ARG(
        """[8,48,7814135,{},"com.myapp.error.object_write_protected",["Object is write protected."]]""",
        Message.Error(
            requestId = 7814135,
            originalType = 48,
            wampErrorUri = "com.myapp.error.object_write_protected",
            arguments = buildJsonArray { add("Object is write protected.") },
            argumentsKw = null
        )
    ),

    /**
     * [ERROR, CALL, CALL.Request|id, Details|dict, Error|uri, Arguments|list, ArgumentsKw|dict]
     */
    CALL_ERROR_FULL_ARGS(
        """[8,48,7814135,{},"com.myapp.error.object_write_protected",["Object is write protected."],{"severity":3}]""",
        Message.Error(
            requestId = 7814135,
            originalType = 48,
            wampErrorUri = "com.myapp.error.object_write_protected",
            arguments = buildJsonArray { add("Object is write protected.") },
            argumentsKw = buildJsonObject { put("severity", 3) }
        )
    )
}