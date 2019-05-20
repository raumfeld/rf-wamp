package com.raumfeld.wamp

import kotlin.math.pow

expect fun getThreadSafeIdStorage(): MutableCollection<Long>

class IdGenerator(
    private val usedIds: MutableCollection<Long> = getThreadSafeIdStorage(),
    private val nextId: (Long) -> Long = { ACCEPTABLE_RANGE.random() }
) {
    companion object {
        private val ACCEPTABLE_RANGE = 1L..2.toDouble().pow(53).toLong()
    }

    private val sequence = generateSequence(0L) { nextId(it) }

    fun newId(): Long = sequence.first { !hasId(it) }.also { usedIds.add(it) }

    fun releaseId(id: Long) = usedIds.remove(id)

    private fun hasId(id: Long) = id in usedIds
}