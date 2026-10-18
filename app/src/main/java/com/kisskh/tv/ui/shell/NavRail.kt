@file:OptIn(ExperimentalComposeUiApi::class)

package com.kisskh.tv.ui.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.R
import com.kisskh.tv.ui.RootTab
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme

/** Collapsed icon rail. Content is padded by this and does not move when the rail opens. */
val RailCollapsedWidth = 72.dp

/** Expanded rail, drawn over the content. */
val RailExpandedWidth = 260.dp

private val ItemHeight = 48.dp

fun iconFor(tab: RootTab): Int = when (tab) {
    RootTab.HOME -> R.drawable.ic_nav_home
    RootTab.SEARCH -> R.drawable.ic_nav_search
    RootTab.SERIES -> R.drawable.ic_nav_series
    RootTab.MOVIES -> R.drawable.ic_nav_movies
    RootTab.COUNTRIES -> R.drawable.ic_nav_countries
}

/**
 * Left navigation. Collapsed it is icons only. Focus expands it over the page (the page does not
 * shift) and fades the names in. Selected is a white icon plus a red bar; focus is the white outline,
 * so the two never look the same.
 *
 * Right or Back (handled by the shell) leaves the rail and returns to the last content item.
 * Focus enters on the selected item.
 */
@Composable
fun NavRail(
    active: RootTab,
    onSelect: (RootTab) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onEnterContent: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val expand by animateFloatAsState(
        targetValue = if (hasFocus) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (hasFocus) KhMotion.RailExpandMillis else KhMotion.RailCollapseMillis,
            easing = KhMotion.EaseOutStrong,
        ),
        label = "railExpand",
    )
    val requesters = remember { RootTab.entries.associateWith { FocusRequester() } }
    val currentActive by rememberUpdatedState(active)
    val labelAlpha = ((expand - 0.35f) / 0.65f).coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxHeight()
            .width(lerp(RailCollapsedWidth, RailExpandedWidth, expand))
            .background(KhColors.Background.copy(alpha = 0.94f * expand))
            .onPreviewKeyEvent { it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight && onEnterContent() }
            .onFocusChanged {
                hasFocus = it.hasFocus
                onFocusChanged(it.hasFocus)
            }
            .focusProperties { enter = { requesters.getValue(currentActive) } }
            .focusGroup(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = KhTheme.spacing.safeVertical, bottom = KhTheme.spacing.safeVertical),
        ) {
            Box(Modifier.width(RailCollapsedWidth).height(36.dp), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.kh_wordmark),
                    contentDescription = stringResource(R.string.kh_logo_description),
                    modifier = Modifier.width(40.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Column {
                RootTab.entries.forEach { tab ->
                    RailItem(
                        tab = tab,
                        selected = tab == active,
                        labelAlpha = labelAlpha,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.focusRequester(requesters.getValue(tab)),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RailItem(
    tab: RootTab,
    selected: Boolean,
    labelAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier
            .width(if (labelAlpha > 0.2f) RailExpandedWidth else RailCollapsedWidth)
            .height(ItemHeight)
            .semantics {
                contentDescription = tab.label
                this.selected = selected
            },
        shape = RoundedCornerShape(8.dp),
        scaleAndLift = false,
    ) { focused ->
        val tint = if (selected || focused) KhColors.TextPrimary else KhColors.TextSecondary
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(RailCollapsedWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(iconFor(tab)),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
                if (selected) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 6.dp)
                            .width(3.dp)
                            .height(22.dp)
                            .background(KhColors.Brand, RoundedCornerShape(2.dp)),
                    )
                }
            }
            Text(
                tab.label,
                style = KhTheme.typography.railLabel,
                color = tint,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.graphicsLayer { alpha = labelAlpha },
            )
        }
    }
}
