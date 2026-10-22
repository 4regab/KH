package com.kisskh.tv.player

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import com.kisskh.tv.api.SubtitleFile
import com.kisskh.tv.api.availabilityKey
import com.kisskh.tv.api.media.AudioCheck
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.SniffedStream
import com.kisskh.tv.api.media.SubFetcher
import com.kisskh.tv.api.media.SubLists
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.PlayPlan
import com.kisskh.tv.api.source.PlayTarget
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhSpinner
import com.kisskh.tv.ui.shelf.ShelfSession
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long a page may take to ask for its stream before it is shown to the viewer instead. */
private const val SNIFF_TIMEOUT_MS = 20_000L

/** A web player page that stays up this long without failing counts as playable. */
private const val WEB_PLAYABLE_MS = 10_000L

/**
 * One player for CineWave, CineJoy, Reelix, Anisuge and Anime Nexus.
 *
 * A server with a direct URL plays in the native player at once. A page server is loaded out of
 * sight and its first HLS or MP4 request goes to the native player. A page that never asks (it
 * wants a tap first) is shown in the web player instead. A server that fails moves on to the next
 * one; the server list shows which ones failed and lets the viewer pick any of them.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun WatchScreen(vm: AppViewModel, dest: Dest.Watch) {
    val library = remember(dest.source) { Library.entries.firstOrNull { it.key == dest.source } ?: Library.CINEWAVE }
    var audio by remember(dest.progressKey) { mutableStateOf(dest.audio) }
    var resolved by remember(dest.progressKey) { mutableStateOf<ShelfSession.Resolved?>(null) }
    var plan by remember(dest.progressKey, audio) { mutableStateOf<PlayPlan?>(null) }
    var error by remember(dest.progressKey, audio) { mutableStateOf<String?>(null) }
    var attempt by remember(dest.progressKey) { mutableIntStateOf(0) }
    var index by remember(dest.progressKey, audio) { mutableIntStateOf(0) }
    // Why each server failed, by index. A server in here is skipped when moving on.
    val failed = remember(dest.progressKey, audio, attempt) { mutableStateMapOf<Int, String>() }
    // Servers that only had foreign audio, by index, with the language's name. Kept as a last resort.
    val foreign = remember(dest.progressKey, audio, attempt) { mutableStateMapOf<Int, String>() }
    // Servers whose language is accepted as it is: picked by the viewer, or played as the last resort.
    val accepted = remember(dest.progressKey, audio, attempt) { mutableStateListOf<Int>() }
    val context = LocalContext.current
    var stream by remember(dest.progressKey, audio) { mutableStateOf<SniffedStream?>(null) }
    // The current page server is shown to the viewer instead of being sniffed.
    var showPage by remember(dest.progressKey, audio) { mutableStateOf(false) }
    var serversOpen by remember(dest.progressKey) { mutableStateOf(false) }
    // Bumped when the viewer picks the server that is already selected, to load it again.
    var reload by remember(dest.progressKey) { mutableIntStateOf(0) }

    LaunchedEffect(dest.progressKey, audio, attempt) {
        error = null
        plan = null
        stream = null
        showPage = false
        index = 0
        val found = try {
            vm.shelf.resolve(library, dest)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "Couldn't open this episode. ${failure.message.orEmpty()}".trim()
            return@LaunchedEffect
        }
        resolved = found
        val ready = try {
            vm.shelf.plan(library, found.card, found.episode, audio)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "Couldn't get the server list. ${failure.message.orEmpty()}".trim()
            return@LaunchedEffect
        }
        if (ready.progressKey.isNotBlank()) vm.media.progress.remember(ready.progressKey, ready.title, ready.poster)
        plan = ready
        if (ready.targets.isEmpty()) error = "No server has this episode."
    }

    val ready = plan
    val targets = ready?.targets.orEmpty()
    val target = targets.getOrNull(index)
    val audios = targets.map { it.audio }.filter { it.isNotBlank() }.distinct()

    fun select(next: Int) {
        failed.remove(next)
        // The viewer chose this one, so whatever language it has is fine.
        if (next !in accepted) accepted.add(next)
        stream = null
        showPage = false
        if (next == index) reload++ else index = next
    }

    /** Every server failed or had foreign audio only: play the first foreign one anyway. Returns false when none is left. */
    fun playForeignFallback(): Boolean {
        // ponytail: the stream is looked up again for this server; keeping the first one would save a few seconds.
        val best = foreign.keys.filter { it !in accepted }.minOrNull() ?: return false
        accepted.add(best)
        failed.remove(best)
        stream = null
        showPage = false
        Toast.makeText(context, "No English audio found. Playing ${foreign[best]}.", Toast.LENGTH_LONG).show()
        if (best == index) reload++ else index = best
        return true
    }

    /** Mark the current server as failed and move to the next one that has not failed yet. */
    fun fail(reason: String) {
        failed[index] = reason
        stream = null
        showPage = false
        val next = (index + 1 until targets.size).firstOrNull { it !in failed } ?: (0 until index).firstOrNull { it !in failed }
        if (next != null) index = next else playForeignFallback()
    }

    /** The stream plays, but only in [language]. Move on, and keep this server in case no other has English. */
    fun foreignAudio(language: String) {
        foreign[index] = language
        fail("only $language audio")
    }

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

    // Subtitle lists some servers keep apart from the video (VidZee, VidRock). Fetched while the page loads.
    var extraSubs by remember(target) { mutableStateOf<List<SubtitleFile>?>(if (target?.subsUrl == null) emptyList() else null) }
    LaunchedEffect(target) {
        val url = target?.subsUrl ?: return@LaunchedEffect
        extraSubs = withContext(Dispatchers.IO) { SubFetcher().load(url, target.referer) { SubLists.parseLabelFile(it) } }
    }

    val title = ready?.title?.ifBlank { null } ?: dest.title.ifBlank { library.label }
    val label = ready?.label ?: dest.label
    val allFailed = targets.isNotEmpty() && failed.size >= targets.size

    // Cards and the title page say "Not available" for a title where every server failed. Any play clears it.
    // ponytail: the mark is for the title, though only the episode the viewer tried failed.
    val markKey = remember(dest.source, dest.kind, dest.titleId) { availabilityKey(dest.source, dest.kind, dest.titleId) }
    LaunchedEffect(allFailed, markKey) { if (allFailed) vm.availability.markUnavailable(markKey) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            error != null -> WatchMessage(
                title = title,
                message = error!!,
                onRetry = { attempt++ },
                onBack = { vm.onBack() },
            )
            ready == null || target == null -> Preparing(title, label, "Finding servers", onBack = { vm.onBack() })
            allFailed -> WatchMessage(
                title = title,
                message = if (targets.size == 1) {
                    "${target.name} couldn't play this episode (${failed[index]})."
                } else {
                    "None of the ${targets.size} servers could play this episode."
                },
                onRetry = {
                    failed.clear()
                    select(0)
                },
                onServers = if (targets.size > 1) ({ serversOpen = true }) else null,
                onBack = { vm.onBack() },
            )
            target.direct != null || (stream != null && !showPage) -> {
                val url = target.direct ?: stream!!.url
                val headers = (if (target.direct != null) target.headers else stream!!.headers).ifEmpty { mapOf("Referer" to target.referer) }
                val subs = target.subs + extraSubs.orEmpty()
                if (target.subsUrl != null && extraSubs == null) {
                    Preparing(title, label, "Loading subtitles", onBack = { vm.onBack() })
                } else {
                    Native(
                        vm = vm,
                        dest = dest,
                        plan = ready,
                        url = url,
                        headers = headers,
                        subs = subs,
                        label = listOf(label, target.name).filter { it.isNotBlank() }.joinToString(" · "),
                        onPrev = resolved?.previous?.let { episode -> { go(episode) } },
                        onNext = resolved?.next?.let { episode -> { go(episode) } },
                        onServers = { serversOpen = true },
                        onFail = { reason -> fail(reason) },
                        checkAudio = wantsEnglishAudio(dest) && index !in accepted,
                        onForeignAudio = { language -> foreignAudio(language) },
                    )
                }
            }
            target.page != null && target.sniff && !showPage -> {
                LaunchedEffect(target.page, index, reload) {
                    delay(SNIFF_TIMEOUT_MS)
                    if (stream == null) showPage = true
                }
                Box(Modifier.fillMaxSize()) {
                    SniffWebView(
                        page = target.page,
                        referer = target.referer,
                        loadKey = "${target.page}#$index#$reload",
                        onStream = { sniffed -> stream = sniffed },
                        onFail = { fail("didn't load") },
                        modifier = Modifier.fillMaxSize(),
                        userAgent = webUserAgent(library),
                    )
                    // The page loads under this. Its own ads and buttons are never shown while sniffing.
                    Preparing(
                        title = title,
                        label = label,
                        status = "Connecting to ${target.name}" + if (targets.size > 1) " · server ${index + 1} of ${targets.size}" else "",
                        onBack = { vm.onBack() },
                        onServers = if (targets.size > 1) ({ serversOpen = true }) else null,
                        modifier = Modifier.background(Color.Black),
                    )
                }
            }
            target.page != null -> {
                // ponytail: WebPlayer has no 'page loaded' signal, so a page that stays up for a while counts as playable.
                LaunchedEffect(dest.progressKey, index, reload) {
                    delay(WEB_PLAYABLE_MS)
                    vm.availability.clear(markKey)
                }
                WebPlayer(
                    key = "${dest.progressKey}#$index#$reload",
                    title = title,
                    label = listOf(label, target.name).filter { it.isNotBlank() }.joinToString(" · "),
                    sources = listOf(WebSource(target.name, target.name, target.page)),
                    referer = target.referer,
                    onBack = { vm.onBack() },
                    onHome = { vm.goHome() },
                    onEpisodes = if (dest.kind != MediaKind.MOVIE) ({ vm.onBack() }) else null,
                    onNext = resolved?.next?.let { episode -> { go(episode) } },
                    userAgent = webUserAgent(library),
                    onServers = { serversOpen = true },
                    onFailed = { reason -> fail(reason) },
                )
            }
            else -> LaunchedEffect(index) { fail("no address") }
        }

        if (serversOpen && ready != null && !Pip.active) {
            PlayerSheet(title = "Servers", onClose = { serversOpen = false }) {
                if (audios.size > 1) {
                    SheetHeading("Audio")
                    audios.forEach { name ->
                        SheetOption(
                            name.replaceFirstChar { it.uppercase() },
                            selected = name.equals(audio.ifBlank { audios.first() }, true),
                        ) {
                            serversOpen = false
                            audio = name
                        }
                    }
                }
                SheetHeading(if (targets.size == 1) "Server" else "${targets.size} servers")
                targets.forEachIndexed { i, server ->
                    val reason = failed[i]
                    SheetOption(
                        text = server.name,
                        selected = i == index && reason == null,
                        note = when {
                            reason != null -> "Failed"
                            i == index -> "Now"
                            server.direct != null -> "Direct"
                            else -> null
                        },
                        dimmed = reason != null,
                    ) {
                        serversOpen = false
                        select(i)
                    }
                }
            }
        }
    }
}

