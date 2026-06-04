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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import lol.omnius.android.ui.theme.OmniusCard
import lol.omnius.android.ui.theme.OmniusGold
import lol.omnius.android.ui.theme.OmniusRed

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContentCard(
    title: String,
    imageUrl: String?,
    rating: Double? = null,
    year: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
    aspectRatio: Float = 2f / 3f,
    onLongClick: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    badge: String? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .width(width)
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
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                // Favorite heart indicator — top right
                if (isFavorite) {
                    Text(
                        text = "\u2665",
                        color = OmniusRed,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    )
                }

                // Badge (e.g. "IMDB" for external results)
                if (badge != null) {
                    Text(
                        text = badge,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color(0xCCF59E0B), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                // Bottom gradient for rating badge readability
                if (rating != null && rating > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                )
                            ),
                    )
                }

                // Rating badge — bottom left
                if (rating != null && rating > 0) {
                    Row(
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.BottomStart),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("★", color = OmniusGold, fontSize = 11.sp)
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (year != null && year > 0) {
                    Text(
                        text = year.toString(),
                        color = Color(0xFF888888),
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}
