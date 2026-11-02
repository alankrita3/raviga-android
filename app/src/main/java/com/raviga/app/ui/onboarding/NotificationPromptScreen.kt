package com.raviga.app.ui.onboarding

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.BottomBar
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.TertiaryButton
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType

/** R-074: whether the end of onboarding shows "Stay in the loop" before Android's own notification prompt. */
object NotificationAsk {
    /** Android 13 (API 33) is the first to ask; before it, notifications are on without a prompt. */
    const val FIRST_SDK_THAT_ASKS = 33

    /** Once only, and never when the answer is already settled (granted, or asked before here or in Settings). */
    fun shouldAsk(sdk: Int, granted: Boolean, askedBefore: Boolean): Boolean =
        sdk >= FIRST_SDK_THAT_ASKS && !granted && !askedBefore
}

/**
 * R-074 (founder, 2026-11-02; same on iOS): one screen right after onboarding's "Start" or "Skip", then [next].
 * "Allow notifications" shows Android's prompt; "Not now" (or back) goes on without it, and Settings > Notifications
 * turns them on later. Marked as asked as soon as it shows, so it never shows twice.
 */
@Composable
fun NotificationPromptScreen(nav: NavController, next: String) {
    val container = LocalAppContainer.current
    val goOn: () -> Unit = { nav.navigate(next) { popUpTo(Routes.NOTIFY_PROMPT) { inclusive = true } } }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { goOn() }
    LaunchedEffect(Unit) { container.prefs.setNotificationsAsked() }
    BackHandler { goOn() }

    ScreenScaffold(
        bottomBar = {
            BottomBar {
                PrimaryButton("Allow notifications", onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) })
                Spacer(Modifier.height(4.dp))
                TertiaryButton("Not now", onClick = goOn)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Ra.gutter)) {
            Spacer(Modifier.height(48.dp))
            Text("Stay in the loop", style = RaType.title, color = Ink.ink)
            Spacer(Modifier.height(12.dp))
            Text(
                "We'll let you know when your quote is ready, when we need your input, and when your project is delivered.",
                style = RaType.body,
                color = Ink.graphite,
            )
        }
    }
}
