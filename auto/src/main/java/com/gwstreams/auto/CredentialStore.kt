package com.gwstreams.auto

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gwstreams.app.data.repo.Session
import com.gwstreams.app.data.repo.XtreamRepository
import kotlinx.coroutines.flow.first

private val Context.autoCreds by preferencesDataStore("gws_auto_creds")

/**
 * Persists the login so the Android Auto service can authenticate on its own,
 * without the phone app being open. Login itself happens once on the phone.
 */
class CredentialStore(private val context: Context) {
    private val kHost = stringPreferencesKey("host")
    private val kUser = stringPreferencesKey("user")
    private val kPass = stringPreferencesKey("pass")

    suspend fun save(host: String, user: String, pass: String) {
        context.autoCreds.edit {
            it[kHost] = host; it[kUser] = user; it[kPass] = pass
        }
    }

    /** Loads saved creds and logs the shared Session in, so the service can browse. */
    suspend fun restoreIntoSession(): Boolean {
        val p = context.autoCreds.data.first()
        val host = p[kHost] ?: return false
        val user = p[kUser] ?: return false
        val pass = p[kPass] ?: return false
        val repo = XtreamRepository()
        val result = repo.login(host, user, pass)
        return result.isSuccess
    }

    suspend fun hasCreds(): Boolean {
        val p = context.autoCreds.data.first()
        return p[kHost] != null
    }
}
