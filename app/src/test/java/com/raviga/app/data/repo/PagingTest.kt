package com.raviga.app.data.repo

import com.raviga.app.data.api.CreditsResponse
import com.raviga.app.data.api.LedgerEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R-094 P2-9 (iOS review 2): a repeated cursor ends paging, and an id already listed isn't listed again. */
class PagingTest {
    @Test fun aRepeatedCursorEndsPaging() {
        assertNull(Paging.next(sent = "c1", answered = "c1"))
        assertNull(Paging.next(sent = "c1", answered = ""))
        assertNull(Paging.next(sent = "c1", answered = null))
        assertEquals("c2", Paging.next(sent = "c1", answered = "c2"))
        assertEquals("c1", Paging.next(sent = null, answered = "c1"))
    }

    @Test fun theLedgerDropsRepeatsWithinAPageToo() {
        val merged = CreditsRepository.withOlder(
            CreditsResponse(ledger = listOf(LedgerEntry("l3"), LedgerEntry("l2")), nextCursor = "c1"),
            CreditsResponse(ledger = listOf(LedgerEntry("l2"), LedgerEntry("l1"), LedgerEntry("l1")), nextCursor = "c1"),
        )
        assertEquals(listOf("l3", "l2", "l1"), merged.ledger.map { it.id })
        assertNull("the same cursor again: no more pages", merged.nextCursor)
    }
}
