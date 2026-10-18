package com.kisskh.tv.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Show(
    val id: Int,
    val title: String?,
    val thumbnail: String?,
    val episodesCount: Int = 0,
    val label: String? = null,
)

@JsonClass(generateAdapter = true)
data class ListPage(
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalCount: Int = 0,
    val data: List<Show> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class Episode(
    val id: Int,
    val number: Float = 0f,
    val sub: Int = 0,
)

@JsonClass(generateAdapter = true)
data class Drama(
    val id: Int,
    val title: String?,
    val description: String? = null,
    val thumbnail: String? = null,
    val type: String? = null,
    val country: String? = null,
    val status: String? = null,
    val releaseDate: String? = null,
    val episodesCount: Int = 0,
    val episodes: List<Episode> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class EpisodeStream(
    @Json(name = "Video") val video: String? = null,
    @Json(name = "Type") val type: Int = 0,
    @Json(name = "ThirdParty") val thirdParty: String? = null,
)

@JsonClass(generateAdapter = true)
data class Sub(
    val src: String,
    val label: String? = null,
    val land: String? = null,
    val default: Boolean = false,
)
