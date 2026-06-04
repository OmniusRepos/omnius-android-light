package lol.omnius.android.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lol.omnius.android.api.ApiClient
import lol.omnius.android.data.FavSeries
import lol.omnius.android.data.FavoritesManager
import lol.omnius.android.data.model.Series
import lol.omnius.android.ui.components.ContentCard
import lol.omnius.android.ui.components.FavoriteDialog
import lol.omnius.android.ui.movies.FilterChip
import lol.omnius.android.ui.theme.OmniusRed
import lol.omnius.android.ui.theme.OmniusSurface

enum class SeriesTab(val label: String) {
    SEARCH("Search"),
    TOP_RATED("Top Rated"),
    LATEST("Latest"),
    ONGOING("Ongoing"),
    CATEGORIES("Categories"),
}

val SERIES_GENRES = listOf(
    "Action", "Adventure", "Animation", "Biography", "Comedy", "Crime",
    "Documentary", "Drama", "Fantasy", "History", "Horror", "Mystery",
    "Romance", "Sci-Fi", "Thriller", "War",
)

data class SeriesBrowseState(
    val isLoading: Boolean = true,
    val series: List<Series> = emptyList(),
    val currentTab: SeriesTab = SeriesTab.TOP_RATED,
    val selectedGenre: String? = null,
    val query: String = "",
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null,
)

class SeriesBrowseViewModel : ViewModel() {
    private val _state = MutableStateFlow(SeriesBrowseState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadSeries()
    }

    fun loadSeries(reset: Boolean = false) {
        viewModelScope.launch {
            val current = _state.value
            val page = if (reset) 1 else current.page

            _state.value = current.copy(
                isLoading = true,
                error = null,
                page = page,
                series = if (reset) emptyList() else current.series,
            )

            try {
                val api = ApiClient.getApi()
                val response = when (current.currentTab) {
                    SeriesTab.SEARCH -> {
                        val q = current.query.trim()
                        if (q.isEmpty()) {
                            _state.value = _state.value.copy(isLoading = false, series = emptyList(), hasMore = false)
                            return@launch
                        }
                        api.searchSeriesOnline(queryTerm = q, limit = 20)
                    }
                    SeriesTab.TOP_RATED -> api.listSeries(
                        limit = 20, page = page,
                        sortBy = "rating", orderBy = "desc",
                    )
                    SeriesTab.LATEST -> api.listSeries(
                        limit = 20, page = page,
                        sortBy = "date_added", orderBy = "desc",
                    )
                    SeriesTab.ONGOING -> api.listSeries(
                        limit = 20, page = page,
                        sortBy = "rating", orderBy = "desc",
                        status = "Continuing",
                    )
                    SeriesTab.CATEGORIES -> api.listSeries(
                        limit = 20, page = page,
                        genre = current.selectedGenre,
                        sortBy = "rating", orderBy = "desc",
                    )
                }
                val newSeries = response.data?.series ?: emptyList()

                // distinctBy id: paginated pages can overlap (tied ratings sort
                // non-deterministically across pages), and the LazyGrid uses id
                // as its key — duplicates crash it with "Key X was already used".
                _state.value = _state.value.copy(
                    isLoading = false,
                    series = if (reset) newSeries else (_state.value.series + newSeries).distinctBy { it.id },
                    hasMore = newSeries.size >= 20 && current.currentTab != SeriesTab.SEARCH,
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

    fun selectTab(tab: SeriesTab) {
        if (_state.value.currentTab == tab) return
        searchJob?.cancel()
        val resetGenre = tab == SeriesTab.CATEGORIES && _state.value.selectedGenre == null
        _state.value = _state.value.copy(
            currentTab = tab,
            selectedGenre = if (resetGenre) SERIES_GENRES.first() else _state.value.selectedGenre,
        )
        loadSeries(reset = true)
    }

    fun selectGenre(genre: String) {
        _state.value = _state.value.copy(selectedGenre = genre)
        loadSeries(reset = true)
    }

    fun updateQuery(q: String) {
        _state.value = _state.value.copy(query = q)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            loadSeries(reset = true)
        }
    }

    fun submitQuery() {
        searchJob?.cancel()
        loadSeries(reset = true)
    }

    fun loadMore() {
        if (!_state.value.isLoading && _state.value.hasMore) {
            loadSeries()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesBrowseScreen(
    onSeriesClick: (Int) -> Unit,
    viewModel: SeriesBrowseViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val favSeries by FavoritesManager.series.collectAsState()
    var favDialogSeries by remember { mutableStateOf<FavSeries?>(null) }

    favDialogSeries?.let { s ->
        FavoriteDialog(
            title = s.title,
            isFavorite = FavoritesManager.isSeriesFav(s.id),
            onToggle = { FavoritesManager.toggleSeries(s) },
            onDismiss = { favDialogSeries = null },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "TV Series",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )

        // Tab bar
        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            items(SeriesTab.entries.toList()) { tab ->
                FilterChip(
                    label = tab.label,
                    selected = state.currentTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    color = Color(0xFF6366F1),
                )
            }
        }

        // Tab-specific sub-row
        when (state.currentTab) {
            SeriesTab.SEARCH -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(OmniusSurface, RoundedCornerShape(8.dp)),
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = state.query,
                        onValueChange = { viewModel.updateQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { viewModel.submitQuery() },
                        ),
                        decorationBox = { innerTextField ->
                            if (state.query.isEmpty()) {
                                Text("Search TV series (EZTV)…", color = Color(0xFF666666), fontSize = 14.sp)
                            }
                            innerTextField()
                        },
                    )
                }
            }
            SeriesTab.CATEGORIES -> {
                TvLazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    items(SERIES_GENRES) { genre ->
                        FilterChip(
                            label = genre,
                            selected = state.selectedGenre == genre,
                            onClick = { viewModel.selectGenre(genre) },
                        )
                    }
                }
            }
            else -> Spacer(Modifier.height(8.dp))
        }

        // Series grid
        if (state.error != null && state.series.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Error", color = OmniusRed, fontSize = 14.sp)
            }
        } else if (state.currentTab == SeriesTab.SEARCH && state.query.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Type to search EZTV", color = Color(0xFF666666), fontSize = 14.sp)
            }
        } else if (state.isLoading && state.series.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading...", color = Color.White)
            }
        } else if (state.series.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results", color = Color(0xFF888888), fontSize = 14.sp)
            }
        } else {
            TvLazyVerticalGrid(
                columns = TvGridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.series, key = { it.id }) { show ->
                    ContentCard(
                        title = show.title,
                        imageUrl = show.posterImage,
                        rating = show.rating,
                        year = show.year,
                        onClick = { onSeriesClick(show.id) },
                        onLongClick = {
                            favDialogSeries = FavSeries(show.id, show.title, show.posterImage, show.year, show.rating)
                        },
                        isFavorite = FavoritesManager.isSeriesFav(show.id),
                    )
                }

                if (state.hasMore && !state.isLoading) {
                    item {
                        LaunchedEffect(Unit) { viewModel.loadMore() }
                    }
                }
            }
        }
    }
}
