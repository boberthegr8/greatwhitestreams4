package com.gwstreams.app.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.watchStore by preferencesDataStore("gw_watch")

/** A lightweight record of something the user opened, for the Continue row (#3). */
data class WatchItem(
    val id: Int,
    val title: String,
    val image: String?,
    val kind: String,           // LIVE / MOVIES / SERIES
    val containerExt: String?,
    val progress: Float = 0f,   // 0..1, best-effort
    val updatedAt: Long = System.currentTimeMillis()
)

class WatchRepository(private val context: Context) {
    private val gson = Gson()
    private val key = stringPreferencesKey("recent")
    private val maxItems = 12

    suspend fun recent(): List<WatchItem> {
        val raw = context.watchStore.data.first()[key] ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<WatchItem>>() {}.type
            gson.fromJson<List<WatchItem>>(raw, type)
        }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }
    }

    suspend fun record(item: WatchItem) {
        val current = recent().toMutableList()
        current.removeAll { it.id == item.id && it.kind == item.kind }
        current.add(0, item)
        val trimmed = current.take(maxItems)
        context.watchStore.edit { it[key] = gson.toJson(trimmed) }
    }

    suspend fun updateProgress(id: Int, kind: String, progress: Float) {
        val current = recent().toMutableList()
        val idx = current.indexOfFirst { it.id == id && it.kind == kind }
        if (idx >= 0) {
            current[idx] = current[idx].copy(progress = progress, updatedAt = System.currentTimeMillis())
            context.watchStore.edit { it[key] = gson.toJson(current) }
        }
    }
}
