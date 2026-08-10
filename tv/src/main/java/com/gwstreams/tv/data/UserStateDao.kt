package com.gwstreams.tv.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface UserStateDao {
    @Query("SELECT * FROM favorite_channels ORDER BY createdAt DESC")
    suspend fun getFavoriteChannels(): List<FavoriteChannelEntity>

    @Upsert
    suspend fun upsertFavoriteChannel(entity: FavoriteChannelEntity)

    @Query("DELETE FROM favorite_channels WHERE channelId = :channelId")
    suspend fun deleteFavoriteChannel(channelId: Int)

    @Query("SELECT * FROM recent_channels ORDER BY lastViewedAt DESC")
    suspend fun getRecentChannels(): List<RecentChannelEntity>

    @Upsert
    suspend fun upsertRecentChannel(entity: RecentChannelEntity)

    @Query("DELETE FROM recent_channels")
    suspend fun clearRecentChannels()

    @Query("SELECT * FROM playback_history ORDER BY watchedAt DESC")
    suspend fun getPlaybackHistory(): List<PlaybackHistoryEntity>

    @Upsert
    suspend fun upsertPlaybackHistory(entity: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history WHERE contentType = :contentType AND contentId = :contentId")
    suspend fun deletePlaybackHistory(contentType: String, contentId: String)

    @Query("SELECT * FROM resume_positions WHERE contentType = :contentType AND contentId = :contentId LIMIT 1")
    suspend fun getResumePosition(contentType: String, contentId: String): ResumePositionEntity?

    @Upsert
    suspend fun upsertResumePosition(entity: ResumePositionEntity)

    @Query("DELETE FROM resume_positions WHERE contentType = :contentType AND contentId = :contentId")
    suspend fun deleteResumePosition(contentType: String, contentId: String)

    @Query("SELECT * FROM group_overrides WHERE section = :section ORDER BY sortOrder ASC, groupId ASC")
    suspend fun getGroupOverrides(section: String): List<GroupOverrideEntity>

    @Upsert
    suspend fun upsertGroupOverride(entity: GroupOverrideEntity)

    @Query("DELETE FROM group_overrides WHERE section = :section AND groupId = :groupId")
    suspend fun deleteGroupOverride(section: String, groupId: String)

    @Query("SELECT * FROM channel_overrides WHERE section = :section ORDER BY sortOrder ASC, channelId ASC")
    suspend fun getChannelOverrides(section: String): List<ChannelOverrideEntity>

    @Upsert
    suspend fun upsertChannelOverride(entity: ChannelOverrideEntity)

    @Query("DELETE FROM channel_overrides WHERE section = :section AND channelId = :channelId")
    suspend fun deleteChannelOverride(section: String, channelId: Int)
}
