package com.kisskh.tv.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.kisskh.tv.api.SavedTitle
import com.kisskh.tv.api.SavedTitles
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.shelf.ShelfCard
import com.kisskh.tv.ui.shelf.cardWidth
import com.kisskh.tv.ui.shelf.openCard
import com.kisskh.tv.ui.shelf.shapeFor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme

/**
 * "My List" and "Watched" on the My KH screen: titles from every source, newest first. A card
 * opens its own source's title page ([openCard] switches the source if needed).
 */
fun LazyListScope.savedSections(vm: AppViewModel, library: Library) {
    item(key = "saved-list") {
        SavedRow(vm, library, "My List", "Tap My List on any title to save it here.") { it.myList() }
    }
    item(key = "saved-watched") {
        SavedRow(vm, library, "Watched", "Titles you mark as watched appear here.") { it.watchedList() }
    }
}

@Composable
private fun SavedRow(
    vm: AppViewModel,
    library: Library,
    title: String,
    emptyText: String,
    pick: (SavedTitles) -> List<SavedTitle>,
) {
    val version by vm.saved.version.collectAsStateWithLifecycle()
    val cards = remember(version) { pick(vm.saved).mapNotNull { it.toCard(sourceLabelAsMeta = true) } }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = KhTheme.typography.rowTitle,
                color = KhColors.TextPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text("${cards.size}", style = KhTheme.typography.caption, color = KhColors.TextSecondary)
        }
        if (cards.isEmpty()) {
            Text(
                emptyText,
                style = KhTheme.typography.caption,
                color = KhColors.TextSecondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            )
        } else {
            val shape = remember(cards) { shapeFor(cards) }
            val width = cardWidth(shape)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cards, key = { "${it.source.key}:${it.kind}:${it.id}" }) { card ->
                    ShelfCard(card, shape, onClick = { vm.openCard(card, library) }, modifier = Modifier.width(width))
                }
            }
        }
    }
}
