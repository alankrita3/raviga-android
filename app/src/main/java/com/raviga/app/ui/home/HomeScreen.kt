package com.raviga.app.ui.home

import com.raviga.app.ui.nav.rememberIsOnTop
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val onTop = nav.rememberIsOnTop()   // review #17: a double tap opens one screen
    val summaries by vm.rows.collectAsStateWithLifecycle()
    val loadedOnce by vm.loadedOnce.collectAsStateWithLifecycle()
    val balance by vm.balance.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val signedOut by vm.signedOut.collectAsStateWithLifecycle()
    val unreadable by vm.unreadableDrafts.collectAsStateWithLifecycle()
    val saveFailing by container.draftStore.saveFailing.collectAsStateWithLifecycle()
    var chooser by remember { mutableStateOf(false) }
    if (chooser) {
        DescribeChooserSheet(
            projectTitle = null,
            onPick = { tab -> chooser = false; nav.navigateAfterConsent(container.session.agreed, Routes.capture(tab = tab)) },
            onDismiss = { chooser = false },
        )
    }
    // A draft on this phone, swiped or long-pressed: ask before deleting it (same words as the brief's "Delete").
    var confirmDelete by remember { mutableStateOf<HomeViewModel.Row?>(null) }
    confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Ink.surface,
            title = { Text("Delete this draft?", style = RaType.heading, color = Ink.ink) },
            text = { Text("The description, the brief and all its versions are deleted. This cannot be undone.", style = RaType.body, color = Ink.graphite) },
            confirmButton = { TextButton(onClick = { confirmDelete = null; vm.deleteDraft(row.id) }) { Text("Delete", style = RaType.button, color = Ink.brick) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep", style = RaType.button, color = Ink.ink) } },
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh(force = false) }

    // Notifications are asked for once, at the end of onboarding ("Stay in the loop", R-074), not here.

    ScreenScaffold(
        topBar = {
            RaTopBar(
                actions = {
                    CreditsPill(balance, onClick = { if (onTop()) nav.navigate(Routes.CREDITS) })
                    IconButton(onClick = { if (onTop()) nav.navigate(Routes.SETTINGS) }) {
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
                error != null && summaries.isEmpty() -> ErrorState(error.orEmpty(), onRetry = { vm.refresh() })
                summaries.isEmpty() && loadedOnce -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
                        Spacer(Modifier.height(12.dp))
                        Illustration(Picture.HomeEmpty, height = 220.dp)
                        Spacer(Modifier.height(24.dp))
                        Text("What should we build?", style = RaType.title, color = Ink.ink)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            // R-116 (4, the PM's copy).
                            "Say it, type it, or upload a document you already have. We write the brief, give you a price, and build it.",
                            style = RaType.body,
                            color = Ink.graphite,
                        )
                        // R-113 item 6: before the first project, the whole flow is a tap away.
                        Spacer(Modifier.height(20.dp))
                        com.raviga.app.ui.components.RaRow(
                            title = com.raviga.app.ui.onboarding.HowItWorks.HOME_CARD,
                            onClick = { nav.navigate(Routes.HOW_IT_WORKS) },
                            modifier = Modifier.tile(padding = 0.dp),
                            hairline = false,
                            horizontalPadding = 16.dp,
                        )
                        if (signedOut) {
                            Spacer(Modifier.height(24.dp))
                            RemovedNotice(
                                onRestore = { vm.dismissSignedOut(); nav.navigateAfterConsent(container.session.agreed, Routes.RECOVERY) },
                                onDismiss = { vm.dismissSignedOut() },
                            )
                        }
                        if (com.raviga.app.BuildConfig.DEBUG && vm.isDemo) {
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
                    // R-094 P2-4 (iOS review 2): said here until a save succeeds, not as a banner a refresh clears.
                    if (saveFailing) item(key = "save_failing") {
                        Text(com.raviga.app.data.drafts.DraftStore.SAVE_FAILED, style = RaType.secondary, color = Ink.brick)
                    }
                    if (unreadable > 0) item(key = "unreadable") {
                        Text(
                            if (unreadable == 1) "One draft on this phone couldn't be opened. It's kept as it is and tried again next time."
                            else "$unreadable drafts on this phone couldn't be opened. They're kept as they are and tried again next time.",
                            style = RaType.secondary, color = Ink.amber,
                        )
                    }
                    // QA O8: removed from the account with drafts on the phone, the way back shows here too.
                    if (signedOut) item(key = "removed") {
                        RemovedNotice(
                            onRestore = { vm.dismissSignedOut(); nav.navigateAfterConsent(container.session.agreed, Routes.RECOVERY) },
                            onDismiss = { vm.dismissSignedOut() },
                        )
                    }
                    items(summaries, key = { it.id }) { row ->
                        if (row.localDraft) {
                            DeletableDraft(row, onClick = { if (onTop()) nav.navigate(row.route) }, onDelete = { confirmDelete = row })
                        } else {
                            ProjectTile(row, onClick = { if (onTop()) nav.navigate(row.route) })
                        }
                    }
                }
            }
        }
    }
}

/** This phone was signed out from another phone and started fresh: the way back (recovery key or email). */
@Composable
private fun RemovedNotice(onRestore: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "This phone was removed from your Raviga account, so it has started fresh. To bring your projects back, use your recovery key or recovery email. Drafts on this phone are kept.",
            style = RaType.body,
            color = Ink.graphite,
        )
        Row(Modifier.offset(x = (-8).dp)) {
            InlineAction("Restore my account", onClick = onRestore)
            InlineAction("Dismiss", onClick = onDismiss, color = Ink.graphite)
        }
    }
}

