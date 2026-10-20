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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme

/** Each source gets a colour, like a Netflix profile, so the current one is known at a glance. */
fun sourceBrush(library: Library): Brush = when (library) {
    Library.CINEWAVE -> Brush.linearGradient(listOf(Color(0xFF1F6BFF), Color(0xFF00B8E6)))
    Library.CINEJOY -> Brush.linearGradient(listOf(Color(0xFF7B2FF7), Color(0xFFD63AF9)))
    Library.KISSKH -> Brush.linearGradient(listOf(Color(0xFFE50914), Color(0xFFFF6A3D)))
}

fun sourceMark(library: Library): String = when (library) {
    Library.KISSKH -> "KH"
    Library.CINEWAVE -> "CW"
    Library.CINEJOY -> "CJ"
}

/** One line on what each source is for, shown under its name in the picker. */
private fun sourceBlurb(library: Library): String = when (library) {
    Library.CINEWAVE -> "Movies, TV and anime"
    Library.CINEJOY -> "Movies and TV, built-in player"
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
    LaunchedEffect(Unit) {
        shown = true
        runCatching { requesters.getValue(current).requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear }
            .background(KhColors.Background)
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val s = 0.96f + 0.04f * appear
                scaleX = s
                scaleY = s
            },
        ) {
            Text(
                "Choose a source",
                style = KhTheme.typography.heading,
                color = KhColors.TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Row(
                Modifier.padding(top = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
            ) {
                LIBRARY_ORDER.forEach { library ->
                    val on = library == current
                    // Focus is the white ring on the avatar only, as on Netflix's profile picker.
                    var focused by remember { mutableStateOf(false) }
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .focusRequester(requesters.getValue(library))
                            .onFocusChanged { focused = it.isFocused }
                            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onPick(library) }
                            .semantics {
                                contentDescription = "${library.label}. ${sourceBlurb(library)}"
                                selected = on
                            },
                    ) {
                        Column(
                            Modifier.width(176.dp).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val grow by animateFloatAsState(
                                targetValue = if (focused) 1f else 0f,
                                animationSpec = tween(KhMotion.FocusMillis, easing = KhMotion.EaseOutStrong),
                                label = "avatar",
                            )
                            SourceAvatar(
                                library,
                                size = 132.dp,
                                modifier = Modifier
                                    .graphicsLayer {
                                        val s = 1f + 0.06f * grow
                                        scaleX = s
                                        scaleY = s
                                    }
                                    .border(
                                        width = 3.dp,
                                        color = if (focused) KhColors.TextPrimary else Color.Transparent,
                                        shape = RoundedCornerShape(24.dp),
                                    ),
                            )
                            Text(
                                library.label,
                                style = KhTheme.typography.title,
                                color = if (focused || on) KhColors.TextPrimary else KhColors.TextSecondary,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                            Text(
                                if (on) "Watching now" else sourceBlurb(library),
                                style = KhTheme.typography.caption,
                                color = if (on) KhColors.Brand else KhColors.TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
