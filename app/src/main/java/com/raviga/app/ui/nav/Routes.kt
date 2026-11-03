package com.raviga.app.ui.nav

object Routes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val TERMS = "terms"

    /** R-056: the first consent, shown at the first Type, Speak, Upload, Restore or Buy credits; then on to [next]. */
    const val CONSENT_FIRST = "consent?next={next}"
    /** R-074: "Stay in the loop" at the end of onboarding, then [next]. */
    const val NOTIFY_PROMPT = "notify?next={next}"
    fun notifyPrompt(next: String) = "notify?next=" + java.net.URLEncoder.encode(next, "UTF-8")
    fun consentFirst(next: String) = "consent?next=" + java.net.URLEncoder.encode(next, "UTF-8")
    const val HOME = "home"
    const val AI_CONSENT = "ai_consent"
    const val UPGRADE = "upgrade"

    const val CAPTURE = "capture/{projectId}?mode={mode}&tab={tab}"
    /** [tab]: "speak", "type" or "upload", as picked in the describe chooser. */
    fun capture(projectId: String = "new", mode: String = "new", tab: String = "speak") = "capture/$projectId?mode=$mode&tab=$tab"

    const val DOCUMENT = "document/{projectId}?reveal={reveal}"
    fun document(projectId: String, reveal: Boolean = false) = "document/$projectId?reveal=$reveal"

    /** Whether a back-stack entry (its route pattern and projectId argument) is this project's brief. */
    fun isBriefOf(route: String?, entryProjectId: String?, projectId: String) = route == DOCUMENT && entryProjectId == projectId

    /** The same for the project's status screen (R-092 #41: brief and status go back to each other, never stack). */
    fun isStatusOf(route: String?, entryProjectId: String?, projectId: String) = route == STATUS && entryProjectId == projectId

    /**
     * Review 2026-11-03 (Android): whether a notification for [projectId] may reuse the screen on top. Navigation's
     * launchSingleTop reuses the top entry for the same route pattern whatever the project, keeping its state: with
     * project A's status on top, a push for B turned A's screen into B's (Back then skipped A), and an open "Cancel
     * project?" or a typed revision request came along to B. Only the same project's screen is reused.
     */
    fun reuseTopFor(topProjectId: String?, projectId: String): Boolean = topProjectId == projectId

    const val SECTION = "section/{projectId}/{sectionId}"
    fun section(projectId: String, sectionId: String) = "section/$projectId/$sectionId"

    const val VERSIONS = "versions/{projectId}"
    fun versions(projectId: String) = "versions/$projectId"

    const val VERSION = "version/{projectId}/{version}"
    fun version(projectId: String, version: Int) = "version/$projectId/$version"

    const val QUOTE = "quote/{projectId}"
    fun quote(projectId: String) = "quote/$projectId"

    const val CONNECT_AWS = "connect_aws"

    const val STATUS = "status/{projectId}"
    fun status(projectId: String) = "status/$projectId"

    const val DELIVERY = "delivery/{projectId}"
    fun delivery(projectId: String) = "delivery/$projectId"
    const val GO_LIVE = "golive/{projectId}"
    fun goLive(projectId: String) = "golive/$projectId"

    const val CREDITS = "credits"
    /** The credits screen, going straight on to buy [productId] (after the first consent). */
    const val CREDITS_BUY = "credits/buy/{productId}"
    fun creditsBuy(productId: String) = "credits/buy/$productId"
    const val SETTINGS = "settings"
    const val DELIVERY_TARGETS = "settings/targets"
    const val PRIVACY = "settings/privacy"
    const val RECOVERY = "settings/recovery"
    const val RECOVERY_EMAIL = "settings/recovery-email"
    const val FONTS = "settings/fonts"
    const val NOTIFICATIONS = "settings/notifications"
    /** R-066: the five welcome pages again, from Settings. */
    const val HOW_IT_WORKS = "settings/how-it-works"

    /** Where a project opens from the list, by status. Drafts open the document screen, which handles "no brief yet". */
    /** A submitted project's own screen. Drafts (ids starting "ld_") open the brief. */
    fun forProject(projectId: String, status: String, hasDocument: Boolean = true): String = when (status) {
        "draft" -> document(projectId)
        "submitted", "changes_requested", "approved", "rejected", "cancelled" -> status(projectId)
        "delivered", "revision_requested", "accepted" -> delivery(projectId)
        else -> status(projectId)
    }
}
