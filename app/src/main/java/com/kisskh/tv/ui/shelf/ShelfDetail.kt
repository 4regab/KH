package com.kisskh.tv.ui.shelf

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import com.kisskh.tv.R
import com.kisskh.tv.api.ReleaseDates
import com.kisskh.tv.api.TitleMark
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.TmdbPaths
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.TitleDetail
import com.kisskh.tv.download.DownloadActionButton
import com.kisskh.tv.download.rememberTitleDownloads
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.EmptyState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.isTv
import com.kisskh.tv.ui.components.rememberAutoFocusAllowed
import com.kisskh.tv.ui.shelf.detail.DetailHeader
import com.kisskh.tv.ui.shelf.detail.DetailSkeleton
import com.kisskh.tv.ui.shelf.detail.EpisodesSection
import com.kisskh.tv.ui.shelf.detail.HeaderActions
import com.kisskh.tv.ui.shelf.detail.castSection
import com.kisskh.tv.ui.shelf.detail.detailHeaderHeight
import com.kisskh.tv.ui.shelf.detail.episodesSection
import com.kisskh.tv.ui.shelf.detail.metaFacts
import com.kisskh.tv.ui.shelf.detail.notOutYetMessage
import com.kisskh.tv.ui.shelf.detail.recommendationsSection
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhMotion

/*
 * The title page. This file decides what the page does (play, resume, marks, downloads, which
 * season is open). How it looks lives in ui/shelf/detail/: header, episodes, cast, more like this, skeleton.
 */

@Composable
fun ShelfDetail(vm: AppViewModel, library: Library, dest: Dest.TitlePage) {
    val state by remember(library, dest.id, dest.kind) {
        vm.shelf.detail(library, dest.id, dest.kind, dest.slug, dest.name, dest.poster)
    }.collectAsStateWithLifecycle()
    // Loading -> page is a 250 ms fade, so the skeleton does not blink out.
    val phase = when (state) {
        UiState.Loading -> 0
        is UiState.Error -> 1
        is UiState.Empty -> 2
        is UiState.Success -> 3
    }
    Crossfade(targetState = phase, animationSpec = KhMotion.crossfade(), label = "detailPhase") { shown ->
        when (val s = state) {
            UiState.Loading -> if (shown == 0) DetailLoading(vm, dest)
            is UiState.Error -> if (shown == 1) Box(Modifier.fillMaxSize()) {
                ErrorState(
                    "Couldn't open this title. ${s.message}",
                    onRetry = { vm.shelf.retryDetail(library, dest.id, dest.kind, dest.slug, dest.name, dest.poster) },
                    autoFocus = true,
                )
                BackCircle(vm, Modifier.align(Alignment.TopStart))
            }
            is UiState.Empty -> if (shown == 2) Box(Modifier.fillMaxSize()) {
                EmptyState(s.message ?: "This title is not available.")
                BackCircle(vm, Modifier.align(Alignment.TopStart))
            }
            is UiState.Success -> if (shown == 3) DetailBody(vm, library, dest, s.data)
        }
    }
}

/** While the title loads: the page in its final shape, with the tapped card's artwork and name already in place. */
@Composable
private fun DetailLoading(vm: AppViewModel, dest: Dest.TitlePage) {
    Box(Modifier.fillMaxSize()) {
        DetailSkeleton(dest)
        BackCircle(vm, Modifier.align(Alignment.TopStart))
    }
}

/** The round Back button over the title artwork (phones have no rail to leave by). */
@Composable
private fun BackCircle(vm: AppViewModel, modifier: Modifier = Modifier) {
    if (isTv()) return
    KhFocusable(
        onClick = { vm.onBack() },
        modifier = modifier
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 4.dp, start = 8.dp)
            .size(48.dp)
            .semantics { contentDescription = "Back" },
        shape = CircleShape,
        scaleAndLift = false,
    ) {
        Box(Modifier.fillMaxSize().padding(4.dp).background(KhColors.ScrimMedium, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_player_back), contentDescription = null, tint = KhColors.OnArt, modifier = Modifier.size(24.dp))
        }
    }
}

/** What the big button on a title page says and plays. [dimmed] draws it grey: it only explains why nothing plays. */
internal data class MainAction(val label: String, val dimmed: Boolean = false, val play: () -> Unit)

