package lol.omnius.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lol.omnius.android.api.ApiClient
import lol.omnius.android.data.model.HomeMovieHero
import lol.omnius.android.data.model.HomeMovieSlim
import lol.omnius.android.data.model.HomeSection

data class HomeUiState(
    val isLoading: Boolean = true,
    val heroMovies: List<HomeMovieHero> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
    // Fallback data if home endpoint has no sections
    val trendingMovies: List<HomeMovieSlim> = emptyList(),
    val latestMovies: List<HomeMovieSlim> = emptyList(),
    val topRatedMovies: List<HomeMovieSlim> = emptyList(),
    val error: String? = null,
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                val api = ApiClient.getApi()

                // Try home endpoint first
                try {
                    val homeResponse = api.getHomeData()
                    val data = homeResponse.data
                    val heroSlider = data?.heroSlider ?: emptyList()
                    val sections = data?.sections ?: emptyList()

                    if (heroSlider.isNotEmpty() || sections.isNotEmpty()) {
                        _uiState.value = HomeUiState(
                            isLoading = false,
                            heroMovies = heroSlider,
                            sections = sections,
                        )

                        // Also load fallback rows in background for richer content
                        loadFallbackRows(api)
                        return@launch
                    }
                } catch (_: Exception) {
                    // Home endpoint failed, fall back to manual loading
                }

                // Fallback: load manually
                loadFallbackRows(api)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load",
                )
            }
        }
    }

    private suspend fun loadFallbackRows(api: lol.omnius.android.api.OmniusApi) {
        try {
            val trending = api.listMovies(limit = 20, sortBy = "download_count")
            val latest = api.listMovies(limit = 20, sortBy = "date_added")
            val topRated = api.listMovies(limit = 20, sortBy = "rating", minimumRating = 7)

            val trendingMovies = (trending.data?.movies ?: emptyList()).map { it.toSlim() }
            val latestMovies = (latest.data?.movies ?: emptyList()).map { it.toSlim() }
            val topRatedMovies = (topRated.data?.movies ?: emptyList()).map { it.toSlim() }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                heroMovies = _uiState.value.heroMovies.ifEmpty {
                    trendingMovies.take(5).map { it.toHero() }
                },
                trendingMovies = trendingMovies,
                latestMovies = latestMovies,
                topRatedMovies = topRatedMovies,
            )
        } catch (e: Exception) {
            if (_uiState.value.isLoading) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load",
                )
            }
        }
    }

    fun retry() = loadHomeData()
}

// Extension to convert full Movie to slim card model
private fun lol.omnius.android.data.model.Movie.toSlim() = HomeMovieSlim(
    id = id,
    title = title,
    year = year,
    rating = rating,
    mediumCoverImage = mediumCoverImage,
)

// Extension to convert slim card to hero (limited fields, for fallback only)
private fun HomeMovieSlim.toHero() = HomeMovieHero(
    id = id,
    title = title,
    year = year,
    rating = rating,
    mediumCoverImage = mediumCoverImage,
)
