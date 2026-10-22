package com.kisskh.tv.download

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.offline.DownloaderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.Executors
import kotlin.coroutines.resumeWithException

private const val TAG = "KhDownloads"
private const val PREFS = "downloads"
private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) Chrome/130 Safari/537.36"
private const val POLL_MS = 1_000L

/** How long a tapped Download shows "preparing" at most, while the service picks the request up. */
private const val PREPARE_GRACE_MS = 3_000L

// ponytail: a subtitle bigger than this is skipped. Real subtitle files are well under 1 MB.
private const val MAX_SUB_BYTES = 5L * 1024 * 1024

/** A finished download opened for playback: the media item and a data source that reads only the offline cache. */
@androidx.annotation.OptIn(UnstableApi::class)
class OfflineSource(val mediaItem: MediaItem, val dataSourceFactory: DataSource.Factory)

/**
 * Offline copies of titles. Owns the Media3 download manager and its cache (both live in the app's
 * private storage), the list of downloads for the screens, and the steps to start and remove one.
 *
 * Everything that touches the manager runs on the main thread.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class KhDownloads private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val databaseProvider = StandaloneDatabaseProvider(app)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val http = OkHttpClient()
    private val executor = Executors.newFixedThreadPool(6)

    // Opening the cache scans its folder, so it starts on a background thread as soon as the app does.
    private val cacheLoad = scope.async(Dispatchers.IO) {
        SimpleCache(File(app.filesDir, "downloads/media"), NoOpCacheEvictor(), databaseProvider)
    }

    // ponytail: the first read of this waits for the cache scan on the main thread; it is short unless
    // the cache holds thousands of files.
    private val cache: Cache by lazy { runBlocking { cacheLoad.await() } }

    private val byId = LinkedHashMap<String, Download>()
    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    private val _loaded = MutableStateFlow(false)
    private val _preparing = MutableStateFlow<Set<String>>(emptySet())
    private var polling: Job? = null

    private val listener = object : DownloadManager.Listener {
        override fun onInitialized(downloadManager: DownloadManager) {
            loadIndex(downloadManager)
            _loaded.value = true
            publish(downloadManager)
            // Downloads that were running when the app closed continue, with their notification.
            if (byId.values.any { it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED }) {
                runCatching { DownloadService.start(app, KhDownloadService::class.java) }
            }
        }

        override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
            if (finalException != null) Log.w(TAG, "download ${download.request.id} ended with an error", finalException)
            byId[download.request.id] = download
            _preparing.update { it - download.request.id }
            publish(downloadManager)
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            byId.remove(download.request.id)
            publish(downloadManager)
            scope.launch(Dispatchers.IO) { subsDir(download.request.id).deleteRecursively() }
        }

        override fun onWaitingForRequirementsChanged(downloadManager: DownloadManager, waitingForRequirements: Boolean) {
            publish(downloadManager)
        }
    }

    /** The manager. The first use creates it. The download service reads it from here too. */
    internal val manager: DownloadManager by lazy {
        val factory = DownloaderFactory { request ->
            val headers = DownloadMeta.fromJson(request.data)?.headers.orEmpty()
            val upstream = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(httpFactory(headers))
            DefaultDownloaderFactory(upstream, executor).createDownloader(request)
        }
        DownloadManager(app, DefaultDownloadIndex(databaseProvider), factory).also { created ->
            created.addListener(listener)
            if (created.isInitialized) listener.onInitialized(created)
        }
    }

    /** Newest first. Downloads being removed are left out. */
    val items: StateFlow<List<DownloadItem>>
        get() {
            manager // Reading the list starts the manager, which fills it.
            return _items
        }

    /** False until the saved downloads have been read, so an empty list can be told from a loading one. */
    val loaded: StateFlow<Boolean>
        get() {
            manager
            return _loaded
        }

    /** Ids of downloads that were just tapped and are still being set up. */
    val preparing: StateFlow<Set<String>> get() = _preparing

    /** True once, on Android 13+ when the notification permission has not been asked for yet. */
    fun shouldAskNotifications(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            !prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false) &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun markNotificationsAsked() {
        prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
    }

    /**
     * Download the title in [offer] at its highest quality. Does nothing when it is already
     * downloaded, downloading, queued or being set up. A failed download starts again.
     */
    fun start(offer: DownloadOffer) {
        val m = manager
        val existing = byId[offer.id]?.let { toItem(m, it) }
        if (existing != null && existing.phase != DownloadPhase.FAILED) return
        if (offer.id in _preparing.value) return
        _preparing.update { it + offer.id }
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { saveSubs(offer) }
                val meta = offer.toMeta(saved, System.currentTimeMillis())
                val request = requestFor(offer, meta)
                DownloadService.sendAddDownload(app, KhDownloadService::class.java, request, false)
                delay(PREPARE_GRACE_MS)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "could not start download ${offer.id}", failure)
                withContext(Dispatchers.IO) { subsDir(offer.id).deleteRecursively() }
                Toast.makeText(app, "Couldn't start the download. Try again.", Toast.LENGTH_LONG).show()
            } finally {
                _preparing.update { it - offer.id }
            }
        }
    }

    /** Stop and delete a download, with its subtitle files. Safe for an id that does not exist. */
    fun remove(id: String) {
        manager.removeDownload(id)
    }

    /** The finished download [id] ready to play with no network, or null when it is missing or not finished. */
    fun offlineSource(id: String): OfflineSource? {
        manager
        val download = byId[id]?.takeIf { it.state == Download.STATE_COMPLETED } ?: return null
        val readOnly = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(null)
            .setCacheWriteDataSinkFactory(null)
        // DefaultDataSource sends file:// (the saved subtitles) to disk and the rest to the cache.
        return OfflineSource(download.request.toMediaItem(), DefaultDataSource.Factory(app, readOnly))
    }

    private fun httpFactory(headers: Map<String, String>) = DefaultHttpDataSource.Factory()
        // A sniffed stream carries the page's own user agent; some CDNs check it matches.
        .setUserAgent(headers["User-Agent"] ?: DEFAULT_USER_AGENT)
        .setDefaultRequestProperties(headers - "User-Agent")
        .setAllowCrossProtocolRedirects(true)

    /**
     * Builds the request. HLS is prepared first so only the best video rendition (and the audio that
     * goes with it) is downloaded. Anything else is downloaded as one file.
     */
    private suspend fun requestFor(offer: DownloadOffer, meta: DownloadMeta): DownloadRequest {
        val uri = Uri.parse(offer.url)
        val data = meta.toJson().toByteArray(Charsets.UTF_8)
        if (Util.inferContentTypeForUriAndMimeType(uri, null) != C.CONTENT_TYPE_HLS) {
            return DownloadRequest.Builder(offer.id, uri).setData(data).build()
        }
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<DownloadRequest> { cont ->
                val item = MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_M3U8).build()
                // No viewport limit: the best rendition the device can decode, whatever the screen size.
                val helper = DownloadHelper.forMediaItem(
                    item,
                    DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS_WITHOUT_CONTEXT,
                    DefaultRenderersFactory(app),
                    httpFactory(offer.headers),
                )
                cont.invokeOnCancellation { helper.release() }
                helper.prepare(
                    object : DownloadHelper.Callback {
                        override fun onPrepared(helper: DownloadHelper) {
                            val result = runCatching { helper.getDownloadRequest(offer.id, data) }
                            helper.release()
                            result.onSuccess { Log.i(TAG, "hls ${offer.id} streamKeys=${it.streamKeys}") }
                            cont.resumeWith(result)
                        }

                        override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                            helper.release()
                            cont.resumeWithException(e)
                        }
                    },
                )
            }
        }
    }

    private fun loadIndex(m: DownloadManager) {
        try {
            m.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    val download = cursor.download
                    byId[download.request.id] = download
                }
            }
        } catch (failure: IOException) {
            Log.e(TAG, "could not read the download index", failure)
        }
    }

    private fun publish(m: DownloadManager) {
        _items.value = byId.values.mapNotNull { toItem(m, it) }.sortedByDescending { it.meta.createdAt }
        if (byId.values.any { it.state == Download.STATE_DOWNLOADING }) poll(m)
    }

    /** While anything downloads, read its progress once a second. The manager has no progress callback. */
    private fun poll(m: DownloadManager) {
        if (polling?.isActive == true) return
        polling = scope.launch {
            while (isActive && byId.values.any { it.state == Download.STATE_DOWNLOADING }) {
                delay(POLL_MS)
                for (download in m.currentDownloads) byId[download.request.id] = download
                publish(m)
            }
        }
    }

    private fun toItem(m: DownloadManager, download: Download): DownloadItem? {
        val meta = DownloadMeta.fromJson(download.request.data) ?: return null
        val phase = when (download.state) {
            Download.STATE_COMPLETED -> DownloadPhase.COMPLETED
            Download.STATE_DOWNLOADING -> DownloadPhase.DOWNLOADING
            Download.STATE_FAILED -> DownloadPhase.FAILED
            Download.STATE_STOPPED -> DownloadPhase.PAUSED
            Download.STATE_QUEUED, Download.STATE_RESTARTING ->
                if (m.isWaitingForRequirements) DownloadPhase.WAITING_NETWORK else DownloadPhase.QUEUED
            else -> return null
        }
        return DownloadItem(
            id = meta.id,
            meta = meta,
            phase = phase,
            percent = download.percentDownloaded,
            bytes = download.bytesDownloaded,
            totalBytes = download.contentLength,
        )
    }

    private fun subsDir(id: String) = File(app.filesDir, "downloads/subs/${DownloadIds.dirName(id)}")

    /** Copies each subtitle into app storage. A subtitle that can't be read is skipped; the video still downloads. */
    private fun saveSubs(offer: DownloadOffer): List<DownloadSub> {
        if (offer.subs.isEmpty()) return emptyList()
        val dir = subsDir(offer.id)
        dir.deleteRecursively()
        dir.mkdirs()
        val saved = ArrayList<DownloadSub>()
        offer.subs.forEachIndexed { index, sub ->
            try {
                val bytes = readSub(sub.uri, offer.headers) ?: return@forEachIndexed
                val head = String(bytes, 0, minOf(bytes.size, 16), Charsets.UTF_8).trimStart('\uFEFF')
                val vtt = head.startsWith("WEBVTT")
                val file = File(dir, "$index.${if (vtt) "vtt" else "srt"}")
                file.writeBytes(bytes)
                saved += DownloadSub(file.absolutePath, sub.label, sub.language)
            } catch (failure: Exception) {
                Log.w(TAG, "skipped subtitle ${sub.label}", failure)
            }
        }
        return saved
    }

    private fun readSub(uri: String, headers: Map<String, String>): ByteArray? {
        if (uri.startsWith("file:")) return File(URI(uri)).readBytes()
        val request = Request.Builder().url(uri).apply { headers.forEach { (name, value) -> header(name, value) } }.build()
        http.newCall(request).execute().use { response ->
            val body = response.body ?: return null
            if (!response.isSuccessful || body.contentLength() > MAX_SUB_BYTES) return null
            return body.bytes()
        }
    }

    companion object {
        @Volatile private var instance: KhDownloads? = null

        fun get(context: Context): KhDownloads =
            instance ?: synchronized(this) { instance ?: KhDownloads(context.applicationContext).also { instance = it } }
    }
}
