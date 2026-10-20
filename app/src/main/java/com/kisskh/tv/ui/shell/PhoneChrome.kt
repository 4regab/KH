package com.kisskh.tv.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.kisskh.tv.ui.RootTab
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

/** Height of the icon+label row. The system navigation inset sits below it. */
val PhoneNavHeight = 64.dp

/**
 * Phone bottom navigation. Selected is white; the rest are muted. Every item opens that section.
 */
@Composable
fun KhBottomNav(
    active: RootTab,
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
        RootTab.entries.forEach { tab ->
            val on = tab == active
            val tint = if (on) KhColors.TextPrimary else KhColors.NavMuted
            KhFocusable(
                onClick = { onSelect(tab) },
                modifier = Modifier
                    .weight(1f)
                    .height(PhoneNavHeight)
                    .semantics {
                        contentDescription = tab.label
                        selected = on
                    },
                shape = KhTheme.shapes.action,
                scaleAndLift = false,
            ) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(iconFor(tab)), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Text(tab.label, color = tint, fontSize = 11.sp, maxLines = 1)
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
