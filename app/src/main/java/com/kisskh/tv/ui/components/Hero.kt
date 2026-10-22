package com.kisskh.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhGradients

/**
 * The big artwork area on Home and Detail. On a wide screen the art sits on the right 70% and a
 * fade from the left keeps the text readable. On a narrow phone the art fills the area and is dimmed.
 * Either way the bottom fades into the page background so the rows below blend in.
 * Not a focus target. Put the text on top of it in the same Box.
 */
@Composable
fun HeroBackdrop(url: String?, title: String?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        if (maxWidth < 600.dp) {
            Artwork(url, title, Modifier.fillMaxSize(), fitPortrait = true, fadeMillis = 250, titleOnFailure = false)
            Box(Modifier.fillMaxSize().background(KhColors.Background.copy(alpha = 0.5f)))
        } else {
            // The fade is drawn over the art's own box, so it is solid at the art's left edge and no seam shows.
            Box(Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(0.7f)) {
                Artwork(url, title, Modifier.fillMaxSize(), fitPortrait = true, fadeMillis = 250, titleOnFailure = false)
                Box(Modifier.fillMaxSize().background(KhGradients.LeftFade))
            }
        }
        // On a narrow screen the text sits higher over the art, so the fade reaches higher too.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(if (maxWidth < 600.dp) 0.75f else 0.5f)
                .background(KhGradients.BottomFade),
        )
    }
}
