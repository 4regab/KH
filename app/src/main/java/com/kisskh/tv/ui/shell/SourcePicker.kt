@file:OptIn(ExperimentalComposeUiApi::class)

package com.kisskh.tv.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.LIBRARY_ORDER
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme

/** Each source gets a colour, like a Netflix profile, so the current one is known at a glance. */
fun sourceBrush(library: Library): Brush = when (library) {
    Library.CINEWAVE -> Brush.linearGradient(listOf(Color(0xFF1F6BFF), Color(0xFF00B8E6)))
    Library.CINEJOY -> Brush.linearGradient(listOf(Color(0xFF7B2FF7), Color(0xFFD63AF9)))
    Library.REELIX -> Brush.linearGradient(listOf(Color(0xFF0E8F6E), Color(0xFF3DDC97)))
    Library.ANISUGE -> Brush.linearGradient(listOf(Color(0xFFFF6A00), Color(0xFFFFB020)))
    Library.NEXUS -> Brush.linearGradient(listOf(Color(0xFF5B4DFF), Color(0xFF9B6BFF)))
    Library.KISSKH -> Brush.linearGradient(listOf(Color(0xFFE50914), Color(0xFFFF6A3D)))
}

fun sourceMark(library: Library): String = when (library) {
    Library.KISSKH -> "KH"
    Library.CINEWAVE -> "CW"
    Library.CINEJOY -> "CJ"
    Library.REELIX -> "RX"
    Library.ANISUGE -> "AS"
    Library.NEXUS -> "NX"
}

/** One line on what each source is for, shown under its name in the picker. */
private fun sourceBlurb(library: Library): String = when (library) {
    Library.CINEWAVE -> "Movies, TV and anime"
    Library.CINEJOY -> "Movies and TV, built-in player"
    Library.REELIX -> "Movies, TV and anime"
    Library.ANISUGE -> "Anime, sub and dub"
    Library.NEXUS -> "Anime seasons and episodes"
    Library.KISSKH -> "Asian dramas and movies"
}

/** The source's coloured square with its two letters. */
@Composable
fun SourceAvatar(library: Library, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(sourceBrush(library), RoundedCornerShape(size * 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            sourceMark(library),
            color = Color.White,
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
        )
    }
}

/**
 * "Choose a source", full screen over the app, laid out like Netflix's "Who's watching?".
 * Focus starts on the current source and cannot leave the picker. Back closes it unchanged.
 */
@Composable
fun SourcePicker(current: Library, onPick: (Library) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val requesters = remember { LIBRARY_ORDER.associateWith { FocusRequester() } }
    var shown by remember { mutableStateOf(false) }
    val appear by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(KhMotion.CrossfadeMillis, easing = KhMotion.EaseOutStrong),
        label = "picker",
    )
    val allowed = com.kisskh.tv.ui.components.rememberAutoFocusAllowed()
    LaunchedEffect(Unit) {
        shown = true
        if (allowed()) runCatching { requesters.getValue(current).requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear }
            .background(KhColors.Background)
            // Taps on the empty background must not reach the screen underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val narrow = maxWidth < 600.dp
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp)
                    .graphicsLayer {
                        val s = 0.96f + 0.04f * appear
                        scaleX = s
                        scaleY = s
                    },
            ) {
                Text(
                    "Choose a source",
                    style = if (narrow) KhTheme.typography.headline else KhTheme.typography.heading,
                    color = KhColors.TextPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Each source has its own catalog and servers.",
                    style = KhTheme.typography.caption,
                    color = KhColors.TextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (narrow) {
                    Column(
                        Modifier.padding(top = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        LIBRARY_ORDER.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                pair.forEach { library ->
                                    SourceTile(library, library == current, 150.dp, 100.dp, requesters.getValue(library)) { onPick(library) }
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        Modifier
                            .padding(top = 36.dp)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        LIBRARY_ORDER.forEach { library ->
                            SourceTile(library, library == current, 176.dp, 132.dp, requesters.getValue(library)) { onPick(library) }
                        }
                    }
                }
                if (narrow) {
                    KhChip("Cancel", onClick = onDismiss, outlined = true, modifier = Modifier.padding(top = 28.dp))
                }
            }
        }
    }
}

/** One source: its coloured avatar (white ring on focus, as on Netflix's profile picker), name and a line about it. */
@Composable
private fun SourceTile(
    library: Library,
    on: Boolean,
    width: Dp,
    avatar: Dp,
    requester: FocusRequester,
    onPick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val grow by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(KhMotion.FocusMillis, easing = KhMotion.EaseOutStrong),
        label = "avatar",
    )
    Box(
        Modifier
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onPick)
            .semantics {
                contentDescription = "${library.label}. ${sourceBlurb(library)}"
                selected = on
            },
    ) {
        Column(Modifier.width(width).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SourceAvatar(
                library,
                size = avatar,
                modifier = Modifier
                    .graphicsLayer {
                        val s = 1f + 0.06f * grow
                        scaleX = s
                        scaleY = s
                    }
                    .border(
                        width = 3.dp,
                        color = when {
                            focused -> KhColors.TextPrimary
                            on -> KhColors.Brand
                            else -> Color.Transparent
                        },
                        shape = RoundedCornerShape(avatar * 0.18f),
                    ),
            )
            Text(
                library.label,
                style = KhTheme.typography.title,
                color = if (focused || on) KhColors.TextPrimary else KhColors.TextSecondary,
                maxLines = 1,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                if (on) "Watching now" else sourceBlurb(library),
                style = KhTheme.typography.caption,
                color = if (on) KhColors.Brand else KhColors.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
