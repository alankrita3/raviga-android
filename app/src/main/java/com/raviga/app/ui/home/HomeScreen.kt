package com.raviga.app.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.raviga.app.ui.components.Dot
import com.raviga.app.ui.components.Illustration
import com.raviga.app.ui.components.Picture
import com.raviga.app.ui.components.tile
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.BottomBar
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.ErrorState
import com.raviga.app.ui.components.InlineAction
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ProgressRule
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.StatusMark
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.onboarding.navigateAfterConsent
import com.raviga.app.ui.capture.DescribeChooserSheet
import com.raviga.app.ui.status.StatusCopy
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink

@Composable
fun HomeScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    val summaries by vm.rows.collectAsStateWithLifecycle()
    val loadedOnce by vm.loadedOnce.collectAsStateWithLifecycle()
    val balance by vm.balance.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val signedOut by vm.signedOut.collectAsStateWithLifecycle()
    var chooser by remember { mutableStateOf(false) }
    if (chooser) {
        DescribeChooserSheet(
            projectTitle = null,
            onPick = { tab -> chooser = false; nav.navigateAfterConsent(container.session.agreed, Routes.capture(tab = tab)) },
            onDismiss = { chooser = false },
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh(force = false) }

    // Android 13+: ask for notifications once, after the client has seen the home screen.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !vm.notificationsAsked()) {
            vm.markNotificationsAsked()
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ScreenScaffold(
        topBar = {
            RaTopBar(
                actions = {
                    CreditsPill(balance, onClick = { nav.navigate(Routes.CREDITS) })
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Ink.ink)
                    }
                },
            )
        },
        bottomBar = {
            BottomBar {
                PrimaryButton("Describe a project", onClick = { chooser = true })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (refreshing && summaries.isNotEmpty()) ProgressRule(Modifier.padding(horizontal = Ra.gutter)) else Spacer(Modifier.height(4.dp))
            when {
                error != null && summaries.isEmpty() -> ErrorState(error!!, onRetry = { vm.refresh() })
                summaries.isEmpty() && loadedOnce -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
                        Spacer(Modifier.height(12.dp))
                        Illustration(Picture.HomeEmpty, height = 220.dp)
                        Spacer(Modifier.height(24.dp))
                        Text("What should we build?", style = RaType.title, color = Ink.ink)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Describe what you want built: say it, type it, or upload a document you already have. We turn it into a brief you can edit, quote it, and build it.",
                            style = RaType.body,
                            color = Ink.graphite,
                        )
                        if (signedOut) {
                            Spacer(Modifier.height(24.dp))
                            Text(
                                "This phone was removed from your Raviga account, so it has started fresh. To bring your projects back, use your recovery key.",
                                style = RaType.secondary,
                                color = Ink.graphite,
                            )
                            Row {
                                InlineAction("Use a recovery key", onClick = { vm.dismissSignedOut(); nav.navigateAfterConsent(container.session.agreed, Routes.RECOVERY) })
                                InlineAction("Dismiss", onClick = { vm.dismissSignedOut() }, color = Ink.graphite)
                            }
                        }
                        if (vm.isDemo) {
                            Spacer(Modifier.height(24.dp))
                            Text("Demo backend: everything works on this phone until the Raviga API is connected.", style = RaType.caption, color = Ink.ash)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                summaries.isEmpty() -> Spacer(Modifier.height(1.dp))
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Ra.gutter, end = Ra.gutter, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(Ra.tileGap),
                ) {
                    item {
                        Text(
                            "Your projects",
                            style = RaType.title,
                            color = Ink.ink,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                        )
                    }
                    items(summaries, key = { it.id }) { row ->
                        ProjectTile(row, onClick = { nav.navigate(row.route) })
                    }
                }
            }
        }
    }
}

/** Marigold dot and the balance; opens Credits. */
@Composable
private fun CreditsPill(balance: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$balance ${if (balance == 1) "credit" else "credits"}" }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(Ink.marigold)
        Spacer(Modifier.width(6.dp))
        Text("$balance ${if (balance == 1) "credit" else "credits"}", style = RaType.secondaryMedium, color = Ink.ink)
    }
}

/** One project on Home: where it stands, its name, and what it cost. */
@Composable
private fun ProjectTile(row: HomeViewModel.Row, onClick: () -> Unit) {
    val line = buildString {
        append(row.line)
        if (row.unread > 0) append(" · ${row.unread} new")
    }
    Row(Modifier.tile(padding = 18.dp, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusMark(if (row.refused) ProjectStatus.REJECTED else row.status)
                Spacer(Modifier.width(8.dp))
                Text(
                    line,
                    style = RaType.caption,
                    color = when {
                        row.refused -> Ink.brick
                        row.unread > 0 -> Ink.teal
                        else -> Ink.graphite
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(row.title, style = RaType.heading, color = Ink.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (row.credits != null && row.status != ProjectStatus.DRAFT) {
                Spacer(Modifier.height(6.dp))
                Text("${row.credits} ${if (row.credits == 1) "credit" else "credits"}", style = RaType.caption, color = Ink.ash)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Ink.ash, modifier = Modifier.size(22.dp))
    }
}
