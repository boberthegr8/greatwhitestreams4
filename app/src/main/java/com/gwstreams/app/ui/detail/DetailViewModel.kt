package com.gwstreams.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gwstreams.app.data.model.Episode
import com.gwstreams.app.data.repo.XtreamRepository
import com.gwstreams.app.ui.home.ContentItem
import com.gwstreams.app.ui.home.Tab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val backdrop: String? = null,
    val poster: String? = null,
    val plot: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val rating: String? = null,
    val duration: String? = null,
    val cast: String? = null,
    // series only
    val seasons: List<Int> = emptyList(),
    val episodesBySeason: Map<Int, List<Episode>> = emptyMap(),
    val error: String? = null
)

class DetailViewModel : ViewModel() {
    private val repo = XtreamRepository()
    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state

    fun load(item: ContentItem) {
        _state.value = DetailUiState(loading = true, poster = item.image)
        viewModelScope.launch {
            try {
                when (item.kind) {
                    Tab.MOVIES -> {
                        val r = repo.vodInfo(item.id)
                        val info = r.info
                        _state.value = DetailUiState(
                            loading = false,
                            backdrop = info?.backdrop?.firstOrNull(),
                            poster = info?.movieImage ?: item.image,
                            plot = info?.plot,
                            genre = info?.genre,
                            year = info?.releaseDate,
                            rating = info?.rating ?: item.rating,
                            duration = info?.duration,
                            cast = info?.cast
                        )
                    }
                    Tab.SERIES -> {
                        val r = repo.seriesInfo(item.id)
                        val info = r.info
                        val eps = r.episodes ?: emptyMap()
                        val bySeason = eps.mapKeys { it.key.toIntOrNull() ?: 0 }
                        _state.value = DetailUiState(
                            loading = false,
                            backdrop = info?.backdrop?.firstOrNull(),
                            poster = info?.cover ?: item.image,
                            plot = info?.plot,
                            genre = info?.genre,
                            year = info?.releaseDate,
                            rating = info?.rating ?: item.rating,
                            cast = info?.cast,
                            seasons = bySeason.keys.sorted(),
                            episodesBySeason = bySeason
                        )
                    }
                    else -> _state.value = DetailUiState(loading = false)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Couldn't load details."
                )
            }
        }
    }
}
