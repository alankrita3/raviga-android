package com.raviga.app.ui

import com.raviga.app.data.api.AiEdits
import com.raviga.app.data.api.AiEditsConfig
import com.raviga.app.data.api.ApiException
import com.raviga.app.ui.document.AiEditsCopy
import com.raviga.app.ui.document.EditPackAsk
import com.raviga.app.ui.onboarding.welcomeSteps
import com.raviga.app.ui.status.StatusCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R-124 (her decisions 2026-11-12): v0.16.0's AI edits in the app's words, and the 11-12 Welcome copy. */
class AiEditsCopyTest {
    private val c = AiEditsConfig()

    @Test fun theCountLine() {
        assertEquals("3 of 5 free AI edits left", AiEditsCopy.countLine(AiEdits(3, true), c))
        assertEquals("1 of 5 free AI edits left", AiEditsCopy.countLine(AiEdits(1, true), c))
        assertEquals("4 AI edits left", AiEditsCopy.countLine(AiEdits(4, false), c))
        assertEquals("1 AI edit left", AiEditsCopy.countLine(AiEdits(1, false), c))
        assertEquals("5 more AI edits cost 1/12 credit", AiEditsCopy.countLine(AiEdits(0, true), c))
        assertNull("not known yet: no line", AiEditsCopy.countLine(null, c))
    }

    @Test fun theConfirm() {
        assertEquals("5 more AI edits for 1/12 credit?", AiEditsCopy.confirmTitle(c))
        assertEquals("Buy 5 AI edits", AiEditsCopy.buyLabel(c))
        assertEquals("Typing your own changes is always free.", AiEditsCopy.TYPING_IS_FREE)
    }

    @Test fun whatARefusalAsks() {
        val words = "You've used this brief's 5 free AI edits. 5 more cost a twelfth of a credit."
        assertEquals(EditPackAsk.Confirm(words), EditPackAsk.from(ApiException(ApiException.AI_EDIT_PACK_REQUIRED, 402, words), buying = false))
        val short = "You need a twelfth of a credit for 5 more AI edits. Buy credits to continue."
        assertEquals(EditPackAsk.NeedCredits(short), EditPackAsk.from(ApiException(ApiException.INSUFFICIENT_CREDITS, 402, short), buying = true))
        assertNull("only a pack's price is this sheet", EditPackAsk.from(ApiException(ApiException.INSUFFICIENT_CREDITS, 402, short), buying = false))
        assertNull(EditPackAsk.from(ApiException(ApiException.AI_UNAVAILABLE, 503, "Busy"), buying = true))
        // A bare 402 without the server's sentence: ours, the same words.
        assertEquals(EditPackAsk.Confirm(words), EditPackAsk.from(ApiException(ApiException.AI_EDIT_PACK_REQUIRED, 402, "Request failed (402)"), buying = false))
        assertEquals(words, StatusCopy.errorLine(ApiException.AI_EDIT_PACK_REQUIRED, words))
    }

    @Test fun welcomeSaysThePriceAndTheStores() {
        assertEquals("See the price first.", welcomeSteps[1].title)
        assertEquals(
            "You see the price as soon as your brief is ready. Pay only if you want us to build your app and make it live.",
            welcomeSteps[1].body,
        )
        assertEquals("We make it live.", welcomeSteps[2].title)
        assertEquals("Your app, in your own App Store and Google Play accounts.", welcomeSteps[2].small)
    }
}
