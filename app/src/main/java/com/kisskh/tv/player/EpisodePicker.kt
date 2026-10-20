package com.kisskh.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kisskh.tv.api.Drama
import com.kisskh.tv.api.Episode
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.EmptyState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.detail.TvEpisodePanel
import com.kisskh.tv.ui.episodeRanges
import com.kisskh.tv.ui.isMovie
import com.kisskh.tv.ui.orderedEpisodes
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The episode list over the video: ranges on one side, episode rows on the other (the same panel the
 * title page uses). Picking an episode calls [onPick]. Back, the Close button, or a tap outside closes it.
 */
@Composable
fun EpisodePicker(
    vm: AppViewModel,
    dramaId: Int,
    currentEpisodeId: Int,
    onPick: (Drama, Episode) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val detail by remember(dramaId) { vm.detail(dramaId) }.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        // Dims the video and closes on tap. pointerInput, not clickable, so D-pad focus never lands here.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
        when (val state = detail) {
            UiState.Loading -> LoadingState(message = "Loading episodes")
            is UiState.Empty -> EmptyState("No episodes found.")
            is UiState.Error -> ErrorState(state.message, onRetry = { vm.retryDetail(dramaId) }, autoFocus = true)
            is UiState.Success -> {
                val drama = state.data
                val ordered = remember(drama) { drama.orderedEpisodes() }
                val parts = drama.isMovie() && ordered.size > 1
                val ranges = remember(ordered) { episodeRanges(ordered) }
                val partNumbers = remember(ordered) { ordered.mapIndexed { i, ep -> ep.id to (i + 1) }.toMap() }
                val entry = remember(drama.id) { vm.repo.history.entry(drama.id) }
                val positions = remember(drama.id) {
                    ordered.associate { it.id to vm.repo.loadPosition(it.id) }.filterValues { it > 0L }
                }
                if (ranges.isEmpty()) {
                    EmptyState("No episodes found.")
                } else {
                    TvEpisodePanel(
                        ranges = ranges,
                        parts = parts,
                        partNumberOf = { partNumbers[it.id] ?: 1 },
                        positions = positions,
                        progressOf = { ep -> entry?.takeIf { it.episodeId == ep.id && it.resumable }?.progress },
                        unavailable = emptySet(),
                        initialEpisodeId = currentEpisodeId,
                        onPlay = { episode -> onPick(drama, episode) },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
        KhButton("Close", onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(24.dp))
    }
}
