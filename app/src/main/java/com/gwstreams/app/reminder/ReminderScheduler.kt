package com.gwstreams.app.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gwstreams.app.R

/**
 * Schedules a local notification a few minutes before a programme starts (#6).
 * No network, no server — pure on-device AlarmManager.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "gw_reminders"
    private const val LEAD_MIN = 2

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "Programme reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Alerts before a show you flagged starts" }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    /** @param startSec programme start in epoch seconds. */
    fun schedule(context: Context, channelTitle: String, programmeTitle: String, startSec: Long) {
        ensureChannel(context)
        val fireAt = (startSec - LEAD_MIN * 60) * 1000L
        if (fireAt <= System.currentTimeMillis()) return  // already started

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("channel", channelTitle)
            putExtra("title", programmeTitle)
        }
        val id = (channelTitle + programmeTitle + startSec).hashCode()
        val pi = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, fireAt, pi)  // fallback if exact alarms denied
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.ensureChannel(context)
        val channel = intent.getStringExtra("channel") ?: "GWStreams"
        val title = intent.getStringExtra("title") ?: "Your programme is starting"

        val notif = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Starting soon on $channel")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { mgr.notify(intent.hashCode(), notif) }
    }
}
