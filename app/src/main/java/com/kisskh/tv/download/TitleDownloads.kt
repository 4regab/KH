package com.kisskh.tv.download

import android.Manifest
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.SniffedStream
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.PlayTarget
import com.kisskh.tv.api.source.TitleCard
import com.kisskh.tv.player.SniffWebView
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "TitleDownloads"

/** How long one page server may take to ask for its stream before the next one is tried. */
private const val SNIFF_TIMEOUT_MS = 20_000L

/** One page being loaded out of sight to catch its stream. */
internal class SniffJob(val page: String, val referer: String?, val userAgent: String?, val key: String) {
    val result = CompletableDeferred<SniffedStream?>()
}

/**
 * The downloads of one title page. It shows the live state of each episode, finds the stream when
 * the viewer taps Download, and hands it to [KhDownloads]. Only one stream is looked for at a time.
 * Leaving the page stops the search; a download that already started goes on in the background.
 *
 * Create it with [rememberTitleDownloads] and call [Host] once on the page.
 */
@Stable
class TitleDownloads internal constructor(
    private val vm: AppViewModel,
    private val library: Library,
    private val card: TitleCard,
    private val scope: CoroutineScope,
    private val context: Context,
    private val items: State<List<DownloadItem>>,
    private val preparing: State<Set<String>>,
) {
    private val downloads = vm.downloads
    private var finding by mutableStateOf<String?>(null)
    private var sniffJob by mutableStateOf<SniffJob?>(null)
    private var search: Job? = null
    private var awaitingPermission: DownloadOffer? = null
    private var sniffCount = 0
    private val resolver = StreamResolver(vm) { target, userAgent -> sniffPage(target, userAgent) }

    /** Opens the notification question. Set by [rememberTitleDownloads]. */
    internal var askNotifications: () -> Unit = {}

    /** The id of the download for [episode], or null when it can't be downloaded. */
    fun idFor(episode: EpisodeRef): String? = downloadIdFor(library, card, episode)

    /** The saved download with [id], if any. Reading it redraws the caller as the download moves. */
    internal fun itemFor(id: String): DownloadItem? = items.value.firstOrNull { it.id == id }

    /** What the control for [id] shows right now. */
    internal fun uiFor(id: String): DownloadUi =
        downloadUi(itemFor(id), finding = finding == id, preparing = id in preparing.value)

    /** Look for the stream of [episode] and download it. A second tap while one is being found only says so. */
    internal fun start(episode: EpisodeRef, episodes: List<EpisodeRef>) {
        val id = idFor(episode) ?: return
        if (finding != null) {
            toast("Still finding another stream. One moment.")
            return
        }
        finding = id
        search = scope.launch {
            try {
                when (val found = resolver.resolve(library, card, episode, episodes)) {
                    is Resolution.Ready -> begin(found.offer)
                    is Resolution.Unavailable -> {
                        toast(found.message)
                        finding = null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "could not find a stream for $id", failure)
                toast("Couldn't find the stream. Try again.")
                finding = null
            }
        }
    }

    /** Stop looking for a stream. */
    internal fun stopFinding() {
        search?.cancel()
        search = null
        sniffJob = null
        awaitingPermission = null
        finding = null
    }

    internal fun remove(id: String) {
        downloads.remove(id)
    }

    internal fun playOffline(id: String) {
        vm.push(Dest.OfflinePlayer(id))
    }

    internal fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    /** Starts the download. On Android 13+ the first one asks for the notification permission; it starts either way. */
    private fun begin(offer: DownloadOffer) {
        toast("Downloading ${offer.title}")
        if (downloads.shouldAskNotifications()) {
            downloads.markNotificationsAsked()
            awaitingPermission = offer
            askNotifications()
        } else {
            downloads.start(offer)
            finding = null
        }
    }

    internal fun notificationsAnswered() {
        awaitingPermission?.let { downloads.start(it) }
        awaitingPermission = null
        finding = null
    }

    private suspend fun sniffPage(target: PlayTarget, userAgent: String?): SniffedStream? {
        val page = target.page ?: return null
        val job = SniffJob(page, target.referer, userAgent, key = "$page#${sniffCount++}")
        sniffJob = job
        try {
            return withTimeoutOrNull(SNIFF_TIMEOUT_MS) { job.result.await() }
        } finally {
            sniffJob = null
        }
    }

    /**
     * Where the hidden page loads while a stream is being found: 1 dp wide and see-through, never
     * focusable, so it can't be seen or reached with the D-pad. Place it once, anywhere on the page.
     */
    @Composable
    fun Host() {
        val job = sniffJob ?: return
        key(job.key) {
            Box(Modifier.size(1.dp).alpha(0.01f).clearAndSetSemantics { }) {
                SniffWebView(
                    page = job.page,
                    referer = job.referer,
                    loadKey = job.key,
                    onStream = { job.result.complete(it) },
                    onFail = { job.result.complete(null) },
                    modifier = Modifier.fillMaxSize(),
                    userAgent = job.userAgent,
                    onCreated = { web ->
                        web.isFocusable = false
                        web.isFocusableInTouchMode = false
                        web.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                )
            }
        }
    }
}

/** The downloads of the title page for [card] on [library]. See [TitleDownloads]. */
@Composable
fun rememberTitleDownloads(vm: AppViewModel, library: Library, card: TitleCard): TitleDownloads {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = vm.downloads.items.collectAsStateWithLifecycle()
    val preparing = vm.downloads.preparing.collectAsStateWithLifecycle()
    val holder = remember(vm, library, card.id) { TitleDownloads(vm, library, card, scope, context, items, preparing) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Allowed or not, the download starts. Without the permission it just has no notification.
        holder.notificationsAnswered()
    }
    holder.askNotifications = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    return holder
}
