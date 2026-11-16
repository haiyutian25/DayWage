package com.lhzkml.DayWage.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.lhzkml.DayWage.ui.theme.LocalAppColors

/**
 * 骨架块：一条从左扫到右的高光，代替「数据还没到 → 显示 0」的那一帧。
 * 尺寸交给调用方的 modifier（宽高、weight 都行）。
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    /** 动画相位偏移，让同一屏的多个块错开一点，不显得齐刷刷。 */
    phase: Float = 0f,
) {
    val c = LocalAppColors.current
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    val base = if (c.isDark) Color.White.copy(alpha = 0.055f) else Color.Black.copy(alpha = 0.045f)
    val highlight = if (c.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.085f)

    Box(
        modifier
            .clip(shape)
            .drawBehind {
                val w = size.width.coerceAtLeast(1f)
                val sweep = ((progress + phase) % 1f) * (w * 2f) - w
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = Offset(sweep, 0f),
                        end = Offset(sweep + w * 0.7f, 0f),
                    ),
                )
            },
    )
}
