package com.kisskh.tv.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

data class SubtitleFile(val uri: String, val label: String, val language: String)

class Repository(context: Context) : CatalogSource {
    private val appContext = context.applicationContext

    private val http = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Referer", "https://kisskh.co/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/130 Safari/537.36")
                .build()
            chain.proceed(req)
        }
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    val api: KissKhApi = Retrofit.Builder()
        .baseUrl("https://kisskh.co/")
        .client(http)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(KissKhApi::class.java)

    private val keys = KeyService(appContext, http)

    /**
     * Returns the playable url for an episode, or null if it isn't released yet.
     * type 1 = HLS (.m3u8), type 0 = direct .mp4, type 2 = third-party countdown widget (not playable).
     */
    suspend fun streamUrl(episodeId: Int): String? {
        val kkey = keys.videoKey(episodeId)
        val s = api.episode(episodeId, kkey)
        android.util.Log.i("KissKH", "stream ep=$episodeId keyLen=${kkey.length} type=${s.type} video=${s.video?.take(80)}")
        val v = s.video ?: return null
        if (s.type == 2 || !v.startsWith("http")) return null
        return v
    }

    /** Downloads every subtitle, decrypts encrypted cues, and writes local SRT files for the player. */
    suspend fun subtitles(episodeId: Int): List<SubtitleFile> = withContext(Dispatchers.IO) {
        val out = ArrayList<SubtitleFile>()
        val list = try {
            api.subs(episodeId, keys.subKey(episodeId))
        } catch (_: Throwable) {
            emptyList()
        }
        val dir = File(appContext.cacheDir, "subs").apply { mkdirs() }
        for ((i, sub) in list.withIndex()) {
            try {
                val raw = http.newCall(Request.Builder().url(sub.src).header("Referer", "https://kisskh.co/").build())
                    .execute().use { it.body?.string() } ?: continue
                val ext = SubtitleCrypto.extensionOf(sub.src)
                val text = if (SubtitleCrypto.isEncrypted(ext)) {
                    raw.split(Regex("\\r?\\n")).joinToString("\n") { line ->
                        val t = line.trim()
                        if (t.isEmpty() || t.all { it.isDigit() } || t.contains("-->")) line
                        else SubtitleCrypto.decryptLine(t, ext)
                    }
                } else raw
                val f = File(dir, "$episodeId-$i.srt")
                f.writeText(text)
                out.add(SubtitleFile(f.toURI().toString(), sub.label ?: "Subtitle", sub.land ?: "und"))
            } catch (_: Throwable) {
            }
        }
        // English default first
        out.sortedByDescending { it.language.startsWith("en") }
    }

    // --- catalog ---

    /** One page of DramaList/List for [query]. Also gives [count] and [availableTabs]. */
    override suspend fun list(query: CatalogQuery, page: Int, pageSize: Int): ListPage =
        api.list(
            page = page,
            pageSize = pageSize,
            type = query.type.id,
            country = query.countryId,
            status = query.status.id,
            order = query.order.id,
        )

    /** Title detail as a [Result], so callers can show an error with Retry instead of hanging. */
    suspend fun dramaResult(id: Int): Result<Drama> = suspendCatching { api.drama(id) }

    // --- resume positions and watch history ---

    /** Saved positions ("resume" prefs, `ep_<id>`) and the per-title last-watched entries ("history" prefs). */
    val history = WatchHistory.create(appContext)

    fun savePosition(episodeId: Int, positionMs: Long, durationMs: Long) =
        history.savePosition(episodeId, positionMs, durationMs)

    fun loadPosition(episodeId: Int): Long = history.loadPosition(episodeId)
}
