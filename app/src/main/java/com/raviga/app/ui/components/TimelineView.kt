package com.raviga.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.raviga.app.data.api.Milestone
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink
import com.raviga.app.util.Time

/**
 * Submitted, Reviewed, Approved, Building, Delivered, Accepted as a vertical
 * sequence: done nodes in ink, the current one a blue ring, the rest outlined.
 */
@Composable
fun TimelineView(
    milestones: List<Milestone>,
    note: String,
    modifier: Modifier = Modifier,
    estimatedDeliveryDate: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        milestones.forEachIndexed { index, m ->
            val last = index == milestones.lastIndex
            Row(Modifier.fillMaxWidth().height(IntrinsicHeightMin)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
                    Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) { Node(m.state) }
                    if (!last) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .weight(1f)
                                .background(if (m.state == "done") Ink.ink else Ink.rule),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 20.dp)) {
                    Text(
                        m.label,
                        style = if (m.state == "current") RaType.bodyMedium else RaType.body,
                        color = when (m.state) {
                            "upcoming" -> Ink.ash
                            "current" -> Ink.link
                            else -> Ink.ink
                        },
                    )
                    val line = when {
                        m.state == "done" && m.at != null -> Time.shortDate(m.at)
                        m.state == "current" -> "Now"
                        m.id == "delivered" && estimatedDeliveryDate != null -> "About ${Time.shortDate(estimatedDeliveryDate)}"
                        m.id == "delivered" -> "Date set after approval"
                        else -> null
                    }
                    if (line != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(line, style = RaType.caption, color = if (m.state == "current") Ink.link else Ink.graphite)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(note, style = RaType.caption, color = Ink.graphite)
    }
}

private val IntrinsicHeightMin = androidx.compose.foundation.layout.IntrinsicSize.Min

@Composable
private fun Node(state: String) {
    when (state) {
        "done" -> Box(Modifier.size(Ra.statusMark).clip(CircleShape).background(Ink.ink))
        "current" -> Box(Modifier.size(12.dp).clip(CircleShape).background(Ink.paper).border(1.5.dp, Ink.blue, CircleShape))
        else -> Box(Modifier.size(Ra.statusMark).clip(CircleShape).background(Ink.paper).border(1.dp, Ink.ruleStrong, CircleShape))
    }
}

/** "What happens next" and similar: numbered only because the content is a sequence. */
@Composable
fun NumberedSteps(steps: List<String>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        steps.forEachIndexed { i, step ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    "${i + 1}",
                    style = RaType.secondaryMedium,
                    color = Ink.graphite,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.width(16.dp).padding(top = 2.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(step, style = RaType.body, color = Ink.ink, modifier = Modifier.weight(1f))
            }
        }
    }
}
