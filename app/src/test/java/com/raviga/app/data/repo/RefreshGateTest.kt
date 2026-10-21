package com.raviga.app.data.repo

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshGateTest {

    private var clock = 0L
    private val gate = RefreshGate<Int>(freshForMs = 5_000, now = { clock })
    private var fetches = 0
    private suspend fun fetch(): Int {
        delay(100)
        return ++fetches
    }

    /** R-019: the start-up and the home screen asked together and cost three requests each. */
    @Test
    fun `concurrent non-forced refreshes share one fetch`() = runTest {
        val results = List(3) { async { gate.run(force = false) { fetch() } } }.awaitAll()
        assertEquals(listOf(1, 1, 1), results)
        assertEquals(1, fetches)
    }

    @Test
    fun `forced refresh always fetches`() = runTest {
        gate.run(force = false) { fetch() }
        assertEquals(2, gate.run(force = true) { fetch() })
        assertEquals(2, fetches)
    }

    @Test
    fun `a result older than the window is fetched again`() = runTest {
        gate.run(force = false) { fetch() }
        clock += 4_999
        assertEquals(1, gate.run(force = false) { fetch() })
        clock += 2
        assertEquals(2, gate.run(force = false) { fetch() })
    }

    @Test
    fun `a failed fetch is not reused and reset forgets`() = runTest {
        val failed = runCatching { gate.run(force = false) { error("offline") } }
        assertTrue(failed.isFailure)
        assertEquals(1, gate.run(force = false) { fetch() })
        gate.reset()
        assertEquals(2, gate.run(force = false) { fetch() })
    }
}
