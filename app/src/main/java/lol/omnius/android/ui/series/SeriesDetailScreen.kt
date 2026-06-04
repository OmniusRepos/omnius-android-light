package lol.omnius.android.ui.series

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lol.omnius.android.api.ApiClient
import lol.omnius.android.data.model.Episode
import lol.omnius.android.data.model.Series
import lol.omnius.android.data.FavSeries
import lol.omnius.android.data.FavoritesManager
import lol.omnius.android.data.WatchHistoryManager
import lol.omnius.android.torrent.TorrentStreamManager
import lol.omnius.android.torrent.TorrentStreamState
import lol.omnius.android.ui.theme.*

class SeriesDetailViewModel : ViewModel() {
    private val _series = MutableStateFlow<Series?>(null)
    val series = _series.asStateFlow()
    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes = _episodes.asStateFlow()
    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason = _selectedSeason.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // Torrent streaming state
    private val _torrentState = MutableStateFlow(TorrentStreamState())
    val torrentState = _torrentState.asStateFlow()

    init {
        viewModelScope.launch {
            TorrentStreamManager.state.collect { state ->
                _torrentState.value = state
            }
        }
    }

    fun load(seriesId: Int) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val api = ApiClient.getApi()
                val response = api.getSeriesDetails(seriesId)
                val show = response.data?.series
                _series.value = show
                val eps = response.data?.episodes
                if (!eps.isNullOrEmpty()) {
                    _episodes.value = eps
                } else if (show != null && show.totalSeasons > 0) {
                    loadSeason(seriesId, 1)
                }