@Composable
private fun DetailBody(vm: AppViewModel, library: Library, dest: Dest.TitlePage, detail: TitleDetail) {
    val card = detail.card
    val movie = card.kind == MediaKind.MOVIE
    val seasons = detail.seasons.filter { movie || it.number > 0 }.ifEmpty { detail.seasons }
    // My List and Watched marks. Reading the version redraws this page after every change.
    val store = vm.saved
    val savedVersion by store.version.collectAsStateWithLifecycle()
    val inList = remember(savedVersion, card) { store.inList(card) }
    val titleWatched = remember(savedVersion, card) { store.isWatched(card) }
    // Coming soon (by release date) or Not available (every server failed last time). The title stays open either way.
    val availabilityVersion by vm.availability.version.collectAsStateWithLifecycle()
    val mark = remember(availabilityVersion, card) { vm.availability.markFor(card) }
    val comingSoon = mark as? TitleMark.ComingSoon
    val context = LocalContext.current
    val notOutYet = { Toast.makeText(context, notOutYetMessage(comingSoon?.date), Toast.LENGTH_SHORT).show() }
    val episodeNotOutYet = { episode: EpisodeRef -> Toast.makeText(context, notOutYetMessage(episode.airDate), Toast.LENGTH_SHORT).show() }
    // Where the viewer left off on this title, newest first. Opens on that season.
    val last = remember(library, card.id) { vm.lastWatched(library, card) }
    // A title marked watched plays from the start: never "Resume", no "Continue" episode.
    val resumeFrom = if (titleWatched) null else last
    var seasonNumber by remember(card.id) {
        mutableIntStateOf(last?.season?.takeIf { s -> seasons.any { it.number == s } } ?: seasons.firstOrNull()?.number ?: 1)
    }
    val selected = seasons.firstOrNull { it.number == seasonNumber } ?: seasons.firstOrNull()
    val inline = selected?.episodes.orEmpty()
    val remote by remember(library, card.id, seasonNumber, inline.isEmpty() && !movie) {
        if (inline.isEmpty() && !movie) vm.shelf.season(library, card.id, seasonNumber)
        else kotlinx.coroutines.flow.MutableStateFlow(UiState.Success(inline))
    }.collectAsStateWithLifecycle()
    val episodes = (remote as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(episodes) { if (episodes.isNotEmpty()) vm.shelf.rememberEpisodes(library, card.id, seasonNumber, episodes) }

    // Download buttons: one per episode row, and for a one-part movie one beside My List.
    val titleDownloads = rememberTitleDownloads(vm, library, card)
    val movieEpisode = when {
        !movie || episodes.size > 1 -> null
        episodes.isNotEmpty() -> episodes.first()
        // Same stand-in the Play button uses when a movie lists no episode. KissKH needs a real episode id.
        library != Library.KISSKH -> EpisodeRef(card.id, 0, 1, "Movie", card.artwork)
        else -> null
    }
    val movieDownload: (@Composable (Modifier, Boolean) -> Unit)? = if (movieEpisode == null || comingSoon != null) null else { size, stacked ->
        DownloadActionButton(titleDownloads, movieEpisode, episodes, card.name, size, stacked)
    }
    val action = remember(card, episodes, resumeFrom, seasonNumber, comingSoon) {
        if (comingSoon != null) MainAction("Coming soon", dimmed = true) { notOutYet() }
        else mainAction(vm, library, card, movie, seasonNumber, episodes, resumeFrom)
    }
    // Play on Home opened this page: start the main action once the episodes are known.
    LaunchedEffect(dest.autoplay, remote is UiState.Loading) {
        if (dest.autoplay && remote !is UiState.Loading) {
            vm.consumeAutoplay()
            action.play()
        }
    }
    val list = rememberSavedList(vm, "${library.key}:title:${card.id}")
    val playFocus = remember { FocusRequester() }
    val allowed = rememberAutoFocusAllowed()
    LaunchedEffect(card.id) { if (allowed()) runCatching { playFocus.requestFocus() } }

    val today = remember { TmdbPaths.todayUtc() }
    val facts = metaFacts(detail, movie, seasons.size, episodes.size)
    val actions = HeaderActions(
        action = action,
        playFocus = playFocus,
        inList = inList,
        watched = titleWatched,
        onToggleList = { store.toggleList(card) },
        onToggleWatched = { store.toggleWatched(card) },
        download = movieDownload,
    )
    // Series, and movies that come in several parts (some KissKH films).
    val episodeList = EpisodesSection(
        movie = movie,
        seasons = seasons,
        seasonNumber = seasonNumber,
        onSeason = { seasonNumber = it },
        episodes = remote,
        marksVersion = savedVersion,
        isWatched = { store.episodeWatched(card, it.season, it.number) },
        progress = { vm.episodeProgress(library, card, it) },
        isCurrent = { resumeFrom != null && resumeFrom.season.coerceAtLeast(1) == it.season.coerceAtLeast(1) && resumeFrom.episode == it.number },
        today = today,
        // A multi-part movie's own length is the whole film, so only a series lends its usual episode length.
        fallbackMinutes = if (movie) null else detail.runtimeMinutes,
        onPlay = { episode, all -> if (comingSoon != null) notOutYet() else vm.playTitle(library, card, episode, all) },
        onNotAired = episodeNotOutYet,
        onToggleWatched = { episode, nowWatched, all ->
            store.setEpisodeWatched(card, episode.season, episode.number, nowWatched, all.map { it.season to it.number })
        },
        onRetry = { vm.shelf.retrySeason(library, card.id, seasonNumber) },
        downloads = titleDownloads,
        showDownload = comingSoon == null,
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val headerHeight = detailHeaderHeight(maxHeight)
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item(key = "header") { DetailHeader(detail, facts, mark, actions, headerHeight) }
            if (!movie || episodes.size > 1) episodesSection(episodeList)
            castSection(detail.cast)
            recommendationsSection(vm, library, card, detail.recommendations)
        }
        titleDownloads.Host()
        StatusBarScrim(list, Modifier.align(Alignment.TopStart))
        BackCircle(vm, Modifier.align(Alignment.TopStart))
    }
}

/**
 * Phone: once the page scrolls, the status bar gets a solid strip of the page colour (as the Home top bar
 * does), so the Play button and the text never slide under the clock. It sits under the Back circle.
 */
@Composable
private fun StatusBarScrim(list: LazyListState, modifier: Modifier = Modifier) {
    if (isTv()) return
    val solid by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 120 } }
    val alpha = animateFloatAsState(if (solid) 1f else 0f, tween(KhMotion.PopoverMillis, easing = KhMotion.EaseOutStrong), label = "statusScrim")
    Box(
        modifier
            .fillMaxWidth()
            .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .graphicsLayer { this.alpha = alpha.value }
            .background(KhColors.Background.copy(alpha = 0.95f)),
    )
}
