package com.gwstreams.tv.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

object Updater {
    /** Change this one constant to move the app to a different GitHub-hosted update feed. */
    const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/boberthegr8/greatwhitestreams4/main/auto/update.json"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_REDIRECTS = 5

    @Immutable
    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val releaseNotes: String,
        val mandatory: Boolean,
        val sha256: String?
    )

    suspend fun checkForUpdateResult(currentVersionCode: Int): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(UPDATE_JSON_URL, "application/json")

            try {
                connection.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val latestCode = when {
                    json.has("versionCode") -> json.optInt("versionCode", 0)
                    json.has("version_code") -> json.optInt("version_code", 0)
                    else -> 0
                }
                val apkUrl = json.optString("apkUrl", "")
                    .ifBlank { json.optString("apk_url", "") }
                    .ifBlank { json.optString("url", "") }
                if (latestCode > currentVersionCode && apkUrl.isNotBlank()) {
                    UpdateInfo(
                        versionCode = latestCode,
                        versionName = json.optString("versionName", "")
                            .ifBlank { json.optString("version_name", "") }
                            .ifBlank { json.optString("version", latestCode.toString()) },
                        apkUrl = apkUrl,
                        releaseNotes = json.optString("releaseNotes", "")
                            .ifBlank { json.optString("release_notes", "") }
                            .ifBlank { "Bug fixes and performance updates." },
                        mandatory = json.optBoolean("mandatory", false),
                        sha256 = json.optString("sha256", "").takeIf { it.isNotBlank() }
                    )
                } else {
                    null
                }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? =
        checkForUpdateResult(currentVersionCode).getOrNull()

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openUnknownAppSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    suspend fun downloadAndInstall(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val updateRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val updateDir = File(updateRoot, "updates").apply { mkdirs() }
            val apkFile = File(updateDir, "gws-update-${info.versionCode}.apk")
            if (apkFile.exists()) apkFile.delete()

            val connection = openConnection(info.apkUrl, "application/vnd.android.package-archive,application/octet-stream,*/*")

            try {
                val total = connection.contentLengthLong.takeIf { it > 0L }
                var downloaded = 0L
                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            total?.let { onProgress(((downloaded * 100) / it).toInt().coerceIn(0, 100)) }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            onProgress(100)

            info.sha256?.let { expected ->
                val actual = sha256(apkFile)
                check(actual.equals(expected, ignoreCase = true)) {
                    "Downloaded APK checksum mismatch"
                }
            }

            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        val authority = "${context.packageName}.provider"
        val uri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
        }

        val handlers = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        handlers.forEach { handler ->
            context.grantUriPermission(handler.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.lowercase(Locale.US)
    }

    private fun openConnection(url: String, accept: String): HttpURLConnection {
        var currentUrl = url
        repeat(MAX_REDIRECTS + 1) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("Accept", accept)
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "GWStreams-TV-Updater")
            }

            when (connection.responseCode) {
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                307,
                308 -> {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location.isNullOrBlank()) error("Redirect missing Location header")
                    currentUrl = URL(URL(currentUrl), location).toString()
                }

                in 200..299 -> return connection
                else -> {
                    val body = runCatching {
                        connection.errorStream?.bufferedReader()?.use { it.readText() }
                    }.getOrNull().orEmpty()
                    val code = connection.responseCode
                    connection.disconnect()
                    error("HTTP $code while fetching update${if (body.isNotBlank()) ": $body" else ""}")
                }
            }
        }
        error("Too many redirects while fetching update")
    }
}
