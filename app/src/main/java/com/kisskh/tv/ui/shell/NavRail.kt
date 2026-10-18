@file:OptIn(ExperimentalComposeUiApi::class)

package com.kisskh.tv.ui.shell

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.R
import com.kisskh.tv.ui.RootTab
import com.kisskh.tv.ui.components.rememberIsTelevision
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

private val ItemSize = 48.dp
private val ExpandedWidth = 220.dp
private val RailEndPad = 8.dp

/** Space the rail takes next to the content. The labels grow over the content and do not move it. */
@Composable
fun railCollapsedWidth(): Dp = railStartPad() + ItemSize + RailEndPad

// TV screens crop their edges a little, so the icons start further in there.
@Composable
private fun railStartPad(): Dp = if (rememberIsTelevision()) 24.dp else 8.dp

private fun iconFor(tab: RootTab): Int = when (tab) {
    RootTab.HOME -> R.drawable.ic_nav_home
    RootTab.SEARCH -> R.drawable.ic_nav_search
    RootTab.SERIES -> R.drawable.ic_nav_series
    RootTab.MOVIES -> R.drawable.ic_nav_movies
    RootTab.COUNTRIES -> R.drawable.ic_nav_countries
}

/**
 * The left navigation rail: the KH wordmark on top and the five places below it.
 *
 * - [active] is the place the user is in: shown white with a red mark at its edge.
 * - The place with focus gets a lighter pill and the white focus ring. The two never look the same.
 * - When the rail gets focus it widens over the content to show names. Content does not move.
 * - Focus enters the rail on [active], whichever way the user comes from.
 * - [onFocusChanged] tells the shell whether the rail has focus (Back then returns to the content).
 * - [onEnterContent] runs when Right is pressed: the shell puts focus back on the last content item.
 *   When it returns false the key does its normal job (focus the nearest item to the right).
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
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "railExpand",
    )
    val collapsed = railCollapsedWidth()
    val startPad = railStartPad()
    val requesters = remember { RootTab.entries.associateWith { FocusRequester() } }
    val currentActive by rememberUpdatedState(active)

    Box(
        modifier
            .fillMaxHeight()
            .width(lerp(collapsed, ExpandedWidth, expand))
            .background(KhColors.Surface.copy(alpha = 0.98f * expand))
            // Right leaves the rail for the content item the user was on, not just the nearest one.
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
                .padding(start = startPad, end = RailEndPad, top = KhTheme.spacing.safeVertical, bottom = KhTheme.spacing.safeVertical),
        ) {
            Image(
                painter = painterResource(R.drawable.kh_wordmark),
                contentDescription = stringResource(R.string.kh_logo_description),
                modifier = Modifier.width(ItemSize),
            )
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(KhTheme.spacing.xs)) {
                RootTab.entries.forEach { tab ->
                    RailItem(
                        tab = tab,
                        selected = tab == active,
                        showLabel = expand > 0.6f,
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
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(ItemSize)
            .semantics {
                contentDescription = tab.label
                this.selected = selected
            },
        shape = RoundedCornerShape(percent = 50),
        scaleAndLift = false,
    ) { focused ->
        val tint = if (selected || focused) KhColors.TextPrimary else KhColors.TextSecondary
        Row(
            Modifier.fillMaxSize().background(if (focused) KhColors.Elevated else KhColors.Surface.copy(alpha = 0f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(ItemSize), contentAlignment = Alignment.Center) {
                Icon(painterResource(iconFor(tab)), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
            if (showLabel) {
                Text(
                    tab.label,
                    style = KhTheme.typography.label,
                    color = tint,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .width(3.dp)
                    .height(20.dp)
                    .background(KhColors.Brand, RoundedCornerShape(2.dp)),
            )
        }
    }
}
