package com.gwstreams.app.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.liveStore by preferencesDataStore("gw_live")

/**
 * Stores favorite channel IDs (#7) and a short list of recently watched
 * channel IDs (#8), persisted across sessions.
 */
class LivePrefsRepository(private val context: Context) {
    private val gson = Gson()
    private val kFav = stringPreferencesKey("favorites")
    private val kRecent = stringPreferencesKey("recent_channels")
    private val maxRecent = 10

    private suspend fun readIds(key: androidx.datastore.preferences.core.Preferences.Key<String>): List<Int> {
        val raw = context.liveStore.data.first()[key] ?: return emptyList()
        return runCatching {
            gson.fromJson<List<Int>>(raw, object : TypeToken<List<Int>>() {}.type)
        }.getOrDefault(emptyList())
    }

    suspend fun favorites(): Set<Int> = readIds(kFav).toSet()

    suspend fun toggleFavorite(streamId: Int): Set<Int> {
        val cur = favorites().toMutableSet()
        if (!cur.add(streamId)) cur.remove(streamId)
        context.liveStore.edit { it[kFav] = gson.toJson(cur.toList()) }
        return cur
    }

    suspend fun recentChannels(): List<Int> = readIds(kRecent)

    /** Most recent first; the previous channel is index 1 (for last-channel flip). */
    suspend fun pushRecent(streamId: Int): List<Int> {
        val cur = recentChannels().toMutableList()
        cur.remove(streamId)
        cur.add(0, streamId)
        val trimmed = cur.take(maxRecent)
        context.liveStore.edit { it[kRecent] = gson.toJson(trimmed) }
        return trimmed
    }
}
