package lol.omnius.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import lol.omnius.android.ui.theme.OmniusCard
import lol.omnius.android.ui.theme.OmniusRed

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoriteDialog(
    title: String,
    isFavorite: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isFavorite) "\u2665" else "\u2661",
                color = OmniusRed,
                fontSize = 36.sp,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )

            Spacer(Modifier.height(16.dp))

            var btnFocused by remember { mutableStateOf(false) }
            val btnShape = RoundedCornerShape(8.dp)
            Surface(
                onClick = {
                    onToggle()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { btnFocused = it.isFocused }
                    .then(
                        if (btnFocused) Modifier.border(BorderStroke(2.dp, Color.White), btnShape)
                        else Modifier
                    ),
                shape = ClickableSurfaceDefaults.shape(shape = btnShape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isFavorite) OmniusCard else OmniusRed,
                    focusedContainerColor = if (isFavorite) Color(0xFF333333) else OmniusRed.copy(alpha = 0.8f),
                ),
            ) {
                Text(
                    text = if (isFavorite) "Remove from My List" else "Add to My List",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally),
                )
            }
        }
    }
}
