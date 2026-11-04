package com.raviga.app.ui.capture

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raviga.app.data.drafts.LocalInput
import com.raviga.app.ui.components.tile
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.util.Time

/** R-108 (her decision): one part of the description (one note), as Capture and the draft list it. */
data class Part(val id: String, val source: String, val text: String, val createdAt: String)

/** Where a part came from, as the draft says it: "Spoken", "Typed" or "From menu.pdf," (followed by when). */
fun partSource(kind: String, fileName: String?): String = when (kind) {
    "voice" -> "Spoken"
    "file" -> "From ${fileName ?: "a document"},"
    else -> "Typed"
}

fun LocalInput.toPart() = Part(id, partSource(kind, fileName), text, createdAt)

/** The parts in order, each in a tile with its type and when: the review and the choices show them (R-108). */
@Composable
fun PartTiles(parts: List<Part>) {
    parts.forEach { part ->
        Column(Modifier.tile()) {
            Text(part.text, style = RaType.body, color = Ink.ink, maxLines = 6, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(part.source + " " + Time.relative(part.createdAt), style = RaType.caption, color = Ink.graphite)
        }
        Spacer(Modifier.height(Ra.tileGap))
    }
}
