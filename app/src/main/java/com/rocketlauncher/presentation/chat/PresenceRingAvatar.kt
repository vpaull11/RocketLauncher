package com.rocketlauncher.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageScope
import com.rocketlauncher.data.realtime.UserPresenceStatus

fun UserPresenceStatus.avatarRingColor(): Color = when (this) {
    UserPresenceStatus.ONLINE -> Color(0xFF43A047)
    UserPresenceStatus.AWAY -> Color(0xFFFFC107)
    UserPresenceStatus.BUSY -> Color(0xFFE53935)
    UserPresenceStatus.OFFLINE -> Color(0xFF212121)
    UserPresenceStatus.UNKNOWN -> Color.Transparent
}

@Composable
fun PresenceRingAsyncImage(
    model: Any?,
    contentDescription: String?,
    size: Dp,
    presence: UserPresenceStatus,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val ringColor = presence.avatarRingColor()
    val ringW = if (ringColor != Color.Transparent) 2.dp else 0.dp
    val outer = if (onClick != null) modifier.size(size).clickable(onClick = onClick)
    else modifier.size(size)
    Box(modifier = outer, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(ringW, ringColor, CircleShape)
                .padding(ringW)
                .clip(CircleShape)
        ) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun PresenceRingSubcomposeAsyncImage(
    model: Any?,
    contentDescription: String?,
    size: Dp,
    presence: UserPresenceStatus,
    modifier: Modifier = Modifier,
    loading: @Composable SubcomposeAsyncImageScope.(AsyncImagePainter.State.Loading) -> Unit,
    error: @Composable SubcomposeAsyncImageScope.(AsyncImagePainter.State.Error) -> Unit,
) {
    val ringColor = presence.avatarRingColor()
    val ringW = if (ringColor != Color.Transparent) 2.dp else 0.dp
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(ringW, ringColor, CircleShape)
                .padding(ringW)
                .clip(CircleShape)
        ) {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                loading = loading,
                error = error,
                contentScale = ContentScale.Crop
            )
        }
    }
}
