package com.kisskh.tv.ui.shelf

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.BrowseFilters
import com.kisskh.tv.api.source.FilterCapabilities
import com.kisskh.tv.api.source.FilterOption
import com.kisskh.tv.ui.components.FilterChip
import com.kisskh.tv.ui.components.FilterDropdown
import com.kisskh.tv.ui.components.edge
import com.kisskh.tv.ui.components.rememberAutoFocusAllowed

/** These filters with everything narrowed removed. The order stays, because it only sorts. */
internal fun BrowseFilters.cleared(): BrowseFilters = copy(country = null, genre = null, year = null, status = null)

/** True when the bar has at least one chip to show for this grid. */
internal fun FilterCapabilities.hasBar(): Boolean = sorts.size > 1 || any

/** What a grid is called in a sentence: "movies", "TV shows" ("TV series" on KissKH), "anime". */
internal fun kindNoun(library: Library, kind: MediaKind): String = when (kind) {
    MediaKind.MOVIE -> "movies"
    MediaKind.TV -> if (library == Library.KISSKH) "TV series" else "TV shows"
    MediaKind.ANIME -> "anime"
}

/**
 * The sentence for a grid that has no titles for [filters], naming each filter that is set:
 * "No Horror movies from South Korea, 2019, on Reelix." Without any filter set it says the source
 * has nothing. The order is not named, because it never empties a list.
 */
internal fun emptyFilterMessage(library: Library, kind: MediaKind, caps: FilterCapabilities, filters: BrowseFilters): String {
    val noun = kindNoun(library, kind)
    if (!filters.narrowed) return "${library.label} has no $noun right now."
    val country = filters.country?.let { code -> caps.countries.firstOrNull { it.id == code }?.label ?: code }
    val genre = filters.genre?.let { id -> caps.genres.firstOrNull { it.id == id }?.label }
    val status = filters.status?.let { id -> caps.statuses.firstOrNull { it.id == id }?.label?.lowercase() }
    val what = listOfNotNull(status, genre, noun).joinToString(" ")
    val where = listOfNotNull(
        country?.let { "from $it" },
        filters.year?.let { if (country == null) "from $it" else "$it" },
    )
    return buildString {
        append("No ").append(what)
        if (where.isNotEmpty()) append(' ').append(where.joinToString(", ")).append(',')
        append(" on ").append(library.label).append('.')
    }
}

/**
 * The row of filter chips above a Movies / TV / Anime grid: Sort, Country, Genre, Year, Status.
 * A chip is there only when this grid can really filter on it ([caps]), and a "Clear" chip joins the row
 * while anything is narrowed. The row scrolls sideways when the chips do not fit. [firstChip] is the focus
 * handle of the first chip, so a screen can send focus back to the bar.
 */
@Composable
internal fun ShelfFilterBar(
    caps: FilterCapabilities,
    filters: BrowseFilters,
    onChange: (BrowseFilters) -> Unit,
    firstChip: FocusRequester,
    modifier: Modifier = Modifier,
) {
    if (!caps.hasBar()) return
    val autoFocus = rememberAutoFocusAllowed()
    var unclaimed = true
    fun claim(): FocusRequester? = if (unclaimed) firstChip.also { unclaimed = false } else null

    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = edge()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (caps.sorts.size > 1) {
            FilterDropdown(
                label = "Sort",
                options = caps.sorts,
                selected = filters.sort,
                optionLabel = { it.label },
                onSelect = { onChange(filters.copy(sort = it)) },
                active = filters.sort != caps.defaultSort,
                chipRequester = claim(),
            )
        }
        if (caps.countries.isNotEmpty()) {
            OptionFilter("Country", caps.countries, filters.country, claim()) { onChange(filters.copy(country = it)) }
        }
        if (caps.genres.isNotEmpty()) {
            OptionFilter("Genre", caps.genres, filters.genre, claim()) { onChange(filters.copy(genre = it)) }
        }
        caps.years?.let { years ->
            // Newest first: the year people look for is usually a recent one.
            val list = remember(years) { listOf<Int?>(null) + years.reversed() }
            FilterDropdown(
                label = "Year",
                options = list,
                selected = filters.year,
                optionLabel = { it?.toString() ?: "All" },
                onSelect = { onChange(filters.copy(year = it)) },
                chipText = filters.year?.let { "Year: $it" } ?: "Year",
                chipRequester = claim(),
            )
        }
        if (caps.statuses.isNotEmpty()) {
            OptionFilter("Status", caps.statuses, filters.status, claim()) { onChange(filters.copy(status = it)) }
        }
        if (filters.narrowed) {
            FilterChip(
                text = "Clear",
                onClick = {
                    onChange(filters.cleared())
                    // The Clear chip leaves the row, so focus goes to the first chip rather than getting lost.
                    if (autoFocus()) runCatching { firstChip.requestFocus() }
                },
                trailingIcon = Icons.Filled.Close,
                modifier = Modifier.semantics { contentDescription = "Clear filters" },
            )
        }
    }
}

/** A chip for a filter that picks one of [options] by id, or "All". Says "Genre: Horror" once one is picked. */
@Composable
private fun OptionFilter(
    label: String,
    options: List<FilterOption>,
    selectedId: String?,
    requester: FocusRequester?,
    onPick: (String?) -> Unit,
) {
    val list = remember(options) { listOf<FilterOption?>(null) + options }
    val selected = list.firstOrNull { it != null && it.id == selectedId }
    FilterDropdown(
        label = label,
        options = list,
        selected = selected,
        optionLabel = { it?.label ?: "All" },
        onSelect = { onPick(it?.id) },
        chipText = selected?.let { "$label: ${it.label}" } ?: label,
        chipRequester = requester,
    )
}
