package lol.omnius.android.ui.favorites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import coil.compose.AsyncImage
import lol.omnius.android.data.FavChannel
import lol.omnius.android.data.FavCountry
import lol.omnius.android.data.FavoritesManager
import lol.omnius.android.ui.components.ContentCard
import lol.omnius.android.ui.theme.OmniusCard
import lol.omnius.android.ui.theme.OmniusRed
import lol.omnius.android.ui.theme.OmniusSurface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    onCountryClick: (String) -> Unit,
    onChannelPlay: (FavChannel) -> Unit,
) {
    val movies by FavoritesManager.movies.collectAsState()
    val series by FavoritesManager.series.collectAsState()
    val channels by FavoritesManager.channels.collectAsState()
    val countries by FavoritesManager.countries.collectAsState()

    val isEmpty = movies.isEmpty() && series.isEmpty() && channels.isEmpty() && countries.isEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "My Favorites",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
        )

        if (isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "\u2665",
                        color = Color(0xFF333333),
                        fontSize = 48.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No favorites yet",
                        color = Color(0xFF666666),
                        fontSize = 16.sp,
                    )
                    Text(
                        text = "Long-press on any content to add it here",
                        color = Color(0xFF444444),
                        fontSize = 13.sp,
                    )
                }
            }
        } else {
            TvLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                // Movies section
                if (movies.isNotEmpty()) {
                    item(key = "fav-movies-header") {
                        SectionHeader("Movies (${movies.size})")
                    }
                    item(key = "fav-movies") {
                        TvLazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(movies, key = { it.id }) { movie ->
                                ContentCard(
                                    title = movie.title,
                                    imageUrl = movie.image,
                                    rating = movie.rating,
                                    year = movie.year,
                                    onClick = { onMovieClick(movie.id) },
                                    onLongClick = { FavoritesManager.toggleMovie(movie) },
                                    isFavorite = true,
                                )
                            }
                        }
                    }
                }

                // Series section
                if (series.isNotEmpty()) {
                    item(key = "fav-series-header") {
                        SectionHeader("Series (${series.size})")
                    }
                    item(key = "fav-series") {
                        TvLazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(series, key = { it.id }) { show ->
                                ContentCard(
                                    title = show.title,
                                    imageUrl = show.image,
                                    rating = show.rating,
                                    year = show.year,
                                    onClick = { onSeriesClick(show.id) },
                                    onLongClick = { FavoritesManager.toggleSeries(show) },
                                    isFavorite = true,
                                )
                            }
                        }
                    }
                }

                // Channels section
                if (channels.isNotEmpty()) {
                    item(key = "fav-channels-header") {
                        SectionHeader("Channels (${channels.size})")
                    }
                    items(channels, key = { "ch-${it.id}" }) { channel ->
                        FavChannelRow(
                            channel = channel,
                            onClick = { onChannelPlay(channel) },
                            onLongClick = { FavoritesManager.toggleChannel(channel) },
                        )
                    }
                }

                // Countries section
                if (countries.isNotEmpty()) {
                    item(key = "fav-countries-header") {
                        SectionHeader("Countries (${countries.size})")
                    }
                    item(key = "fav-countries") {
                        TvLazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(countries, key = { it.code }) { country ->
                                FavCountryCard(
                                    country = country,
                                    onClick = { onCountryClick(country.code) },
                                    onLongClick = { FavoritesManager.toggleCountry(country) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FavChannelRow(
    channel: FavChannel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) Modifier.border(BorderStroke(2.dp, OmniusRed), shape)
                else Modifier
            ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = OmniusSurface,
            focusedContainerColor = OmniusCard,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = channel.name.take(2).uppercase(),
                        color = Color(0xFF666666),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                channel.country?.let {
                    Text(
                        text = it.uppercase(),
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                    )
                }
            }

            Text("\u2665", color = OmniusRed, fontSize = 14.sp)

            if (channel.streamUrl != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "LIVE",
                    color = OmniusRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FavCountryCard(
    country: FavCountry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .width(180.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) Modifier.border(BorderStroke(2.dp, OmniusRed), shape)
                else Modifier
            ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = OmniusCard,
            focusedContainerColor = OmniusRed.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = country.flag ?: "",
                fontSize = 28.sp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(country.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Text("\u2665", color = OmniusRed, fontSize = 14.sp)
        }
    }
}
