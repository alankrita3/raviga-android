package com.raviga.app.ui.document

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.raviga.app.data.api.AiEdits
import com.raviga.app.data.api.AiEditsConfig
import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.CreditsConfig
import com.raviga.app.di.AppContainer
import com.raviga.app.ui.credits.BriefPricing
import com.raviga.app.ui.credits.NeedCreditsSheet
import com.raviga.app.ui.credits.PayCopy
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.theme.RaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * R-124 (contract v0.16.0, her decision 2026-11-12): each brief has 5 free AI edits (Regenerate, Add more), then 5
 * more for a twelfth of a credit, bought only after the person says yes. Typing a change by hand is always free.
 * iOS's words (docs/walkthrough-round2-2026-11-11.md, "v0.16.0"; the PM approved them as built).
 */
object AiEditsCopy {
    /**
     * "3 of 5 free AI edits left" (her words) while the free ones last, down to "0 of 5 free AI edits left"; after a
     * pack (or for an old draft's new id) "4 of 5 AI edits left". Null until the server has said.
     */
    fun countLine(edits: AiEdits?, c: AiEditsConfig): String? = when {
        edits == null -> null
        edits.free -> "${edits.left} of ${c.freePerBrief} free AI edits left"
        else -> "${edits.left} of ${c.packSize} AI edits left"
    }

    /** The Regenerate sheet's line: the count, then "Typing your own changes is always free." */
    fun regenerateLine(edits: AiEdits?, c: AiEditsConfig): String? = countLine(edits, c)?.let { "$it. $TYPING_IS_FREE" }

    /** The confirm's title: "5 more AI edits for 1/12 credit?" */
    fun confirmTitle(c: AiEditsConfig): String = "${c.packSize} more AI edits for ${BriefPricing.briefPrice(c.packPriceCredits)}?"

    const val TYPING_IS_FREE = "Typing your own changes is always free."
    const val BUY = "Buy"
    const val NOT_NOW = "Not now"
    const val NEED_TITLE = "Add credits for more AI edits"

    /** The server always sends its sentence; these stand in only for a bare 402 (its words). */
    fun packFallback(c: AiEditsConfig): String = "You've used this brief's ${c.freePerBrief} free AI edits. ${c.packSize} more cost a twelfth of a credit."
    fun shortFallback(c: AiEditsConfig): String = "You need a twelfth of a credit for ${c.packSize} more AI edits. Buy credits to continue."
}

/** What an AI edit's refusal asks of the person. Nothing ran and nothing was charged in either case. */
sealed interface EditPackAsk {
    /** None left: buy 5 more? ([message] is the server's, as sent.) */
    data class Confirm(val message: String) : EditPackAsk

    /** The pack was asked for, and the account is short of its twelfth: buy credits first. */
    data class NeedCredits(val message: String) : EditPackAsk

    companion object {
        /** [buying]: the call carried buyEditPack, so a 402 insufficient_credits is the pack's price. */
        fun from(e: Throwable, buying: Boolean, c: AiEditsConfig = AiEditsConfig()): EditPackAsk? {
            val api = e as? ApiException ?: return null
            // The server's sentence, else ours (a bare status carries "Request failed (402)" or the code).
            val words = api.message?.takeUnless { it.isBlank() || it == api.code || it.startsWith("Request failed (") }
            return when {
                api.code == ApiException.AI_EDIT_PACK_REQUIRED -> Confirm(words ?: AiEditsCopy.packFallback(c))
                api.code == ApiException.INSUFFICIENT_CREDITS && buying -> NeedCredits(words ?: AiEditsCopy.shortFallback(c))
                else -> null
            }
        }
    }
}

/**
 * One screen's pack ask: the confirm or the credits sheet, and the same AI edit to send again with buyEditPack after
 * "Buy", or after credits bought from the sheet. Each ViewModel that runs an AI edit holds one.
 */
