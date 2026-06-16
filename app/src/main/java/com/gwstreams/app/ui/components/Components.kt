package com.gwstreams.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gwstreams.app.ui.theme.*
import kotlin.math.abs

/** Geometric "play wave" mark — three rising bars morphing into a play triangle. */
@Composable
fun GwLogo(size: Int = 64) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(Brush.linearGradient(listOf(AquaDim, Aqua))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size((size * 0.5).dp)) {
            val w = this.size.width
            val h = this.size.height
            val barW = w / 7f
            val heights = listOf(0.45f, 0.8f, 0.6f)
            heights.forEachIndexed { i, frac ->
                val x = i * (barW * 2f) + barW / 2f
                drawLine(
                    color = Midnight,
                    start = Offset(x, h),
                    end = Offset(x, h * (1 - frac)),
                    strokeWidth = barW,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun LiveBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text("LIVE", style = MaterialTheme.typography.labelSmall, color = TextHi)
    }
}

/** Animated shimmer brush for skeleton placeholders (#2). */
@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -600f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    return Brush.linearGradient(
        colors = listOf(Surface1, SurfaceHi, Surface1),
        start = Offset(x, 0f),
        end = Offset(x + 300f, 300f)
    )
}

/** A single shimmering placeholder card matching the poster grid. */
@Composable
fun SkeletonPoster() {
    val brush = shimmerBrush()
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(14.dp))
                .background(brush)
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth(0.8f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** Colored tile with channel initials when no logo is available (#6). */
@Composable
fun InitialsTile(name: String, size: Int = 54) {
    val initials = name.trim().split(" ", "-", "_")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }
    // Deterministic hue from the name so a channel always gets the same color.
    val palette = listOf(
        Color(0xFF2DE2C4), Color(0xFF6C7CFF), Color(0xFFFF6B6B),
        Color(0xFFFFB454), Color(0xFFB084FF), Color(0xFF4EC8FF)
    )
    val color = palette[abs(name.hashCode()) % palette.size]
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.15f)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = (size * 0.34).sp,
            textAlign = TextAlign.Center
        )
    }
}

/** Modifier that gently scales a card down while pressed (#4). */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pressScale"
    )
    return this.scale(scale)
}
