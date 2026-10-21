package com.kisskh.tv.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.R
import com.kisskh.tv.api.media.LIBRARY_ORDER
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.ui.RootTab
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

/** Height of the icon+label row. The system navigation inset sits below it. */
val PhoneNavHeight = 64.dp

/** Source switch that sits just above the phone bottom bar. */
val SourceBarHeight = 44.dp

/**
 * Phone bottom navigation. Selected is white; the rest are muted. Every item opens that section.
 */
@Composable
fun KhBottomNav(
    active: RootTab,
    tabs: List<RootTab>,
    labelOf: (RootTab) -> String,
    onSelect: (RootTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(KhColors.Background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(PhoneNavHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val on = tab == active
            val tint = if (on) KhColors.TextPrimary else KhColors.NavMuted
            val label = labelOf(tab)
            KhFocusable(
                onClick = { onSelect(tab) },
                modifier = Modifier
                    .weight(1f)
                    .height(PhoneNavHeight)
                    .semantics {
                        contentDescription = label
                        selected = on
                    },
                shape = KhTheme.shapes.action,
                scaleAndLift = false,
            ) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(iconFor(tab)), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Text(label, color = tint, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SourceBar(
    library: Library,
    onSelect: (Library) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(SourceBarHeight)
            .background(KhColors.Background)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LIBRARY_ORDER.forEach { source ->
            val on = source == library
            KhFocusable(
                onClick = { onSelect(source) },
                modifier = Modifier
                    .height(32.dp)
                    .semantics {
                        contentDescription = "Source ${source.label}"
                        selected = on
                    },
                shape = KhTheme.shapes.chip,
                scaleAndLift = false,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .background(if (on) KhColors.TextPrimary else KhColors.Surface, KhTheme.shapes.chip),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        source.label,
                        color = if (on) KhColors.OnLight else KhColors.TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun PhoneWordmark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.kh_wordmark),
        contentDescription = stringResource(R.string.kh_logo_description),
        modifier = modifier.width(36.dp),
    )
}

/** Opens Search. Sits at the end of the phone top bar. */
@Composable
fun PhoneSearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier.size(40.dp).semantics { contentDescription = "Search" },
        shape = KhTheme.shapes.chip,
        scaleAndLift = false,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_nav_search),
                contentDescription = null,
                tint = KhColors.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
