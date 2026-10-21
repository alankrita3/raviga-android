package com.raviga.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raviga.app.R
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink

/** The clay illustrations (docs/DESIGN.md, Pictures). Each one belongs to one moment in the flow. */
enum class Picture(@DrawableRes val res: Int) {
    Welcome(R.drawable.illo_welcome),
    /** Onboarding step 1: the same person speaking, typing and uploading, ideas flowing into a brief. */
    Describe(R.drawable.illo_describe),
    HomeEmpty(R.drawable.illo_home_empty),
    Speak(R.drawable.illo_speak),
    Type(R.drawable.illo_type),
    Upload(R.drawable.illo_upload),
    Writing(R.drawable.illo_writing),
    Quote(R.drawable.illo_quote),
    Building(R.drawable.illo_building),
    /** Onboarding step 3 only: clay robots building the app, an engineer with a checklist approving. */
    AIBuild(R.drawable.illo_ai_build),
    Delivered(R.drawable.illo_delivered),
    Credits(R.drawable.illo_credits),
    Privacy(R.drawable.illo_privacy),
}

/**
 * A picture cropped to its frame around a focus point (0,0 keeps the top-left, 0.5,0.5 the
 * middle), never stretched, with rounded corners.
 */
@Composable
fun Illustration(
    picture: Picture,
    modifier: Modifier = Modifier,
    height: Dp? = null,
    radius: Dp = Ra.imageRadius,
    focusX: Float = 0.5f,
    focusY: Float = 0.5f,
) {
    Image(
        painter = painterResource(picture.res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(focusX * 2 - 1, focusY * 2 - 1),
        modifier = modifier
            .fillMaxWidth()
            .then(if (height != null) Modifier.height(height) else Modifier)
            .clip(RoundedCornerShape(radius)),
    )
}

/** A full-bleed picture at the top of a screen, under the status bar, with rounded bottom corners. */
@Composable
fun HeroIllustration(picture: Picture, height: Dp, modifier: Modifier = Modifier, focusY: Float = 0.5f) {
    Image(
        painter = painterResource(picture.res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(0f, focusY * 2 - 1),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(bottomStart = Ra.heroRadius, bottomEnd = Ra.heroRadius)),
    )
}

/** A whole square picture straight on the paper (the transparent ones: Writing, Credits, Privacy). */
@Composable
fun SpotIllustration(picture: Picture, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(picture.res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

/** White, softly bordered tile on the paper. For choices and grouped facts, not for every block of text. */
fun Modifier.tile(padding: Dp = 16.dp, onClick: (() -> Unit)? = null): Modifier {
    val shape = RoundedCornerShape(Ra.tileRadius)
    return this
        .fillMaxWidth()
        .clip(shape)
        .background(Ink.surface, shape)
        .border(Ra.hairline, Ink.rule, shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(padding)
}

enum class ChipTone { Plain, Teal, Marigold }

/** Small rounded label: a status, a tier, a promise. Optional 7dp dot or a small icon. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.Plain,
    dot: Color? = null,
    icon: ImageVector? = null,
    textStyle: androidx.compose.ui.text.TextStyle = RaType.chip,
    singleLine: Boolean = false,
) {
    val (fg, bg) = when (tone) {
        ChipTone.Plain -> Ink.ink to Ink.wash
        ChipTone.Teal -> Ink.tealInk to Ink.tealWash
        ChipTone.Marigold -> Ink.marigoldInk to Ink.marigoldWash
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            icon != null -> {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
            }
            dot != null -> {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
            }
        }
        Text(text, style = textStyle, color = fg, maxLines = if (singleLine) 1 else Int.MAX_VALUE, softWrap = !singleLine)
    }
}

/** One chip for [ChipRows]. */
data class ChipSpec(val text: String, val dot: Color? = null, val icon: ImageVector? = null, val tone: ChipTone = ChipTone.Plain)

/**
 * Chips in fixed rows that never wrap: when a row is wider than the screen, every chip's text
 * shrinks by the same amount (to [minScale] at most) so the rows keep their shape (Welcome on a
 * narrow phone; same fix as iOS).
 */
@Composable
fun ChipRows(rows: List<List<ChipSpec>>, modifier: Modifier = Modifier, minScale: Float = 0.8f) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Text draws with the theme's style merged under ours (its letter spacing included), so measure the same.
    val base = androidx.compose.material3.LocalTextStyle.current.merge(RaType.chip)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = constraints.maxWidth.toFloat()
        // Text shaped at a smaller size isn't exactly proportional; leave room instead of landing on the edge.
        val slack = with(density) { 8.dp.toPx() }
        val needed = remember(rows, available, density, base) {
            rows.minOf { row ->
                val text = row.sumOf { measurer.measure(it.text, base, maxLines = 1).size.width }.toFloat()
                val chrome = with(density) { (row.fold(0.dp) { acc, c -> acc + chipChrome(c) } + 8.dp * (row.size - 1)).toPx() }
                (available - chrome - slack) / text
            }
        }
        val scale = needed.coerceIn(minScale, 1f)
        // Past minScale (very large font settings) the chips wrap their words rather than cut them off.
        val oneLine = needed >= minScale
        val style = if (scale < 1f) RaType.chip.copy(fontSize = RaType.chip.fontSize * scale, lineHeight = RaType.chip.lineHeight * scale, letterSpacing = base.letterSpacing * scale) else RaType.chip
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { Chip(it.text, tone = it.tone, dot = it.dot, icon = it.icon, textStyle = style, singleLine = oneLine) }
                }
            }
        }
    }
}

/** A chip's width besides its text: 11dp padding each side, plus the icon or dot and its gap. */
private fun chipChrome(chip: ChipSpec): Dp = 22.dp + when {
    chip.icon != null -> 13.dp + 6.dp
    chip.dot != null -> 7.dp + 6.dp
    else -> 0.dp
}

/** Chips laid out in rows, wrapping when the width runs out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(modifier: Modifier = Modifier, perRow: Int = Int.MAX_VALUE, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = perRow,
    ) { content() }
}

/** The teal note with a lock: what stays private. With a [title], the title leads in bold. */
@Composable
fun PrivacyNote(text: String, modifier: Modifier = Modifier, title: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ra.imageRadius))
            .background(Ink.tealWash)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Ink.tealInk, modifier = Modifier.padding(top = 3.dp).size(16.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            if (title != null) {
                Text(title, style = RaType.bodyMedium, color = Ink.tealInk)
                Spacer(Modifier.height(4.dp))
            }
            Text(text, style = if (title != null) RaType.secondary else RaType.secondaryMedium, color = Ink.tealInk)
        }
    }
}

/** Said once, warmly, right before the client hands over their idea (Quote). */
@Composable
fun PrivacyPromise(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ra.imageRadius))
            .background(Ink.tealWash)
            .semantics(mergeDescendants = true) {}
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpotIllustration(Picture.Privacy, size = 72.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Your idea stays yours", style = RaType.bodyMedium, color = Ink.tealInk)
            Spacer(Modifier.height(6.dp))
            Text(
                "We use your brief only to build this project. We never sell it or use it to train AI, and we delete it from our servers when you accept the delivery.",
                style = RaType.secondary,
                color = Ink.tealInk,
            )
        }
    }
}