/** CineJoy's page picks its own (better) layout for a phone browser; embed hosts want a desktop one. */
internal fun webUserAgent(library: Library): String? = if (library == Library.CINEJOY) null else DESKTOP_UA

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
    onFail: (String) -> Unit,
    checkAudio: Boolean,
    onForeignAudio: (String) -> Unit,
) {
    // Read again for every stream, so a server switch resumes where the last one stopped.
    val resume = remember(dest.progressKey, url) { vm.media.progress.position(dest.progressKey) }
    VideoPlayer(
        url = url,
        subs = subs,
        resumeMs = resume,
        title = plan.title.ifBlank { dest.title },
        label = label,
        onProgress = { pos, dur ->
            vm.media.progress.save(dest.progressKey, plan.title, plan.poster, pos, dur)
            // The stream moved past where it started: this title plays, so it is no longer Not available.
            if (dur > 0 && pos > resume) vm.availability.clear(availabilityKey(dest.source, dest.kind, dest.titleId))
        },
        onPlaybackError = { onFail(it) },
        onBack = { vm.onBack() },
        onHome = { vm.goHome() },
        onEpisodes = { vm.onBack() },
        onNext = onNext,
        onPrev = onPrev,
        onServers = onServers,
        requestHeaders = headers,
        showEpisodes = dest.kind != MediaKind.MOVIE,
        // Anime's sub or dub is the source's own audio setting; every movie and series starts in English.
        preferEnglishAudio = wantsEnglishAudio(dest),
        // A server can fall back to a dub when its English one is down; only the stream says which language it has.
        onAudioLanguages = if (checkAudio) {
            ({ languages: List<String?> -> if (AudioCheck.foreignOnly(languages)) onForeignAudio(AudioCheck.label(languages)) })
        } else {
            null
        },
    )
}

