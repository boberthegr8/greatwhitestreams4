package com.gwstreams.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gwstreams.app.data.repo.SettingsRepository
import com.gwstreams.tv.data.TvCredentialStore
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val appContext = context.applicationContext
            val shouldLaunch = runBlocking {
                val settings = SettingsRepository(appContext).load()
                val creds = TvCredentialStore(appContext).load()
                settings.cableBoxMode && creds != null
            }
            if (shouldLaunch) {
                val launchIntent = Intent(context, TvActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(launchIntent)
            }
        }
    }
}
