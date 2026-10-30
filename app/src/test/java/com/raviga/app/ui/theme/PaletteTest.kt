package com.raviga.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Palette C (the founder's choice, 2026-10-30; R-059/R-060): the same tokens as iOS's Theme.swift. */
class PaletteTest {

    private fun hex(c: Color) = String.format("#%06X", c.toArgb() and 0xFFFFFF)

    private fun luminance(c: Color): Double {
        fun ch(v: Float) = if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `tokens match iOS`() {
        val expected = mapOf(
            "paper" to "#F6F3EE", "surface" to "#FFFFFF", "ink" to "#14183A", "graphite" to "#474B66", "ash" to "#686B81",
            "rule" to "#E6E1D6", "ruleStrong" to "#D3CDC0", "wash" to "#ECE7DD", "blue" to "#6F8CFF", "link" to "#3B54C4",
            "blueWash" to "#EAEEFF", "blueInk" to "#1E2A66", "marigold" to "#F2A33A", "marigoldWash" to "#FCE6BF",
            "marigoldInk" to "#5C3A00", "moss" to "#1F6F43", "amber" to "#9A5B00", "brick" to "#B3261E",
        )
        val actual = mapOf(
            "paper" to Ink.paper, "surface" to Ink.surface, "ink" to Ink.ink, "graphite" to Ink.graphite, "ash" to Ink.ash,
            "rule" to Ink.rule, "ruleStrong" to Ink.ruleStrong, "wash" to Ink.wash, "blue" to Ink.blue, "link" to Ink.link,
            "blueWash" to Ink.blueWash, "blueInk" to Ink.blueInk, "marigold" to Ink.marigold, "marigoldWash" to Ink.marigoldWash,
            "marigoldInk" to Ink.marigoldInk, "moss" to Ink.moss, "amber" to Ink.amber, "brick" to Ink.brick,
        ).mapValues { hex(it.value) }
        assertEquals(expected, actual)
    }

    @Test
    fun `text colours read on the paper, and the logo blue is kept for marks`() {
        for ((name, c) in listOf("ink" to Ink.ink, "graphite" to Ink.graphite, "ash" to Ink.ash, "link" to Ink.link)) {
            assertTrue("$name on paper is ${contrast(c, Ink.paper)}", contrast(c, Ink.paper) >= 4.5)
        }
        assertTrue(contrast(Ink.link, Ink.paper) > 5.7)                  // iOS: 5.8:1
        assertTrue(contrast(Ink.ash, Ink.paper) > 4.6)                   // iOS: 4.7:1 (the mockup's #6E7187 was 4.3)
        assertTrue(contrast(Ink.blue, Ink.paper) < 3.0)                  // 2.8:1: never text
        assertTrue(contrast(Ink.blueInk, Ink.blueWash) >= 4.5)           // privacy notes, the delivered chip
        assertTrue(contrast(Ink.surface, Ink.link) >= 4.5)               // the white check on a done Go-live step
    }
}
