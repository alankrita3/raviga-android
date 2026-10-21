package com.raviga.app.ui.credits

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
        viewModelScope.launch { runCatching { container.credits.refresh() }.onFailure { e -> if (_state.value.credits == null) _state.update { it.copy(error = e.userLine()) } } }
    }

    fun buy(activity: Activity, pack: CreditPack) {
        if (_state.value.buying != null) return
        viewModelScope.launch {
            _state.update { it.copy(buying = pack.productId, error = null, notice = null) }
            val outcome = runCatching { container.credits.purchase(activity, pack) }.getOrElse { PurchaseOutcome.Failed(it.userLine()) }
            // Confirmed means the balance synced (contract v0.9); bonus credits included.
            if (outcome == PurchaseOutcome.Success) container.analytics.creditsPurchased(pack.productId, pack.total)
            _state.update {
                it.copy(
                    buying = null,
                    error = (outcome as? PurchaseOutcome.Failed)?.message,
                    notice = when (outcome) {
                        PurchaseOutcome.Success -> "${pack.total} credits added."
                        PurchaseOutcome.Pending -> "Payment pending. Your credits arrive as soon as your bank confirms it."
                        PurchaseOutcome.NotBookedYet -> "Payment received. Your credits will appear in a minute."
                        else -> null
                    },
                )
            }
        }
    }

    fun restore() {
        viewModelScope.launch {
            _state.update { it.copy(restoring = true, error = null, notice = null) }
            runCatching { container.credits.restore() }
                .onSuccess { _state.update { it.copy(notice = "Purchases checked. Balance is up to date.") } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(restoring = false) }
        }
    }
}

@Composable
fun CreditsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: CreditsViewModel = viewModel { CreditsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val balance = state.credits?.balance ?: container.session.me.value?.credits?.balance ?: 0

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
                        "Credits pay for a project when you submit it, and come back if it is cancelled or not accepted. The quote tells you how many each one needs.",
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
                val enabled = (state.billingAvailable || state.demoBilling) && state.buying == null
                Row(
                    Modifier
                        .padding(horizontal = Ra.gutter)
                        .padding(bottom = 10.dp)
                        .tile(padding = 16.dp, onClick = if (enabled) ({ context.findActivity()?.let { vm.buy(it, pack) } }) else null),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(Money.credits(pack.credits), style = RaType.bodyMedium, color = if (enabled) Ink.ink else Ink.ash)
                        if (pack.bonusCredits > 0) {
                            Spacer(Modifier.height(6.dp))
                            Chip("+${pack.bonusCredits} bonus", tone = ChipTone.Marigold)
                        }
                        if (state.demoBilling) {
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
                    if (state.credits == null && state.error == null) ProgressRule(Modifier.padding(vertical = 12.dp))
                    else if (state.credits?.ledger.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("No credits yet. Buy a pack to submit your first project.", style = RaType.body, color = Ink.graphite)
                    }
                }
            }
            items(state.credits?.ledger.orEmpty(), key = { it.id }) { entry -> LedgerRow(entry) }
            item { Spacer(Modifier.height(32.dp)) }
        }
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
