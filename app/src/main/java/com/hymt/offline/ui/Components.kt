package com.hymt.offline.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hymt.offline.UiState
import com.hymt.offline.data.Languages

val PanelShape = RoundedCornerShape(22.dp)
val ChipShape = RoundedCornerShape(14.dp)

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(PanelShape)
            .background(Brush.verticalGradient(listOf(PanelTop, Panel)))
            .border(1.dp, PanelStroke, PanelShape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SectionLabel(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = TextDim
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(
                text = trailing,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = TextFaint
            )
        }
    }
}

@Composable
fun StatusDot(active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(if (active) Mint else TextFaint)
    )
}

/** 轻量胶囊按钮：图标 + 文案 */
@Composable
fun ActionChip(
    icon: ImageVector,
    text: String,
    enabled: Boolean = true,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> TextFaint
        accent -> Mint
        else -> TextDim
    }
    Row(
        modifier = Modifier
            .clip(ChipShape)
            .background(if (accent) Mint.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, if (accent && enabled) Mint.copy(alpha = 0.35f) else PanelStroke, ChipShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = text, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = text, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** 主操作按钮：大面积渐变 + 外发光 */
@Composable
fun PrimaryAction(
    text: String,
    enabled: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val brush = if (enabled) {
        Brush.horizontalGradient(listOf(Mint, Color(0xFF3FC7AE)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF1B2A32), Color(0xFF16222A)))
    }
    Box(
        modifier = modifier
            .shadow(
                elevation = if (enabled) 22.dp else 0.dp,
                shape = shape,
                ambientColor = Mint.copy(alpha = 0.45f),
                spotColor = Mint.copy(alpha = 0.45f)
            )
            .clip(shape)
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                color = if (enabled) Color(0xFF032018) else TextFaint,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            if (busy) {
                Spacer(Modifier.width(10.dp))
                BusyDots()
            }
        }
    }
}

/** 三个呼吸的点，表示正在推理 */
@Composable
fun BusyDots() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "busy")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(
                        durationMillis = 700,
                        delayMillis = index * 140
                    ),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF032018).copy(alpha = alpha))
            )
        }
    }
}

/** 细长的进度条，用于模型下载 / 导入 */
@Composable
fun ThinProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(Color(0xFF1A242C))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Mint, Amber)))
        )
    }
}

/** 顶部品牌栏，右侧状态胶囊可进入设置 */
@Composable
fun AppHeader(state: UiState, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GridMark(24)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "ZRC",
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.5.sp
            )
            Text(
                text = "离线翻译 · Hy-MT3",
                color = TextFaint,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(ChipShape)
                .background(Panel)
                .border(1.dp, PanelStroke, ChipShape)
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(active = !state.translating)
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (state.translating) "推理中" else "已就绪",
                color = TextDim,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.Settings,
                contentDescription = "设置",
                tint = TextFaint,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

/** 原文 / 译文语言选择条 */
@Composable
fun LanguageSelectorRow(
    state: UiState,
    onSwap: () -> Unit,
    onPickSource: () -> Unit,
    onPickTarget: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LangButton(
            label = "原文",
            value = Languages.displayName(state.sourceLang),
            modifier = Modifier.weight(1f),
            onClick = onPickSource
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Panel)
                .border(1.dp, PanelStroke, CircleShape)
                .clickable(onClick = onSwap),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.SwapHoriz,
                contentDescription = "对调语言",
                tint = Mint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        LangButton(
            label = "译文",
            value = Languages.displayName(state.targetLang),
            modifier = Modifier.weight(1f),
            onClick = onPickTarget
        )
    }
}

@Composable
fun LangButton(
    label: String,
    value: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(Panel)
            .border(1.dp, PanelStroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text = label, color = TextFaint, fontSize = 10.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                color = TextMain,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = TextFaint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
