package com.raumfeld.wamp.protocol

import kotlinx.serialization.json.json
import kotlinx.serialization.json.jsonArray

/**
 * The examples are taken from https://wamp-proto.org/_static/gen/wamp_latest.html#messages
 */
internal enum class ExampleMessage(val messageJson: String, val message: Message) {
    /**
     * [HELLO, Realm|uri, Details|dict]
     */
    HELLO(
        """[1,"somerealm",{"roles":{"publisher":{},"subscriber":{},"caller":{},"callee":{}}}]""",
        Message.Hello("somerealm", json {
            "roles" to json {
                "publisher" to emptyJsonObject()
                "subscriber" to emptyJsonObject()
                "caller" to emptyJsonObject()
                "callee" to emptyJsonObject()
            }
        })
    ),
    /**
     * [WELCOME, Session|id, Details|dict]
     */
    WELCOME(
        """[2,9129137332,{"roles":{"broker":{}}}]""",
        Message.Welcome(9129137332, json { "roles" to json { "broker" to emptyJsonObject() } })
    ),
    /**
     * [ABORT, Details|dict, Reason|uri]
     */
    ABORT_REALM_DOES_NOT_EXIST(
        """[3,{"message":"The realm does not exist."},"wamp.error.no_such_realm"]""",
        Message.Abort(json { "message" to "The realm does not exist." }, "wamp.error.no_such_realm")
    ),
    /**
     * [ABORT, Details|dict, Reason|uri]
     */
    ABORT_SHUTDOWN(
        """[3,{},"wamp.close.system_shutdown"]""",
        Message.Abort(reason = "wamp.close.system_shutdown")
    ),
    /**
     * [GOODBYE, Details|dict, Reason|uri]
     */
    GOODBYE_SHUTDOWN_WITH_MESSAGE(
        """[6,{"message":"The host is shutting down now."},"wamp.close.system_shutdown"]""",
        Message.Goodbye(json { "message" to "The host is shutting down now." }, "wamp.close.system_shutdown")
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
     * [SUBSCRIBED, SUBSCRIBE.Request|id, Subscription|id]
     */
    SUBSCRIBED(
        """[33,713845233,5512315355]""",
        Message.Subscribed(requestId = 713845233, subscriptionId = 5512315355)
    ),
    /**
     * [ERROR, SUBSCRIBE, SUBSCRIBE.Request|id, Details|dict, Error|uri]
     */
    SUBSCRIBE_ERROR(
        """[8,32,713845233,{},"wamp.error.not_authorized"]""",
        Message.Error(requestId = 713845233, originalType = 32, wampErrorUri = "wamp.error.not_authorized")
    ),
    /**
     * [UNSUBSCRIBE, Request|id, SUBSCRIBED.Subscription|id]
     */
    UNSUBSCRIBE(
        """[34,85346237,5512315355]""",
        Message.Unsubscribe(requestId = 85346237, subscriptionId = 5512315355)
    ),
    /**
     * [UNSUBSCRIBED, UNSUBSCRIBE.Request|id]
     */
    UNSUBSCRIBED(
        """[35,85346237]""",
        Message.Unsubscribed(requestId = 85346237)
    ),
    /**
     * [UNSUBSCRIBE, Request|id, SUBSCRIBED.Subscription|id]
     */
    UNSUBSCRIBE_ERROR(
        """[8,34,85346237,{},"wamp.error.no_such_subscription"]""",
        Message.Error(requestId = 85346237, originalType = 34, wampErrorUri = "wamp.error.no_such_subscription")
    ),
    /**
     * [PUBLISH, Request|id, Options|dict, Topic|uri]
     */
    PUBLISH_NO_ARG(
        """[16,239714735,{},"com.myapp.mytopic1"]""",
        Message.Publish(239714735, "com.myapp.mytopic1", null, null)
    ),
    /**
     * [PUBLISH, Request|id, Options|dict, Topic|uri, Arguments|list]
     */
    PUBLISH_ONLY_ARRAY_ARG(
        """[16,239714735,{},"com.myapp.mytopic1",["Hello, world!"]]""",
        Message.Publish(239714735, "com.myapp.mytopic1", jsonArray { +"Hello, world!" }, null)
    ),
    /**
     * [PUBLISH, Request|id, Options|dict, Topic|uri, Arguments|list, ArgumentsKw|dict]
     */
    PUBLISH_FULL_ARGS(
        """[16,239714735,{},"com.myapp.mytopic1",[],{"color":"orange","sizes":[23,42,7]}]""",
        Message.Publish(
            requestId = 239714735,
            topic = "com.myapp.mytopic1",
            arguments = emptyJsonArray(),
            argumentsKw = json {
                "color" to "orange"
                "sizes" to jsonArray {
                    +(23 as Number)
                    +(42 as Number)
                    +(7 as Number)
                }
            })
    ),
    /**
     * [PUBLISHED, PUBLISH.Request|id, Publication|id]
     */
    PUBLISHED(
        """[17,239714735,4429313566]""",
        Message.Published(requestId = 239714735, publicationId = 4429313566)
    ),
    /**
     * [ERROR, PUBLISH, PUBLISH.Request|id, Details|dict, Error|uri]
     */
    PUBLISH_ERROR(
        """[8,16,239714735,{},"wamp.error.not_authorized"]""",
        Message.Error(requestId = 239714735, originalType = 16, wampErrorUri = "wamp.error.not_authorized")
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
     * [EVENT, SUBSCRIBED.Subscription|id, PUBLISHED.Publication|id, Details|dict, PUBLISH.Arguments|list]
     */
    EVENT_ONLY_ARRAY_ARG(
        """[36,5512315355,4429313566,{},["Hello, world!"]]""",
        Message.Event(
            subscriptionId = 5512315355,
            publicationId = 4429313566,
            details = emptyJsonObject(),
            arguments = jsonArray { +"Hello, world!" },
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
            argumentsKw = json {
                "color" to "orange"
                "sizes" to jsonArray {
                    +(23 as Number)
                    +(42 as Number)
                    +(7 as Number)
                }
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
     * [REGISTERED, REGISTER.Request|id, Registration|id]
     */
    REGISTERED(
        """[65,25349185,2103333224]""",
        Message.Registered(requestId = 25349185, registrationId = 2103333224)
    ),
    /**
     * [ERROR, REGISTER, REGISTER.Request|id, Details|dict, Error|uri]
     */
    REGISTER_ERROR(
        """[8,64,25349185,{},"wamp.error.procedure_already_exists"]""",
        Message.Error(requestId = 25349185, originalType = 64, wampErrorUri = "wamp.error.procedure_already_exists")
    ),
    /**
     * [UNREGISTER, Request|id, REGISTERED.Registration|id]
     */
    UNREGISTER(
        """[66,788923562,2103333224]""",
        Message.Unregister(requestId = 788923562, registrationId = 2103333224)
    ),
    /**
     * [UNREGISTERED, UNREGISTER.Request|id]
     */
    UNREGISTERED(
        """[67,788923562]""",
        Message.Unregistered(requestId = 788923562)
    ),
    /**
     * [ERROR, UNREGISTER, UNREGISTER.Request|id, Details|dict, Error|uri]
     */
    UNREGISTER_ERROR(
        """[8,66,788923562,{},"wamp.error.no_such_registration"]""",
        Message.Error(requestId = 788923562, originalType = 66, wampErrorUri = "wamp.error.no_such_registration")
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
    /**
     * [CALL, Request|id, Options|dict, Procedure|uri, Arguments|list]
     */
    CALL_ONLY_ARRAY_ARG(
        """[48,7814135,{},"com.myapp.echo",["Hello, world!"]]""",
        Message.Call(
            requestId = 7814135,
            procedureId = "com.myapp.echo",
            arguments = jsonArray { +"Hello, world!" },
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
            arguments = jsonArray { +"johnny" },
            argumentsKw = json {
                "firstname" to "John"
                "surname" to "Doe"
            }
        )
    ),
    /**
     * [INVOCATION, Request|id, REGISTERED.Registration|id, Details|dict]
     */
    INVOCATION_NO_ARG(
        """[68,6131533,9823526,{}]""",
        Message.Invocation(
            requestId = 6131533,
            registrationId = 9823526,
            arguments = null,
            argumentsKw = null
        )
    ),
    /**
     * [INVOCATION, Request|id, REGISTERED.Registration|id, Details|dict, CALL.Arguments|list]
     */
    INVOCATION_ONLY_ARRAY_ARG(
        """[68,6131533,9823527,{},["Hello, world!"]]""",
        Message.Invocation(
            requestId = 6131533,
            registrationId = 9823527,
            arguments = jsonArray { +"Hello, world!" },
            argumentsKw = null
        )
    ),
    /**
     * [INVOCATION, Request|id, REGISTERED.Registration|id, Details|dict, CALL.Arguments|list, CALL.ArgumentsKw|dict]]
     */
    INVOCATION_FULL_ARGS(
        """[68,6131533,9823529,{},["johnny"],{"firstname":"John","surname":"Doe"}]""",
        Message.Invocation(
            requestId = 6131533,
            registrationId = 9823529,
            arguments = jsonArray { +"johnny" },
            argumentsKw = json {
                "firstname" to "John"
                "surname" to "Doe"
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
            arguments = jsonArray { +"Hello, world!" },
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
            argumentsKw = json {
                "userid" to 123
                "karma" to 10
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
     * [RESULT, CALL.Request|id, Details|dict, YIELD.Arguments|list]
     */
    RESULT_ONLY_ARRAY_ARG(
        """[50,7814135,{},["Hello, world!"]]""",
        Message.Result(
            requestId = 7814135,
            arguments = jsonArray { +"Hello, world!" },
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
            argumentsKw = json {
                "userid" to 123
                "karma" to 10
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
            arguments = jsonArray { +"Object is write protected." },
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
            arguments = jsonArray { +"Object is write protected." },
            argumentsKw = json { "severity" to 3 }
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
            arguments = jsonArray { +"Object is write protected." },
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
            arguments = jsonArray { +"Object is write protected." },
            argumentsKw = json { "severity" to 3 }
        )
    )
}