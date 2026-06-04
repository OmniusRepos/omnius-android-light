package lol.omnius.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import lol.omnius.android.data.model.HomeMovieSlim
import lol.omnius.android.data.model.Movie
import lol.omnius.android.data.model.Series

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieRow(
    title: String,
    movies: List<Movie>,
    onMovieClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onMovieLongClick: ((Movie) -> Unit)? = null,
    isMovieFavorite: ((Int) -> Boolean)? = null,
) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
        )

        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(movies, key = { it.id }) { movie ->
                ContentCard(
                    title = movie.title,
                    imageUrl = movie.mediumCoverImage,
                    rating = movie.rating,
                    year = movie.year,
                    onClick = { onMovieClick(movie.id) },
                    onLongClick = onMovieLongClick?.let { { it(movie) } },
                    isFavorite = isMovieFavorite?.invoke(movie.id) == true,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SlimMovieRow(
    title: String,
    movies: List<HomeMovieSlim>,
    onMovieClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onMovieLongClick: ((HomeMovieSlim) -> Unit)? = null,
    isMovieFavorite: ((Int) -> Boolean)? = null,
) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
        )

        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(movies, key = { it.id }) { movie ->
                ContentCard(
                    title = movie.title,
                    imageUrl = movie.mediumCoverImage,
                    rating = movie.rating,
                    year = movie.year,
                    onClick = { onMovieClick(movie.id) },
                    onLongClick = onMovieLongClick?.let { { it(movie) } },
                    isFavorite = isMovieFavorite?.invoke(movie.id) == true,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesRow(
    title: String,
    series: List<Series>,
    onSeriesClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onSeriesLongClick: ((Series) -> Unit)? = null,
    isSeriesFavorite: ((Int) -> Boolean)? = null,
) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
        )

        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(series, key = { it.id }) { show ->
                ContentCard(
                    title = show.title,
                    imageUrl = show.posterImage,
                    rating = show.rating,
                    year = show.year,
                    onClick = { onSeriesClick(show.id) },
                    onLongClick = onSeriesLongClick?.let { { it(show) } },
                    isFavorite = isSeriesFavorite?.invoke(show.id) == true,
                )
            }
        }
    }
}
