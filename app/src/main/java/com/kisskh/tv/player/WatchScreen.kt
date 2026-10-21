package com.kisskh.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import com.kisskh.tv.api.SubtitleFile
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.SniffedStream
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.PlayPlan
import com.kisskh.tv.api.source.PlayTarget
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.shelf.ShelfSession
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * One player for CineWave, CineJoy, Reelix, Anisuge and Anime Nexus.
 * A target with a direct URL plays immediately. Otherwise the page is loaded and the first real
 * HLS or MP4 request is handed to the native player. If that never arrives, the page stays up.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun WatchScreen(vm: AppViewModel, dest: Dest.Watch) {
    val library = remember(dest.source) { Library.entries.firstOrNull { it.key == dest.source } ?: Library.CINEWAVE }
    var audio by remember(dest.progressKey) { mutableStateOf(dest.audio) }
    var resolved by remember(dest.progressKey) { mutableStateOf<ShelfSession.Resolved?>(null) }
    var plan by remember(dest.progressKey, audio) { mutableStateOf<PlayPlan?>(null) }
    var error by remember(dest.progressKey, audio) { mutableStateOf<String?>(null) }
    var loading by remember(dest.progressKey, audio) { mutableStateOf(true) }
    var index by remember(dest.progressKey, audio) { mutableIntStateOf(0) }
    var stream by remember(dest.progressKey, audio) { mutableStateOf<SniffedStream?>(null) }
    var showPage by remember(dest.progressKey, audio) { mutableStateOf(false) }
    var serversOpen by remember(dest.progressKey) { mutableStateOf(false) }
    var attempt by remember(dest.progressKey) { mutableIntStateOf(0) }

    LaunchedEffect(dest.progressKey, audio, attempt) {
        loading = true
        error = null
        stream = null
        showPage = false
        index = 0
        plan = null
        val found = try {
            vm.shelf.resolve(library, dest)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            error = failed.message ?: "Couldn't open this episode."
            loading = false
            return@LaunchedEffect
        }
        resolved = found
        val ready = try {
            vm.shelf.plan(library, found.card, found.episode, audio)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            error = failed.message ?: "Couldn't find a server."
            loading = false
            return@LaunchedEffect
        }
        if (ready.progressKey.isNotBlank()) vm.media.progress.remember(ready.progressKey, ready.title, ready.poster)
        plan = ready
        loading = false
        if (ready.targets.isEmpty()) error = "No server for this episode."
    }

    val ready = plan
    val target = ready?.targets?.getOrNull(index)
    val audios = ready?.targets?.map { it.audio }?.filter { it.isNotBlank() }?.distinct().orEmpty()

    fun go(episode: EpisodeRef?) {
        if (episode == null || ready == null) return
        vm.openWatch(
            dest.copy(
                season = episode.season.coerceAtLeast(1),
                episode = episode.number.coerceAtLeast(1),
                episodeId = episode.id,
                episodeSlug = episode.slug,
                episodeToken = episode.token,
                episodeName = episode.name,
                audio = audio,
                title = ready.title,
                poster = ready.poster,
            ),
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading -> LoadingState(message = "Opening ${target?.name ?: dest.title}")
            error != null && stream == null && target?.direct == null -> WatchMessage(
                error ?: "Playback failed.",
                onBack = { vm.onBack() },
                onHome = { vm.goHome() },
                onRetry = { attempt++ },
            )
            target == null -> WatchMessage("No server for this episode.", onBack = { vm.onBack() }, onHome = { vm.goHome() })
            target.direct != null -> {
                val current = checkNotNull(ready)
                Native(
                    vm = vm,
                    dest = dest,
                    plan = current,
                    url = target.direct,
                    headers = target.headers.ifEmpty { mapOf("Referer" to target.referer) },
                    subs = target.subs,
                    label = listOf(current.label, target.name).filter { it.isNotBlank() }.joinToString(" · "),
                    onPrev = resolved?.previous?.let { episode -> { go(episode) } },
                    onNext = resolved?.next?.let { episode -> { go(episode) } },
                    onServers = { serversOpen = true },
                    onFail = {
                        if (index < current.targets.lastIndex) {
                            index++
                        } else {
                            error = "Playback failed."
                        }
                    },
                )
            }
            stream != null && !showPage -> {
                val current = checkNotNull(ready)
                Native(
                    vm = vm,
                    dest = dest,
                    plan = current,
                    url = stream!!.url,
                    headers = stream!!.headers.ifEmpty { mapOf("Referer" to target.referer) },
                    subs = target.subs,
                    label = listOf(current.label, target.name).filter { it.isNotBlank() }.joinToString(" · "),
                    onPrev = resolved?.previous?.let { episode -> { go(episode) } },
                    onNext = resolved?.next?.let { episode -> { go(episode) } },
                    onServers = { serversOpen = true },
                    onFail = {
                        stream = null
                        if (index < current.targets.lastIndex) index++ else showPage = true
                    },
                )
            }
            target.page != null -> {
                val current = checkNotNull(ready)
                val sniffing = target.sniff && !showPage
                if (sniffing) {
                    LaunchedEffect(target.page, index) {
                        delay(20_000)
                        if (stream == null) {
                            if (index < current.targets.lastIndex) index++ else showPage = true
                        }
                    }
                }
                SniffWebView(
                    page = target.page,
                    referer = target.referer,
                    loadKey = "${target.page}#$index#$showPage",
                    onStream = { sniffed -> if (sniffing) stream = sniffed },
                    onFail = {
                        if (!sniffing) return@SniffWebView
                        if (index < current.targets.lastIndex) index++ else showPage = true
                    },
                    modifier = Modifier.fillMaxSize(),
                    sniff = sniffing,
                )
                if (sniffing) {
                    Text(
                        "Trying ${target.name}",
                        color = KhColors.TextSecondary,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                    )
                }
            }
        }
        if (ready != null && ready.targets.size > 1 && (stream == null && target?.direct == null || serversOpen)) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.72f)).padding(12.dp)) {
                if (audios.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        audios.forEach { name ->
                            KhChip(name.replaceFirstChar { it.uppercase() }, selected = name.equals(audio.ifBlank { audios.first() }, true), onClick = { audio = name })
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(ready.targets) { i, server ->
                        KhChip(server.name, selected = i == index, onClick = {
                            serversOpen = false
                            showPage = false
                            stream = null
                            if (i == index) return@KhChip
                            index = i
                        })
                    }
                }
                if (serversOpen) {
                    KhButton("Close", onClick = { serversOpen = false }, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
    BackHandler(enabled = serversOpen) { serversOpen = false }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun Native(
    vm: AppViewModel,
    dest: Dest.Watch,
    plan: PlayPlan,
    url: String,
    headers: Map<String, String>,
    subs: List<SubtitleFile>,
    label: String,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onServers: () -> Unit,
    onFail: () -> Unit,
) {
    val resume = remember(dest.progressKey) { vm.media.progress.position(dest.progressKey) }
    VideoPlayer(
        url = url,
        subs = subs,
        resumeMs = resume,
        title = plan.title.ifBlank { dest.title },
        label = label,
        onProgress = { pos, dur -> vm.media.progress.save(dest.progressKey, plan.title, plan.poster, pos, dur) },
        onPlaybackError = { onFail() },
        onBack = { vm.onBack() },
        onHome = { vm.goHome() },
        onEpisodes = { vm.onBack() },
        onNext = onNext,
        onPrev = onPrev,
        onServers = onServers,
        requestHeaders = headers,
        showEpisodes = dest.kind != com.kisskh.tv.api.media.MediaKind.MOVIE,
    )
}

@Composable
private fun WatchMessage(message: String, onBack: () -> Unit, onHome: () -> Unit, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message, style = KhTheme.typography.title, color = KhColors.TextPrimary)
            if (onRetry != null) KhButton("Try again", onClick = onRetry, primary = true)
            KhButton("Back", onClick = onBack)
            KhButton("Home", onClick = onHome)
        }
    }
}
