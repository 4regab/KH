package com.kisskh.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

/**
 * The panel every player uses for choices (servers, subtitles, audio, quality): it slides over the
 * right side of the video, at most 380 dp wide. A tap outside it or Back closes it.
 */
@Composable
internal fun PlayerSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .background(KhColors.Surface)
                // Taps inside the panel must not close it.
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = KhTheme.typography.rowTitle,
                    color = KhColors.TextPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                KhChip("Close", onClick = onClose)
            }
            content()
        }
    }
}

@Composable
internal fun SheetHeading(text: String) {
    Text(
        text.uppercase(),
        style = KhTheme.typography.badge,
        color = KhColors.TextSecondary,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** One choice. At least 48 dp tall, a tick on the current one, an optional note on the right. */
@Composable
internal fun SheetOption(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    note: String? = null,
    dimmed: Boolean = false,
    onClick: () -> Unit,
) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { this.selected = selected },
        shape = KhTheme.shapes.button,
        scaleAndLift = false,
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(if (focused) KhColors.Elevated else Color.Transparent, KhTheme.shapes.button)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (selected) "✓" else "", color = KhColors.TextPrimary, modifier = Modifier.width(24.dp))
            Text(
                text,
                style = KhTheme.typography.label,
                color = when {
                    selected -> KhColors.TextPrimary
                    dimmed -> KhColors.NavMuted
                    else -> KhColors.TextSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (note != null) {
                Text(note, style = KhTheme.typography.caption, color = KhColors.NavMuted, maxLines = 1)
            }
        }
    }
}
