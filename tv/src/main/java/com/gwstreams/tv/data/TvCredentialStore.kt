package com.gwstreams.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.tvCreds by preferencesDataStore("gws_tv_creds")

/** Persists the TV login so the app stays signed in across launches. */
class TvCredentialStore(private val context: Context) {
    private val kHost = stringPreferencesKey("host")
    private val kUser = stringPreferencesKey("user")
    private val kPass = stringPreferencesKey("pass")

    data class Creds(val host: String, val user: String, val pass: String)

    suspend fun save(host: String, user: String, pass: String) {
        context.tvCreds.edit {
            it[kHost] = host; it[kUser] = user; it[kPass] = pass
        }
    }

    suspend fun load(): Creds? {
        val p = context.tvCreds.data.first()
        val host = p[kHost] ?: return null
        val user = p[kUser] ?: return null
        val pass = p[kPass] ?: return null
        return Creds(host, user, pass)
    }

    suspend fun clear() {
        context.tvCreds.edit { it.clear() }
    }
}