class EditPackFlow(private val container: AppContainer, private val scope: CoroutineScope) {
    private val _ask = MutableStateFlow<EditPackAsk?>(null)
    val ask: StateFlow<EditPackAsk?> = _ask.asStateFlow()
    private var again: (() -> Unit)? = null

    /**
     * Shows what [e] asks, if it's a pack ask (true), keeping [buyAgain]: the same call with the flag. After a call
     * that carried the flag (bought, short, or refunded on our side) the balance is read again.
     */
    fun handle(e: Throwable?, buying: Boolean, buyAgain: () -> Unit): Boolean {
        if (buying) refreshCredits()
        val ask = e?.let { EditPackAsk.from(it, buying, container.session.config.value.credits.aiEdits) } ?: return false
        again = buyAgain
        _ask.value = ask
        return true
    }

    /** "Buy": the same call with the flag. */
    fun buy() {
        val run = again
        again = null
        _ask.value = null
        run?.invoke()
    }

    fun dismiss() {
        again = null
        _ask.value = null
    }

    /** "Buy 0.25 credits": the sheet closes; the call waits for the purchase ([creditsAdded]). */
    fun awaitPurchase() {
        _ask.value = null
    }

    /** Back from a purchase made from the sheet: once the balance covers the pack, the same call with the flag. */
    fun creditsAdded() {
        val run = again ?: return
        again = null
        val price = container.session.config.value.credits.aiEdits.packPriceCredits
        scope.launch {
            repeat(6) {
                runCatching { container.credits.refresh() }
                if ((container.credits.credits.value?.balance ?: 0.0) >= price) return@repeat
                delay(2_000)
            }
            run()
        }
    }

    private fun refreshCredits() {
        container.appScope.launch { runCatching { container.credits.refresh() }; runCatching { container.session.refreshMe() } }
    }
}

/**
 * The pack's confirm, or the short-of-credits sheet with the smallest pack first (as a new brief's). A purchase from
 * the sheet comes back here ([PayCopy.CREDITS_ADDED]) and the same AI edit goes again with the flag.
 */
@Composable
fun EditPackDialogs(flow: EditPackFlow, credits: CreditsConfig, nav: NavController) {
    val ask by flow.ask.collectAsStateWithLifecycle()
    val handle = nav.currentBackStackEntry?.savedStateHandle
    val added by (handle?.getStateFlow(PayCopy.CREDITS_ADDED, false) ?: MutableStateFlow(false)).collectAsStateWithLifecycle()
    LaunchedEffect(added) {
        if (added) {
            handle?.set(PayCopy.CREDITS_ADDED, false)
            handle?.set(PayCopy.RETURN_AFTER_BUY, false)
            flow.creditsAdded()
        }
    }
    when (val shown = ask) {
        is EditPackAsk.Confirm -> AlertDialog(
            onDismissRequest = { flow.dismiss() },
            containerColor = Ink.surface,
            title = { Text(AiEditsCopy.confirmTitle(credits.aiEdits), style = RaType.heading, color = Ink.ink) },
            text = { Text(shown.message, style = RaType.body, color = Ink.graphite) },
            confirmButton = { TextButton(onClick = { flow.buy() }) { Text(AiEditsCopy.BUY, style = RaType.button, color = Ink.link) } },
            dismissButton = { TextButton(onClick = { flow.dismiss() }) { Text(AiEditsCopy.NOT_NOW, style = RaType.button, color = Ink.ink) } },
        )
        is EditPackAsk.NeedCredits -> NeedCreditsSheet(
            title = AiEditsCopy.NEED_TITLE,
            body = shown.message,
            config = credits,
            onBuy = { productId ->
                flow.awaitPurchase()
                handle?.set(PayCopy.RETURN_AFTER_BUY, true)
                nav.navigate(Routes.creditsBuy(productId))
            },
            onAllPacks = { flow.dismiss(); nav.navigate(Routes.CREDITS) },
            onDismiss = { flow.dismiss() },
        )
        null -> Unit
    }
}
