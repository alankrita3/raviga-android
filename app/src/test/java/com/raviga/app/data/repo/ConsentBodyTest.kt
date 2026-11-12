package com.raviga.app.data.repo

import com.raviga.app.data.api.ConsentRequest
import com.raviga.app.data.api.LegalConfig
import com.raviga.app.ui.onboarding.ConsentCopy
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** R-107 (her decision; contract v0.11.4): one tap on "Before you start" records the terms and AI processing together. */
class ConsentBodyTest {
    private val json = Json { explicitNulls = false; encodeDefaults = true }
    private val legal = LegalConfig(termsVersion = "2026-11-04", privacyVersion = "2026-11-04")
    private fun body(r: ConsentRequest) = json.encodeToString(ConsentRequest.serializer(), r)

    @Test fun theFirstTapSendsTermsPrivacyAndAi() {
        assertEquals(
            """{"termsVersion":"2026-11-04","privacyVersion":"2026-11-04","aiProcessing":true}""",
            body(consentBody(legal, ai = true, analytics = null)),
        )
    }

    @Test fun analyticsOnlyWhenSwitchedOn() {
        assertEquals(
            """{"termsVersion":"2026-11-04","privacyVersion":"2026-11-04","aiProcessing":true,"analytics":true}""",
            body(consentBody(legal, ai = true, analytics = true)),
        )
    }

    @Test fun anOlderAgreementNeverSendsAiOnItsOwn() {
        assertEquals("""{"termsVersion":"2026-11-04","privacyVersion":"2026-11-04"}""", body(consentBody(legal, ai = false, analytics = null)))
    }

    @Test fun theSheetSaysWhatTheTapDoes() {
        assertEquals("Allow AI and continue", ConsentCopy.BUTTON)
        assertEquals(
            "By continuing you agree to the Terms of Use and Privacy Policy, and allow AI processing as described above.",
            ConsentCopy.CAPTION,
        )
        // Her static text, verbatim as iOS (not /config.ai.providers).
        assertEquals("AI processing", ConsentCopy.AI_TITLE)
        // R-121 (her rule 2026-11-12, as iOS): "What we keep." is gone; retention lives in the policy.
        assertEquals(listOf("What is sent.", "Who processes it.", "Your choice."), ConsentCopy.AI_LINES.map { it.first })
        assertEquals("Anthropic's Claude, under its business terms. Never used to train AI.", ConsentCopy.AI_LINES[1].second)
        assertEquals(
            "Raviga can't work without it. You can withdraw your permission at any time by deleting your account in Settings.",
            ConsentCopy.AI_LINES.last().second,
        )
    }
}
