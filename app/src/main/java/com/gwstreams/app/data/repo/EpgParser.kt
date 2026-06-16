package com.gwstreams.app.data.repo

import android.util.Base64
import com.gwstreams.app.data.model.EpgListing

/** A parsed programme with real epoch-second bounds and decoded text. */
data class Programme(
    val title: String,
    val description: String,
    val start: Long,   // epoch seconds
    val stop: Long
) {
    fun progressAt(nowSec: Long): Float {
        if (stop <= start) return 0f
        return ((nowSec - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
    }
    fun isLiveAt(nowSec: Long) = nowSec in start until stop
}

/** Now + the upcoming programme for a channel. */
data class NowNext(
    val now: Programme?,
    val next: Programme?
)

object EpgParser {

    private fun decode(s: String?): String = runCatching {
        if (s.isNullOrBlank()) "" else String(Base64.decode(s, Base64.DEFAULT)).trim()
    }.getOrDefault(s ?: "")

    fun toProgrammes(listings: List<EpgListing>): List<Programme> =
        listings.mapNotNull { l ->
            val start = l.startTimestamp?.toLongOrNull()
            val stop = l.stopTimestamp?.toLongOrNull()
            if (start == null || stop == null) null
            else Programme(decode(l.title), decode(l.description), start, stop)
        }.sortedBy { it.start }

    fun nowNext(listings: List<EpgListing>, nowSec: Long = System.currentTimeMillis() / 1000): NowNext {
        val progs = toProgrammes(listings)
        val now = progs.firstOrNull { it.isLiveAt(nowSec) }
        val next = progs.firstOrNull { it.start >= (now?.stop ?: nowSec) }
        return NowNext(now, next)
    }
}
