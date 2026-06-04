package lol.omnius.android.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.draw.clip
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.*
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lol.omnius.android.data.FavMovie
import lol.omnius.android.data.FavSeries
import lol.omnius.android.data.FavoritesManager
import lol.omnius.android.data.WatchEntry
import lol.omnius.android.data.WatchHistoryManager
import lol.omnius.android.data.model.HomeMovieHero
import lol.omnius.android.ui.components.FavoriteDialog
import lol.omnius.android.ui.components.SeriesRow
import lol.omnius.android.ui.components.SlimMovieRow
import lol.omnius.android.ui.components.Top10Row
import lol.omnius.android.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val favMovies by FavoritesManager.movies.collectAsState()
    val favSeries by FavoritesManager.series.collectAsState()
    val continueWatching by WatchHistoryManager.continueWatching.collectAsState()

    // Dialog state for long-press favorite
    var favDialogMovie by remember { mutableStateOf<FavMovie?>(null) }
    var favDialogSeries by remember { mutableStateOf<FavSeries?>(null) }

    favDialogMovie?.let { movie ->
        FavoriteDialog(
            title = movie.title,
            isFavorite = FavoritesManager.isMovieFav(movie.id),
            onToggle = { FavoritesManager.toggleMovie(movie) },
            onDismiss = { favDialogMovie = null },
        )
    }
    favDialogSeries?.let { series ->
        FavoriteDialog(
            title = series.title,
            isFavorite = FavoritesManager.isSeriesFav(series.id),
            onToggle = { FavoritesManager.toggleSeries(series) },
            onDismiss = { favDialogSeries = null },
        )
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading...", color = Color.White, fontSize = 16.sp)
        }
        return
    }

    if (state.error != null && state.heroMovies.isEmpty() && state.trendingMovies.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error ?: "Error", color = OmniusRed, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                var retryFocused by remember { mutableStateOf(false) }
                val retryShape = RoundedCornerShape(8.dp)
                Surface(
                    onClick = { viewModel.retry() },
                    modifier = Modifier
                        .onFocusChanged { retryFocused = it.isFocused }
                        .then(
                            if (retryFocused) Modifier.border(BorderStroke(2.dp, Color.White), retryShape)
                            else Modifier
                        ),
                    colors = ClickableSurfaceDefaults.colors(containerColor = OmniusRed),
                    shape = ClickableSurfaceDefaults.shape(shape = retryShape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                ) {
                    Text(
                        "Retry",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
        }
        return
    }

    val listState = rememberTvLazyListState()
    val coroutineScope = rememberCoroutineScope()

    TvLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // Hero slider — scroll to top when hero gets focus
        if (state.heroMovies.isNotEmpty()) {
            item {
                HeroSlider(
                    movies = state.heroMovies,
                    onMovieClick = onMovieClick,
                    modifier = Modifier.onFocusChanged { focusState ->
                        if (focusState.hasFocus) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        }
                    },
                )
            }
        }

        // Continue Watching row
        if (continueWatching.isNotEmpty()) {
            item(key = "continue-watching") {
                ContinueWatchingRow(
                    entries = continueWatching,
                    onEntryClick = { entry ->
                        if (entry.contentType == "movie") {
                            onMovieClick(entry.contentId)
                        } else if (entry.contentType == "episode") {
                            val sId = entry.seriesId
                            if (sId != null && sId > 0) {
                                onSeriesClick(sId)
                            }
                        }
                    },
                )
            }
        }

        // API-provided sections
        state.sections.forEach { section ->
            val movies = section.movies ?: emptyList()
            val series = section.series ?: emptyList()

            if (movies.isNotEmpty()) {
                item(key = "section-${section.id ?: section.title}") {
                    if (section.displayType == "top10") {
                        Top10Row(
                            title = section.title,
                            movies = movies,
                            onMovieClick = onMovieClick,
                        )
                    } else {
                        SlimMovieRow(
                            title = section.title,
                            movies = movies,
                            onMovieClick = onMovieClick,
                            onMovieLongClick = { m ->
                                favDialogMovie = FavMovie(m.id, m.title, m.mediumCoverImage, m.year, m.rating)
                            },
                            isMovieFavorite = { FavoritesManager.isMovieFav(it) },
                        )
                    }
                }
            }
            if (series.isNotEmpty()) {
                item(key = "section-series-${section.id ?: section.title}") {
                    SeriesRow(
                        title = section.title,
                        series = series,
                        onSeriesClick = onSeriesClick,
                        onSeriesLongClick = { s ->
                            favDialogSeries = FavSeries(s.id, s.title, s.posterImage, s.year, s.rating)
                        },
                        isSeriesFavorite = { FavoritesManager.isSeriesFav(it) },
                    )
                }
            }
        }

        // Fallback rows (always show if data available, supplements API sections)
        if (state.trendingMovies.isNotEmpty()) {
            item(key = "trending") {
                SlimMovieRow(
                    title = "Trending Now",
                    movies = state.trendingMovies,
                    onMovieClick = onMovieClick,
                    onMovieLongClick = { m ->
                        favDialogMovie = FavMovie(m.id, m.title, m.mediumCoverImage, m.year, m.rating)
                    },
                    isMovieFavorite = { FavoritesManager.isMovieFav(it) },
                )
            }
        }

        if (state.latestMovies.isNotEmpty()) {
            item(key = "latest") {
                SlimMovieRow(
                    title = "Recently Added",
                    movies = state.latestMovies,
                    onMovieClick = onMovieClick,
                    onMovieLongClick = { m ->
                        favDialogMovie = FavMovie(m.id, m.title, m.mediumCoverImage, m.year, m.rating)
                    },
                    isMovieFavorite = { FavoritesManager.isMovieFav(it) },
                )
            }
        }

        if (state.topRatedMovies.isNotEmpty()) {
            item(key = "top-rated") {
                SlimMovieRow(
                    title = "Top Rated",
                    movies = state.topRatedMovies,
                    onMovieClick = onMovieClick,
                    onMovieLongClick = { m ->
                        favDialogMovie = FavMovie(m.id, m.title, m.mediumCoverImage, m.year, m.rating)
                    },
                    isMovieFavorite = { FavoritesManager.isMovieFav(it) },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroSlider(
    movies: List<HomeMovieHero>,
    onMovieClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Auto-rotate index
    var currentIndex by remember { mutableIntStateOf(0) }

    if (movies.size > 1) {
        LaunchedEffect(movies.size) {
            while (true) {
                delay(5000)
                currentIndex = (currentIndex + 1) % movies.size
            }
        }
    }

    val movie = movies.getOrNull(currentIndex) ?: movies.first()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp),
    ) {
        // Background image with crossfade
        Crossfade(
            targetState = currentIndex,
            animationSpec = tween(durationMillis = 800),
            label = "hero-crossfade",
        ) { index ->
            val m = movies.getOrNull(index) ?: movies.first()
            val bgImage = m.backgroundImageOriginal.ifEmpty {
                m.backgroundImage.ifEmpty {
                    m.largeCoverImage.ifEmpty { m.mediumCoverImage }
                }
            }
            AsyncImage(
                model = bgImage,
                contentDescription = m.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Gradient overlays — horizontal fade from left + vertical fade from bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            OmniusDark.copy(alpha = 0.95f),
                            OmniusDark.copy(alpha = 0.7f),
                            Color.Transparent,
                        ),
                        startX = 0f,
                        endX = 800f,
                    )
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            OmniusDark.copy(alpha = 0.6f),
                            Color.Transparent,
                            OmniusDark.copy(alpha = 0.8f),
                            OmniusDark,
                        ),
                        startY = 0f,
                    )
                ),
        )

        // Hero content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 28.dp, end = 32.dp)
                .widthIn(max = 500.dp),
        ) {
            // Badge
            Box(
                modifier = Modifier
                    .background(OmniusRed, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("MOVIE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))

            // Title
            Text(
                text = movie.title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 32.sp,
            )

            Spacer(Modifier.height(8.dp))

            // Meta: year, rating, genres
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = movie.year.toString(),
                    color = Color.White,
                    fontSize = 13.sp,
                )
                if (movie.rating > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("★", color = OmniusGold, fontSize = 13.sp)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = String.format("%.1f", movie.rating),
                            color = OmniusGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (movie.genres.isNotEmpty()) {
                    Text(
                        text = movie.genres.take(2).joinToString(", "),
                        color = Color(0xFFAAAAAA),
                        fontSize = 13.sp,
                    )
                }
                if (movie.runtime > 0) {
                    Text(
                        text = "${movie.runtime}min",
                        color = Color(0xFF888888),
                        fontSize = 13.sp,
                    )
                }
            }

            // Summary
            if (movie.summary.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = movie.summary,
                    color = Color(0xFFBBBBBB),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                )
            }

            Spacer(Modifier.height(14.dp))

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                var playFocused by remember { mutableStateOf(false) }
                val playShape = RoundedCornerShape(6.dp)
                Surface(
                    onClick = { onMovieClick(movie.id) },
                    modifier = Modifier
                        .onFocusChanged { playFocused = it.isFocused }
                        .then(
                            if (playFocused) Modifier.border(BorderStroke(2.dp, OmniusRed), playShape)
                            else Modifier
                        ),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.9f),
                    ),
                    shape = ClickableSurfaceDefaults.shape(shape = playShape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                ) {
                    Text(
                        "▶  Play",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                var infoFocused by remember { mutableStateOf(false) }
                val infoShape = RoundedCornerShape(6.dp)
                Surface(
                    onClick = { onMovieClick(movie.id) },
                    modifier = Modifier
                        .onFocusChanged { infoFocused = it.isFocused }
                        .then(
                            if (infoFocused) Modifier.border(BorderStroke(2.dp, OmniusRed), infoShape)
                            else Modifier
                        ),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF333333),
                        focusedContainerColor = Color(0xFF444444),
                    ),
                    shape = ClickableSurfaceDefaults.shape(shape = infoShape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                ) {
                    Text(
                        "More Info",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }

            // Dots indicator
            if (movies.size > 1) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    movies.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .size(if (index == currentIndex) 8.dp else 6.dp)
                                .background(
                                    if (index == currentIndex) OmniusRed else Color(0xFF555555),
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContinueWatchingRow(
    entries: List<WatchEntry>,
    onEntryClick: (WatchEntry) -> Unit,
) {
    Column(modifier = Modifier.padding(start = 32.dp, top = 16.dp)) {
        Text(
            text = "Continue Watching",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        TvLazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 32.dp),
        ) {
            items(entries, key = { "${it.contentType}-${it.contentId}" }) { entry ->
                ContinueWatchingCard(
                    entry = entry,
                    onClick = { onEntryClick(entry) },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContinueWatchingCard(
    entry: WatchEntry,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(180.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused) Modifier.border(BorderStroke(2.dp, OmniusRed), shape)
                else Modifier
            ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = OmniusCard,
            focusedContainerColor = OmniusCard,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
            ) {
                AsyncImage(
                    model = entry.image,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Bottom gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            )
                        ),
                )
                // Progress bar at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0xFF333333)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(entry.progressPercent)
                            .background(OmniusRed),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = entry.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val remaining = entry.durationMs - entry.positionMs
                val mins = (remaining / 60_000).coerceAtLeast(1)
                Text(
                    text = "${mins}m remaining",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                )
            }
        }
    }
}
