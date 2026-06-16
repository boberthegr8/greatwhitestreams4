package com.gwstreams.app.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gwstreams.app.data.repo.DownloadEntry
import com.gwstreams.app.data.repo.DownloadRepository
import com.gwstreams.app.data.repo.DownloadState
import com.gwstreams.app.data.repo.Session
import com.gwstreams.app.ui.home.ContentItem
import com.gwstreams.app.ui.home.Tab
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DownloadsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DownloadRepository(app)

    private val _entries = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val entries: StateFlow<List<DownloadEntry>> = _entries

    // live progress per system downloadId
    private val _progress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val progress: StateFlow<Map<Long, Float>> = _progress

    init { startPolling() }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                val list = repo.refreshStatuses()
                _entries.value = list
                val running = list.filter { it.state == DownloadState.RUNNING.name }
                if (running.isNotEmpty()) {
                    _progress.value = running.associate { it.downloadId to repo.progress(it.downloadId) }
                }
                delay(1500)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { _entries.value = repo.refreshStatuses() }
    }

    /** Queue a download for a movie or a specific episode. */
    fun download(
        id: Int,
        kind: Tab,
        title: String,
        image: String?,
        ext: String?
    ) {
        viewModelScope.launch {
            val url = when (kind) {
                Tab.MOVIES -> Session.vodUrl(id, ext)
                Tab.SERIES -> Session.seriesUrl(id, ext)
                Tab.LIVE -> return@launch  // live isn't downloadable
            }
            repo.enqueue(id, kind.name, title, image, url, ext)
            _entries.value = repo.refreshStatuses()
        }
    }

    fun downloadItem(item: ContentItem) =
        download(item.id, item.kind, item.title, item.image, item.containerExt)

    fun remove(entry: DownloadEntry) {
        viewModelScope.launch {
            repo.remove(entry)
            _entries.value = repo.refreshStatuses()
        }
    }

    /** Local file uri if this content is downloaded and complete, else null. */
    suspend fun localUriFor(id: Int, kind: Tab): String? {
        val e = repo.entryFor(id, kind.name) ?: return null
        return if (e.state == DownloadState.COMPLETE.name) e.localUri else null
    }

    suspend fun isDownloaded(id: Int, kind: Tab): Boolean =
        repo.entryFor(id, kind.name)?.state == DownloadState.COMPLETE.name
}
