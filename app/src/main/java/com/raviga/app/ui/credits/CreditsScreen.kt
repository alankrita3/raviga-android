package com.raviga.app.ui.credits

import com.raviga.app.ui.account.ProtectCreditsSheet
import com.raviga.app.ui.account.RecoveryEmailPrompt
import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.raviga.app.ui.nav.Routes
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.app.data.api.CreditPack
import com.raviga.app.data.api.CreditsResponse
import com.raviga.app.data.api.LedgerEntry
import com.raviga.app.data.billing.PurchaseOutcome
import com.raviga.app.di.AppContainer
import com.raviga.app.di.AppEvent
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.RaRow
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.InlineNotice
import com.raviga.app.ui.components.ProgressRule
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.SectionHeading
import com.raviga.app.ui.components.TertiaryButton
import com.raviga.app.ui.findActivity
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.userLine
import com.raviga.app.util.Time
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import com.raviga.app.ui.components.Chip
import com.raviga.app.ui.components.ChipTone
import com.raviga.app.ui.components.InlineAction
import com.raviga.app.ui.components.Picture
import com.raviga.app.ui.components.SpotIllustration
import com.raviga.app.ui.components.tile
import com.raviga.app.util.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreditsViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val credits: CreditsResponse? = null,
        val packs: List<CreditPack> = emptyList(),
        val prices: Map<String, String> = emptyMap(),
        val creditValueUsd: Double = 10.0,
        val buying: String? = null,
        val restoring: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
        val demoBilling: Boolean = false,
        val billingAvailable: Boolean = false,
        /** R-079: offer a recovery email (once per install, after credits were added). */
        val offerRecoveryEmail: Boolean = false,
        val loadingOlder: Boolean = false,
        val olderError: String? = null,
    )

    private val _state = MutableStateFlow(State(demoBilling = container.credits.isDemoBilling, billingAvailable = container.billing.isAvailable))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch { container.credits.credits.collect { c -> _state.update { it.copy(credits = c) } } }
        viewModelScope.launch { container.credits.prices.collect { p -> _state.update { it.copy(prices = p) } } }
        viewModelScope.launch {
            container.session.config.collect { c ->
                _state.update { it.copy(packs = c.credits.packs, creditValueUsd = c.credits.creditValueUsd) }
                container.credits.loadPrices(c.credits.packs)
            }
        }
        viewModelScope.launch { container.events.collect { if (it is AppEvent.CreditsUpdated) runCatching { container.credits.refresh() } } }
        // R-056: before Agree there is no account yet, so no balance to fetch (and nothing may leave the phone).
        if (container.session.hasAccount) viewModelScope.launch {
            // R-092 #20 (as iOS): a purchase that wasn't booked yet is synced on the next open, not only re-read.
            if (container.prefs.current().creditsSyncDue) {
                runCatching { container.credits.sync() }.onSuccess { container.prefs.setCreditsSyncDue(false) }
            }
        }
        if (container.session.hasAccount) viewModelScope.launch { runCatching { container.credits.refresh() }.onFailure { e -> if (_state.value.credits == null) _state.update { it.copy(error = e.userLine()) } } }
    }

    fun buy(activity: Activity, pack: CreditPack) {
        if (_state.value.buying != null) return
        viewModelScope.launch {
            _state.update { it.copy(buying = pack.productId, error = null, notice = null) }
            val outcome = runCatching {
                // Signed out on this phone (R-079): the credits need an account, and the store must know it first.
                if (!container.session.hasAccount) {
                    container.session.ensureAccount()
                    container.session.clientId?.let { container.billing.identify(it) }
                }
                container.credits.purchase(activity, pack)
            }.getOrElse { PurchaseOutcome.Failed(it.userLine()) }
            // Confirmed means the balance synced (contract v0.9); bonus credits included.
            if (outcome == PurchaseOutcome.Success) container.analytics.creditsPurchased(pack.productId, pack.total)
            if (outcome == PurchaseOutcome.Success && pack.total > 0) offerRecoveryEmailOnce()
            // NotBookedYet: the repository marked a sync due as soon as the store took the money (next open syncs).
            _state.update {
                it.copy(
                    buying = null,
                    error = (outcome as? PurchaseOutcome.Failed)?.message,
                    notice = when (outcome) {
                        PurchaseOutcome.Success -> "${pack.total} credits added."
                        PurchaseOutcome.Pending -> "Payment pending. Your credits arrive as soon as your bank confirms it."
                        PurchaseOutcome.NotBookedYet -> "Your purchase is confirmed. The credits arrive in a moment."   // R-092 #20, as iOS
                        else -> null
                    },
                )
            }
        }
    }

    private suspend fun offerRecoveryEmailOnce() {
        val shown = container.prefs.current().recoveryEmailPromptShown
        if (!RecoveryEmailPrompt.shouldOffer(container.session.me.value, shown)) return
        container.prefs.setRecoveryEmailPromptShown()
        _state.update { it.copy(offerRecoveryEmail = true) }
    }

    fun recoveryEmailOffered() = _state.update { it.copy(offerRecoveryEmail = false) }

    /** QA J5 (as iOS): the next older page of the history. A failure is said, and the button stays to try again. */
    fun showMore() {
        if (_state.value.loadingOlder) return
        viewModelScope.launch {
            _state.update { it.copy(loadingOlder = true, olderError = null) }
            runCatching { container.credits.loadOlder() }.onFailure { e -> _state.update { it.copy(olderError = e.userLine()) } }
            _state.update { it.copy(loadingOlder = false) }
        }
    }

    fun restore() {
        viewModelScope.launch {
            _state.update { it.copy(restoring = true, error = null, notice = null) }
            runCatching { container.credits.restore() }
                .onSuccess { r ->
                    val line = if (r.added > 0) "Purchases checked. ${com.raviga.app.util.Money.credits(r.added)} added." else "Purchases checked. Balance is up to date."
                    _state.update { it.copy(notice = line) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(restoring = false) }
        }
    }
}

