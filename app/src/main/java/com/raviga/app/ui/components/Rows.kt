package com.raviga.app.ui.components

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.raviga.app.ui.status.StatusCopy
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink

/** Full-width row, hairline below, chevron only when it navigates. */
@Composable
fun RaRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = onClick != null,
    titleColor: Color = Ink.ink,
    subtitleColor: Color = Ink.graphite,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    hairline: Boolean = true,
    horizontalPadding: androidx.compose.ui.unit.Dp = Ra.gutter,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)   // review #20
                .padding(horizontal = horizontalPadding, vertical = Ra.rowPadding + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = RaType.bodyMedium, color = titleColor)
                if (subtitle != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, style = RaType.secondary, color = subtitleColor)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
            if (chevron) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Ink.ash, modifier = Modifier.size(22.dp))
            }
        }
        if (hairline) Hairline(Modifier.padding(horizontal = horizontalPadding))
    }
}

/** 8dp filled circle coloured by status. */
@Composable
fun StatusMark(status: String, modifier: Modifier = Modifier) {
    Box(modifier.size(Ra.statusMark).clip(CircleShape).background(StatusCopy.markColor(status)))
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = Ra.statusMark) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}
