package com.raumfeld.wamp

import kotlin.test.Test
import kotlin.test.expect

class IdGeneratorTest {

    private fun withGenerator(ids: MutableCollection<Long>, block: IdGenerator.(resetCounter: () -> Unit) -> Unit) {
        var idCounter = 0L
        val resetCounter = { idCounter = 0 }
        with(IdGenerator(ids) {
            idCounter++
        }) {
            block(resetCounter)
        }
    }

    @Test
    fun mustNotReuseIds() = withGenerator(mutableListOf(0, 1, 2)) { resetCounter ->
        expect(3) { newId() }
        resetCounter() // nextId would yield 4 automatically otherwise, we want it to start at 0 again, though
        expect(4) { newId() }
    }

    @Test
    fun mustReleaseIds() = withGenerator(mutableListOf(0, 1, 2)) { resetCounter ->
        expect(3) { newId() }
        releaseId(3)
        resetCounter()
        expect(3) { newId() }
    }
}