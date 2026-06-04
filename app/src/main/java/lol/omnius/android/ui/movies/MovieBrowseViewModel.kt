package lol.omnius.android.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lol.omnius.android.api.ApiClient
import lol.omnius.android.data.model.Movie

enum class MovieTab(val label: String) {
    SEARCH("Search"),
    TRENDING("Trending"),
    TOP_RATED("Top Rated"),
    YEAR("Year"),
    CATEGORIES("Categories"),
}

data class MovieBrowseState(
    val isLoading: Boolean = true,
    val movies: List<Movie> = emptyList(),
    val currentTab: MovieTab = MovieTab.TRENDING,
    val selectedGenre: String? = null,
    val query: String = "",
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null,
)

val MOVIE_GENRES = listOf(
    "Action", "Adventure", "Animation", "Biography", "Comedy", "Crime",
    "Documentary", "Drama", "Family", "Fantasy", "History", "Horror",
    "Music", "Mystery", "Romance", "Sci-Fi", "Sport", "Thriller", "War", "Western",
)

class MovieBrowseViewModel : ViewModel() {
    private val _state = MutableStateFlow(MovieBrowseState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadMovies()
    }

    fun loadMovies(reset: Boolean = false) {
        viewModelScope.launch {
            val current = _state.value
            val page = if (reset) 1 else current.page

            _state.value = current.copy(
                isLoading = true,
                error = null,
                page = page,
                movies = if (reset) emptyList() else current.movies,
            )

            try {
                val api = ApiClient.getApi()
                val response = when (current.currentTab) {
                    MovieTab.SEARCH -> {
                        val q = current.query.trim()
                        if (q.isEmpty()) {
                            _state.value = _state.value.copy(isLoading = false, movies = emptyList(), hasMore = false)
                            return@launch
                        }
                        // Always hit YTS for explicit search
                        api.searchMoviesOnline(queryTerm = q, limit = 20)
                    }
                    MovieTab.TRENDING -> api.listMovies(
                        limit = 20, page = page,
                        sortBy = "download_count", orderBy = "desc",
                    )
                    MovieTab.TOP_RATED -> api.listMovies(
                        limit = 20, page = page,
                        sortBy = "rating", orderBy = "desc",
                    )
                    MovieTab.YEAR -> api.listMovies(
                        limit = 20, page = page,
                        sortBy = "year", orderBy = "desc",
                    )
                    MovieTab.CATEGORIES -> api.listMovies(
                        limit = 20, page = page,
                        genre = current.selectedGenre,
                        sortBy = "download_count", orderBy = "desc",
                    )
                }
                val newMovies = response.data?.movies ?: emptyList()

                _state.value = _state.value.copy(
                    isLoading = false,
                    movies = if (reset) newMovies else _state.value.movies + newMovies,
                    hasMore = newMovies.size >= 20 && current.currentTab != MovieTab.SEARCH,
                    page = page + 1,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message,
                )
            }
        }
    }

    fun selectTab(tab: MovieTab) {
        if (_state.value.currentTab == tab) return
        searchJob?.cancel()
        val resetGenre = tab == MovieTab.CATEGORIES && _state.value.selectedGenre == null
        _state.value = _state.value.copy(
            currentTab = tab,
            selectedGenre = if (resetGenre) MOVIE_GENRES.first() else _state.value.selectedGenre,
        )
        loadMovies(reset = true)
    }

    fun selectGenre(genre: String) {
        _state.value = _state.value.copy(selectedGenre = genre)
        loadMovies(reset = true)
    }

    fun updateQuery(q: String) {
        _state.value = _state.value.copy(query = q)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            loadMovies(reset = true)
        }
    }

    fun submitQuery() {
        searchJob?.cancel()
        loadMovies(reset = true)
    }

    fun loadMore() {
        if (!_state.value.isLoading && _state.value.hasMore) {
            loadMovies()
        }
    }
}
