package com.kisskh.tv.ui.shelf.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.source.CastMember
import com.kisskh.tv.api.source.TitleCard
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.components.cardGap
import com.kisskh.tv.ui.components.edge
import com.kisskh.tv.ui.components.isTv
import com.kisskh.tv.ui.shelf.ShelfCard
import com.kisskh.tv.ui.shelf.cardWidth
import com.kisskh.tv.ui.shelf.listKey
import com.kisskh.tv.ui.shelf.openCard
import com.kisskh.tv.ui.shelf.rememberSavedList
import com.kisskh.tv.ui.shelf.rememberTitleMark
import com.kisskh.tv.ui.shelf.shapeFor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme

/*
 * The two rows under the episodes: Cast and More like this. Each is left out when the source has
 * nothing for it (KissKH has neither).
 */

/** A row of billed actors: a round photo (or their initial), the name, and the character they play. */
internal fun LazyListScope.castSection(cast: List<CastMember>) {
    val people = cast.filter { it.name.isNotBlank() }
    if (people.isEmpty()) return
    item(key = "cast") {
        Column(Modifier.fillMaxWidth()) {
            SectionTitle("Cast")
            LazyRow(
                contentPadding = PaddingValues(horizontal = edge(), vertical = KhTheme.spacing.focusBleed),
                horizontalArrangement = Arrangement.spacedBy(if (isTv()) 16.dp else 12.dp),
            ) {
                itemsIndexed(people, key = { index, member -> "cast:$index:${member.name}" }) { _, member -> CastTile(member) }
            }
        }
    }
}

/**
 * One cast member. It takes focus on a remote (a white ring on the photo) so the row can be
 * reached and scrolled, but it does nothing when pressed: there is no actor page.
 */
@Composable
private fun CastTile(member: CastMember) {
    val tv = isTv()
    val photo = if (tv) 72.dp else 64.dp
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val character = member.character?.trim()?.takeIf { it.isNotEmpty() }
    Column(
        Modifier
            .width(if (tv) 96.dp else 84.dp)
            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(member.name, character?.let { "as $it" }).joinToString(" ") }
            .focusable(interactionSource = source),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(photo)
                .clip(CircleShape)
                .background(KhColors.Surface, CircleShape)
                .then(if (focused) Modifier.border(2.dp, KhColors.Outline, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            CastPhoto(member.photo, member.name)
        }
        Text(member.name, style = detailCaption(), color = KhColors.TextPrimary, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (character != null) {
            Text(character, style = detailCaption(), color = KhColors.TextSecondary, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The photo, or the first letter of the name when there is no photo or it fails. */
@Composable
private fun CastPhoto(url: String?, name: String) {
    var failed by remember(url) { mutableStateOf(url.isNullOrBlank()) }
    if (failed) {
        Text(castInitial(name), style = KhTheme.typography.title, color = KhColors.TextSecondary)
        return
    }
    val context = LocalContext.current
    val request = remember(url) { ImageRequest.Builder(context).data(url).crossfade(KhMotion.PopoverMillis).build() }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier = Modifier.fillMaxSize(),
    )
}

/** "More like this": a row of cards from the same source. Opening one pushes that title's page. */
internal fun LazyListScope.recommendationsSection(vm: AppViewModel, library: Library, current: TitleCard, recommendations: List<TitleCard>) {
    val cards = recommendations.filter { it.id != current.id || it.source != current.source }.distinctBy(TitleCard::listKey)
    if (cards.isEmpty()) return
    item(key = "recommendations") {
        val state = rememberSavedList(vm, "${library.key}:title:${current.id}:similar")
        val shape = remember(cards) { shapeFor(cards) }
        val width = cardWidth(shape)
        Column(Modifier.fillMaxWidth()) {
            SectionTitle("More like this")
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = edge(), vertical = if (isTv()) 12.dp else 4.dp),
                horizontalArrangement = Arrangement.spacedBy(cardGap()),
            ) {
                items(cards, key = TitleCard::listKey) { card ->
                    ShelfCard(card, shape, onClick = { vm.openCard(card, library) }, modifier = Modifier.width(width), mark = rememberTitleMark(vm, card))
                }
            }
        }
    }
}
