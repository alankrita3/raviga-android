package com.raviga.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.raviga.app.data.drafts.DraftStore

/**
 * R-094 P2-4 (iOS review 2): "Couldn't save your drafts on this phone" stays while saves fail ([failing], the drafts
 * store's saveFailing) and goes once one succeeds. [shown] is the screen's own error line, so it isn't said twice.
 */
@Composable
fun SaveFailingNotice(failing: Boolean, shown: String?, modifier: Modifier = Modifier) {
    if (failing && shown != DraftStore.SAVE_FAILED) InlineNotice(DraftStore.SAVE_FAILED, modifier)
}
