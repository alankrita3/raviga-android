package com.raviga.app.ui.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.raviga.app.data.api.ApiException
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.capture.CaptureScreen
import com.raviga.app.ui.components.ErrorState
import com.raviga.app.ui.components.LaunchScreen
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.credits.CreditsScreen
import com.raviga.app.ui.document.DocumentScreen
import com.raviga.app.ui.document.SectionEditorScreen
import com.raviga.app.ui.document.VersionPreviewScreen
import com.raviga.app.ui.document.VersionsScreen
import com.raviga.app.ui.home.HomeScreen
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.onboarding.TermsScreen
import com.raviga.app.ui.onboarding.NotificationPromptScreen
import com.raviga.app.ui.onboarding.WelcomeScreen
import com.raviga.app.ui.openLink
import com.raviga.app.ui.quote.ConnectAwsScreen
import com.raviga.app.ui.quote.QuoteScreen
import com.raviga.app.ui.settings.DeliveryTargetsScreen
import com.raviga.app.ui.settings.NotificationsScreen
import com.raviga.app.ui.settings.PrivacyScreen
import com.raviga.app.ui.settings.SettingsScreen
import com.raviga.app.ui.status.DeliveryScreen
import com.raviga.app.ui.status.StatusScreen
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink
import androidx.compose.ui.platform.LocalContext

