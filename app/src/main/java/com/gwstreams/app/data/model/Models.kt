package com.gwstreams.app.data.model

import com.google.gson.annotations.SerializedName

/** Response from player_api.php with no action — login + server info. */
data class AuthResponse(
    @SerializedName("user_info") val userInfo: UserInfo?,
    @SerializedName("server_info") val serverInfo: ServerInfo?
)

data class UserInfo(
    val username: String?,
    val password: String?,
    val status: String?,
    @SerializedName("exp_date") val expDate: String?,
    @SerializedName("is_trial") val isTrial: String?,
    @SerializedName("active_cons") val activeConnections: String?,
    @SerializedName("max_connections") val maxConnections: String?
)

data class ServerInfo(
    val url: String?,
    val port: String?,
    @SerializedName("https_port") val httpsPort: String?,
    @SerializedName("server_protocol") val protocol: String?
)

data class Category(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("parent_id") val parentId: Int?
)

data class LiveStream(
    @SerializedName("num") val num: Int?,
    @SerializedName("name") val name: String,
    @SerializedName("stream_id") val streamId: Int,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("epg_channel_id") val epgChannelId: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("tv_archive") val tvArchive: Int? = 0,
    @SerializedName("tv_archive_duration") val tvArchiveDuration: Int? = 0
)

data class VodStream(
    @SerializedName("name") val name: String,
    @SerializedName("stream_id") val streamId: Int,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    @SerializedName("category_id") val categoryId: String?
)

data class SeriesStream(
    @SerializedName("name") val name: String,
    @SerializedName("series_id") val seriesId: Int,
    @SerializedName("cover") val cover: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("category_id") val categoryId: String?
)

/** Short EPG entry from get_short_epg. */
data class EpgListing(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("start") val start: String?,
    @SerializedName("end") val end: String?,
    @SerializedName("start_timestamp") val startTimestamp: String?,
    @SerializedName("stop_timestamp") val stopTimestamp: String?
)

data class EpgResponse(
    @SerializedName("epg_listings") val listings: List<EpgListing>?
)

/** get_vod_info response. */
data class VodInfoResponse(
    @SerializedName("info") val info: VodInfo?,
    @SerializedName("movie_data") val movieData: MovieData?
)

data class VodInfo(
    @SerializedName("movie_image") val movieImage: String?,
    @SerializedName("backdrop_path") val backdrop: List<String>?,
    @SerializedName("plot") val plot: String?,
    @SerializedName("genre") val genre: String?,
    @SerializedName("releasedate") val releaseDate: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("director") val director: String?,
    @SerializedName("cast") val cast: String?
)

data class MovieData(
    @SerializedName("stream_id") val streamId: Int?,
    @SerializedName("container_extension") val containerExtension: String?
)

/** get_series_info response. */
data class SeriesInfoResponse(
    @SerializedName("info") val info: SeriesInfo?,
    @SerializedName("seasons") val seasons: List<SeasonMeta>?,
    @SerializedName("episodes") val episodes: Map<String, List<Episode>>?
)

data class SeriesInfo(
    @SerializedName("name") val name: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("backdrop_path") val backdrop: List<String>?,
    @SerializedName("plot") val plot: String?,
    @SerializedName("genre") val genre: String?,
    @SerializedName("releaseDate") val releaseDate: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("cast") val cast: String?
)

data class SeasonMeta(
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("name") val name: String?
)

data class Episode(
    @SerializedName("id") val id: String,
    @SerializedName("episode_num") val episodeNum: Int?,
    @SerializedName("title") val title: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    @SerializedName("season") val season: Int?
)
