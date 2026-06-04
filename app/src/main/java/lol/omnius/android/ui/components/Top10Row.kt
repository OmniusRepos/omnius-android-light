package lol.omnius.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.*
import coil.compose.AsyncImage
import lol.omnius.android.data.model.HomeMovieSlim
import lol.omnius.android.ui.theme.OmniusDark
import lol.omnius.android.ui.theme.OmniusGold
import lol.omnius.android.ui.theme.OmniusRed

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Top10Row(
    title: String,
    movies: List<HomeMovieSlim>,
    onMovieClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = movies.take(10)

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
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(items, key = { _, movie -> movie.id }) { index, movie ->
                Top10Card(
                    rank = index + 1,
                    movie = movie,
                    onClick = { onMovieClick(movie.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Top10Card(
    rank: Int,
    movie: HomeMovieSlim,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused) Modifier.border(BorderStroke(2.dp, OmniusRed), shape)
                else Modifier
            ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
        ) {
            // Large rank number — overlaps poster slightly
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .width(if (rank >= 10) 60.dp else 48.dp)
                    .height(180.dp),
            ) {
                // Stroke outline
                Text(
                    text = rank.toString(),
                    color = Color(0xFF555555),
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 96.sp,
                    modifier = Modifier.offset(x = 8.dp),
                    style = androidx.compose.ui.text.TextStyle(
                        drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 8f,
                        ),
                    ),
                )
                // Solid fill on top
                Text(
                    text = rank.toString(),
                    color = OmniusDark,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 96.sp,
                    modifier = Modifier.offset(x = 8.dp),
                )
            }

            // Poster
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(180.dp)
                    .clip(shape),
            ) {
                AsyncImage(
                    model = movie.mediumCoverImage,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                // Bottom gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            )
                        ),
                )

                // Title + rating overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                ) {
                    Text(
                        text = movie.title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (movie.rating > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("★", color = OmniusGold, fontSize = 10.sp)
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = String.format("%.1f", movie.rating),
                                color = OmniusGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