@Composable
fun AppNavHost(nav: NavHostController, pendingDeepLinkProject: String?, onDeepLinkConsumed: () -> Unit) {
    val container = LocalAppContainer.current
    val app: AppViewModel = viewModel { AppViewModel(container) }
    val state by app.state.collectAsStateWithLifecycle()

    if (!state.ready) {
        if (state.error != null) {
            ScreenScaffold { padding ->
                ErrorState(state.error.orEmpty(), Modifier.padding(padding), onRetry = { app.bootstrap() })
            }
        } else {
            LaunchScreen()
        }
        return
    }
    if (state.upgradeRequired) {
        UpgradeScreen()
        return
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { app.onForeground() }
    LaunchedEffect(state.legalDue) {
        if (!state.legalDue) return@LaunchedEffect
        val here = nav.currentDestination?.route
        if (here != Routes.TERMS && here != Routes.CONSENT_FIRST && here != Routes.WELCOME) nav.navigate(Routes.TERMS) { launchSingleTop = true }
        app.legalShown()
    }

    LaunchedEffect(state.restartAt) {
        val route = state.restartAt ?: return@LaunchedEffect
        nav.navigate(route) { popUpTo(0) { inclusive = true } }
        app.restartConsumed()
    }

    LaunchedEffect(pendingDeepLinkProject) {
        val id = pendingDeepLinkProject ?: return@LaunchedEffect
        // Onboarding (welcome, terms) comes first; the project is on Home afterwards.
        if (state.startRoute == Routes.HOME) {
            val projects = container.projects
            projects.warmProject(id)
            val status = projects.cachedProject(id)?.status
                ?: projects.summaries.value.firstOrNull { it.id == id }?.status
                ?: try { projects.refresh(id).status } catch (e: ApiException) { null }
            if (status != null) {
                val onTop = nav.currentBackStackEntry?.arguments?.getString("projectId")
                nav.navigate(Routes.forProject(id, status)) { launchSingleTop = Routes.reuseTopFor(onTop, id) }
            }
        }
        // Consumed last: clearing it earlier would restart this effect mid-lookup.
        onDeepLinkConsumed()
    }

    NavHost(navController = nav, startDestination = state.startRoute) {
        composable(Routes.WELCOME) { WelcomeScreen(nav) }
        // R-113 item 6: the approved eight-step flow (Settings, Home before the first project, by the timeline).
        composable(Routes.HOW_IT_WORKS) { com.raviga.app.ui.onboarding.HowItWorksScreen(nav) }
        composable(Routes.NOTIFY_PROMPT, arguments = listOf(navArgument("next") { type = NavType.StringType })) { entry ->
            NotificationPromptScreen(nav, next = entry.arguments?.getString("next") ?: Routes.HOME)
        }
        composable(Routes.TERMS) { TermsScreen(nav) }
        composable(Routes.CONSENT_FIRST, arguments = listOf(navArgument("next") { type = NavType.StringType })) { entry ->
            TermsScreen(nav, next = entry.arguments?.getString("next"))
        }
        composable(Routes.HOME) { HomeScreen(nav) }
        // R-107: no separate AI sheet; an account without AI consent sees "Before you start" again (one tap, both).
        composable(Routes.AI_CONSENT) { TermsScreen(nav, aiReask = true) }
        composable(
            Routes.CAPTURE,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("mode") { type = NavType.StringType; defaultValue = "new" },
                navArgument("tab") { type = NavType.StringType; defaultValue = "speak" },
            ),
        ) { entry ->
            CaptureScreen(
                nav = nav,
                projectId = entry.arguments?.getString("projectId") ?: "new",
                mode = entry.arguments?.getString("mode") ?: "new",
                tab = entry.arguments?.getString("tab") ?: "speak",
            )
        }
        composable(
            Routes.DOCUMENT,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("reveal") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            DocumentScreen(
                nav = nav,
                projectId = entry.arguments?.getString("projectId").orEmpty(),
                reveal = entry.arguments?.getBoolean("reveal") ?: false,
            )
        }
        composable(Routes.SECTION) { entry ->
            SectionEditorScreen(
                nav = nav,
                projectId = entry.arguments?.getString("projectId").orEmpty(),
                sectionId = entry.arguments?.getString("sectionId").orEmpty(),
            )
        }
        composable(Routes.VERSIONS) { entry ->
            VersionsScreen(nav, entry.arguments?.getString("projectId").orEmpty())
        }
        composable(
            Routes.VERSION,
            arguments = listOf(navArgument("version") { type = NavType.IntType }),
        ) { entry ->
            VersionPreviewScreen(
                nav,
                entry.arguments?.getString("projectId").orEmpty(),
                entry.arguments?.getInt("version") ?: 0,
            )
        }
        composable(Routes.QUOTE) { entry -> QuoteScreen(nav, entry.arguments?.getString("projectId").orEmpty()) }
        composable(Routes.CONNECT_AWS) { ConnectAwsScreen(nav) }
        composable(Routes.STATUS) { entry -> StatusScreen(nav, entry.arguments?.getString("projectId").orEmpty()) }
        composable(Routes.DELIVERY) { entry -> DeliveryScreen(nav, entry.arguments?.getString("projectId").orEmpty()) }
        composable(Routes.GO_LIVE) { entry -> com.raviga.app.ui.status.GoLiveScreen(nav, entry.arguments?.getString("projectId").orEmpty()) }
        composable(Routes.CREDITS) { CreditsScreen(nav) }
        composable(Routes.CREDITS_BUY) { entry -> CreditsScreen(nav, buy = entry.arguments?.getString("productId")) }
        composable(
            Routes.CREDITS_FOR,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("short") { type = NavType.FloatType; defaultValue = 0f },
            ),
        ) { entry ->
            CreditsScreen(nav, forProject = entry.arguments?.getString("projectId"), short = entry.arguments?.getFloat("short")?.toDouble() ?: 0.0)
        }
        composable(Routes.SETTINGS) { SettingsScreen(nav) }
        composable(Routes.DELIVERY_TARGETS) { DeliveryTargetsScreen(nav) }
        composable(Routes.PRIVACY) { PrivacyScreen(nav) }
        composable(Routes.RECOVERY) { com.raviga.app.ui.account.MoveToAnotherPhoneScreen(nav) }
        composable(Routes.RECOVERY_EMAIL) { com.raviga.app.ui.account.RecoveryEmailScreen(nav) }
        composable(Routes.FONTS) { com.raviga.app.ui.settings.FontsScreen(nav) }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen(nav) }
    }
}

@Composable
private fun UpgradeScreen() {
    val context = LocalContext.current
    ScreenScaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Ra.gutter)) {
            Spacer(Modifier.height(48.dp))
            Text("Update Raviga", style = RaType.title, color = Ink.ink)
            Spacer(Modifier.height(16.dp))
            // The same words as iOS (O7), with Google Play for the App Store.
            Text(
                "This version is too old to use. Update to the latest from Google Play to carry on. Your drafts stay on this phone.",
                style = RaType.body, color = Ink.graphite,
            )
            Spacer(Modifier.height(32.dp))
            PrimaryButton("Open Google Play", onClick = { openLink(context, "https://play.google.com/store/apps/details?id=com.raviga.app") })
        }
    }
}
