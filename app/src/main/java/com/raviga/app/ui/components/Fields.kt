package com.raviga.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink

/** Surface fill, radius 16, 14dp padding, 1dp rule border; blue 1.5dp when focused, brick on error. */
@Composable
fun RaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = RaType.body,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    isError: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Ra.fieldRadius)
    val outline = when {
        isError -> Ink.brick
        focused -> Ink.blue
        else -> Ink.rule
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .background(Ink.surface, shape)
            .border(if (focused || isError) 1.5.dp else Ra.hairline, outline, shape)
            .padding(Ra.fieldPadding),
        enabled = enabled,
        textStyle = textStyle.copy(color = if (enabled) Ink.ink else Ink.ash),
        cursorBrush = SolidColor(Ink.link),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).heightIn(min = (textStyle.lineHeight.value * minLines).dp.coerceAtLeast(24.dp))) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = textStyle, color = Ink.ash)
                    }
                    inner()
                }
                if (trailing != null) trailing()
            }
        },
    )
}

/** Long text in a field box (typed description, transcript review): surface fill, rule border, blue when focused. */
@Composable
fun BoxedEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    minHeight: androidx.compose.ui.unit.Dp = 220.dp,
    textStyle: TextStyle = RaType.body,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Ra.fieldRadius)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(Ink.surface, shape)
            .border(if (focused) 1.5.dp else Ra.hairline, if (focused) Ink.blue else Ink.rule, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        textStyle = textStyle.copy(color = Ink.ink),
        cursorBrush = SolidColor(Ink.link),
        interactionSource = interaction,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && placeholder != null) Text(placeholder, style = textStyle, color = Ink.ash)
                inner()
            }
        },
    )
}

/** Borderless editor for long text (section bodies): paper background, body type. */
@Composable
fun PlainEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    textStyle: TextStyle = RaType.body,
    minLines: Int = 6,
    enabled: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        textStyle = textStyle.copy(color = Ink.ink),
        cursorBrush = SolidColor(Ink.link),
        minLines = minLines,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && placeholder != null) Text(placeholder, style = textStyle, color = Ink.ash)
                inner()
            }
        },
    )
}
