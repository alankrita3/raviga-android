package com.raviga.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.raviga.app.R
import com.raviga.app.ui.theme.Ink

/** The wordmark's width over its height, cut tight as on iOS (viewBox 0 64 523 194). */
private const val WORDMARK_RATIO = 523f / 194f
private val LAUNCH_WIDTH = 92.dp

/**
 * The R-045 wordmark: navy letters, the i's blue square (`tools/wordmark_vectors.py`). Size it by height or width; it
 * keeps its proportions. Only on the launch and loading screens and Welcome page 1.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Image(
        painterResource(R.drawable.ic_wordmark),
        contentDescription = "Raviga",
        modifier = modifier.aspectRatio(WORDMARK_RATIO),
    )
}

/**
 * What shows while the app gets ready: the launch screen's wordmark at the same size and place (92dp wide, centred on
 * the window), so the hand-over doesn't jump, with a 92dp progress bar ending 28dp below it.
 */
@Composable
fun LaunchScreen() {
    Box(Modifier.fillMaxSize().background(Ink.paper), contentAlignment = Alignment.Center) {
        Wordmark(Modifier.width(LAUNCH_WIDTH))
        // The bar is 4dp tall and centred like the wordmark; this puts its bottom 28dp below the wordmark's bottom.
        ProgressRule(Modifier.width(LAUNCH_WIDTH).offset(y = LAUNCH_WIDTH / WORDMARK_RATIO / 2 + 28.dp - 2.dp))
    }
}
