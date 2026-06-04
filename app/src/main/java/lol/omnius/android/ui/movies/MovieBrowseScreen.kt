package lol.omnius.android.ui.movies

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import lol.omnius.android.data.FavMovie
import lol.omnius.android.data.FavoritesManager
import lol.omnius.android.ui.components.ContentCard
import lol.omnius.android.ui.components.FavoriteDialog
import lol.omnius.android.ui.theme.OmniusRed
import lol.omnius.android.ui.theme.OmniusSurface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieBrowseScreen(
    onMovieClick: (Int) -> Unit,
    viewModel: MovieBrowseViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val favMovies by FavoritesManager.movies.collectAsState()
    var favDialogMovie by remember { mutableStateOf<FavMovie?>(null) }

    favDialogMovie?.let { movie ->
        FavoriteDialog(
            title = movie.title,
            isFavorite = FavoritesManager.isMovieFav(movie.id),
            onToggle = { FavoritesManager.toggleMovie(movie) },
            onDismiss = { favDialogMovie = null },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Movies",
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
            items(MovieTab.entries.toList()) { tab ->
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
            MovieTab.SEARCH -> {
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
                                Text("Search movies (YTS)…", color = Color(0xFF666666), fontSize = 14.sp)
                            }
                            innerTextField()
                        },
                    )
                }
            }
            MovieTab.CATEGORIES -> {
                TvLazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    items(MOVIE_GENRES) { genre ->
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

        // Movie grid
        if (state.error != null && state.movies.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Error", color = OmniusRed, fontSize = 14.sp)
            }
        } else if (state.currentTab == MovieTab.SEARCH && state.query.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Type to search YTS", color = Color(0xFF666666), fontSize = 14.sp)
            }
        } else if (state.movies.isEmpty() && !state.isLoading) {
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
                items(state.movies, key = { it.id }) { movie ->
                    ContentCard(
                        title = movie.title,
                        imageUrl = movie.mediumCoverImage,
                        rating = movie.rating,
                        year = movie.year,
                        onClick = { onMovieClick(movie.id) },
                        onLongClick = {
                            favDialogMovie = FavMovie(movie.id, movie.title, movie.mediumCoverImage, movie.year, movie.rating)
                        },
                        isFavorite = FavoritesManager.isMovieFav(movie.id),
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color = OmniusRed,
) {
    var chipFocused by remember { mutableStateOf(false) }
    val chipShape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { chipFocused = it.isFocused }
            .then(
                if (chipFocused) Modifier.border(BorderStroke(2.dp, color), chipShape)
                else Modifier
            ),
        shape = ClickableSurfaceDefaults.shape(shape = chipShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) color else OmniusSurface,
            focusedContainerColor = if (selected) color else Color(0xFF333333),
        ),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