/**
 * A draft only on this phone: swipe it left, or long-press it, to delete it (Home asks first). Nothing was
 * sent, so nothing else is touched.
 */
@Composable
private fun DeletableDraft(row: HomeViewModel.Row, onClick: () -> Unit, onDelete: () -> Unit) {
    val swipe = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        // Swiped all the way: the tile springs back first, then Home asks; the confirm decides.
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                scope.launch { swipe.reset() }
                onDelete()
            }
        },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(Ra.tileRadius)).background(Ink.brick).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("Delete", style = RaType.button, color = Ink.surface)
            }
        },
    ) {
        ProjectTile(
            row,
            onClick = onClick,
            onLongClick = onDelete,
            modifier = Modifier.semantics { customActions = listOf(CustomAccessibilityAction("Delete draft") { onDelete(); true }) },
        )
    }
}

/** Marigold dot and the balance; opens Credits. */
@Composable
private fun CreditsPill(balance: Double, onClick: () -> Unit) {
    Row(
        Modifier
            .minimumInteractiveComponentSize()   // R-092 #40: 48dp to tap
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = com.raviga.app.util.Money.credits(balance) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(Ink.marigold)
        Spacer(Modifier.width(6.dp))
        Text(com.raviga.app.util.Money.credits(balance), style = RaType.secondaryMedium, color = Ink.ink)
    }
}

/** One project on Home: where it stands, its name, and what it cost. */
@Composable
private fun ProjectTile(row: HomeViewModel.Row, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    val line = buildString {
        append(row.line)
        if (row.unread > 0) append(" · ${row.unread} new")
    }
    // QA R1 (as iOS fc9c380): at the large text sizes the status line and the title wrap in full instead of "...".
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    Row(modifier.tile(padding = 18.dp, onClick = onClick, onLongClick = onLongClick), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusMark(if (row.refused) ProjectStatus.REJECTED else row.status)
                Spacer(Modifier.width(8.dp))
                Text(
                    line,
                    style = RaType.caption,
                    color = when {
                        row.refused -> Ink.brick
                        row.unread > 0 -> Ink.link
                        else -> Ink.graphite
                    },
                    maxLines = if (large) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(row.title, style = RaType.heading, color = Ink.ink, maxLines = if (large) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            if (row.credits != null && row.status != ProjectStatus.DRAFT) {
                Spacer(Modifier.height(6.dp))
                Text("${row.credits} ${if (row.credits == 1) "credit" else "credits"}", style = RaType.caption, color = Ink.ash)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Ink.ash, modifier = Modifier.size(22.dp))
    }
}
