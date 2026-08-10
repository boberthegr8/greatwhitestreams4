package com.gwstreams.app.data.repo

import com.gwstreams.app.data.model.UserInfo

object Session {
    var host: String = ""
    var username: String = ""
    var password: String = ""
    var userInfo: UserInfo? = null
    
    private val cleanHost get() = host.removeSuffix("/")

    fun liveUrl(streamId: Int): String = "$cleanHost/live/$username/$password/$streamId.ts"
    fun vodUrl(streamId: Int, ext: String?): String = "$cleanHost/movie/$username/$password/$streamId.${if (ext.isNullOrBlank()) "mp4" else ext}"
    fun seriesUrl(episodeId: Int, ext: String?): String = "$cleanHost/series/$username/$password/$episodeId.${if (ext.isNullOrBlank()) "mp4" else ext}"
    fun archiveUrl(streamId: Int, startUtc: String, durationMin: Int): String =
        "$cleanHost/streaming/timeshift.php?username=$username&password=$password&stream=$streamId&start=$startUtc&duration=$durationMin"
}
