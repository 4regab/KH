package com.kisskh.tv.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.UiState
import androidx.compose.runtime.getValue

/**
 * The episode after [episode] in the same season, or null for a movie, the last episode, or while the
 * season is still loading. It is read from the season list the title page already loaded, so every
 * episode reached by Next, Continue or Recent gets the same answer.
 */
@Composable
fun rememberNextEpisode(vm: AppViewModel, kind: MediaKind, tmdbId: Int, season: Int, episode: Int): Int? {
    if (kind.isMovie) return null
    val state by remember(tmdbId, season) { vm.media.season(tmdbId, season) }.collectAsStateWithLifecycle()
    val episodes = (state as? UiState.Success)?.data ?: return null
    val index = episodes.indexOfFirst { it.number == episode }
    // An episode that has not aired yet is not offered: no server has it.
    return if (index >= 0) episodes.getOrNull(index + 1)?.takeIf { it.aired() }?.number else null
}