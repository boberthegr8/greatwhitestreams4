package com.gwstreams.app.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.settingsStore by preferencesDataStore("gws_settings")

/** User-configurable app settings, shared by phone and TV. */
data class AppSettings(
    val autoFetchEpg: Boolean = true,
    val epgRefreshMinutes: Int = 10,
    val bufferSeconds: Int = 30,          // target buffer
    val hiddenCategories: Set<String> = emptySet(),   // category ids hidden from view
    val categoryOrder: List<String> = emptyList()     // explicit ordering of category ids
)

class SettingsRepository(private val context: Context) {
    private val gson = Gson()

    private val kAutoEpg = booleanPreferencesKey("auto_epg")
    private val kEpgMin = intPreferencesKey("epg_min")
    private val kBuffer = intPreferencesKey("buffer_sec")
    private val kHidden = stringPreferencesKey("hidden_cats")
    private val kOrder = stringPreferencesKey("cat_order")

    suspend fun load(): AppSettings {
        val p = context.settingsStore.data.first()
        val hidden = p[kHidden]?.let {
            runCatching { gson.fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type) }
                .getOrDefault(emptyList())
        }?.toSet() ?: emptySet()
        val order = p[kOrder]?.let {
            runCatching { gson.fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type) }
                .getOrDefault(emptyList())
        } ?: emptyList()
        return AppSettings(
            autoFetchEpg = p[kAutoEpg] ?: true,
            epgRefreshMinutes = p[kEpgMin] ?: 10,
            bufferSeconds = p[kBuffer] ?: 30,
            hiddenCategories = hidden,
            categoryOrder = order
        )
    }

    suspend fun setAutoFetchEpg(v: Boolean) =
        context.settingsStore.edit { it[kAutoEpg] = v }

    suspend fun setEpgRefreshMinutes(v: Int) =
        context.settingsStore.edit { it[kEpgMin] = v }

    suspend fun setBufferSeconds(v: Int) =
        context.settingsStore.edit { it[kBuffer] = v }

    suspend fun setHiddenCategories(ids: Set<String>) =
        context.settingsStore.edit { it[kHidden] = gson.toJson(ids.toList()) }

    suspend fun setCategoryOrder(ids: List<String>) =
        context.settingsStore.edit { it[kOrder] = gson.toJson(ids) }
}
