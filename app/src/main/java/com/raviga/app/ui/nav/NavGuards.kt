package com.raviga.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/**
 * Review #17/#18 (Android): whether this screen is still the top of the back stack. Navigating only then means a second
 * tap during the transition, or an answer that lands after Back, does nothing: it used to push a second Home (Welcome's
 * Start, the notification prompt, a Home row) or pop the screen underneath (Settings under How Raviga works, Capture or
 * the brief under the AI consent).
 */
@Composable
fun NavController.rememberIsOnTop(): () -> Boolean {
    val entry = LocalLifecycleOwner.current as? NavBackStackEntry
    return remember(this, entry) { { NavGuard.isOnTop(entry?.id, currentBackStackEntry?.id) } }
}

object NavGuard {
    /** Outside a NavHost (no entry) there is nothing to guard. */
    fun isOnTop(thisEntryId: String?, topEntryId: String?): Boolean = thisEntryId == null || thisEntryId == topEntryId
}
