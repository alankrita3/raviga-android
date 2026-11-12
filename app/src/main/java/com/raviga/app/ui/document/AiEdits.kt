package com.raviga.app.ui.document

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.raviga.app.data.api.AiEdits
import com.raviga.app.data.api.AiEditsConfig
import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.CreditsConfig
import com.raviga.app.ui.credits.BriefPricing
import com.raviga.app.ui.credits.NeedCreditsSheet
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.theme.RaType

/**
 * R-124 (contract v0.16.0, her decision 2026-11-12): each brief has 5 free AI edits (Regenerate, Add more), then 5
 * more for a twelfth of a credit, bought only after the person says yes. Typing a change by hand is always free.
 */
object AiEditsCopy {
    /** "3 of 5 free AI edits left" (the contract's words); "4 AI edits left" on a bought pack; null when not known yet. */
    fun countLine(edits: AiEdits?, c: AiEditsConfig): String? = when {
        edits == null -> null
        edits.free && edits.left > 0 -> "${edits.left} of ${c.freePerBrief} free AI edits left"
        edits.left == 1 -> "1 AI edit left"
        edits.left > 0 -> "${edits.left} AI edits left"
        else -> "${c.packSize} more AI edits cost ${BriefPricing.briefPrice(c.packPriceCredits)}"
    }

    /** The confirm's question (the contract's words): "5 more AI edits for 1/12 credit?" */
    fun confirmTitle(c: AiEditsConfig): String = "${c.packSize} more AI edits for ${BriefPricing.briefPrice(c.packPriceCredits)}?"

    fun buyLabel(c: AiEditsConfig): String = "Buy ${c.packSize} AI edits"

    const val TYPING_IS_FREE = "Typing your own changes is always free."
    const val NEED_TITLE = "Add credits for more AI edits"
    const val NOT_NOW = "Not now"

    /** The server's words when it sent none (as its own): what the pack costs. */
    fun packFallback(c: AiEditsConfig): String = "You've used this brief's ${c.freePerBrief} free AI edits. ${c.packSize} more cost a twelfth of a credit."
    fun shortFallback(c: AiEditsConfig): String = "You need a twelfth of a credit for ${c.packSize} more AI edits. Buy credits to continue."
}

/** What an AI edit's refusal asks of the person. Nothing ran and nothing was charged in either case. */
sealed interface EditPackAsk {
    /** None left: buy 5 more? ([message] is the server's.) */
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

/** The pack's confirm, or the short-of-credits sheet with the smallest pack first (as a new brief's). */
@Composable
fun EditPackDialogs(
    ask: EditPackAsk?,
    credits: CreditsConfig,
    onBuyEdits: () -> Unit,
    onBuyCredits: (String) -> Unit,
    onAllPacks: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (ask) {
        is EditPackAsk.Confirm -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Ink.surface,
            title = { Text(AiEditsCopy.confirmTitle(credits.aiEdits), style = RaType.heading, color = Ink.ink) },
            text = { Text(ask.message + " " + AiEditsCopy.TYPING_IS_FREE, style = RaType.body, color = Ink.graphite) },
            confirmButton = { TextButton(onClick = onBuyEdits) { Text(AiEditsCopy.buyLabel(credits.aiEdits), style = RaType.button, color = Ink.link) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(AiEditsCopy.NOT_NOW, style = RaType.button, color = Ink.ink) } },
        )
        is EditPackAsk.NeedCredits -> NeedCreditsSheet(
            title = AiEditsCopy.NEED_TITLE,
            body = ask.message,
            config = credits,
            onBuy = onBuyCredits,
            onAllPacks = onAllPacks,
            onDismiss = onDismiss,
        )
        null -> Unit
    }
}