                // Auto-refresh in background to get latest episodes + torrents
                launch {
                    try {
                        api.refreshSeries(mapOf("series_id" to seriesId))
                        // Reload to get updated data
                        val refreshed = api.getSeriesDetails(seriesId)
                        val updatedShow = refreshed.data?.series
                        if (updatedShow != null) _series.value = updatedShow
                        val updatedEps = refreshed.data?.episodes
                        if (!updatedEps.isNullOrEmpty()) _episodes.value = updatedEps
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }

    fun selectSeason(seriesId: Int, season: Int) {
        _selectedSeason.value = season
        loadSeason(seriesId, season)
    }

    private fun loadSeason(seriesId: Int, season: Int) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getApi().getSeasonEpisodes(seriesId, season)
                _episodes.value = response.data ?: emptyList()
            } catch (_: Exception) {
                _episodes.value = emptyList()
            }
        }
    }

    fun startEpisodeStream(hash: String, fileIndex: Int?, title: String) {
        TorrentStreamManager.startStream(hash, title, fileIndex)
    }

    override fun onCleared() {
        super.onCleared()
        if (_torrentState.value.isStreaming) {
            TorrentStreamManager.stopStream()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesId: Int,
    onBack: () -> Unit,
    onPlay: (title: String, streamUrl: String, imdbCode: String, isTorrent: Boolean, contentId: Int, contentImage: String, seriesId: Int, seriesTitle: String, seasonNumber: Int, episodeNumber: Int, torrentHash: String, fileIndex: Int) -> Unit,
    viewModel: SeriesDetailViewModel = viewModel(),
) {
    val show by viewModel.series.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val torrentState by viewModel.torrentState.collectAsState()
    val favSeries by FavoritesManager.series.collectAsState()
    val watchHistory by WatchHistoryManager.history.collectAsState()

    LaunchedEffect(seriesId) { viewModel.load(seriesId) }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading...", color = Color.White)
        }
        return
    }

    val s = show ?: return

    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // Backdrop
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
            ) {
                AsyncImage(
                    model = s.backgroundImage,
                    contentDescription = s.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, OmniusDark.copy(alpha = 0.8f), OmniusDark),
                            )
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp),
                ) {
                    Text(s.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${s.year}${s.endYear?.let { " - $it" } ?: ""} • ${s.totalSeasons} Seasons • ${s.status}",
                        color = OmniusTextSecondary, fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(s.genres.joinToString(", "), color = OmniusGold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(s.summary, color = Color(0xFFBBBBBB), fontSize = 13.sp, maxLines = 3)
                    Spacer(Modifier.height(12.dp))

                    // Favorite button
                    val isFav = FavoritesManager.isSeriesFav(s.id)
                    var favFocused by remember { mutableStateOf(false) }
                    val favShape = RoundedCornerShape(8.dp)
                    Surface(
                        onClick = {
                            FavoritesManager.toggleSeries(
                                FavSeries(s.id, s.title, s.posterImage, s.year, s.rating)
                            )
                        },
                        modifier = Modifier
                            .onFocusChanged { favFocused = it.isFocused }
                            .then(
                                if (favFocused) Modifier.border(BorderStroke(2.dp, OmniusRed), favShape)
                                else Modifier
                            ),
                        shape = ClickableSurfaceDefaults.shape(shape = favShape),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isFav) OmniusRed else OmniusSurface,
                            focusedContainerColor = if (isFav) OmniusRed.copy(alpha = 0.8f) else Color(0xFF333333),
                        ),
                    ) {
                        Text(
                            text = if (isFav) "\u2665 Listed" else "\u2661 My List",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }

        // Season tabs
        item {
            TvLazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 12.dp),
            ) {
                items((1..s.totalSeasons).toList()) { season ->
                    var seasonFocused by remember { mutableStateOf(false) }
                    val seasonShape = RoundedCornerShape(8.dp)
                    Surface(
                        onClick = { viewModel.selectSeason(seriesId, season) },
                        modifier = Modifier
                            .onFocusChanged { seasonFocused = it.isFocused }
                            .then(
                                if (seasonFocused) Modifier.border(BorderStroke(2.dp, OmniusRed), seasonShape)
                                else Modifier
                            ),
                        shape = ClickableSurfaceDefaults.shape(shape = seasonShape),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (season == selectedSeason) OmniusRed else OmniusSurface,
                        ),
                    ) {
                        Text(
                            "Season $season",
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // Episodes
        items(episodes, key = { it.id }) { episode ->
            val watchEntry = watchHistory.find { it.contentId == episode.id && it.contentType == "episode" }
            EpisodeRow(
                episode = episode,
                isStreaming = torrentState.isStreaming,
                isWatched = watchEntry?.isWatched == true,
                progressPercent = watchEntry?.progressPercent ?: 0f,
                onPlay = { hash, fileIndex ->
                    if (!torrentState.isStreaming) {
                        val title = "${s.title} S${episode.seasonNumber}E${episode.episodeNumber}"
                        viewModel.startEpisodeStream(hash, fileIndex, title)
                        onPlay(
                            title, "", s.imdbCode, true,
                            episode.id, episode.stillImage ?: s.posterImage,
                            s.id, s.title, episode.seasonNumber, episode.episodeNumber,
                            hash, fileIndex ?: -1,
                        )
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EpisodeRow(
    episode: Episode,
    isStreaming: Boolean,
    isWatched: Boolean = false,
    progressPercent: Float = 0f,
    onPlay: (hash: String, fileIndex: Int?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .background(OmniusSurface, RoundedCornerShape(8.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (episode.stillImage != null) {
                Box {
                    AsyncImage(
                        model = episode.stillImage,
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(120.dp)
                            .height(68.dp),
                    )
                    if (progressPercent > 0f && !isWatched) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(3.dp)
                                .align(Alignment.BottomStart)
                                .background(Color(0xFF333333)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressPercent)
                                    .background(OmniusRed),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "E${episode.episodeNumber} - ${episode.title}",
                        color = if (isWatched) OmniusTextSecondary else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isWatched) {
                        Spacer(Modifier.width(8.dp))
                        Text("\u2713", color = OmniusGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (episode.summary != null) {
                    Text(episode.summary, color = OmniusTextSecondary, fontSize = 12.sp, maxLines = 2)
                }
            }
        }

        // Quality torrent buttons
        val torrents = episode.torrents
        if (!torrents.isNullOrEmpty()) {
            Spacer(Modifier.height(10.dp))
            // One button per quality (best-seeded wins), ordered by resolution.
            // FlowRow wraps to new rows so options never run off-screen.
            val qualityRank = { q: String ->
                when {
                    q.contains("2160") -> 4
                    q.contains("1080") -> 3
                    q.contains("720") -> 2
                    q.contains("480") -> 1
                    else -> 0
                }
            }
            val uniqueTorrents = torrents
                .sortedByDescending { it.seeds }
                .distinctBy { it.quality.lowercase() }
                .sortedByDescending { qualityRank(it.quality) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uniqueTorrents.forEach { torrent ->
                    var btnFocused by remember { mutableStateOf(false) }
                    val btnShape = RoundedCornerShape(6.dp)
                    Surface(
                        onClick = {
                            if (!isStreaming) onPlay(torrent.hash, torrent.fileIndex)
                        },
                        modifier = Modifier
                            .onFocusChanged { btnFocused = it.isFocused }
                            .then(
                                if (btnFocused) Modifier.border(BorderStroke(2.dp, OmniusRed), btnShape)
                                else Modifier
                            ),
                        shape = ClickableSurfaceDefaults.shape(shape = btnShape),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = OmniusCard,
                            focusedContainerColor = OmniusRed,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "\u25B6  ${torrent.quality}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = "${torrent.size} \u2022 ${torrent.seeds} seeds",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

