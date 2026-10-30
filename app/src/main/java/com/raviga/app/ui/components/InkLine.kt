package com.raviga.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.raviga.app.ui.theme.Ink
import kotlin.math.PI
import kotlin.math.sin

/**
 * The signature element: one thin line across the page that ripples with the
 * client's voice. Idle it is a flat rule-grey line; live it is blue. With
 * animations disabled it stays flat and pulses opacity instead.
 */
@Composable
fun InkLine(
    level: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduceMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE) == 0f }.getOrDefault(false)
    }
    val smooth = remember { Animatable(0f) }
    LaunchedEffect(level, active) {
        smooth.animateTo(if (active) level.coerceIn(0f, 1f) else 0f, tween(if (level > smooth.value) 60 else 220))
    }
    var phase by remember { mutableFloatStateOf(0f) }
    var pulse by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(active, reduceMotion) {
        if (!active) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val t = (now - start) / 1_000_000_000f
                phase = t
                pulse = 0.55f + 0.45f * ((sin(t * 2.2f) + 1f) / 2f)
            }
        }
    }

    Canvas(modifier.fillMaxWidth().height(56.dp)) {
        val w = size.width
        val mid = size.height / 2f
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        if (!active) {
            drawLine(Ink.rule, androidx.compose.ui.geometry.Offset(0f, mid), androidx.compose.ui.geometry.Offset(w, mid), strokeWidth = stroke.width, cap = StrokeCap.Round)
            return@Canvas
        }
        if (reduceMotion) {
            drawLine(Ink.blue.copy(alpha = pulse), androidx.compose.ui.geometry.Offset(0f, mid), androidx.compose.ui.geometry.Offset(w, mid), strokeWidth = stroke.width, cap = StrokeCap.Round)
            return@Canvas
        }
        val amp = smooth.value * mid * 0.9f
        val path = Path()
        val n = 160
        for (i in 0..n) {
            val t = i / n.toFloat()
            val x = w * t
            val env = sin(PI * t).toFloat()
            val wave = 0.55f * sin(2f * PI.toFloat() * (t * 2.6f + phase * 1.3f)) +
                0.30f * sin(2f * PI.toFloat() * (t * 5.8f - phase * 2.1f)) +
                0.15f * sin(2f * PI.toFloat() * (t * 11.3f + phase * 3.4f))
            val y = mid + amp * env * wave
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Ink.blue, style = stroke)
    }
}
