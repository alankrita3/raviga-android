package com.raviga.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink

/** A rounded 4dp blue bar on a wash track. Determinate when [progress] is given, otherwise a sweep. */
@Composable
fun ProgressRule(modifier: Modifier = Modifier, progress: Float? = null) {
    val target = progress?.coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = target ?: 0f, animationSpec = tween(300), label = "progress")
    val sweep = rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "sweepValue",
    )
    Canvas(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))) {
        val w = size.width
        val h = size.height
        val y = h / 2
        drawLine(Ink.wash, Offset(0f, y), Offset(w, y), strokeWidth = h)
        if (target != null) {
            if (animated > 0f) drawLine(Ink.blue, Offset(h / 2, y), Offset((w * animated).coerceAtLeast(h / 2), y), strokeWidth = h, cap = StrokeCap.Round)
        } else {
            val span = w * 0.3f
            val start = -span + (w + span) * sweep.value
            drawLine(Ink.blue, Offset(start, y), Offset(start + span, y), strokeWidth = h, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(Ra.gutter), contentAlignment = Alignment.Center) {
        Text(text, style = RaType.body, color = Ink.graphite, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier.fillMaxSize().padding(Ra.gutter), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Text(message, style = RaType.body, color = Ink.graphite, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(8.dp))
            TertiaryButton("Try again", onClick = onRetry, fullWidth = false)
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = Ra.gutter, vertical = 24.dp)) {
        ProgressRule()
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(message, style = RaType.secondary, color = Ink.graphite)
        }
    }
}

/** One quiet line of error copy under a form or a bar, in brick. */
@Composable
fun InlineNotice(text: String?, modifier: Modifier = Modifier, color: Color = Ink.brick) {
    if (text.isNullOrBlank()) return
    Text(text, style = RaType.secondary, color = color, modifier = modifier.fillMaxWidth().padding(top = 8.dp))
}

/** Capture's ways of describing: plain text tabs, the selected one in ink with a 2dp underline. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled, role = Role.Tab) { onSelect(i) }
                    .semantics { this.selected = on }
                    .padding(vertical = 6.dp),
            ) {
                Text(label, style = RaType.bodyMedium, color = if (on) Ink.ink else Ink.ash)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (on) Ink.ink else Color.Transparent))
            }
            if (i < options.lastIndex) Spacer(Modifier.width(24.dp))
        }
    }
}

/** Label on the left, value on the right, hairline below. */
@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Ink.ink) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = Ra.rowPadding), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = RaType.body, color = Ink.graphite, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(value, style = RaType.body, color = valueColor, textAlign = TextAlign.End, modifier = Modifier.weight(1.6f))
        }
        Hairline()
    }
}
