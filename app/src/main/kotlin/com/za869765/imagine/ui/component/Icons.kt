package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Maps the Material Symbols names used by the JSX prototype to Compose ImageVectors.
// "fill" 1 → filled glyph, 0 → outlined.
fun materialSymbolToVector(name: String, fill: Int = 0): ImageVector = when (name to (fill == 1)) {
    "settings" to false -> Icons.Outlined.Settings
    "settings" to true -> Icons.Filled.Settings
    "arrow_back" to false -> Icons.AutoMirrored.Outlined.ArrowBack
    "arrow_back" to true -> Icons.AutoMirrored.Outlined.ArrowBack
    "add" to false -> Icons.Outlined.Add
    "add" to true -> Icons.Filled.Add
    "add_photo_alternate" to false -> Icons.Outlined.AddPhotoAlternate
    "add_photo_alternate" to true -> Icons.Outlined.AddPhotoAlternate
    "close" to false -> Icons.Outlined.Close
    "close" to true -> Icons.Outlined.Close
    "auto_awesome" to false -> Icons.Outlined.AutoAwesome
    "auto_awesome" to true -> Icons.Filled.AutoAwesome
    "edit" to false -> Icons.Outlined.Edit
    "edit" to true -> Icons.Filled.Edit
    "history" to false -> Icons.Outlined.History
    "history" to true -> Icons.Filled.History
    "check" to false -> Icons.Outlined.Check
    "check" to true -> Icons.Filled.Check
    "expand_more" to false -> Icons.Outlined.ExpandMore
    "expand_more" to true -> Icons.Outlined.ExpandMore
    "expand_less" to false -> Icons.Outlined.ExpandLess
    "expand_less" to true -> Icons.Outlined.ExpandLess
    "play_arrow" to false -> Icons.Outlined.PlayArrow
    "play_arrow" to true -> Icons.Filled.PlayArrow
    "fingerprint" to false -> Icons.Outlined.Fingerprint
    "fingerprint" to true -> Icons.Outlined.Fingerprint
    "lock" to false -> Icons.Outlined.Lock
    "lock" to true -> Icons.Outlined.Lock
    "key" to false -> Icons.Outlined.VpnKey
    "key" to true -> Icons.Outlined.VpnKey
    "image" to false -> Icons.Outlined.Image
    "image" to true -> Icons.Outlined.Image
    "lightbulb" to false -> Icons.Outlined.Lightbulb
    "lightbulb" to true -> Icons.Filled.Lightbulb
    "movie" to false -> Icons.Outlined.Movie
    "movie" to true -> Icons.Outlined.Movie
    "download" to false -> Icons.Outlined.Download
    "download" to true -> Icons.Outlined.Download
    "share" to false -> Icons.Outlined.Share
    "share" to true -> Icons.Outlined.Share
    "content_copy" to false -> Icons.Outlined.ContentCopy
    "content_copy" to true -> Icons.Outlined.ContentCopy
    "content_paste" to false -> Icons.Outlined.ContentPaste
    "content_paste" to true -> Icons.Outlined.ContentPaste
    "visibility" to false -> Icons.Outlined.Visibility
    "visibility" to true -> Icons.Outlined.Visibility
    "warning" to false -> Icons.Outlined.Warning
    "warning" to true -> Icons.Filled.Warning
    "refresh" to false -> Icons.Outlined.Refresh
    "refresh" to true -> Icons.Filled.Refresh
    "search" to false -> Icons.Outlined.Search
    "search" to true -> Icons.Outlined.Search
    "language" to false -> Icons.Outlined.Language
    "language" to true -> Icons.Outlined.Language
    "star" to false -> Icons.Outlined.StarBorder
    "star" to true -> Icons.Filled.Star
    else -> Icons.Outlined.AutoAwesome
}

@Composable
fun ImagineIcon(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    fill: Int = 0,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Icon(
        imageVector = materialSymbolToVector(name, fill),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

@Composable
fun ImagineIconButton(
    name: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    fill: Int = 0,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(40.dp)
            .background(Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ImagineIcon(name = name, size = size, fill = fill, tint = tint)
    }
}