@Composable
fun CreditsScreen(nav: NavController, buy: String? = null) {
    val container = LocalAppContainer.current
    val vm: CreditsViewModel = viewModel { CreditsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val balance = state.credits?.balance ?: container.session.me.value?.credits?.balance ?: 0

    // After the first consent (R-056): carry on into the pack the client tapped, once the store knows this account.
    var buyStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(buy, state.packs) {
        val pack = state.packs.firstOrNull { it.productId == buy } ?: return@LaunchedEffect
        if (buyStarted) return@LaunchedEffect
        repeat(20) {
            if (container.credits.storeReady || state.demoBilling) {
                buyStarted = true
                context.findActivity()?.let { vm.buy(it, pack) }
                return@LaunchedEffect
            }
            delay(250)
        }
    }

    ScreenScaffold(topBar = { RaTopBar(onBack = { nav.popBackStack() }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(horizontal = Ra.gutter)) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Money.credits(balance), style = RaType.display, color = Ink.ink)
                            Spacer(Modifier.height(6.dp))
                            Text("1 credit is ${Money.usd(state.creditValueUsd)}", style = RaType.secondary, color = Ink.graphite)
                        }
                        Spacer(Modifier.width(12.dp))
                        SpotIllustration(Picture.Credits, size = 124.dp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        // R-103 (contract v0.10.0): paid once accepted, refunded if a paid project is declined.
                        "Credits pay for a project once we accept it, and come back if we decline it after you paid. The quote tells you how many each one needs.",
                        style = RaType.body, color = Ink.graphite,
                    )
                    InlineNotice(state.error)
                    InlineNotice(state.notice, color = Ink.moss)
                    Spacer(Modifier.height(28.dp))
                    Text("Buy credits", style = RaType.heading, color = Ink.ink)
                    if (!state.billingAvailable && !state.demoBilling) {
                        Spacer(Modifier.height(8.dp))
                        Text("Purchases are not set up on this build yet.", style = RaType.secondary, color = Ink.amber)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            items(state.packs, key = { it.productId }) { pack ->
                // The store's own price; the config's dollar hint only until it loads.
                val price = state.prices[pack.productId]
                    ?: pack.priceHintUsd?.let { Money.usd(it) }
                    ?: ""
                // Before Agree a tap goes to the first consent (R-056), then back here to buy this pack.
                val agreed = container.session.agreed
                val enabled = (!agreed || state.billingAvailable || state.demoBilling) && state.buying == null
                Row(
                    Modifier
                        .padding(horizontal = Ra.gutter)
                        .padding(bottom = 10.dp)
                        .tile(padding = 16.dp, onClick = if (enabled) ({
                            if (!agreed) nav.navigate(Routes.consentFirst(Routes.creditsBuy(pack.productId)))
                            else context.findActivity()?.let { vm.buy(it, pack) }
                        }) else null),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(Money.credits(pack.credits), style = RaType.bodyMedium, color = if (enabled) Ink.ink else Ink.ash)
                        if (pack.bonusCredits > 0) {
                            Spacer(Modifier.height(6.dp))
                            Chip("+${pack.bonusCredits} bonus", tone = ChipTone.Marigold)
                        }
                        if (com.raviga.app.BuildConfig.DEBUG && state.demoBilling) {
                            Spacer(Modifier.height(4.dp))
                            Text("Demo, no charge", style = RaType.caption, color = Ink.ash)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (state.buying == pack.productId) "…" else price,
                        style = RaType.bodyMedium,
                        color = if (enabled) Ink.ink else Ink.ash,
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = Ra.gutter)) {
                    Spacer(Modifier.height(8.dp))
                    if (state.billingAvailable) {
                        InlineAction(if (state.restoring) "Checking…" else "Restore purchases", enabled = !state.restoring, onClick = { vm.restore() }, modifier = Modifier.offset(x = (-8).dp))
                    }
                    Text("Billed through Google Play. Credits are used only inside Raviga.", style = RaType.caption, color = Ink.graphite)
                    Spacer(Modifier.height(Ra.sectionGap))
                    Text("History", style = RaType.heading, color = Ink.ink)
                    Spacer(Modifier.height(4.dp))
                    // No account on this phone (before Agree, or signed out): nothing to load, so no endless rule.
                    if (state.credits == null && state.error == null && container.session.hasAccount) ProgressRule(Modifier.padding(vertical = 12.dp))
                    else if (state.credits?.ledger.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("No credits yet. Buy a pack to submit your first project.", style = RaType.body, color = Ink.graphite)
                    }
                }
            }
            items(state.credits?.ledger.orEmpty(), key = { it.id }) { entry -> LedgerRow(entry) }
            if (state.credits?.nextCursor != null && state.credits?.ledger.isNullOrEmpty().not()) item {
                Column(Modifier.padding(horizontal = Ra.gutter).padding(top = 12.dp)) {
                    InlineAction(
                        if (state.loadingOlder) "Loading" else "Show more", enabled = !state.loadingOlder,
                        onClick = { vm.showMore() }, modifier = Modifier.offset(x = (-8).dp),
                    )
                    InlineNotice(state.olderError)
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (state.offerRecoveryEmail) {
        ProtectCreditsSheet(
            onAdd = { vm.recoveryEmailOffered(); nav.navigate(Routes.RECOVERY_EMAIL) },
            onNotNow = { vm.recoveryEmailOffered() },
        )
    }
}

@Composable
private fun LedgerRow(entry: LedgerEntry) {
    val title = when (entry.type) {
        "purchase" -> "Bought ${entry.ref?.productId?.removePrefix("credits_")?.let { "$it credits" } ?: "credits"}"
        "bonus" -> "Pack bonus"
        "charge" -> entry.note.ifBlank { "Project submitted" }
        "refund_project" -> entry.note.ifBlank { "Project refund" }
        "refund_store" -> "Store refund"
        "revision_charge" -> "Revision"
        else -> entry.note.ifBlank { "Adjustment" }
    }
    val signed = if (entry.credits > 0) "+${entry.credits}" else "${entry.credits}"
    RaRow(
        title = title,
        subtitle = Time.dateTime(entry.at) + "  ·  balance ${entry.balanceAfter}",
        chevron = false,
        trailing = { Text(signed, style = RaType.bodyMedium, color = if (entry.credits > 0) Ink.moss else Ink.ink) },
    )
}
