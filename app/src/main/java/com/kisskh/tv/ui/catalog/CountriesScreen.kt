package com.kisskh.tv.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kisskh.tv.api.Country
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.FocusKeys
import com.kisskh.tv.ui.components.FocusTracker
import com.kisskh.tv.ui.components.RegisterFocusSaver
import com.kisskh.tv.ui.components.cardGap
import com.kisskh.tv.ui.components.focusIfRestoring
import com.kisskh.tv.ui.components.isCompactWidth
import com.kisskh.tv.ui.components.rememberFocusRestore
import com.kisskh.tv.ui.components.revealIndex
import com.kisskh.tv.ui.components.screenContentPadding
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

/** The verified countries, as big tiles. Opening one shows that country's titles. */
@Composable
fun CountriesScreen(vm: AppViewModel, dest: Dest.Countries) {
    val restore = rememberFocusRestore(
        remember {
            dest.focusKey?.takeIf { key -> Country.entries.any { FocusKeys.country(it) == key } }
                ?: FocusKeys.country(Country.entries.first())
        },
    )
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = dest.scrollIndex)
    val tracker = remember { FocusTracker() }
    RegisterFocusSaver(tracker, restore) { vm.saveFocus(tracker.key, gridState.firstVisibleItemIndex) }

    LaunchedEffect(Unit) {
        val index = Country.entries.indexOfFirst { FocusKeys.country(it) == restore.key }
        if (index >= 0) gridState.revealIndex(index)
    }

    val gap = cardGap()
    val tv = KhTheme.formFactor == FormFactor.TV
    Column(Modifier.fillMaxSize().padding(top = KhTheme.spacing.safeVertical)) {
        Text(
            "Countries",
            style = if (tv) KhTheme.typography.heading else KhTheme.typography.headline,
            modifier = Modifier.padding(start = screenStart(), end = KhTheme.spacing.safeHorizontal),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (isCompactWidth()) 200.dp else 180.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(Country.entries, key = { it.id }, contentType = { "country" }) { country ->
                val key = FocusKeys.country(country)
                KhFocusable(
                    onClick = {
                        vm.saveFocus(key, gridState.firstVisibleItemIndex)
                        vm.push(Dest.CountryCatalog(country))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isCompactWidth()) 72.dp else 88.dp)
                        .focusIfRestoring(restore, key)
                        .onFocusChanged { if (it.isFocused) tracker.key = key },
                    shape = KhTheme.shapes.tile,
                ) {
                    Row(
                        Modifier.fillMaxSize().background(KhColors.Surface).padding(horizontal = KhTheme.spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The flag is extra. The name is always there.
                        Text(country.flagEmoji(), style = KhTheme.typography.headline)
                        Spacer(Modifier.width(KhTheme.spacing.m))
                        Text(country.label, style = KhTheme.typography.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Country flag from its two-letter code: each letter becomes a regional indicator symbol. */
private fun Country.flagEmoji(): String {
    val code = when (this) {
        Country.SOUTH_KOREA -> "KR"
        Country.CHINA -> "CN"
        Country.UNITED_STATES -> "US"
        Country.THAILAND -> "TH"
        Country.PHILIPPINES -> "PH"
        Country.JAPAN -> "JP"
        Country.HONG_KONG -> "HK"
        Country.TAIWAN -> "TW"
    }
    return code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}
