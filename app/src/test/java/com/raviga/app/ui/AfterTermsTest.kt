package com.raviga.app.ui

import com.raviga.app.data.api.ApiException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R-149 (the PM): after the updated terms are agreed over the Quote screen, "Agree to the updated terms to continue."
 * goes and the refused quote is asked for again by itself, without a manual Try again.
 */
class AfterTermsTest {
    private fun refusal(vararg missing: String) = ApiException(
        ApiException.CONSENT_REQUIRED, 403, "Consent is required first.",
        details = buildJsonObject { put("missing", JsonArray(missing.map { JsonPrimitive(it) })) },
    )

    @Test fun theRefusedActionRunsOnceWhenTheTermsAreAgreed() {
        val after = AfterTerms()
        var quotes = 0
        assertTrue(after.refused(refusal("terms", "privacy")) { quotes++ })
        assertEquals(0, quotes)
        after.agreed()
        assertEquals("asked again by itself", 1, quotes)
        after.agreed()
        assertEquals("only once", 1, quotes)
    }

    @Test fun otherRefusalsAreNotKept() {
        val after = AfterTerms()
        var runs = 0
        assertFalse("AI consent is asked where it's needed", after.refused(refusal("aiProcessing")) { runs++ })
        assertFalse(after.refused(ApiException(ApiException.NETWORK, 0, "offline")) { runs++ })
        after.agreed()
        assertEquals(0, runs)
    }

    @Test fun theTermsLineGoesAndOtherLinesStay() {
        assertEquals(TERMS_REFUSED_LINE, refusal("terms").userLine())
        assertEquals("Agree to the updated terms to continue.", TERMS_REFUSED_LINE)
        assertNull(AfterTerms.clearedLine(TERMS_REFUSED_LINE))
        assertEquals("Couldn't reach Raviga.", AfterTerms.clearedLine("Couldn't reach Raviga."))
        assertNull(AfterTerms.clearedLine(null))
    }
}
