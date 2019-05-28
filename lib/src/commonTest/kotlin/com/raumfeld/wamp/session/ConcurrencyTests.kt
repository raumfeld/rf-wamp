package com.raumfeld.wamp.session

import com.raumfeld.wamp.IdGenerator
import com.raumfeld.wamp.runTest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.BeforeTest
import kotlin.test.Test

internal class ConcurrencyTests : BaseSessionTests() {


    @BeforeTest
    override fun setup() {
        super.setup()
        // use a proper one for these tests because it's impossible to mock the responses correctly in a concurrent scenario
        mockIdGenerator = IdGenerator()
    }

    @Test
    fun test() = runTest {
        failOnSessionAbort()
        joinRealm()
        repeat(5) {
            coroutineScope {
                launch { client() }
                launch { client() }
                launch { server() }
                launch { client() }
                launch { client() }
            }
        }
    }

    private suspend fun delayRandomly() = delay((0..100L).random())

    private suspend fun client() {
        // subscribe1
        delayRandomly()
        // subscribe2
        delayRandomly()
        // call1
        // register1
        delayRandomly()
        // unsubscribe1
        delayRandomly()
        // call1
        // call2
        delayRandomly()
        // publish
        delayRandomly()
        // unregister1
        // unsubscribe2
        delayRandomly()
        // subscribe1
    }

    private suspend fun server() {

    }

}