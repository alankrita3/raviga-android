package com.raviga.app.ui.capture

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raviga.app.ui.LocalAppContainer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import com.raviga.app.ui.components.Illustration
import com.raviga.app.ui.components.Picture
import com.raviga.app.ui.components.PrivacyNote
import com.raviga.app.ui.components.SectionHeading
import com.raviga.app.ui.components.tile
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink

/** The three ways to describe a project; the value is the Capture route's `tab`. */
object DescribeWith {
    const val SPEAK = "speak"
    const val TYPE = "type"
    const val UPLOAD = "upload"
}

/**
 * "Describe your project" (or "Add to <title>"): Speak, Type or Upload a
 * document. Opened from Home and from the brief's "Add more".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescribeChooserSheet(projectTitle: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Ink.paper,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        DescribeChoices(projectTitle, onPick, Modifier.padding(top = 28.dp, bottom = 20.dp).navigationBarsPadding())
    }
}

/** The three choices; also shown on Capture after "Add more", with the [parts] so far above them (R-108). */
@Composable
fun DescribeChoices(projectTitle: String?, onPick: (String) -> Unit, modifier: Modifier = Modifier, parts: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = Ra.gutter)) {
        Text(
            if (projectTitle == null) "Describe your project" else "Add to ${projectTitle.ifBlank { "this project" }}",
            style = RaType.title,
            color = Ink.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (projectTitle == null) "Whichever is easiest. You can mix them, and edit everything after." else "Add more detail, whichever way is easiest.",
            style = RaType.secondary,
            color = Ink.graphite,
        )
        Spacer(Modifier.height(24.dp))
        if (parts != null) {
            SectionHeading("Your description")
            Spacer(Modifier.height(12.dp))
            parts()
            Spacer(Modifier.height(12.dp))
            SectionHeading("Add more")
            Spacer(Modifier.height(12.dp))
        }
        Choice(Picture.Speak, "Speak", "Talk it through. We write it up.") { onPick(DescribeWith.SPEAK) }
        Spacer(Modifier.height(Ra.tileGap))
        Choice(Picture.Type, "Type", "Write or paste a description.") { onPick(DescribeWith.TYPE) }
        Spacer(Modifier.height(Ra.tileGap))
        Choice(Picture.Upload, "Upload a document", "A PDF, Word or text file you already have.") { onPick(DescribeWith.UPLOAD) }
        Spacer(Modifier.height(20.dp))
        PrivacyNote(
            "Your recordings and files never leave your phone. Drafts are kept only on this phone.",
            title = "Your idea stays private",
        )
    }
}

/** One way of describing: its picture, a heading and one line, in a tile. */
@Composable
private fun Choice(picture: Picture, title: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.tile(padding = 12.dp, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Illustration(picture, modifier = Modifier.size(84.dp), height = 84.dp, radius = 18.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = RaType.heading, color = Ink.ink)
            Spacer(Modifier.height(4.dp))
            Text(detail, style = RaType.secondary, color = Ink.graphite)
        }
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Ink.ash, modifier = Modifier.size(22.dp))
    }
}
