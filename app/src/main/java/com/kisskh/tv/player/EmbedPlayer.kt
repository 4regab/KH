package com.kisskh.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.kisskh.tv.api.media.EmbedServers
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.media.rememberNextEpisode

/** CineWave: the site's embed servers, in its order, full screen with the KH player overlay. */
@Composable
fun EmbedPlayer(vm: AppViewModel, dest: Dest.EmbedPlayer) {
    val next = rememberNextEpisode(vm, dest.kind, dest.tmdbId, dest.season, dest.episode)
    val sources = remember(dest.kind, dest.tmdbId, dest.season, dest.episode) {
        EmbedServers.playable.map { WebSource(it.name, it.code, it.url(dest.kind, dest.tmdbId, dest.season, dest.episode)) }
    }
    LaunchedEffect(dest.progressKey) {
        vm.media.progress.remember(dest.progressKey, dest.title, dest.poster)
    }
    WebPlayer(
        key = dest.progressKey,
        title = dest.title.ifBlank { "CineWave" },
        label = dest.label,
        sources = sources,
        referer = CINEWAVE_REFERER,
        onBack = { vm.onBack() },
        onHome = { vm.goHome() },
        onEpisodes = if (dest.kind.isMovie) null else ({ vm.onBack() }),
        onNext = next?.let { episode -> { vm.switchEmbed(dest.copy(episode = episode, episodeName = "")) } },
    )
}

private const val CINEWAVE_REFERER = "https://watch.cinewave.qzz.io/"
