package com.raviga.app.ui.components

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink

/** Bricolage section heading as the document's voice. A heading for TalkBack's heading navigation (QA R4). */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier, color: Color = Ink.ink) {
    Text(text, style = RaType.heading, color = color, modifier = modifier.semantics { heading() })
}

/**
 * Renders the backend's markdown-lite: paragraphs, "- " bullets with real
 * bullet glyphs, and **bold**. Nothing else is supported by the contract.
 */
@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = RaType.body,
    color: Color = Ink.ink,
) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(if (block is Block.Bullet && blocks[index - 1] is Block.Bullet) 6.dp else 12.dp))
            when (block) {
                is Block.Paragraph -> Text(inline(block.text), style = style, color = color)
                is Block.Bullet -> Row(Modifier.fillMaxWidth()) {
                    Text("•", style = style, color = color, modifier = Modifier.width(18.dp))
                    Text(inline(block.text), style = style, color = color, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** What an empty section shows: its hint, in ash italics. */
@Composable
fun SectionHint(hint: String, modifier: Modifier = Modifier) {
    Text(hint, style = RaType.body.copy(fontStyle = FontStyle.Italic), color = Ink.ash, modifier = modifier)
}

private sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Bullet(val text: String) : Block
}

private fun parseBlocks(text: String): List<Block> {
    val out = mutableListOf<Block>()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out += Block.Paragraph(para.toString().trim())
        para.clear()
    }
    text.lines().forEach { raw ->
        val line = raw.trimEnd()
        val trimmed = line.trimStart()
        when {
            trimmed.isBlank() -> flush()
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                flush()
                out += Block.Bullet(trimmed.drop(2).trim())
            }
            else -> {
                if (para.isNotEmpty()) para.append(' ')
                para.append(trimmed)
            }
        }
    }
    flush()
    return out
}

/** `**bold**` only. */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val start = text.indexOf("**", i)
        if (start < 0) { append(text.substring(i)); break }
        val end = text.indexOf("**", start + 2)
        if (end < 0) { append(text.substring(i)); break }
        append(text.substring(i, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(text.substring(start + 2, end)) }
        i = end + 2
    }
}

@Composable
private fun <T> remember(key: Any?, calc: () -> T): T = androidx.compose.runtime.remember(key) { calc() }
