package com.raviga.app.ui.onboarding

/** R-107 (her decisions via the PM, 2026-11-04): the words of the one "Before you start" sheet, verbatim as iOS. */
object ConsentCopy {
    const val BUTTON = "Allow AI and continue"
    const val CAPTION =
        "By continuing you agree to the Terms of Use and Privacy Policy, and allow AI processing as described above."

    /** Her static text (not /config.ai.providers): a heading, then four lines with a lead-in. No switch. */
    const val AI_TITLE = "AI processing"
    val AI_LINES = listOf(
        "What is sent." to "Only text about your project. Never your recordings or files.",
        "Who processes it." to "Anthropic's Claude, under its business terms: deleted within 30 days, never used to train AI.",
        "What we keep." to "Nothing, until you submit a project. Then we keep your brief while it is being built, and delete it when the project closes.",
        "Your choice." to "Raviga can't work without it. You can withdraw your permission at any time by deleting your account in Settings.",
    )
}
