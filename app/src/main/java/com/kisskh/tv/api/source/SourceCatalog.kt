package com.kisskh.tv.api.source

import com.kisskh.tv.api.CatalogQuery
import com.kisskh.tv.api.ContentType
import com.kisskh.tv.api.Country
import com.kisskh.tv.api.Repository
import com.kisskh.tv.api.Show
import com.kisskh.tv.api.SortOrder
import com.kisskh.tv.api.SubtitleFile
import com.kisskh.tv.api.media.CineJoyWeb
import com.kisskh.tv.api.media.EmbedServers
import com.kisskh.tv.api.media.FebParse
import com.kisskh.tv.api.media.Febbox
import com.kisskh.tv.api.media.FebboxClient
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.SubFetcher
import com.kisskh.tv.api.media.SubLists
import com.kisskh.tv.api.media.TmdbClient
import com.kisskh.tv.api.media.TmdbPaths
import com.kisskh.tv.api.media.TmdbTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * The one place a screen asks a source for rows, a title, or a play plan.
 * KissKH still goes through [Repository] so the stream key and subtitle decrypt stay where they are.
 */
object SourceCatalog {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    private val cinejoyHeaders = mapOf("Referer" to "https://cinejoy.pk/", "Origin" to "https://cinejoy.pk")
    private val waveReferer = "https://watch.cinewave.qzz.io/"

