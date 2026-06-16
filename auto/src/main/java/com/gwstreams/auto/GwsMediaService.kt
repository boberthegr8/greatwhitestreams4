package com.gwstreams.auto

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.gwstreams.app.data.model.Category
import com.gwstreams.app.data.model.LiveStream
import com.gwstreams.app.data.repo.Session
import com.gwstreams.app.data.repo.XtreamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.runBlocking

/**
 * Serves a browsable media tree to Android Auto:
 *   root -> [music categories] -> [channels in category]
 * Selecting a channel plays its audio via ExoPlayer in a background media session.
 *
 * Note: Android Auto draws the UI itself from this tree; we only supply data + playback.
 */
@UnstableApi
class GwsMediaService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private val repo = XtreamRepository()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Cache of channels we've surfaced, so playback can resolve a stream URL by id.
    private val channelCache = mutableMapOf<String, LiveStream>()

    companion object {
        private const val ROOT_ID = "gws_root"
        private const val CAT_PREFIX = "cat:"
        private const val CHAN_PREFIX = "chan:"
    }

    override fun onCreate() {
        super.onCreate()

        val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("GWStreams")
        val mediaSourceFactory =
            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(httpFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        session = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .build()

        runCatching { runBlocking { CredentialStore(this@GwsMediaService).restoreIntoSession() } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onDestroy() {
        session.release()
        player.release()
        super.onDestroy()
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Great White Streams")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            try {
                if (Session.host.isBlank()) {
                    return@future LibraryResult.ofItemList(ImmutableList.of(), params)
                }
                when {
                    parentId == ROOT_ID -> {
                        val cats = repo.liveCategories()
                        LibraryResult.ofItemList(
                            ImmutableList.copyOf(cats.map { it.toBrowsableItem() }), params
                        )
                    }
                    parentId.startsWith(CAT_PREFIX) -> {
                        val catId = parentId.removePrefix(CAT_PREFIX)
                        val streams = repo.liveStreams(catId)
                        streams.forEach { channelCache[CHAN_PREFIX + it.streamId] = it }
                        LibraryResult.ofItemList(
                            ImmutableList.copyOf(streams.map { it.toPlayableItem() }), params
                        )
                    }
                    else -> LibraryResult.ofItemList(ImmutableList.of(), params)
                }
            } catch (e: Exception) {
                LibraryResult.ofItemList(ImmutableList.of(), params)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val cached = channelCache[mediaId]
            return if (cached != null) {
                Futures.immediateFuture(LibraryResult.ofItem(cached.toPlayableItem(), null))
            } else {
                Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
        }

        // When Auto asks to play a media id, resolve it to a real stream URL.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val stream = channelCache[item.mediaId]
                if (stream != null) {
                    val url = Session.liveUrl(stream.streamId)
                    item.buildUpon().setUri(url).build()
                } else item
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    private fun Category.toBrowsableItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(CAT_PREFIX + categoryId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(categoryName)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

    private fun LiveStream.toPlayableItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(CHAN_PREFIX + streamId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist("Live")
                    .setArtworkUri(streamIcon?.let { android.net.Uri.parse(it) })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
}
