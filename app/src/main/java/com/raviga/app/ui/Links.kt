package com.raviga.app.ui

import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.raviga.app.ui.theme.Ink

/**
 * R-092 #23 (as iOS 1da76d6): only web addresses open. "https://..." and "http://..." as they are; a bare host
 * ("legal.ravigaapps.com/terms") gets https; anything else (intent:, file:, javascript:...) is not opened.
 */
fun webUrl(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null
    val scheme = trimmed.substringBefore(':', "").lowercase()
    return when {
        scheme == "https" || scheme == "http" -> trimmed.takeIf { it.substringAfter("://", "").isNotBlank() }
        "://" !in trimmed && Regex("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+(/.*)?$").matches(trimmed) -> "https://$trimmed"
        else -> null
    }
}

/** Opens a web address in an in-app browser tab (paper toolbar), falling back to the system browser. */
fun openLink(context: Context, url: String) {
    val uri = webUrl(url)?.toUri() ?: return
    try {
        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(Ink.paper.value.toInt() and 0xFFFFFF or (0xFF shl 24))
            .build()
        CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(params)
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

fun emailLink(context: Context, to: String, subject: String = "") {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:$to".toUri()).apply {
        if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    runCatching { context.startActivity(intent) }
}
