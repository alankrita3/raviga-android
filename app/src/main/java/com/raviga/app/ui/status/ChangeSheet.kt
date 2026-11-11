package com.raviga.app.ui.status

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raviga.app.ui.components.InlineNotice
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ProgressRule
import com.raviga.app.ui.components.RaTextField
import com.raviga.app.ui.components.TertiaryButton
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.util.Money

/**
 * "Request a change" (R-109, E; PM-approved copy as iOS): the client describes it, the AI writes a short change brief
 * and prices it (floor 1 credit), the client pays and it's built into a new demo. Bugs and brief mismatches go to
 * "Something's wrong" instead, free. A refusal shows the server's words.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeSheet(
    state: StatusViewModel.State,
    onPrice: (String) -> Unit,
    onPay: () -> Unit,
    onBuyCredits: () -> Unit,
    onDismiss: () -> Unit,
) {
    val max = state.config.report.descriptionMaxChars
    var text by rememberSaveable { mutableStateOf("") }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!state.busy) onDismiss() },
        sheetState = sheet,
        containerColor = Ink.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)
                .padding(top = 24.dp, bottom = 16.dp).navigationBarsPadding().imePadding(),
        ) {
            val change = state.quotedChange
            when {
                change != null -> {
                    // The AI's short change brief and its price.
                    Text(DeliveryCopy.YOUR_CHANGE, style = RaType.heading, color = Ink.ink)
                    change.brief?.let { brief ->
                        if (brief.title.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(brief.title, style = RaType.bodyMedium, color = Ink.ink)
                        }
                        brief.items.forEach { item ->
                            Spacer(Modifier.height(4.dp))
                            Text("•  $item", style = RaType.body, color = Ink.ink)
                        }
                    }
                    val quote = change.quote
                    if (quote != null) {
                        Spacer(Modifier.height(14.dp))
                        val usd = quote.usd.takeIf { it > 0.0 } ?: (quote.credits * state.config.credits.creditValueUsd)
                        Text(DeliveryCopy.changePrice(Money.credits(quote.credits), Money.usd(usd)), style = RaType.bodyMedium, color = Ink.ink)
                    }
                    if (state.changeShortBy > 0.0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You have ${Money.credits(state.balance)}. You need ${Money.creditsOwed(state.changeShortBy)} more.",
                            style = RaType.body, color = Ink.ink,
                        )
                    }
                    InlineNotice(state.changeError, Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(16.dp))
                    if (state.changeShortBy > 0.0) {
                        PrimaryButton("Buy credits", onClick = onBuyCredits)
                    } else if (quote != null) {
                        PrimaryButton("Pay ${Money.credits(quote.credits)}", loading = state.busy, onClick = onPay)
                    }
                    TertiaryButton("Not now", enabled = !state.busy, onClick = onDismiss)
                }
                state.pricing -> {
                    Text(DeliveryCopy.REQUEST_CHANGE, style = RaType.heading, color = Ink.ink)
                    Spacer(Modifier.height(16.dp))
                    Text(DeliveryCopy.PRICING, style = RaType.secondary, color = Ink.link)
                    Spacer(Modifier.height(12.dp))
                    ProgressRule()
                    Spacer(Modifier.height(16.dp))
                }
                else -> {
                    Text(DeliveryCopy.REQUEST_CHANGE, style = RaType.heading, color = Ink.ink)
                    Spacer(Modifier.height(8.dp))
                    // R-113: it points to Something's wrong only while that's offered (the free-fix window).
                    Text(DeliveryCopy.changeLine(state.freeFixesOpen), style = RaType.secondary, color = Ink.graphite)
                    Spacer(Modifier.height(16.dp))
                    RaTextField(
                        value = text,
                        onValueChange = { text = it.take(max) },
                        label = DeliveryCopy.CHANGE_FIELD,
                        minLines = 3,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        Text("${text.length}/$max", style = RaType.caption, color = Ink.graphite)
                    }
                    // A refusal (content_rejected) or a failed pricing: the server's words, the text kept.
                    InlineNotice(state.changeError, Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(DeliveryCopy.GET_A_PRICE, enabled = text.isNotBlank(), onClick = { onPrice(text) })
                }
            }
        }
    }
}
