package com.raviga.app.ui.onboarding

/** R-107 (her decisions via the PM, 2026-11-04): the words of the one "Before you start" sheet, verbatim as iOS. */
object ConsentCopy {
    const val BUTTON = "Allow AI and continue"
    const val CAPTION =
        "By continuing you agree to the Terms of Use and Privacy Policy, and allow AI processing as described above."

    /** Her static text (not /config.ai.providers): a heading, then three lines with a lead-in. No switch. */
    const val AI_TITLE = "AI processing"
    val AI_LINES = listOf(
        "What is sent." to "Only text about your project. Never your recordings or files.",
        // R-121 (her rule 2026-11-12: no keep or delete words; the PM's pick, as iOS): retention lives in the policy.
        "Who processes it." to "Anthropic's Claude, under its business terms. Never used to train AI.",
        "Your choice." to "Raviga can't work without it. You can withdraw your permission at any time by deleting your account in Settings.",
    )
}
