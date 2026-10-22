package com.kisskh.tv.ui.shelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.ui.components.KhActionButton
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

/**
 * The actions under Play on a title page: "My List" (save for later), "Watched" (the whole title)
 * and, for a movie, "Download". Both marks are real toggles: the icon becomes a check once set.
 *
 * [download] draws the movie's Download button; it gets the sizing modifier and `stacked` (true
 * for the phone style). It is null for a series, where every episode has its own Download button.
 *
 * Wide screens ([buttonWidth] set): labelled grey buttons side by side. Phones: one row of equal
 * icon-over-label buttons, as in Netflix's mobile app, so every title page has the same layout.
 */
@Composable
internal fun SavedActions(
    inList: Boolean,
    watched: Boolean,
    onToggleList: () -> Unit,
    onToggleWatched: () -> Unit,
    modifier: Modifier = Modifier,
    buttonWidth: Dp? = null,
    download: (@Composable (Modifier, Boolean) -> Unit)? = null,
) {
    val listIcon = if (inList) Icons.Filled.Check else Icons.Filled.Add
    val listSpoken = if (inList) "Remove from My List" else "Add to My List"
    val watchedIcon = if (watched) Icons.Filled.Check else Icons.Outlined.CheckCircle
    val watchedSpoken = if (watched) "Mark as unwatched" else "Mark as watched"
    if (buttonWidth != null) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KhActionButton("My List", onToggleList, Modifier.width(buttonWidth).semantics { contentDescription = listSpoken }, icon = listIcon, height = 48.dp)
            KhActionButton("Watched", onToggleWatched, Modifier.width(buttonWidth).semantics { contentDescription = watchedSpoken }, icon = watchedIcon, height = 48.dp)
            // "Downloading 42%" needs more room than the other two labels.
            download?.invoke(Modifier.width(buttonWidth + 40.dp), false)
        }
        return
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        StackedAction(
            label = "My List",
            description = listSpoken,
            onClick = onToggleList,
            modifier = Modifier.weight(1f),
        ) { Icon(listIcon, contentDescription = null, tint = KhColors.TextPrimary, modifier = Modifier.size(26.dp)) }
        StackedAction(
            label = "Watched",
            description = watchedSpoken,
            onClick = onToggleWatched,
            modifier = Modifier.weight(1f),
        ) { Icon(watchedIcon, contentDescription = null, tint = KhColors.TextPrimary, modifier = Modifier.size(26.dp)) }
        download?.invoke(Modifier.weight(1f), true)
    }
}

/**
 * One phone title-page action: an icon over a short label, no fill, at least 64 dp tall.
 * Download uses it too, so the three buttons always match.
 */
@Composable
internal fun StackedAction(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp).semantics { contentDescription = description },
        shape = KhTheme.shapes.button,
        scaleAndLift = false,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
            Text(
                label,
                style = KhTheme.typography.caption,
                color = KhColors.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
