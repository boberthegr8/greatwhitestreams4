package com.gwstreams.app.data.repo

import com.gwstreams.app.data.model.UserInfo

/**
 * Holds the active credentials and builds the playable stream URLs.
 */
object Session {
    var host: String = ""
    var username: String = ""
    var password: String = ""
    var userInfo: UserInfo? = null

    fun liveUrl(streamId: Int): String =
        "$host/live/$username/$password/$streamId.ts"

    fun vodUrl(streamId: Int, ext: String?): String =
        "$host/movie/$username/$password/$streamId.${ext ?: "mp4"}"

    fun seriesUrl(episodeId: Int, ext: String?): String =
        "$host/series/$username/$password/$episodeId.${ext ?: "mp4"}"

    fun archiveUrl(streamId: Int, startUtc: String, durationMin: Int): String =
        "$host/streaming/timeshift.php?username=$username&password=$password" +
            "&stream=$streamId&start=$startUtc&duration=$durationMin"
}
