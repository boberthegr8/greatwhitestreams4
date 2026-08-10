package com.gwstreams.tv.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorite_channels",
    indices = [Index(value = ["createdAt"])]
)
data class FavoriteChannelEntity(
    @PrimaryKey val channelId: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recent_channels",
    indices = [Index(value = ["lastViewedAt"])]
)
data class RecentChannelEntity(
    @androidx.room.PrimaryKey val channelId: Int,
    val lastViewedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playback_history",
    primaryKeys = ["contentType", "contentId"],
    indices = [Index(value = ["watchedAt"])]
)
data class PlaybackHistoryEntity(
    val contentType: String,
    val contentId: String,
    val streamId: Int? = null,
    val title: String? = null,
    val artworkUrl: String? = null,
    val watchedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "resume_positions",
    primaryKeys = ["contentType", "contentId"],
    indices = [Index(value = ["updatedAt"])]
)
data class ResumePositionEntity(
    val contentType: String,
    val contentId: String,
    val positionMs: Long,
    val durationMs: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "group_overrides",
    primaryKeys = ["section", "groupId"],
    indices = [Index(value = ["section", "sortOrder"])]
)
data class GroupOverrideEntity(
    val section: String,
    val groupId: String,
    val customName: String? = null,
    val sortOrder: Int = 0,
    val isHidden: Boolean = false
)

@Entity(
    tableName = "channel_overrides",
    primaryKeys = ["section", "channelId"],
    indices = [Index(value = ["section", "groupId", "sortOrder"])]
)
data class ChannelOverrideEntity(
    val section: String,
    val channelId: Int,
    val groupId: String? = null,
    val customName: String? = null,
    val sortOrder: Int = 0,
    val isHidden: Boolean = false
)
