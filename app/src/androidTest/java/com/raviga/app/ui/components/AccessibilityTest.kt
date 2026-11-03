package com.raviga.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R-092 #40 (iOS review): fields keep their name once typed in, a switch's row says what the switch is for, and quiet
 * text actions are 48dp to tap.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {

    @get:Rule val rule = createComposeRule()

    @Test fun aFieldKeepsItsNameOnceSomethingIsTyped() {
        var value by mutableStateOf("")
        rule.setContent { RaTextField(value = value, onValueChange = { value = it }, placeholder = "octocat", label = "GitHub username") }
        assertEquals(listOf("GitHub username"), names())
        rule.onNode(hasSetTextAction()).performTextInput("dogwalker")
        rule.waitForIdle()
        assertEquals("dogwalker", value)
        assertEquals("still named once the placeholder is gone", listOf("GitHub username"), names())
    }

    @Test fun thePlaceholderNamesAFieldWithoutALabelAndIsNotReadTwice() {
        rule.setContent { RaTextField(value = "", onValueChange = {}, placeholder = "Recovery key") }
        assertEquals(listOf("Recovery key"), names())
        rule.onAllNodesWithText("Recovery key", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun aSwitchRowSaysWhatTheSwitchIsFor() {
        var on by mutableStateOf(false)
        rule.setContent {
            Row(Modifier.switchRow(on) { on = it }) {
                Text("Usage analytics")
                Switch(checked = on, onCheckedChange = null)
            }
        }
        val row = rule.onNode(isToggleable())
        val text = row.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString { it.text }
        assertTrue("the switch's node carries its subject, not just On/Off: $text", "Usage analytics" in text)
        row.assertIsOff()
        row.performClick()
        row.assertIsOn()
        assertTrue(on)
    }

    @Test fun quietTextActionsAre48dpToTap() {
        rule.setContent { InlineAction("Send", onClick = {}) }
        rule.onNodeWithText("Send").assertTouchHeightIsEqualTo(48.dp)
    }

    /** Review #20: a row that opens something says it is a button. */
    @Test fun aRowThatOpensSomethingIsAButton() {
        rule.setContent { RaRow(title = "Delivery targets", onClick = {}) }
        val role = rule.onNode(androidx.compose.ui.test.hasClickAction()).fetchSemanticsNode().config.let { if (SemanticsProperties.Role in it) it[SemanticsProperties.Role] else null }
        assertEquals(androidx.compose.ui.semantics.Role.Button, role)
    }

    /** A recovery code field tells autofill it's a one-time code, so it's never offered to be saved as a password. */
    @Test fun aCodeFieldIsAOneTimeCodeForAutofill() {
        rule.setContent {
            RaTextField(value = "", onValueChange = {}, placeholder = "6-digit code", contentType = androidx.compose.ui.autofill.ContentType.SmsOtpCode)
        }
        val config = rule.onNode(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNode().config
        assertEquals(androidx.compose.ui.autofill.ContentType.SmsOtpCode, if (SemanticsProperties.ContentType in config) config[SemanticsProperties.ContentType] else null)
    }

    /** Content descriptions on the text field's node and the plain nodes inside it: what TalkBack reads with the field. */
    private fun names(): List<String> {
        fun collect(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
            node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() } +
                node.children.filterNot { it.config.isMergingSemanticsOfDescendants }.flatMap { collect(it) }
        return collect(rule.onNode(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNode()).distinct()
    }
}
