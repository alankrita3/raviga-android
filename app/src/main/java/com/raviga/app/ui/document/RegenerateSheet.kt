package com.raviga.app.ui.document

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raviga.app.ui.components.RaTextField
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ProgressRule
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink

/** Optional instruction, then "Regenerate". Stays open while the job runs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegenerateSheet(
    heading: String,
    busyMessage: String?,
    busyProgress: Float?,
    onDismiss: () -> Unit,
    onRegenerate: (String) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var instruction by rememberSaveable { mutableStateOf("") }   // review #3: kept through a rebuild
    ModalBottomSheet(
        onDismissRequest = { if (busyMessage == null) onDismiss() },
        sheetState = sheet,
        containerColor = Ink.surface,
        dragHandle = null,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Ra.gutter).padding(top = 24.dp, bottom = 16.dp).navigationBarsPadding()) {
            Text("Regenerate $heading", style = RaType.heading, color = Ink.ink)
            Spacer(Modifier.height(8.dp))
            Text("Tell the AI what to change, or leave it blank for a fresh take.", style = RaType.secondary, color = Ink.graphite)
            Spacer(Modifier.height(16.dp))
            if (busyMessage != null) {
                ProgressRule(progress = busyProgress?.takeIf { it > 0f })
                Spacer(Modifier.height(12.dp))
                Text(busyMessage, style = RaType.secondary, color = Ink.graphite)
                Spacer(Modifier.height(24.dp))
            } else {
                RaTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    placeholder = "Make it shorter, add offline mode…",
                    label = "What to change",
                    minLines = 2,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Regenerate", onClick = { onRegenerate(instruction) })
            }
        }
    }
}
