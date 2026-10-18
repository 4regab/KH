package com.kisskh.tv.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface KissKhApi {
    @GET("api/DramaList/Show")
    suspend fun show(): List<Show>

    @GET("api/DramaList/LastUpdate")
    suspend fun lastUpdate(@Query("ispc") pc: Boolean = true): List<Show>

    @GET("api/DramaList/MostView")
    suspend fun mostView(@Query("ispc") pc: Boolean = true, @Query("c") c: Int = 1): List<Show>

    @GET("api/DramaList/TopRating")
    suspend fun topRating(@Query("ispc") pc: Boolean = true): List<Show>

    @GET("api/DramaList/Upcoming")
    suspend fun upcoming(@Query("ispc") pc: Boolean = true): List<Show>

    @GET("api/DramaList/Animate")
    suspend fun animate(@Query("ispc") pc: Boolean = true): List<Show>

    @GET("api/DramaList/List")
    suspend fun list(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 40,
        @Query("type") type: Int = 0,
        @Query("sub") sub: Int = 0,
        @Query("country") country: Int = 0,
        @Query("status") status: Int = 0,
        @Query("order") order: Int = 1,
    ): ListPage

    @GET("api/DramaList/Search")
    suspend fun search(@Query("q") q: String, @Query("type") type: Int = 0): List<Show>

    @GET("api/DramaList/Drama/{id}")
    suspend fun drama(@Path("id") id: Int, @Query("isq") isq: Boolean = false): Drama

    // kkey must be appended by the caller (generated from the site's common.js).
    @GET("api/DramaList/Episode/{id}.png")
    suspend fun episode(
        @Path("id") id: Int,
        @Query("kkey") kkey: String,
        @Query("err") err: Boolean = false,
        @Query("ts") ts: String = "",
        @Query("time") time: String = "",
    ): EpisodeStream

    @GET("api/Sub/{id}")
    suspend fun subs(@Path("id") id: Int, @Query("kkey") kkey: String): List<Sub>
}