    fun get(url: String, headers: Map<String, String> = emptyMap(), client: OkHttpClient = http): String {
        val builder = Request.Builder().url(url).header("User-Agent", UA).header("Accept", headers["Accept"] ?: "*/*")
        headers.forEach { (key, value) -> builder.header(key, value) }
        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return body
        }
    }

    suspend fun home(library: Library, repo: Repository, tmdb: TmdbClient): List<ShelfRow> = when (library) {
        Library.KISSKH -> kissHome(repo)
        Library.CINEWAVE -> listOf(
            ShelfRow("Trending", tmdb.trendingAll().items.map { it.card(library) }),
            ShelfRow("Trending movies", tmdb.trendingMovies().items.map { it.card(library, MediaKind.MOVIE) }),
            ShelfRow("Trending TV", tmdb.trendingTv().items.map { it.card(library, MediaKind.TV) }),
            ShelfRow("Anime", tmdb.anime(1).items.map { it.card(library, MediaKind.ANIME) }),
        ).filter { it.items.isNotEmpty() }
        Library.CINEJOY -> listOf(
            ShelfRow("Trending movies", tmdb.trendingMovies().items.map { it.card(library, MediaKind.MOVIE) }),
            ShelfRow("Trending TV", tmdb.trendingTv().items.map { it.card(library, MediaKind.TV) }),
            ShelfRow("Popular movies", tmdb.discover("movie", false, 1, TmdbPaths.todayUtc()).items.map { it.card(library, MediaKind.MOVIE) }),
            ShelfRow("On the air", tmdb.onTheAir().items.map { it.card(library, MediaKind.TV) }),
        ).filter { it.items.isNotEmpty() }
        Library.REELIX -> reelixRows()
        Library.ANISUGE -> Anisuge.parseHome(get(Anisuge.SITE + "/home", mapOf("Referer" to Anisuge.REFERER)))
        Library.NEXUS -> listOf(
            ShelfRow("Featured", Nexus.parseList(get(Nexus.featuredUrl(), Nexus.headers()))),
            ShelfRow("Popular", Nexus.parseList(get(Nexus.popularUrl(), Nexus.headers()))),
            ShelfRow("Recent", Nexus.parseList(get(Nexus.recentUrl(), Nexus.headers()))),
            ShelfRow("Seasonal", runCatching { Nexus.parseList(get(Nexus.seasonalUrl(), Nexus.headers())) }.getOrDefault(emptyList())),
        ).filter { it.items.isNotEmpty() }
    }

    /**
     * One page of a Movies / TV / Anime grid. [country] (an ISO code, see [OriginCountries]) only
     * applies to CineWave and CineJoy movies and TV; every other grid ignores it.
     */
    suspend fun browse(library: Library, kind: MediaKind, page: Int, repo: Repository, tmdb: TmdbClient, country: String? = null): ShelfPage = when (library) {
        Library.KISSKH -> {
            val query = CatalogQuery(type = kind.toContent())
            val list = repo.list(query, page, 40)
            val size = list.pageSize.coerceAtLeast(1)
            ShelfPage(list.data.map { it.card(kind) }, page, page * size < list.totalCount)
        }
        Library.CINEWAVE, Library.CINEJOY -> {
            val today = TmdbPaths.todayUtc()
            val result = when (kind) {
                // ponytail: anime stays unfiltered, its list is picked by a TMDB keyword, not by country.
                MediaKind.ANIME -> tmdb.anime(page)
                MediaKind.MOVIE -> tmdb.discover("movie", false, page, today, country)
                MediaKind.TV -> tmdb.discover("tv", false, page, today, country)
            }
            ShelfPage(result.items.map { it.card(library, kind) }, result.page, result.page < result.totalPages)
        }
        Library.REELIX -> {
            val html = get(Reelix.listUrl(kind, page), mapOf("Referer" to Reelix.REFERER))
            ShelfPage(Reelix.parseCards(html), page, Reelix.hasNextPage(html, page))
        }
        Library.ANISUGE -> {
            val html = get(Anisuge.SITE + "/home", mapOf("Referer" to Anisuge.REFERER))
            val items = Anisuge.parsePosters(html)
            ShelfPage(items, 1, false)
        }
        Library.NEXUS -> {
            val json = get(Nexus.latestUrl(page), Nexus.headers())
            val items = Nexus.parseList(json)
            if (items.isEmpty() && page == 1) {
                ShelfPage(Nexus.parseList(get(Nexus.popularUrl("week", 24), Nexus.headers())), 1, false)
            } else {
                ShelfPage(items, page, page < Nexus.lastPage(json))
            }
        }
    }

    suspend fun search(library: Library, query: String, repo: Repository, tmdb: TmdbClient): List<TitleCard> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return when (library) {
            Library.KISSKH -> repo.api.search(q).map { show ->
                val kind = if (show.label?.contains("movie", true) == true) MediaKind.MOVIE else MediaKind.TV
                show.card(kind)
            }
            Library.CINEWAVE, Library.CINEJOY -> tmdb.search(q).map { it.card(library) }
            Library.REELIX -> Reelix.parseSearch(get(Reelix.searchUrl(q), mapOf("Referer" to Reelix.REFERER, "Accept" to "application/json")))
            Library.ANISUGE -> Anisuge.parseSearch(
                get(Anisuge.searchUrl(q), mapOf("Referer" to Anisuge.REFERER, "X-Requested-With" to "XMLHttpRequest", "Accept" to "application/json")),
            )
            Library.NEXUS -> Nexus.parseList(get(Nexus.searchUrl(q), Nexus.headers()))
        }
    }

    suspend fun detail(library: Library, id: String, kind: MediaKind, slug: String, name: String, poster: String?, repo: Repository, tmdb: TmdbClient): TitleDetail =
        when (library) {
            Library.KISSKH -> kissDetail(repo, id, kind)
            Library.CINEWAVE, Library.CINEJOY -> tmdbDetail(library, id, kind, tmdb)
            Library.REELIX -> reelixDetail(id, kind, slug, name, poster, tmdb)
            Library.ANISUGE -> anisugeDetail(slug.ifBlank { id }, name, poster)
            Library.NEXUS -> nexusDetail(id, slug, name, poster)
        }

    fun episodes(library: Library, id: String, season: Int, tmdb: TmdbClient): List<EpisodeRef> = when (library) {
        Library.CINEWAVE, Library.CINEJOY -> tmdb.season(id.toInt(), season).map { ep ->
            EpisodeRef(ep.number.toString(), season, ep.number, ep.name, ep.still)
        }
        Library.REELIX -> Reelix.parseSeason(
            get(Reelix.seasonUrl(id, season), mapOf("Referer" to Reelix.REFERER, "Accept" to "application/json")),
            season,
        )
        else -> emptyList()
    }

    fun play(
        library: Library,
        card: TitleCard,
        episode: EpisodeRef,
        audio: String,
        cinejoyToken: String,
        febbox: FebboxClient,
    ): PlayPlan {
        val movie = card.kind == MediaKind.MOVIE
        val season = if (movie) 1 else episode.season.coerceAtLeast(1)
        val number = if (movie) 1 else episode.number.coerceAtLeast(1)
        val label = if (movie) "Movie" else episode.name.ifBlank { "S$season E$number" }.let { name ->
            if (name.startsWith("S$season")) name else "S$season E$number · $name"
        }
        val key = progressKey(library.key, if (movie) MediaKind.MOVIE else card.kind, card.id, season, number)
        val targets = when (library) {
            Library.CINEWAVE -> waveTargets(card, movie, season, number)
            Library.CINEJOY -> joyTargets(card, movie, season, number, cinejoyToken, febbox)
            Library.REELIX -> Reelix.targets(movie, card.id, season, number)
            Library.ANISUGE -> anisugeTargets(card, episode, audio)
            Library.NEXUS -> listOf(Nexus.player(episode.id, episode.slug))
            Library.KISSKH -> emptyList()
        }
        return PlayPlan(card.name, label, card.poster ?: card.backdrop, key, targets)
    }

    private fun waveTargets(card: TitleCard, movie: Boolean, season: Int, episode: Int): List<PlayTarget> {
        val id = card.id.toInt()
        val kind = if (movie) MediaKind.MOVIE else MediaKind.TV
        return EmbedServers.playable.map { server ->
            PlayTarget(
                name = server.name,
                page = server.url(kind, id, season, episode),
                referer = waveReferer,
                subsUrl = server.subsUrl(kind, id, season, episode),
                sniff = server.sniffs,
            )
        }
    }

    private fun joyTargets(
        card: TitleCard,
        movie: Boolean,
        season: Int,
        episode: Int,
        token: String,
        febbox: FebboxClient,
    ): List<PlayTarget> {
        val id = card.id.toInt()
        val subs = SubFetcher().load(CineJoyWeb.subtitlesUrl(movie, id, season, episode), CineJoyWeb.SITE) { SubLists.parseWing(it) }
        if (token.isNotBlank()) {
            val url = Febbox.requestUrl(movie, id, season, episode, token)
            if (url != null) {
                when (val parsed = febbox.fetch(url)) {
                    is FebParse.Ready -> return listOf(
                        PlayTarget(
                            name = "Febbox ${parsed.quality}".trim(),
                            direct = parsed.url,
                            referer = CineJoyWeb.SITE + "/",
                            headers = cinejoyHeaders,
                            subs = parsed.subtitles.map { SubtitleFile(it.url, it.label, it.lang) }.ifEmpty { subs },
                        ),
                    )
                    else -> Unit
                }
            }
        }
        return listOf(
            PlayTarget(
                name = "CineJoy",
                page = CineJoyWeb.watchUrl(movie, id, season, episode),
                referer = CineJoyWeb.SITE + "/",
                subs = subs,
            ),
        )
    }

    private fun anisugeTargets(card: TitleCard, episode: EpisodeRef, audio: String): List<PlayTarget> {
        val current = if (episode.token.isNotBlank()) episode else {
            val show = get(Anisuge.showUrl(card.slug.ifBlank { card.id }), mapOf("Referer" to Anisuge.REFERER))
            val manga = Anisuge.mangaId(show) ?: return emptyList()
            val list = get(
                Anisuge.episodeListUrl(manga, Anisuge.vrf(manga)),
                mapOf("Referer" to Anisuge.REFERER, "X-Requested-With" to "XMLHttpRequest", "Accept" to "application/json"),
            )
            val html = jsonResult(list)
            Anisuge.parseEpisodes(html).firstOrNull { it.number == episode.number } ?: return emptyList()
        }
        val serversHtml = jsonResult(
            get(
                Anisuge.serverListUrl(current.token),
                mapOf("Referer" to Anisuge.REFERER, "X-Requested-With" to "XMLHttpRequest", "Accept" to "application/json"),
            ),
        )
        val wanted = audio.ifBlank { "sub" }
        val servers = Anisuge.parseServers(serversHtml)
        val picked = servers.filter { it.audio.equals(wanted, true) }.ifEmpty { servers }.take(6)
        return picked.mapNotNull { server ->
            val body = get(
                Anisuge.serverUrl(server.linkId),
                mapOf("Referer" to Anisuge.REFERER, "X-Requested-With" to "XMLHttpRequest", "Accept" to "application/json"),
            )
            val page = Anisuge.parseServerUrl(body) ?: return@mapNotNull null
            megaplayOrPage(server, page)
        }
    }

    private fun megaplayOrPage(server: Anisuge.Server, page: String): PlayTarget {
        val id = MegaPlay.streamId(page)
        if (id != null && MegaPlay.handles(page)) {
            try {
                val json = get(
                    MegaPlay.sourcesUrl(id),
                    mapOf(
                        "Referer" to page,
                        "Origin" to MegaPlay.ORIGIN,
                        "Accept" to "application/json",
                    ),
                )
                val resolved = MegaPlay.resolve(json)
                if (resolved != null) {
                    return PlayTarget(
                        name = server.name,
                        audio = server.audio,
                        page = page,
                        referer = MegaPlay.REFERER,
                        direct = resolved.url,
                        headers = MegaPlay.playbackHeaders(),
                        subs = resolved.subs,
                        sniff = false,
                    )
                }
            } catch (_: Exception) {
                // The embed page is still a player if getSources is down.
            }
        }
        return PlayTarget(name = server.name, audio = server.audio, page = page, referer = Anisuge.REFERER)
    }

    private fun jsonResult(json: String): String {
        val html = Regex(""""result"\s*:\s*"((?:\\.|[^"\\])*)"""").find(json)?.groupValues?.get(1)
        if (html != null) return html.replace("\\/", "/").replace("\\\"", "\"").replace("\\n", "").replace("\\u0026", "&")
        return json
    }

    // The Reelix home page can hang for a long time. It gets a short timeout and is optional:
    // if it fails, the screen just loses the Trending row. Movies and TV load beside it.
    private val reelixHomeHttp: OkHttpClient = http.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()

    private suspend fun reelixRows(): List<ShelfRow> = coroutineScope {
        val headers = mapOf("Referer" to Reelix.REFERER)
        val homeCall = async(Dispatchers.IO) { runCatching { get(Reelix.SITE + "/", headers, reelixHomeHttp) } }
        val moviesCall = async(Dispatchers.IO) { runCatching { get(Reelix.listUrl(MediaKind.MOVIE, 1), headers) } }
        val tvCall = async(Dispatchers.IO) { runCatching { get(Reelix.listUrl(MediaKind.TV, 1), headers) } }
        val home = homeCall.await()
        val moviesHtml = moviesCall.await()
        val tvHtml = tvCall.await()
        if (moviesHtml.isFailure && tvHtml.isFailure) throw moviesHtml.exceptionOrNull()!!
        val rows = ArrayList<ShelfRow>()
        val trending = home.getOrNull()?.let { Reelix.parseCards(it).take(24) }.orEmpty()
        if (trending.isNotEmpty()) rows += ShelfRow("Trending", trending)
        val movies = moviesHtml.getOrNull()?.let { Reelix.parseCards(it).take(20) }.orEmpty()
        if (movies.isNotEmpty()) rows += ShelfRow("Movies", movies)
        val tv = tvHtml.getOrNull()?.let { Reelix.parseCards(it).take(20) }.orEmpty()
        if (tv.isNotEmpty()) rows += ShelfRow("TV Shows", tv)
        rows
    }

    private suspend fun reelixDetail(id: String, kind: MediaKind, slug: String, name: String, poster: String?, tmdb: TmdbClient): TitleDetail = coroutineScope {
        val pageCall = async(Dispatchers.IO) {
            if (slug.isNotBlank()) {
                runCatching { get(Reelix.titleUrl(kind, slug), mapOf("Referer" to Reelix.REFERER)) }.getOrNull()
            } else {
                null
            }
        }
        // Reelix ids are TMDB ids but its cards carry no release date. Ask TMDB so unreleased titles show "Coming soon".
        // A TMDB failure only means no date.
        val dateCall = async(Dispatchers.IO) {
            val numeric = id.toIntOrNull()
            if (numeric == null) {
                null
            } else {
                runCatching { (if (kind == MediaKind.MOVIE) tmdb.movie(numeric) else tmdb.tv(numeric)).title.releaseDate }.getOrNull()
            }
        }
        val html = pageCall.await()
        val releaseDate = dateCall.await()
        val card = html?.let { Reelix.parseCards(it).firstOrNull { card -> card.id == id } }
        val base = (card ?: TitleCard(Library.REELIX, id, kind, name.ifBlank { "Reelix" }, poster, null, null, null, slug))
            .copy(releaseDate = releaseDate)
        val overview = html?.let { HtmlText.paragraph(it, "reel-title-overview") }
        val seasons = if (kind == MediaKind.MOVIE || html == null) {
            emptyList()
        } else {
            Reelix.seasons(html).map { SeasonBlock(it, "Season $it", emptyList()) }
        }
        val blocks = if (kind == MediaKind.MOVIE) listOf(movieBlock(base)) else seasons.ifEmpty { listOf(SeasonBlock(1, "Season 1", emptyList())) }
        TitleDetail(base.copy(overview = overview ?: base.overview), overview ?: base.overview.orEmpty(), blocks)
    }

    private fun anisugeDetail(slug: String, name: String, poster: String?): TitleDetail {
        val html = get(Anisuge.showUrl(slug), mapOf("Referer" to Anisuge.REFERER))
        val manga = Anisuge.mangaId(html) ?: error("This title has no episode list.")
        val title = Anisuge.showName(html) ?: name.ifBlank { slug }
        val list = get(
            Anisuge.episodeListUrl(manga, Anisuge.vrf(manga)),
            mapOf("Referer" to Anisuge.REFERER, "X-Requested-With" to "XMLHttpRequest", "Accept" to "application/json"),
        )
        val episodes = Anisuge.parseEpisodes(jsonResult(list))
        // The show page has its own poster. It beats the tapped card's picture, which may be a wide banner.
        val card = TitleCard(Library.ANISUGE, slug, MediaKind.ANIME, title, Anisuge.showPoster(html) ?: poster, null, null, null, slug)
        return TitleDetail(card, "", listOf(SeasonBlock(1, "Episodes", episodes)))
    }

    private fun nexusDetail(id: String, slug: String, name: String, poster: String?): TitleDetail {
        val episodes = ArrayList<EpisodeRef>()
        var page = 1
        var last = 1
        while (page <= last && page <= 8) {
            val json = get(Nexus.episodesUrl(id, page), Nexus.headers())
            episodes += Nexus.parseEpisodes(json)
            last = Nexus.lastPage(json)
            page++
        }
        // ponytail: the tapped card's picture is all Nexus detail gets; the episodes call has no show art.
        val card = TitleCard(Library.NEXUS, id, MediaKind.ANIME, name.ifBlank { "Anime" }, poster, null, null, null, slug)
        return TitleDetail(card, "", listOf(SeasonBlock(1, "Episodes", episodes)))
    }

    private fun tmdbDetail(library: Library, id: String, kind: MediaKind, tmdb: TmdbClient): TitleDetail {
        val numeric = id.toInt()
        val details = if (kind == MediaKind.MOVIE) tmdb.movie(numeric) else tmdb.tv(numeric)
        val card = details.title.card(library, if (kind == MediaKind.ANIME) MediaKind.ANIME else kind)
        val blocks = if (kind == MediaKind.MOVIE) {
            listOf(movieBlock(card))
        } else {
            details.seasons.map { SeasonBlock(it.number, it.name.ifBlank { "Season ${it.number}" }, emptyList()) }
        }
        return TitleDetail(card, card.overview.orEmpty(), blocks, details.certification)
    }

    private suspend fun kissHome(repo: Repository): List<ShelfRow> {
        val rows = ArrayList<ShelfRow>()
        fun add(title: String, shows: List<Show>, kind: MediaKind) {
            if (shows.isNotEmpty()) rows += ShelfRow(title, shows.take(20).map { it.card(kind) })
        }
        catching { repo.api.lastUpdate() }?.let { add("Latest", it, MediaKind.TV) }
        catching { repo.api.mostView() }?.let { add("Popular", it, MediaKind.TV) }
        catching { repo.api.topRating() }?.let { add("Top Rating", it, MediaKind.TV) }
        catching { repo.api.animate() }?.let { add("Anime", it, MediaKind.ANIME) }
        catching { repo.api.upcoming() }?.let { add("Upcoming", it, MediaKind.TV) }
        catching {
            repo.list(CatalogQuery(ContentType.SERIES, Country.SOUTH_KOREA, order = SortOrder.POPULAR), 1, 20).data
        }?.let { add("Korean Series", it, MediaKind.TV) }
        catching {
            repo.list(CatalogQuery(ContentType.MOVIE, order = SortOrder.POPULAR), 1, 20).data
        }?.let { add("Movies", it, MediaKind.MOVIE) }
        return rows
    }

    private suspend fun kissDetail(repo: Repository, id: String, hinted: MediaKind): TitleDetail {
        val drama = repo.api.drama(id.toInt())
        val movie = drama.type.orEmpty().contains("movie", true) || hinted == MediaKind.MOVIE
        val kind = when {
            movie -> MediaKind.MOVIE
            hinted == MediaKind.ANIME -> MediaKind.ANIME
            else -> MediaKind.TV
        }
        val card = TitleCard(
            source = Library.KISSKH,
            id = drama.id.toString(),
            kind = kind,
            name = drama.title.orEmpty(),
            poster = drama.thumbnail,
            backdrop = drama.thumbnail,
            year = drama.releaseDate?.take(4),
            overview = drama.description,
            meta = drama.country,
        )
        val episodes = drama.episodes.sortedBy { it.number }.map { ep ->
            val number = if (ep.number % 1f == 0f) ep.number.toInt() else ep.number.toInt().coerceAtLeast(1)
            EpisodeRef(
                id = ep.id.toString(),
                season = 1,
                number = number,
                name = "Episode ${trimNumber(ep.number)}",
                still = drama.thumbnail,
            )
        }
        val blocks = if (episodes.isEmpty() && movie) listOf(movieBlock(card)) else listOf(SeasonBlock(1, "Episodes", episodes))
        return TitleDetail(card, drama.description.orEmpty(), blocks)
    }

    private fun movieBlock(card: TitleCard) = SeasonBlock(
        0,
        "Movie",
        listOf(EpisodeRef(card.id, 0, 1, "Movie", card.artwork)),
    )

    private fun trimNumber(number: Float): String =
        if (number % 1f == 0f) number.toInt().toString() else number.toString()
}

private suspend fun <T> catching(block: suspend () -> T): T? = try {
    block()
} catch (_: Exception) {
    null
}

private fun MediaKind.toContent(): ContentType = when (this) {
    MediaKind.MOVIE -> ContentType.MOVIE
    MediaKind.ANIME -> ContentType.ANIME
    MediaKind.TV -> ContentType.SERIES
}

private fun Show.card(kind: MediaKind) = TitleCard(
    source = Library.KISSKH,
    id = id.toString(),
    kind = kind,
    name = title.orEmpty().ifBlank { "Untitled" },
    poster = thumbnail,
    backdrop = thumbnail,
    year = null,
    overview = null,
    meta = label,
)

private fun TmdbTitle.card(library: Library, forced: MediaKind? = null): TitleCard {
    val kind = forced ?: if (mediaType == "tv") MediaKind.TV else MediaKind.MOVIE
    return TitleCard(
        source = library,
        id = id.toString(),
        kind = kind,
        name = name,
        poster = poster,
        backdrop = backdrop,
        year = year,
        overview = overview.ifBlank { null },
        meta = voteText(),
        releaseDate = releaseDate,
    )
}
