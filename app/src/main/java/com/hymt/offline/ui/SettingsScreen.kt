package com.hymt.offline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hymt.offline.ModelStatus
import com.hymt.offline.UiState
import com.hymt.offline.data.HistoryItem

@Composable
fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onUseHistory: (HistoryItem) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteModel: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg, BgGlow, Bg)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextDim,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("设置", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        ) {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "自动朗读译文",
                            color = TextMain,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "译文生成后自动用目标语言读出来",
                            color = TextFaint,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = state.autoSpeak,
                        onCheckedChange = { onToggleAutoSpeak() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF032018),
                            checkedTrackColor = Mint,
                            uncheckedThumbColor = TextDim,
                            uncheckedTrackColor = PanelTop
                        )
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "如果目标语言读不出来，说明当前语音引擎缺少该语言语音包。到系统「文字转语音」里把首选引擎换成 RHVoice 这类支持多语种的引擎即可。",
                    color = TextFaint,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            SettingsCard {
                Text(
                    text = "模型",
                    color = TextDim,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Hy-MT3-1.8B · Q4_K_M（1.35 GB）",
                    color = TextMain,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (state.modelStatus) {
                        is ModelStatus.Ready -> "已加载，可离线翻译"
                        is ModelStatus.Loading -> "正在加载权重…"
                        is ModelStatus.Missing -> "未安装"
                        is ModelStatus.Downloading -> "下载中"
                        is ModelStatus.Copying -> "导入中"
                        is ModelStatus.Failed -> "加载失败"
                    },
                    color = TextFaint,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "删除模型（会清空 1.35 GB 缓存）",
                    color = Danger,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(ChipShape)
                        .clickable(onClick = onDeleteModel)
                        .padding(vertical = 6.dp, horizontal = 2.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "翻译记录",
                        color = TextDim,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.weight(1f))
                    if (state.history.isNotEmpty()) {
                        Text(
                            text = "清空",
                            color = TextFaint,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(ChipShape)
                                .clickable(onClick = onClearHistory)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (state.history.isEmpty()) {
                    Text("暂无记录", color = TextFaint, fontSize = 12.sp)
                } else {
                    state.history.forEach { item ->
                        HistoryRow(item) { onUseHistory(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Brush.verticalGradient(listOf(PanelTop, Panel)))
            .padding(14.dp),
        content = content
    )
}

@Composable
private fun HistoryRow(item: HistoryItem, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ChipShape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Row {
            Text(
                text = item.source,
                color = TextDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = item.target,
                color = TextMain,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
