package lol.omnius.android.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lol.omnius.android.api.ApiClient
import lol.omnius.android.data.model.Movie
import lol.omnius.android.data.model.MovieDetails
import lol.omnius.android.torrent.TorrentStreamManager
import lol.omnius.android.torrent.TorrentStreamState

data class MovieDetailState(
    val isLoading: Boolean = true,
    val movie: MovieDetails? = null,
    val error: String? = null,
    // Related content
    val franchiseMovies: List<Movie> = emptyList(),
    val suggestions: List<Movie> = emptyList(),
    // Streaming (from TorrentStreamManager)
    val selectedHash: String? = null,
    val torrentState: TorrentStreamState = TorrentStreamState(),
    // Live seed/peer counts from tracker scrape
    val liveStats: Map<String, TorrentStreamManager.ScrapeStats> = emptyMap(),
    val liveStatsLoading: Boolean = false,
)

class MovieDetailViewModel : ViewModel() {
    private val _state = MutableStateFlow(MovieDetailState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            TorrentStreamManager.state.collect { torrentState ->
                _state.value = _state.value.copy(torrentState = torrentState)
            }
        }
    }

    fun loadMovie(movieId: Int) {
        viewModelScope.launch {
            try {
                _state.value = MovieDetailState(isLoading = true)
                val api = ApiClient.getApi()
                val response = api.getMovieDetails(movieId)
                _state.value = MovieDetailState(
                    isLoading = false,
                    movie = response.data?.movie,
                )

                // Auto-refresh torrents in background
                launch {
                    try {
                        api.refreshMovie(mapOf("movie_id" to movieId))
                        // Reload to get updated torrents
                        val refreshed = api.getMovieDetails(movieId)
                        val updatedMovie = refreshed.data?.movie
                        if (updatedMovie != null) {
                            _state.value = _state.value.copy(movie = updatedMovie)
                        }
                    } catch (_: Exception) {}
                }

                // Load related content in background
                launch {
                    try {
                        val franchise = api.getFranchiseMovies(movieId)
                        _state.value = _state.value.copy(
                            franchiseMovies = franchise.data?.movies ?: emptyList(),
                        )
                    } catch (_: Exception) {}
                }
                launch {
                    try {
                        val suggestions = api.getMovieSuggestions(movieId)
                        _state.value = _state.value.copy(
                            suggestions = suggestions.data?.movies ?: emptyList(),
                        )
                    } catch (_: Exception) {}
                }
                // Scrape live seed/peer counts from trackers
                launch {
                    val hashes = response.data?.movie?.torrents?.map { it.hash } ?: emptyList()
                    if (hashes.isNotEmpty()) {
                        _state.value = _state.value.copy(liveStatsLoading = true)
                        try {
                            val stats = TorrentStreamManager.scrapePeers(hashes)
                            _state.value = _state.value.copy(
                                liveStats = stats,
                                liveStatsLoading = false,
                            )
                        } catch (_: Exception) {
                            _state.value = _state.value.copy(liveStatsLoading = false)
                        }
                    }
                }
            } catch (e: Exception) {
                _state.value = MovieDetailState(
                    isLoading = false,
                    error = e.message,
                )
            }
        }
    }

    fun startStream(torrentHash: String, title: String) {
        _state.value = _state.value.copy(selectedHash = torrentHash)
        TorrentStreamManager.startStream(torrentHash, title)
    }

    override fun onCleared() {
        super.onCleared()
    }
}
