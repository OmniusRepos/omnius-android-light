package lol.omnius.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val OmniusRed = Color(0xFFE50914)
val OmniusDark = Color(0xFF0A0A0A)
val OmniusSurface = Color(0xFF1A1A1A)
val OmniusCard = Color(0xFF222222)
val OmniusGold = Color(0xFFF39C12)
val OmniusGreen = Color(0xFF2ECC71)
val OmniusTextPrimary = Color(0xFFE0E0E0)
val OmniusTextSecondary = Color(0xFF888888)

@OptIn(ExperimentalTvMaterial3Api::class)
private val OmniusColorScheme = darkColorScheme(
    primary = OmniusRed,
    onPrimary = Color.White,
    secondary = OmniusGold,
    background = OmniusDark,
    surface = OmniusSurface,
    onBackground = OmniusTextPrimary,
    onSurface = OmniusTextPrimary,
    error = OmniusRed,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OmniusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OmniusColorScheme,
        content = content,
    )
}
