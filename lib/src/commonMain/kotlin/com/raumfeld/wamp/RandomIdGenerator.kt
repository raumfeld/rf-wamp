package com.raumfeld.wamp

import kotlin.collections.*
import kotlin.math.pow

expect fun getThreadSafeIdStorage(): MutableCollection<Long>

object RandomIdGenerator {

    private val acceptableRange = 1L..2.toDouble().pow(53).toLong()

    private val usedIds = getThreadSafeIdStorage()

    private val sequence = generateSequence(0L) { acceptableRange.random() }

    fun newId(): Long = sequence.first { isValid(it) }.also { usedIds.add(it) }

    fun releaseId(id: Long) = usedIds.remove(id)

    private fun isValid(id: Long) = id in acceptableRange && !hasId(id)

    private fun hasId(id: Long) = id in usedIds
}