/** True when this title should start in English audio. Anime keeps its own sub or dub setting. */
internal fun wantsEnglishAudio(dest: Dest.Watch): Boolean =
    dest.kind != MediaKind.ANIME && dest.source != Library.ANISUGE.key && dest.source != Library.NEXUS.key

/** Black screen with the title, a spinner and what is happening. Back always works. */
@Composable
private fun Preparing(
    title: String,
    label: String,
    status: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onServers: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            KhButton("Back", onClick = onBack)
            Column(Modifier.padding(start = 16.dp)) {
                Text(title, style = KhTheme.typography.title, color = KhColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (label.isNotBlank()) Text(label, style = KhTheme.typography.caption, color = KhColors.TextSecondary, maxLines = 1)
            }
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KhSpinner(color = KhColors.Brand)
            Text(status, style = KhTheme.typography.body, color = KhColors.TextSecondary, textAlign = TextAlign.Center)
        }
        if (onServers != null) {
            KhButton("Servers", onClick = onServers, modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun WatchMessage(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onServers: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = KhTheme.typography.caption, color = KhColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(message, style = KhTheme.typography.title, color = KhColors.TextPrimary, textAlign = TextAlign.Center)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KhButton("Try again", onClick = onRetry, primary = true)
                if (onServers != null) KhButton("Servers", onClick = onServers)
                KhButton("Back", onClick = onBack)
            }
        }
    }
}
