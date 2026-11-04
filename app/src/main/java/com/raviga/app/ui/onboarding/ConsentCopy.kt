package com.raviga.app.ui.onboarding

/** R-107 (her decisions via the PM, 2026-11-04): the words of the one "Before you start" sheet, as iOS. */
object ConsentCopy {
    const val BUTTON = "Allow AI and continue"
    const val CAPTION =
        "By continuing you agree to the Terms of Use and Privacy Policy, and allow AI processing as described above."

    // Until iOS sends the section verbatim: the approved lines of the old AI sheet.
    const val AI_TITLE = "AI processing"
    fun aiBody(providers: List<String>): String =
        "Only the text of your notes and brief is sent, to write and price it. Never your recordings or files. We don't store it." +
            providers.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "\n") .orEmpty()
}
