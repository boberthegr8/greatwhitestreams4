package com.gwstreams.tv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteChannelEntity::class,
        RecentChannelEntity::class,
        PlaybackHistoryEntity::class,
        ResumePositionEntity::class,
        GroupOverrideEntity::class,
        ChannelOverrideEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class UserStateDatabase : RoomDatabase() {
    abstract fun userStateDao(): UserStateDao

    companion object {
        @Volatile
        private var INSTANCE: UserStateDatabase? = null

        fun getInstance(context: Context): UserStateDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UserStateDatabase::class.java,
                    "user_state.db"
                ).build().also { INSTANCE = it }
            }
    }
}
