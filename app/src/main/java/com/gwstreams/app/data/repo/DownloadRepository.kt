package com.gwstreams.app.data.repo

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.downloadStore by preferencesDataStore("gw_downloads")

enum class DownloadState { RUNNING, COMPLETE, FAILED }

/** A tracked VOD/episode download saved to the public Movies folder. */
data class DownloadEntry(
    val id: Int,                 // stream/episode id (our key)
    val title: String,
    val image: String?,
    val kind: String,            // MOVIES / SERIES
    val downloadId: Long,        // system DownloadManager id
    val fileName: String,
    val localUri: String? = null,
    val state: String = DownloadState.RUNNING.name,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * Wraps the system DownloadManager. Files land in the public Movies/GWStreams
 * folder so they appear in the phone's Files/gallery, and survive uninstall.
 */
class DownloadRepository(private val context: Context) {
    private val gson = Gson()
    private val key = stringPreferencesKey("downloads")
    private val subDir = "GWStreams"

    suspend fun all(): List<DownloadEntry> {
        val raw = context.downloadStore.data.first()[key] ?: return emptyList()
        return runCatching {
            gson.fromJson<List<DownloadEntry>>(raw, object : TypeToken<List<DownloadEntry>>() {}.type)
        }.getOrDefault(emptyList()).sortedByDescending { it.addedAt }
    }

    suspend fun entryFor(id: Int, kind: String): DownloadEntry? =
        all().firstOrNull { it.id == id && it.kind == kind }

    private suspend fun save(list: List<DownloadEntry>) {
        context.downloadStore.edit { it[key] = gson.toJson(list) }
    }

    /** Sanitize a title into a safe file name and append the right extension. */
    private fun fileNameFor(title: String, ext: String?): String {
        val clean = title.replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().ifBlank { "video" }
        val e = (ext ?: "mp4").lowercase().removePrefix(".")
        return "$clean.$e"
    }

    /** Queue a download. Returns the new entry, or existing one if already present. */
    suspend fun enqueue(
        id: Int,
        kind: String,
        title: String,
        image: String?,
        url: String,
        ext: String?
    ): DownloadEntry {
        all().firstOrNull { it.id == id && it.kind == kind }?.let { return it }

        val fileName = fileNameFor(title, ext)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(title)
            setDescription("GWStreams download")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_MOVIES, "$subDir/$fileName"
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val entry = DownloadEntry(
            id = id, title = title, image = image, kind = kind,
            downloadId = downloadId, fileName = fileName
        )
        save(all() + entry)
        return entry
    }

    /** Poll the system for status and update our records. */
    suspend fun refreshStatuses(): List<DownloadEntry> {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val updated = all().map { entry ->
            if (entry.state == DownloadState.COMPLETE.name) return@map entry
            val q = DownloadManager.Query().setFilterById(entry.downloadId)
            dm.query(q).use { c ->
                if (c != null && c.moveToFirst()) {
                    val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val localIdx = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    when (c.getInt(statusIdx)) {
                        DownloadManager.STATUS_SUCCESSFUL -> entry.copy(
                            state = DownloadState.COMPLETE.name,
                            localUri = if (localIdx >= 0) c.getString(localIdx) else null
                        )
                        DownloadManager.STATUS_FAILED -> entry.copy(state = DownloadState.FAILED.name)
                        else -> entry
                    }
                } else entry
            }
        }
        save(updated)
        return updated.sortedByDescending { it.addedAt }
    }

    /** Progress 0..1 for an in-flight download (best-effort). */
    fun progress(downloadId: Long): Float {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(downloadId)).use { c ->
            if (c != null && c.moveToFirst()) {
                val soFar = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val total = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val a = if (soFar >= 0) c.getLong(soFar) else 0L
                val b = if (total >= 0) c.getLong(total) else -1L
                return if (b > 0) (a.toFloat() / b).coerceIn(0f, 1f) else 0f
            }
        }
        return 0f
    }

    suspend fun remove(entry: DownloadEntry) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        runCatching { dm.remove(entry.downloadId) }  // also deletes the file
        save(all().filterNot { it.id == entry.id && it.kind == entry.kind })
    }
}
