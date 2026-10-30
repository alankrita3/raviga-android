package com.raviga.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.material3.ripple
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink

private val ButtonShape = RoundedCornerShape(Ra.buttonRadius)

/** Ink fill, paper text, a little smaller while pressed. The one action that moves the flow forward. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(Ra.buttonHeight).graphicsLayer { scaleX = scale; scaleY = scale },
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink.ink,
            contentColor = Ink.paper,
            disabledContainerColor = Ink.ash.copy(alpha = 0.55f),
            disabledContentColor = Ink.paper,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 20.dp),
        interactionSource = interaction,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Ink.paper, strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = RaType.button)
        }
    }
}

/** 1.5dp strong rule outline, ink text. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(Ra.buttonHeight),
        shape = ButtonShape,
        border = BorderStroke(1.5.dp, if (enabled) Ink.ruleStrong else Ink.rule),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink.ink, disabledContentColor = Ink.ash),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = RaType.button)
    }
}

/** Link-blue text, no chrome. */
@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ink.link,
    fullWidth: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = (if (fullWidth) modifier.fillMaxWidth() else modifier).height(Ra.buttonHeight),
        shape = ButtonShape,
        colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = Ink.ash),
    ) {
        Text(text, style = RaType.secondaryMedium)
    }
}

/** Brick text, no chrome. */
@Composable
fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    TertiaryButton(text, onClick, modifier, enabled, color = Ink.brick)

/** Small link-blue text action used inline next to headings ("Edit", "Regenerate"). */
@Composable
fun InlineAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = Ink.link) {
    Text(
        text = text,
        style = RaType.secondaryMedium,
        color = if (enabled) color else Ink.ash,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** 72dp ink circle; while recording the glyph becomes a 20dp paper square. */
@Composable
fun MicButton(recording: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(Ra.micSize)
            .clip(CircleShape)
            .background(if (enabled) Ink.ink else Ink.ash)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(color = Ink.paper),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = if (recording) "Stop recording" else "Start recording" },
        contentAlignment = Alignment.Center,
    ) {
        if (recording) {
            Box(Modifier.size(Ra.stopGlyph).clip(RoundedCornerShape(3.dp)).background(Ink.paper))
        } else {
            Icon(icon, contentDescription = null, tint = Ink.paper, modifier = Modifier.size(30.dp))
        }
    }
}
