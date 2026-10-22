package com.kisskh.tv.download

import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import okio.Buffer
import java.util.Locale

/*
 * The plain data behind a download. Nothing here touches Android, so it can run in a JVM test.
 */

const val KIND_MOVIE = "movie"
const val KIND_EPISODE = "episode"

/** Ids of downloads. One id per movie or episode, so tapping Download twice never makes two copies. */
object DownloadIds {
    /** KissKH episodes have no progress key, so they get their own id. */
    fun kisskh(episodeId: Int) = "kh:ep:$episodeId"

    /** Every other source already has a stable progress key such as `cw:tv:1396:1:2`. */
    fun media(progressKey: String) = progressKey

    /** The id as a folder name: letters, digits, dot, dash and underscore only. */
    fun dirName(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

/** One subtitle file saved inside the app's own storage next to a download. */
data class DownloadSub(val path: String, val label: String, val language: String)

/**
 * Everything the app needs to list and play a download later. It is saved as JSON inside the
 * download itself (`DownloadRequest.data`), so it survives a restart with the download index.
 */
data class DownloadMeta(
    val id: String,
    val title: String,
    /** For example "S1 E2 · Name" or "Movie". */
    val label: String,
    val poster: String?,
    /** The name of the source the video came from, for example "CineWave". */
    val source: String,
    val kind: String,
    /** Progress key for every source except KissKH. Null for KissKH. */
    val progressKey: String?,
    val episodeId: Int,
    val dramaId: Int,
    val episodeNumber: Float,
    val subs: List<DownloadSub>,
    /** Sent with every request of this download (Referer, Origin, User-Agent). */
    val headers: Map<String, String>,
    val createdAt: Long,
) {
    val isKisskh: Boolean get() = progressKey.isNullOrBlank()

    fun toJson(): String {
        val out = Buffer()
        JsonWriter.of(out).use { w ->
            w.beginObject()
            w.name("id").value(id)
            w.name("title").value(title)
            w.name("label").value(label)
            w.name("poster").value(poster)
            w.name("source").value(source)
            w.name("kind").value(kind)
            w.name("progressKey").value(progressKey)
            w.name("episodeId").value(episodeId.toLong())
            w.name("dramaId").value(dramaId.toLong())
            w.name("episodeNumber").value(episodeNumber.toDouble())
            w.name("subs").beginArray()
            for (sub in subs) {
                w.beginObject()
                w.name("path").value(sub.path)
                w.name("label").value(sub.label)
                w.name("language").value(sub.language)
                w.endObject()
            }
            w.endArray()
            w.name("headers").beginObject()
            for ((key, value) in headers) w.name(key).value(value)
            w.endObject()
            w.name("createdAt").value(createdAt)
            w.endObject()
        }
        return out.readUtf8()
    }

    companion object {
        /** Reads what [toJson] wrote. Returns null for empty or damaged data. */
        fun fromJson(raw: ByteArray?): DownloadMeta? {
            if (raw == null || raw.isEmpty()) return null
            return try {
                JsonReader.of(Buffer().write(raw)).use { read(it) }
            } catch (_: Exception) {
                null
            }
        }

        private fun read(r: JsonReader): DownloadMeta? {
            var id = ""
            var title = ""
            var label = ""
            var poster: String? = null
            var source = ""
            var kind = KIND_EPISODE
            var progressKey: String? = null
            var episodeId = 0
            var dramaId = 0
            var episodeNumber = 0f
            var subs = emptyList<DownloadSub>()
            var headers = emptyMap<String, String>()
            var createdAt = 0L
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "id" -> id = r.text()
                    "title" -> title = r.text()
                    "label" -> label = r.text()
                    "poster" -> poster = r.textOrNull()
                    "source" -> source = r.text()
                    "kind" -> kind = r.text()
                    "progressKey" -> progressKey = r.textOrNull()
                    "episodeId" -> episodeId = r.nextInt()
                    "dramaId" -> dramaId = r.nextInt()
                    "episodeNumber" -> episodeNumber = r.nextDouble().toFloat()
                    "subs" -> subs = readSubs(r)
                    "headers" -> headers = readHeaders(r)
                    "createdAt" -> createdAt = r.nextLong()
                    else -> r.skipValue()
                }
            }
            r.endObject()
            if (id.isBlank()) return null
            return DownloadMeta(id, title, label, poster, source, kind, progressKey, episodeId, dramaId, episodeNumber, subs, headers, createdAt)
        }

        private fun readSubs(r: JsonReader): List<DownloadSub> {
            val out = ArrayList<DownloadSub>()
            r.beginArray()
            while (r.hasNext()) {
                var path = ""
                var label = ""
                var language = ""
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "path" -> path = r.text()
                        "label" -> label = r.text()
                        "language" -> language = r.text()
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                if (path.isNotBlank()) out += DownloadSub(path, label, language)
            }
            r.endArray()
            return out
        }

        private fun readHeaders(r: JsonReader): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            r.beginObject()
            while (r.hasNext()) {
                val key = r.nextName()
                out[key] = r.text()
            }
            r.endObject()
            return out
        }

        private fun JsonReader.text(): String = textOrNull().orEmpty()

        private fun JsonReader.textOrNull(): String? =
            if (peek() == JsonReader.Token.NULL) {
                skipValue()
                null
            } else {
                nextString()
            }
    }
}

/** Where a download is in its life. */
enum class DownloadPhase { QUEUED, DOWNLOADING, WAITING_NETWORK, PAUSED, COMPLETED, FAILED }

/** One row of the Downloads list and the state behind the player's download button. */
data class DownloadItem(
    val id: String,
    val meta: DownloadMeta,
    val phase: DownloadPhase,
    /** 0 to 100, or -1 when the size is not known yet. */
    val percent: Float,
    val bytes: Long,
    /** The size on disk once the download is complete, else -1. */
    val totalBytes: Long,
) {
    val status: String get() = downloadStatus(phase, percent, bytes, totalBytes)

    /** True while bytes are moving or about to move. */
    val active: Boolean get() = phase == DownloadPhase.DOWNLOADING || phase == DownloadPhase.QUEUED
}

/** The short line under a download's title: "Downloading 42%", "Queued", "Ready · 1.2 GB". */
fun downloadStatus(phase: DownloadPhase, percent: Float, bytes: Long, totalBytes: Long): String = when (phase) {
    DownloadPhase.QUEUED -> "Queued"
    DownloadPhase.DOWNLOADING -> when {
        percent >= 0f -> "Downloading ${percent.toInt()}%"
        bytes > 0L -> "Downloading \u00B7 ${formatBytes(bytes)}"
        else -> "Downloading"
    }
    DownloadPhase.WAITING_NETWORK -> "Paused - waiting for network"
    DownloadPhase.PAUSED -> "Paused"
    DownloadPhase.FAILED -> "Failed"
    DownloadPhase.COMPLETED -> {
        val size = if (totalBytes > 0L) totalBytes else bytes
        if (size > 0L) "Ready \u00B7 ${formatBytes(size)}" else "Ready"
    }
}

fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%d MB", Math.round(bytes / mb))
        bytes >= kb -> String.format(Locale.US, "%d KB", Math.round(bytes / kb))
        else -> "$bytes B"
    }
}
