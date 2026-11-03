package com.raviga.app.data.repo

import com.raviga.app.data.api.CreditsResponse
import com.raviga.app.data.api.LedgerEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** QA J5 (as iOS's Show more): older history pages go below, without repeats, until there is no cursor. */
class LedgerPagingTest {
    private fun rows(vararg ids: String) = ids.map { LedgerEntry(id = it, credits = 1) }

    @Test fun anOlderPageGoesBelowWithoutRepeats() {
        val first = CreditsResponse(balance = 188, ledger = rows("l9", "l8", "l7"), nextCursor = "c1")
        val older = CreditsResponse(balance = 189, ledger = rows("l7", "l6", "l5"), nextCursor = "c2")
        val merged = CreditsRepository.withOlder(first, older)
        assertEquals(listOf("l9", "l8", "l7", "l6", "l5"), merged.ledger.map { it.id })
        assertEquals("the cursor moves on", "c2", merged.nextCursor)
        assertEquals("the newer read of the balance", 189, merged.balance)
    }

    @Test fun theLastPageEndsShowMore() {
        val merged = CreditsRepository.withOlder(
            CreditsResponse(ledger = rows("l2"), nextCursor = "c1"),
            CreditsResponse(ledger = rows("l1"), nextCursor = null),
        )
        assertNull(merged.nextCursor)
        assertEquals(listOf("l2", "l1"), merged.ledger.map { it.id })
    }
}
