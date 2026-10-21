package com.raviga.app.data.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/** Where events go: Firebase in the app, a list in tests. */
interface AnalyticsSink {
    fun setCollectionEnabled(enabled: Boolean)
    fun log(name: String, params: Map<String, Any>)
    fun reset()
}

/** Does nothing until google-services.json exists, like push. */
class FirebaseAnalyticsSink(private val context: Context) : AnalyticsSink {
    private val firebase: FirebaseAnalytics? by lazy {
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAnalytics.getInstance(context)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        firebase?.setAnalyticsCollectionEnabled(enabled)
    }

    override fun log(name: String, params: Map<String, Any>) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is Int -> bundle.putLong(key, value.toLong())
                is String -> bundle.putString(key, value)
            }
        }
        firebase?.logEvent(name, bundle)
    }

    override fun reset() {
        firebase?.resetAnalyticsData()
    }
}

/**
 * The usage funnel of contract v0.9 section 22. Logs only while the client has granted analytics
 * (collection ships off in the manifest). Names and parameters are fixed here and values are only
 * the contract's enums and counts: never text, titles, file names or any id of a person or project.
 */
class Analytics(private val sink: AnalyticsSink) {

    enum class InputKind(val wire: String) { VOICE("voice"), TEXT("text"), FILE("file") }

    @Volatile
    var enabled: Boolean = false
        private set

    /** Follows consent.analytics.granted; the first call always reaches the SDK. */
    @Synchronized
    fun setEnabled(on: Boolean) {
        if (applied && on == enabled) return
        enabled = on
        applied = true
        sink.setCollectionEnabled(on)
    }

    private var applied = false

    /** After account deletion: turn collection off and drop what the SDK holds. */
    fun clearAfterDeletion() {
        setEnabled(false)
        sink.reset()
    }

    fun onboardingComplete() = log("onboarding_complete")

    fun briefStarted(kind: InputKind) = log("brief_started", "input_kind" to kind.wire)

    fun briefGenerated(inputs: Int, sectionsFilled: Int) =
        log("brief_generated", "inputs" to inputs.coerceAtLeast(0), "sections_filled" to sectionsFilled.coerceIn(0, 12))

    fun quoteViewed(bracket: String?, credits: Int) = log("quote_viewed", "bracket" to bracket.ifKnown(BRACKETS), "credits" to credits)

    fun projectSubmitted(bracket: String?, credits: Int) = log("project_submitted", "bracket" to bracket.ifKnown(BRACKETS), "credits" to credits)

    fun creditsPurchased(productId: String, credits: Int) =
        log("credits_purchased", "product_id" to productId.ifKnown(PRODUCTS), "credits" to credits)

    fun deliveryAccepted(bracket: String?, revisionsUsed: Int) =
        log("delivery_accepted", "bracket" to bracket.ifKnown(BRACKETS), "revisions_used" to revisionsUsed.coerceAtLeast(0))

    private fun log(name: String, vararg params: Pair<String, Any?>) {
        if (!enabled) return
        sink.log(name, params.mapNotNull { (key, value) -> value?.let { key to it } }.toMap())
    }

    private fun String?.ifKnown(allowed: Set<String>): String? = this?.takeIf { it in allowed }

    companion object {
        val BRACKETS = setOf("micro", "starter", "standard", "pro", "enterprise")
        val PRODUCTS = setOf("credits_10", "credits_25", "credits_50", "credits_100")
    }
}
