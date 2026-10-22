package com.kisskh.tv.ui.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.RootTab
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.shell.PhoneSearchButton
import com.kisskh.tv.ui.shell.PhoneWordmark
import com.kisskh.tv.ui.shell.SourceAvatar
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

/**
 * Phone top bar: the KH mark (or a screen [title]), the source switch and Search. It sits over the
 * Home artwork and turns solid once the page scrolls ([solid] 0..1).
 */
@Composable
fun ShelfTopBar(
    vm: AppViewModel,
    library: Library,
    solid: Float,
    title: String? = null,
    showSearch: Boolean = true,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        KhColors.Background.copy(alpha = 0.55f + 0.4f * solid),
                        KhColors.Background.copy(alpha = 0.95f * solid),
                    ),
                ),
            )
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) {
        Row(
            Modifier.fillMaxWidth().height(TopBarHeight).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (title == null) {
                PhoneWordmark()
            } else {
                Text(
                    title,
                    style = KhTheme.typography.headline,
                    color = KhColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.weight(1f))
            SourceButton(library, onClick = { vm.sourcePicker.value = true })
            if (showSearch) PhoneSearchButton(onClick = { vm.selectTab(RootTab.SEARCH) }, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** The current source, as a pill. Opens "Choose a source". */
@Composable
fun SourceButton(library: Library, onClick: () -> Unit, modifier: Modifier = Modifier) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = "Source: ${library.label}. Change source" },
        shape = KhTheme.shapes.chip,
        scaleAndLift = false,
    ) { focused ->
        Row(
            Modifier
                .align(Alignment.Center)
                .padding(vertical = 6.dp)
                .background(if (focused) KhColors.Elevated else KhColors.Surface.copy(alpha = 0.85f), KhTheme.shapes.chip)
                .border(1.dp, KhColors.ChipOutline, KhTheme.shapes.chip)
                .padding(start = 6.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceAvatar(library, size = 22.dp)
            Text(
                library.label,
                style = KhTheme.typography.caption,
                color = KhColors.TextPrimary,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp, end = 2.dp),
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = KhColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}
