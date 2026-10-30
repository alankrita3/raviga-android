package com.raviga.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Workshop" colour tokens in the logo's colours (the founder's choice C, 2026-10-30; R-059/R-060), mirrored from
 * Raviga iOS/docs/DESIGN.md: cream paper, navy ink, the logo's blue for anything live, marigold for the moments worth
 * a smile. Light appearance only.
 */
object Ink {
    /** The page, and the launch screen. */
    val paper = Color(0xFFF6F3EE)
    /** Tiles, fields and sheets that sit on the page. */
    val surface = Color(0xFFFFFFFF)
    val ink = Color(0xFF14183A)
    val graphite = Color(0xFF474B66)
    /** A shade darker than the mockup's #6E7187, so captions keep 4.5:1 on the paper. */
    val ash = Color(0xFF686B81)
    /** Hairlines and tile borders. */
    val rule = Color(0xFFE6E1D6)
    /** Outline of secondary buttons. */
    val ruleStrong = Color(0xFFD3CDC0)
    /** Chips and quiet fills. */
    val wash = Color(0xFFECE7DD)
    /** Live state as a mark: dots, pills, progress, focus borders, the ink line. Too light for text (2.8:1 on the paper). */
    val blue = Color(0xFF6F8CFF)
    /** The live colour as text or a control: links, quiet buttons, switches, the cursor (5.8:1 on the paper). */
    val link = Color(0xFF3B54C4)
    /** Privacy notes, the delivered chip, the delivery tiles' glyphs. */
    val blueWash = Color(0xFFEAEEFF)
    val blueInk = Color(0xFF1E2A66)
    /** Highlights: the credits dot, the tier chip, the breakdown bar. */
    val marigold = Color(0xFFF2A33A)
    val marigoldWash = Color(0xFFFCE6BF)
    val marigoldInk = Color(0xFF5C3A00)
    val moss = Color(0xFF1F6F43)
    val amber = Color(0xFF9A5B00)
    val brick = Color(0xFFB3261E)
}
