package com.raviga.app.ui.nav

object Routes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val TERMS = "terms"

    /** R-056: the first consent, shown at the first Type, Speak, Upload, Restore or Buy credits; then on to [next]. */
    const val CONSENT_FIRST = "consent?next={next}"
    fun consentFirst(next: String) = "consent?next=" + java.net.URLEncoder.encode(next, "UTF-8")
    const val HOME = "home"
    const val AI_CONSENT = "ai_consent"
    const val UPGRADE = "upgrade"

    const val CAPTURE = "capture/{projectId}?mode={mode}&tab={tab}"
    /** [tab]: "speak", "type" or "upload", as picked in the describe chooser. */
    fun capture(projectId: String = "new", mode: String = "new", tab: String = "speak") = "capture/$projectId?mode=$mode&tab=$tab"

    const val DOCUMENT = "document/{projectId}?reveal={reveal}"
    fun document(projectId: String, reveal: Boolean = false) = "document/$projectId?reveal=$reveal"

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
