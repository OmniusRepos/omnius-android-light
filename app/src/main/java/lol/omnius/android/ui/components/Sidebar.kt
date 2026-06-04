package lol.omnius.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lol.omnius.android.R
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import lol.omnius.android.ui.navigation.SidebarItem
import lol.omnius.android.ui.theme.OmniusDark
import lol.omnius.android.ui.theme.OmniusRed
import lol.omnius.android.ui.theme.OmniusTextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Sidebar(
    currentRoute: String?,
    expanded: Boolean,
    onItemClick: (SidebarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val width = if (expanded) 200.dp else 56.dp

    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(OmniusDark)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Logo — matching desktop SVG
        if (expanded) {
            Image(
                painter = painterResource(R.drawable.omnius_logo_full),
                contentDescription = "Omnius",
                modifier = Modifier
                    .padding(bottom = 32.dp, start = 16.dp)
                    .height(18.dp)
                    .width(91.dp),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.omnius_logo_o),
                contentDescription = "Omnius",
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .size(24.dp),
            )
        }

        SidebarItem.entries.forEach { item ->
            val isActive = currentRoute == item.route ||
                (currentRoute != null && currentRoute.startsWith(item.route + "/"))

            SidebarNavItem(
                item = item,
                isActive = isActive,
                expanded = expanded,
                onClick = { onItemClick(item) },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SidebarNavItem(
    item: SidebarItem,
    isActive: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = when {
        isActive -> OmniusRed.copy(alpha = 0.15f)
        isFocused -> Color.White.copy(alpha = 0.1f)
        else -> Color.Transparent
    }
    val textColor = when {
        isActive -> OmniusRed
        isFocused -> Color.White
        else -> OmniusTextSecondary
    }

    val navShape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused) Modifier.border(BorderStroke(2.dp, OmniusRed), navShape)
                else Modifier
            ),
        shape = ClickableSurfaceDefaults.shape(shape = navShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bgColor,
            focusedContainerColor = Color.White.copy(alpha = 0.15f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon: ImageVector = when (item) {
                SidebarItem.HOME -> Icons.Filled.Home
                SidebarItem.MOVIES -> Icons.Rounded.Movie
                SidebarItem.SERIES -> Icons.Rounded.Tv
                SidebarItem.LIVE -> Icons.Rounded.LiveTv
                SidebarItem.FAVORITES -> Icons.Filled.Favorite
                SidebarItem.SETTINGS -> Icons.Filled.Settings
            }
            val iconColor = when (item) {
                SidebarItem.LIVE -> if (isActive) OmniusRed else OmniusRed.copy(alpha = 0.6f)
                else -> textColor
            }
            Icon(
                imageVector = icon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
            if (expanded) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.label,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
