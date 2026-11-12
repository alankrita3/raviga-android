package com.raviga.app.ui.credits

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raviga.app.data.api.CreditsConfig
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.SecondaryButton
import com.raviga.app.ui.components.TertiaryButton
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType

/**
 * Short of a little credit, nothing counted: the smallest pack first, then all packs. R-099 (L, as iOS) for a new
 * brief; R-124 (v0.16.0) for 5 more AI edits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeedCreditsSheet(
    title: String,
    body: String,
    config: CreditsConfig,
    onBuy: (String) -> Unit,
    onAllPacks: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Ra.gutter).padding(top = 24.dp, bottom = 16.dp).navigationBarsPadding()) {
            Text(title, style = RaType.heading, color = Ink.ink)
            Spacer(Modifier.height(8.dp))
            Text(body, style = RaType.body, color = Ink.graphite)
            Spacer(Modifier.height(20.dp))
            val pack = BriefPricing.smallestPack(config)
            if (pack != null) {
                PrimaryButton(BriefPricing.buyLabel(config).orEmpty(), onClick = { onBuy(pack.productId) })
                Spacer(Modifier.height(8.dp))
            }
            SecondaryButton("See all packs", onClick = onAllPacks)
            Spacer(Modifier.height(4.dp))
            TertiaryButton("Not now", onClick = onDismiss)
        }
    }
}
