package com.hymt.offline.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hymt.offline.UiState

/**
 * 对讲模式：一个巨大的语音按钮，点一下说话、再点一下结束，
 * 识别 → 翻译 → 自动朗读整条链路自动完成。
 */
@Composable
fun VoiceScreen(
    state: UiState,
    micGranted: Boolean,
    onMic: () -> Unit,
    onSwap: () -> Unit,
    onPickSource: () -> Unit,
    onPickTarget: () -> Unit,
    onSpeak: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg, BgGlow, Bg)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        AppHeader(state, onOpenSettings)
        Spacer(Modifier.height(12.dp))
        LanguageSelectorRow(state, onSwap, onPickSource, onPickTarget)
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BigMicButton(state.listening, onMic)
                Spacer(Modifier.height(22.dp))
                Text(
                    text = if (state.listening) "正在聆听，说完再点一次" else "点一下开始说话",
                    color = if (state.listening) Mint else TextDim,
                    fontSize = 14.sp
                )
                if (!micGranted) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "麦克风未授权 · 点此授权",
                        color = Amber,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(ChipShape)
                            .clickable(onClick = onMic)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                if (state.partial.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = state.partial,
                        color = Amber,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }

        Panel(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            SectionLabel("译文", state.stats)
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = state.output.ifBlank {
                        if (state.translating) "正在生成…" else "说完会自动翻译并朗读"
                    },
                    color = if (state.output.isBlank()) TextFaint else TextMain,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionChip(
                    icon = Icons.Filled.VolumeUp,
                    text = "再读一遍",
                    enabled = state.output.isNotEmpty(),
                    onClick = onSpeak
                )
                Spacer(Modifier.weight(1f))
                if (state.translating) BusyDots()
            }
        }
    }
}

@Composable
private fun BigMicButton(listening: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "mic-pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(animation = tween(1100), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )
    val scale = if (listening) pulse else 1f

    Box(contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier
                    .size(212.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Mint.copy(alpha = 0.10f))
            )
        }
        Box(
            Modifier
                .size(172.dp)
                .scale(scale)
                .shadow(
                    elevation = 26.dp,
                    shape = CircleShape,
                    ambientColor = Mint.copy(alpha = 0.45f),
                    spotColor = Mint.copy(alpha = 0.45f)
                )
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Mint, Color(0xFF3FC7AE))))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = "说话",
                tint = Color(0xFF032018),
                modifier = Modifier.size(68.dp)
            )
        }
    }
}
