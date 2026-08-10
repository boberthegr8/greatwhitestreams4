package com.gwstreams.tv.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.gwstreams.app.data.repo.LivePrefsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.userStateMigrationStore by preferencesDataStore("gw_user_state_migrations")

class UserStateRepository(
    private val context: Context,
    private val dao: UserStateDao = UserStateDatabase.getInstance(context).userStateDao(),
    private val legacyLivePrefs: LivePrefsRepository = LivePrefsRepository(context)
) {
    private val migrationMutex = Mutex()

    suspend fun favorites(): Set<Int> = withContext(Dispatchers.IO) {
        ensureLegacyLivePrefsMigrated()
        dao.getFavoriteChannels().mapTo(linkedSetOf()) { it.channelId }
    }

    suspend fun toggleFavorite(channelId: Int): Set<Int> = withContext(Dispatchers.IO) {
        ensureLegacyLivePrefsMigrated()
        val current = dao.getFavoriteChannels().mapTo(linkedSetOf()) { it.channelId }
        if (!current.add(channelId)) {
            dao.deleteFavoriteChannel(channelId)
            current.remove(channelId)
        } else {
            dao.upsertFavoriteChannel(FavoriteChannelEntity(channelId = channelId))
        }
        current
    }

    suspend fun recentChannels(): List<Int> = withContext(Dispatchers.IO) {
        ensureLegacyLivePrefsMigrated()
        dao.getRecentChannels()
            .map { it.channelId }
            .filter { it > 0 }
            .take(MAX_RECENT_CHANNELS)
    }

    suspend fun lastPlayedChannelId(): Int? = recentChannels().firstOrNull()

    suspend fun pushRecent(channelId: Int): List<Int> = withContext(Dispatchers.IO) {
        ensureLegacyLivePrefsMigrated()
        dao.upsertRecentChannel(
            RecentChannelEntity(
                channelId = channelId,
                lastViewedAt = System.currentTimeMillis()
            )
        )
        trimRecentChannels()
        dao.getRecentChannels().map { it.channelId }.take(MAX_RECENT_CHANNELS)
    }

    suspend fun clearRecentChannels() = withContext(Dispatchers.IO) {
        ensureLegacyLivePrefsMigrated()
        dao.clearRecentChannels()
    }

    suspend fun recordLivePlaybackStart(channelId: Int, title: String?, artworkUrl: String?) = withContext(Dispatchers.IO) {
        ensureLegacyLivePrefsMigrated()
        dao.upsertPlaybackHistory(
            PlaybackHistoryEntity(
                contentType = CONTENT_TYPE_LIVE,
                contentId = channelId.toString(),
                streamId = channelId,
                title = title,
                artworkUrl = artworkUrl,
                watchedAt = System.currentTimeMillis()
            )
        )
        trimPlaybackHistory()
    }

    suspend fun clearLivePlaybackHistory() = withContext(Dispatchers.IO) {
        ensureLegacyLivePrefsMigrated()
        dao.clearPlaybackHistory(CONTENT_TYPE_LIVE)
    }

    private suspend fun trimRecentChannels() {
        val overflow = dao.getRecentChannels().drop(MAX_RECENT_CHANNELS).map { it.channelId }
        if (overflow.isNotEmpty()) {
            dao.deleteRecentChannels(overflow)
        }
    }

    private suspend fun trimPlaybackHistory() {
        dao.getPlaybackHistory()
            .asSequence()
            .filter { it.contentType == CONTENT_TYPE_LIVE }
            .drop(MAX_PLAYBACK_HISTORY)
            .forEach { dao.deletePlaybackHistory(it.contentType, it.contentId) }
    }

    private suspend fun ensureLegacyLivePrefsMigrated() {
        migrationMutex.withLock {
            val prefs = context.userStateMigrationStore.data.first()
            if (prefs[MIGRATED_LIVE_PREFS_KEY] == true) return

            val legacyFavorites = legacyLivePrefs.favorites().toList().normalizeIds()
            val legacyRecents = legacyLivePrefs.recentChannels().normalizeIds()
            val roomFavorites = dao.getFavoriteChannels().map { it.channelId }.normalizeIds()
            val roomRecents = dao.getRecentChannels().map { it.channelId }.normalizeIds()

            val missingFavorites = legacyFavorites.filterNot(roomFavorites::contains)
            if (missingFavorites.isNotEmpty()) {
                val now = System.currentTimeMillis()
                dao.upsertFavoriteChannels(
                    missingFavorites.mapIndexed { index, channelId ->
                        FavoriteChannelEntity(channelId = channelId, createdAt = now - index)
                    }
                )
            }

            val mergedRecents = (roomRecents + legacyRecents).normalizeIds().take(MAX_RECENT_CHANNELS)
            if (mergedRecents.isNotEmpty()) {
                val now = System.currentTimeMillis()
                dao.upsertRecentChannels(
                    mergedRecents.mapIndexed { index, channelId ->
                        RecentChannelEntity(channelId = channelId, lastViewedAt = now - index)
                    }
                )
                trimRecentChannels()
            }

            context.userStateMigrationStore.edit { it[MIGRATED_LIVE_PREFS_KEY] = true }
        }
    }

    private fun List<Int>.normalizeIds(): List<Int> =
        asSequence().filter { it > 0 }.distinct().toList()

    companion object {
        private val MIGRATED_LIVE_PREFS_KEY = booleanPreferencesKey("live_prefs_to_room_v1")
        private const val CONTENT_TYPE_LIVE = "live"
        private const val MAX_RECENT_CHANNELS = 10
        private const val MAX_PLAYBACK_HISTORY = 50
    }
}
