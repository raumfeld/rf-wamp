# rf-wamp

Kotlin-based multi-platform [WAMP](https://wamp-proto.org/) client. Currently only Android is supported as target.
We aim to add iOS support later when the MPP framework has matured.

## Download

The library is published via [JitPack](https://jitpack.io/docs/ANDROID/), so you will need to add this repository to your Android project's *build.gradle*:
```groovy
repositories {
        maven { url "https://jitpack.io" }
}
```

### Core library

This contains just the core library. You will have to provide your own [WebSocketFactory](core/src/commonMain/kotlin/com/raumfeld/wamp/websocket/WebSocketFactory.kt) and [WebSocketDelegate](core/src/commonMain/kotlin/com/raumfeld/wamp/websocket/WebSocketDelegate.kt). 
```groovy
implementation 'com.github.raumfeld.rf-wamp:core:VERSION@aar'
```
### Android OkHttpExtensions

If you don't want to implement your own WebSocket delegates, you can also just use this one, powered by [OkHttp](https://github.com/square/okhttp).

```groovy
implementation 'com.github.raumfeld.rf-wamp:extensions-android-okhttp:VERSION@aar'
```

*Note:* You will also need to add the following due to OkHttp's use of [Static interface methods](https://github.com/square/okhttp/issues/4668)

```groovy
compileOptions {
       sourceCompatibility JavaVersion.VERSION_1_7 // set to 8 if you want to actually code against Java 8 stuff (not needed if you only use Kotlin)
       targetCompatibility JavaVersion.VERSION_1_8 // needed for newer OkHttp versions
}
```

## Examples

Check out this runnable example to see rf-wamp in action: [Example App](example-android/src/main/java/com/raumfeld/wamp/examples/android/MainActivity.kt).
In order to use the example app properly, you will need to have a WAMP router running that the app can connect to.

### Creating a WAMP session 

```kotlin
var session: WampSession? = null

fun createSession(url: String, sessionListener: WampSession.WampSessionListener) {
    WampClient(OkHttpWebSocketFactory()).createSession(
        uri = url,
        sessionListener = sessionListener
    ) { result ->
        result.onSuccess {
            println("Session created successfully")
            session = it
        }
        result.onFailure {
            println("Could not create session: ${it.message}")
            session = null
        }
    }
}

suspend fun realm() {
    session?.join("myrealm")
    session?.leave() // underlying WebSocket stays open, can join again afterwards
    session?.shutdown() // session now terminally shut down, WebSocket closed, cannot join again
}
```

### Publishing/Subscribing

```kotlin

suspend fun subscribe() {
    val subscription = session?.subscribe("mytopic") ?: return
    subscription.consumeEach {
        when (it) {
            is SubscriptionEstablished -> println("Subscription established. SubscriptionId = ${it.subscriptionId}")
            is Payload                 -> println("Received event: Arguments = ${it.arguments} ArgumentsKw = ${it.argumentsKw}")
            SubscriptionClosed         -> println("Subscription has ended.")
            is SubscriptionFailed      -> println("Subscription failed with ${it.errorUri}")
            is UnsubscriptionFailed    -> println("Unsubscription failed with ${it.errorUri}")
        }
    }
    println("Subscription lifetime ended.")
}

suspend fun unsubscribe(subscriptionId: SubscriptionId) {
    session?.unsubscribe(subscriptionId) // if this fails for some reason, you will get an UnsubscriptionFailed on the original subscription channel
}

suspend fun publish() {
    // if acknowledge is `false` you will not be notified about the delivery and the publication channel is closed immediately
    val publication =
        session?.publish(
            topic = "mytopic",
            arguments = jsonArray { +"some arg" }, // can be null
            argumentsKw = json { "answer" to 42 }, // can be null
            acknowledge = true // `false` for "fire-and-forget"
        ) ?: return
    publication.consumeEach {
        when (it) {
            is PublicationSucceeded -> println("Event successfully published")
            is PublicationFailed    -> println("Event could not be published: ${it.errorUri}")
        }
    }
    println("Event was published (or not ;))")
}
```

### RPC

```kotlin
suspend fun registerFunction() {
    val registration = session?.register("myfunction") ?: return
    registration.consumeEach {
        when (it) {
            is ProcedureRegistered  -> println("Procedure registered successfully! RegistrationId = ${it.registrationId}")
            is Invocation           -> {
                println("Someone wants to call us. Arguments = ${it.arguments} ArgumentsKw = ${it.argumentsKw}")
                // don't forget to actually respond!
                it.returnResult(CallSucceeded(arguments = jsonArray { +"It worked!" }))
            }
            ProcedureUnregistered   -> println("Procedure unregistered successfully!")
            is RegistrationFailed   -> println("Registration failed with ${it.errorUri}")
            is UnregistrationFailed -> println("Unregisration failed with ${it.errorUri}")
        }
    }
    println("Registration lifetime ended.")
}

suspend fun unregisterFunction(registrationId: RegistrationId) {
    session?.unregister(registrationId) // if this fails for some reason, you will get an UnregistrationFailed on the original registration channel
}

suspend fun callFunction() {
    val call = session?.call(
        "myfunction",
        arguments = jsonArray { +"some parameter" },
        argumentsKw = json { "additional" to "parameter" }) ?: return
    call.consumeEach {
        when (it) {
            is CallSucceeded -> println("Call succeeded!")
            is CallFailed    -> println("Call failed!")
        }
    }
    println("Call finished.")
}
```

### Running a local WAMP router

Follow these instructions: [Starting a Crossbar.io Router](https://crossbar.io/docs/Getting-Started/#starting-a-crossbar-io-router)

## Tests

Unit tests can be run by executing the `testReleaseUnitTest` gradle task.
There is also an integration test that runs against a real WAMP router. If you want to
run it, make sure to change the `LOCAL_WAMP_ROUTER` variable in [ConcurrencyTests](core/src/commonTest/kotlin/com/raumfeld/wamp/session/ConcurrencyTests.kt) so it points to a running router.

## License

This project is licensed under the MIT License - see the [LICENSE.txt](LICENSE.txt) file for details

