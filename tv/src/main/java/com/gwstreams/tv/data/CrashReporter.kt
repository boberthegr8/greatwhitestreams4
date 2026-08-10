package com.gwstreams.tv.data

import android.content.Context
import com.gwstreams.app.data.repo.Session
import com.gwstreams.tv.BuildConfig
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    data class CrashSummary(
        val timestamp: String,
        val threadName: String,
        val throwableClass: String,
        val message: String?,
        val appVersion: String
    ) {
        val displayMessage: String
            get() = buildString {
                append(throwableClass)
                if (!message.isNullOrBlank()) {
                    append(": ")
                    append(message)
                }
            }
    }

    private const val CRASH_DIR = "crash"
    private const val CRASH_FILE = "last_crash_report.json"
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)

    fun recordUncaughtException(context: Context, thread: Thread, throwable: Throwable) {
        runCatching {
            val crashFile = context.filesDir.resolve(CRASH_DIR).apply { mkdirs() }.resolve(CRASH_FILE)
            val rawStackTrace = StringWriter().also { writer ->
                throwable.printStackTrace(PrintWriter(writer))
            }.toString()
            val payload = JSONObject().apply {
                put("timestamp", timestampFormat.format(Date()))
                put("threadName", redact(thread.name))
                put("throwableClass", throwable.javaClass.name)
                put("message", redact(throwable.message))
                put("stackTrace", redact(rawStackTrace))
                put("appVersion", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            }
            crashFile.writeText(payload.toString(2))
        }
    }

    fun readLastCrashSummary(context: Context): CrashSummary? = runCatching {
        readCrashJson(context)?.let { json ->
            CrashSummary(
                timestamp = json.optString("timestamp"),
                threadName = json.optString("threadName"),
                throwableClass = json.optString("throwableClass"),
                message = json.optString("message").takeUnless { it.isBlank() || it == "null" },
                appVersion = json.optString("appVersion")
            )
        }
    }.getOrNull()

    fun readLastCrashDetails(context: Context): String? = runCatching {
        readCrashJson(context)?.optString("stackTrace")?.takeUnless { it.isBlank() || it == "null" }
    }.getOrNull()

    fun clearLastCrash(context: Context) {
        runCatching {
            context.filesDir.resolve(CRASH_DIR).resolve(CRASH_FILE).delete()
        }
    }

    private fun readCrashJson(context: Context): JSONObject? {
        val crashFile = context.filesDir.resolve(CRASH_DIR).resolve(CRASH_FILE)
        if (!crashFile.exists()) return null
        return JSONObject(crashFile.readText())
    }

    private fun redact(value: String?): String? {
        if (value == null) return null
        if (value.isBlank()) return value

        var redacted: String = value
        val sessionHost = Session.host.takeIf { it.isNotBlank() }
        val sessionUsername = Session.username.takeIf { it.isNotBlank() }
        val sessionPassword = Session.password.takeIf { it.isNotBlank() }

        sessionHost?.let { redacted = redacted.replace(it, "<redacted-host>") }
        sessionUsername?.let { redacted = redacted.replace(it, "<redacted-username>") }
        sessionPassword?.let { redacted = redacted.replace(it, "<redacted-password>") }

        redacted = redacted
            .replace(Regex("(?i)(https?://)([^\\s/@:]+):([^\\s/@]+)@"), "$1<redacted>@")
            .replace(Regex("(?i)/(live|movie|series)/[^/\\s]+/[^/\\s]+/"), "/$1/<redacted-user>/<redacted-pass>/")
            .replace(Regex("(?i)([?&](?:username|user|password|pass|pwd|token|auth|authorization)=)[^&\\s]+"), "$1<redacted>")
            .replace(Regex("(?i)((?:password|pass|pwd|token|auth|authorization)\\s*[:=]\\s*)([^,\\s\\n]+)"), "$1<redacted>")
            .replace(Regex("(?i)((?:username|user|host)\\s*[:=]\\s*)([^,\\s\\n]+)"), "$1<redacted>")

        return redacted
    }
}
